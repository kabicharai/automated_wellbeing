package com.samsungmodes.poc.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.samsungmodes.poc.ble.model.BleDeviceId
import com.samsungmodes.poc.ble.model.BleDiscoveredDevice
import com.samsungmodes.poc.ble.model.BleRawAdvertisement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Robust Android BLE Scanner for Android 16 (API 36) down to API 29.
 * Emits reactive device discovery updates and handles hardware capability checks.
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
        val totalPacketsReceived: Long = 0L
    )

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var leScanner: BluetoothLeScanner? = null

    private val _scannerState = MutableStateFlow(ScannerState())
    val scannerState: StateFlow<ScannerState> = _scannerState.asStateFlow()

    private val deviceMap = ConcurrentHashMap<String, BleDiscoveredDevice>()
    private var totalPackets: Long = 0L

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
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed with BLE stack"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE Scan feature unsupported on this device"
                SCAN_FAILED_INTERNAL_ERROR -> "Internal Bluetooth controller error"
                else -> "BLE scan failed with error code: $errorCode"
            }
            _scannerState.value = _scannerState.value.copy(
                isScanning = false,
                errorMessage = message
            )
        }
    }

    init {
        updateBluetoothAndPermissionStatus()
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
            // Restart scan with new settings
            stopScan()
            startScan()
        }
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

        val filters = mutableListOf<ScanFilter>() // Open scanning for all nearby devices

        return try {
            leScanner?.startScan(filters, settings, scanCallback)
            _scannerState.value = _scannerState.value.copy(
                isScanning = true,
                errorMessage = null
            )
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
        try {
            if (_scannerState.value.isScanning) {
                leScanner?.stopScan(scanCallback)
            }
        } catch (_: Exception) {
        } finally {
            _scannerState.value = _scannerState.value.copy(isScanning = false)
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val record = result.scanRecord
        val device = result.device
        val rssi = result.rssi
        val now = System.currentTimeMillis()

        totalPackets++

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

        // Build Stable Device ID
        val rawAddress = device.address
        val rawName = record?.deviceName ?: device.name

        val bleDeviceId = BleDeviceId.createFromScan(
            address = rawAddress,
            name = rawName,
            manufacturerId = primaryMfgId,
            manufacturerData = primaryMfgData,
            serviceUuids = serviceUuids
        )

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

        val isSmartTag = rawAd.isSamsungManufacturer || 
                         (rawName != null && rawName.contains("SmartTag", ignoreCase = true)) ||
                         (rawName != null && rawName.contains("Galaxy", ignoreCase = true))

        val key = bleDeviceId.primaryKey
        val existing = deviceMap[key]

        val updatedDevice = if (existing != null) {
            existing.copy(
                name = rawName ?: existing.name,
                currentRssi = rssi,
                lastSeenMillis = now,
                totalSamples = existing.totalSamples + 1,
                advertisement = rawAd,
                isSmartTagCandidate = isSmartTag || existing.isSmartTagCandidate
            )
        } else {
            BleDiscoveredDevice(
                deviceId = bleDeviceId,
                name = rawName ?: (if (isSmartTag) "Samsung SmartTag (Unresolved Name)" else "BLE Beacon / Device"),
                address = rawAddress ?: "",
                currentRssi = rssi,
                firstSeenMillis = now,
                lastSeenMillis = now,
                totalSamples = 1,
                advertisement = rawAd,
                isSmartTagCandidate = isSmartTag
            )
        }

        deviceMap[key] = updatedDevice

        // Sort: recently active first, then highest RSSI
        val sortedList = deviceMap.values
            .sortedWith(compareByDescending<BleDiscoveredDevice> { it.isRecentlyActive }
                .thenByDescending { it.currentRssi })

        _scannerState.value = _scannerState.value.copy(
            discoveredDevices = sortedList,
            totalPacketsReceived = totalPackets
        )
    }
}
