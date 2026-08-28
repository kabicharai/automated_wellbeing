package com.samsungmodes.poc.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.samsungmodes.poc.ble.model.BleDeviceId
import com.samsungmodes.poc.ble.model.BleDiscoveredDevice
import com.samsungmodes.poc.ble.model.BleRawAdvertisement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Robust Android BLE Scanner for Android 16 (API 36) down to API 29.
 * Includes Watchdog keep-alive, anti-throttling gentle scan refresh,
 * and automatic radio failure recovery.
 */
class BleScanner(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    enum class ScanModePreference(val scanModeInt: Int, val displayName: String) {
        BALANCED(ScanSettings.SCAN_MODE_BALANCED, "Balanced"),
        LOW_LATENCY(ScanSettings.SCAN_MODE_LOW_LATENCY, "High Reliability (Low Latency)"),
        LOW_POWER(ScanSettings.SCAN_MODE_LOW_POWER, "Battery Saver (Low Power)")
    }

    data class ScannerState(
        val isScanning: Boolean = false,
        val isBluetoothEnabled: Boolean = false,
        val hasPermissions: Boolean = false,
        val scanMode: ScanModePreference = ScanModePreference.BALANCED,
        val discoveredDevices: List<BleDiscoveredDevice> = emptyList(),
        val errorMessage: String? = null,
        val totalPacketsReceived: Long = 0L,
        val lastPacketTimeMillis: Long = 0L,
        val watchdogRestartsCount: Int = 0,
        val isWatchdogActive: Boolean = true,
        val autoRecoverEnabled: Boolean = true,
        val isBackgroundServiceRunning: Boolean = false
    )

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var leScanner: BluetoothLeScanner? = null

    private val _scannerState = MutableStateFlow(ScannerState())
    val scannerState: StateFlow<ScannerState> = _scannerState.asStateFlow()

    private val deviceMap = ConcurrentHashMap<String, BleDiscoveredDevice>()
    private var totalPackets: Long = 0L
    private var lastPacketTimestamp: Long = 0L
    private var watchdogJob: Job? = null
    private var isIntentReceiverRegistered = false

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_ON -> {
                        updateBluetoothAndPermissionStatus()
                        if (_scannerState.value.isScanning || _scannerState.value.autoRecoverEnabled) {
                            coroutineScope.launch(Dispatchers.Main) {
                                delay(1000)
                                startScan()
                            }
                        }
                    }
                    BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                        _scannerState.value = _scannerState.value.copy(
                            isBluetoothEnabled = false,
                            errorMessage = "Bluetooth was turned off"
                        )
                    }
                }
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { handleScanResult(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            val message = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed with BLE stack (Retrying...)"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE Scan feature unsupported on this device"
                SCAN_FAILED_INTERNAL_ERROR -> "Internal Bluetooth controller error"
                else -> "BLE scan failed with error code: $errorCode"
            }
            _scannerState.value = _scannerState.value.copy(
                isScanning = false,
                errorMessage = message
            )

            // Auto-recover from transient BLE stack registration errors
            if (_scannerState.value.autoRecoverEnabled && 
                (errorCode == SCAN_FAILED_APPLICATION_REGISTRATION_FAILED || errorCode == SCAN_FAILED_INTERNAL_ERROR)) {
                coroutineScope.launch(Dispatchers.Main) {
                    delay(2000)
                    if (_scannerState.value.autoRecoverEnabled) {
                        startScan()
                    }
                }
            }
        }
    }

    init {
        updateBluetoothAndPermissionStatus()
        registerBluetoothStateReceiver()
    }

    private fun registerBluetoothStateReceiver() {
        if (!isIntentReceiverRegistered) {
            try {
                val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
                context.registerReceiver(bluetoothStateReceiver, filter)
                isIntentReceiverRegistered = true
            } catch (_: Exception) {
            }
        }
    }

    fun updateBluetoothAndPermissionStatus() {
        val btEnabled = bluetoothAdapter?.isEnabled == true
        val hasPerms = checkPermissions()
        _scannerState.value = _scannerState.value.copy(
            isBluetoothEnabled = btEnabled,
            hasPermissions = hasPerms
        )
    }

    fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val connectGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            scanGranted && connectGranted
        } else {
            val locationGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            locationGranted
        }
    }

    fun setScanMode(mode: ScanModePreference) {
        _scannerState.value = _scannerState.value.copy(scanMode = mode)
        if (_scannerState.value.isScanning) {
            stopScan()
            startScan()
        }
    }

    fun setAutoRecoverEnabled(enabled: Boolean) {
        _scannerState.value = _scannerState.value.copy(autoRecoverEnabled = enabled)
        if (enabled && _scannerState.value.isScanning) {
            startWatchdog()
        } else if (!enabled) {
            stopWatchdog()
        }
    }

    fun setBackgroundServiceRunning(isRunning: Boolean) {
        _scannerState.value = _scannerState.value.copy(isBackgroundServiceRunning = isRunning)
    }

    fun clearDevices() {
        deviceMap.clear()
        totalPackets = 0L
        _scannerState.value = _scannerState.value.copy(
            discoveredDevices = emptyList(),
            totalPacketsReceived = 0L
        )
    }

    @SuppressLint("MissingPermission")
    fun startScan(): Boolean {
        updateBluetoothAndPermissionStatus()
        if (!_scannerState.value.hasPermissions) {
            _scannerState.value = _scannerState.value.copy(
                errorMessage = "Bluetooth scan permissions not granted."
            )
            return false
        }

        if (!_scannerState.value.isBluetoothEnabled || bluetoothAdapter == null) {
            _scannerState.value = _scannerState.value.copy(
                errorMessage = "Bluetooth is currently disabled on this device."
            )
            return false
        }

        leScanner = bluetoothAdapter.bluetoothLeScanner
        if (leScanner == null) {
            _scannerState.value = _scannerState.value.copy(
                errorMessage = "BluetoothLeScanner is unavailable."
            )
            return false
        }

        val settings = ScanSettings.Builder()
            .setScanMode(_scannerState.value.scanMode.scanModeInt)
            .setReportDelay(0)
            .build()

        val filters = mutableListOf<ScanFilter>()

        return try {
            leScanner?.startScan(filters, settings, scanCallback)
            _scannerState.value = _scannerState.value.copy(
                isScanning = true,
                errorMessage = null
            )
            startWatchdog()
            true
        } catch (e: Exception) {
            _scannerState.value = _scannerState.value.copy(
                isScanning = false,
                errorMessage = "Failed to start BLE scan: ${e.localizedMessage}"
            )
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        stopWatchdog()
        try {
            if (_scannerState.value.isScanning) {
                leScanner?.stopScan(scanCallback)
            }
        } catch (_: Exception) {
        } finally {
            _scannerState.value = _scannerState.value.copy(isScanning = false)
        }
    }

    /**
     * Intelligent Watchdog:
     * 1. Performs an anti-throttling scan refresh every 3 minutes (180s) to reset Android's
     *    10-minute continuous scan limit.
     * 2. Detects packet stagnation: if 0 packets received for > 20s while scanning, restarts scan.
     */
    private fun startWatchdog() {
        stopWatchdog()
        if (!_scannerState.value.autoRecoverEnabled) return

        _scannerState.value = _scannerState.value.copy(isWatchdogActive = true)

        watchdogJob = coroutineScope.launch(Dispatchers.Default) {
            var cycleTimerSeconds = 0
            while (isActive) {
                delay(5000L)
                cycleTimerSeconds += 5

                if (!_scannerState.value.isScanning) break

                val now = System.currentTimeMillis()
                val silenceDuration = if (lastPacketTimestamp > 0) now - lastPacketTimestamp else 0L

                // Condition 1: Anti-throttling refresh cycle every 3 minutes (180s)
                val isAntiThrottleCycleDue = cycleTimerSeconds >= 180

                // Condition 2: Silence stall detection (> 20s of total packet silence while scanning)
                val isSilenceStallDetected = lastPacketTimestamp > 0 && silenceDuration > 20000L

                if (isAntiThrottleCycleDue || isSilenceStallDetected) {
                    cycleTimerSeconds = 0
                    val restarts = _scannerState.value.watchdogRestartsCount + 1
                    _scannerState.value = _scannerState.value.copy(watchdogRestartsCount = restarts)

                    // Perform gentle non-blocking scan bounce
                    try {
                        leScanner?.stopScan(scanCallback)
                    } catch (_: Exception) {
                    }
                    delay(300L)
                    try {
                        val settings = ScanSettings.Builder()
                            .setScanMode(_scannerState.value.scanMode.scanModeInt)
                            .setReportDelay(0)
                            .build()
                        leScanner = bluetoothAdapter?.bluetoothLeScanner
                        leScanner?.startScan(emptyList(), settings, scanCallback)
                        lastPacketTimestamp = System.currentTimeMillis()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun stopWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
        _scannerState.value = _scannerState.value.copy(isWatchdogActive = false)
    }

    private fun handleScanResult(result: ScanResult) {
        val record = result.scanRecord
        val device = result.device
        val rssi = result.rssi
        val now = System.currentTimeMillis()

        totalPackets++
        lastPacketTimestamp = now

        // Extract manufacturer data
        val mfgMap = mutableMapOf<Int, ByteArray>()
        val mfgSparse = record?.manufacturerSpecificData
        var primaryMfgId: Int? = null
        var primaryMfgData: ByteArray? = null

        if (mfgSparse != null) {
            for (i in 0 until mfgSparse.size()) {
                val mfgId = mfgSparse.keyAt(i)
                val data = mfgSparse.valueAt(i)
                mfgMap[mfgId] = data
                if (primaryMfgId == null) {
                    primaryMfgId = mfgId
                    primaryMfgData = data
                }
            }
        }

        // Extract Service UUIDs
        val serviceUuids = record?.serviceUuids?.map { it.uuid } ?: emptyList()

        // Extract Service Data
        val serviceDataMap = mutableMapOf<UUID, ByteArray>()
        record?.serviceData?.forEach { (parcelUuid, data) ->
            serviceDataMap[parcelUuid.uuid] = data
        }

        // Raw hex bytes representation
        val rawBytes = record?.bytes
        val rawBytesHex = rawBytes?.joinToString("") { "%02X".format(it) } ?: ""

        val rawAd = BleRawAdvertisement(
            advertiseFlags = record?.advertiseFlags ?: -1,
            txPowerLevel = record?.txPowerLevel,
            manufacturerDataMap = mfgMap,
            serviceUuids = serviceUuids,
            serviceDataMap = serviceDataMap,
            rawBytesHex = rawBytesHex,
            isConnectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) result.isConnectable else true,
            timestampNanos = result.timestampNanos
        )

        val rawAddress = device.address
        val rawName = record?.deviceName ?: device.name

        // Classify device using comprehensive Samsung & BLE Classifier
        val classification = SamsungDeviceClassifier.classify(
            rawName = rawName,
            rawAddress = rawAddress,
            rawAd = rawAd
        )

        // Build Stable Device ID using smart tag privacy ID or hardware MAC
        val bleDeviceId = BleDeviceId.createFromScan(
            address = rawAddress,
            name = classification.displayName,
            manufacturerId = primaryMfgId,
            manufacturerData = primaryMfgData,
            serviceUuids = serviceUuids,
            smartTagPrivacyId = classification.offlineFindingPrivacyId
        )

        val key = bleDeviceId.primaryKey
        val existing = deviceMap[key]

        val updatedDevice = if (existing != null) {
            val mergedName = if (!rawName.isNullOrBlank()) rawName else existing.name
            existing.copy(
                name = if (mergedName.isNotBlank()) mergedName else classification.displayName,
                currentRssi = rssi,
                lastSeenMillis = now,
                totalSamples = existing.totalSamples + 1,
                advertisement = rawAd,
                isSmartTagCandidate = classification.isSmartTag,
                categoryLabel = classification.category.label,
                categoryBadgeColor = classification.category.badgeColorHex,
                offlineFindingPrivacyId = classification.offlineFindingPrivacyId ?: existing.offlineFindingPrivacyId,
                tagStatus = classification.tagStatusDescription ?: existing.tagStatus,
                subtitle = classification.subtitle
            )
        } else {
            BleDiscoveredDevice(
                deviceId = bleDeviceId,
                name = classification.displayName,
                address = rawAddress ?: "",
                currentRssi = rssi,
                firstSeenMillis = now,
                lastSeenMillis = now,
                totalSamples = 1,
                advertisement = rawAd,
                isSmartTagCandidate = classification.isSmartTag,
                categoryLabel = classification.category.label,
                categoryBadgeColor = classification.category.badgeColorHex,
                offlineFindingPrivacyId = classification.offlineFindingPrivacyId,
                tagStatus = classification.tagStatusDescription,
                subtitle = classification.subtitle
            )
        }

        deviceMap[key] = updatedDevice

        // Sort: real SmartTags first, then recently active, then highest RSSI
        val sortedList = deviceMap.values
            .sortedWith(
                compareByDescending<BleDiscoveredDevice> { it.isSmartTagCandidate }
                    .thenByDescending { it.isRecentlyActive }
                    .thenByDescending { it.currentRssi }
            )

        _scannerState.value = _scannerState.value.copy(
            discoveredDevices = sortedList,
            totalPacketsReceived = totalPackets,
            lastPacketTimeMillis = now
        )
    }
}

