package com.samsungmodes.poc.ui

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samsungmodes.poc.ble.BlePermissionHelper
import com.samsungmodes.poc.ble.BleScanner
import com.samsungmodes.poc.ble.RssiTracker
import com.samsungmodes.poc.ble.model.BleDeviceProfile
import com.samsungmodes.poc.ble.model.BleDiscoveredDevice
import com.samsungmodes.poc.ble.model.BleProximityDevice
import com.samsungmodes.poc.model.CurrentModeResult
import com.samsungmodes.poc.model.ModeOperationResult
import com.samsungmodes.poc.proximity.automation.ProximityAutomationController
import com.samsungmodes.poc.proximity.service.ProximityForegroundService
import com.samsungmodes.poc.proximity.storage.ProximityStorageRepository
import com.samsungmodes.poc.samsung.SamsungCapabilityDetector
import com.samsungmodes.poc.samsung.SamsungModeController
import com.samsungmodes.poc.samsung.SamsungPackageInspector
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class UiLogEntry(
    val timestamp: String,
    val level: String, // "INFO", "SUCCESS", "WARN", "ERROR"
    val message: String
)

sealed class FullTestState {
    object Idle : FullTestState()
    data class Running(val stepIndex: Int, val stepDescription: String) : FullTestState()
    data class Completed(val outcome: String, val summary: String) : FullTestState() // "PASS", "PARTIAL PASS", "FAIL"
}

data class MainUiState(
    val deviceModel: String = "",
    val androidVersion: String = "",
    val sdkVersion: Int = 0,
    val oneUiVersion: String = "",
    val routinesVersionName: String = "",
    val routinesVersionCode: Long = 0L,
    val selectedBackendName: String = "",
    val isSupported: Boolean = false,
    val modeUuid: String = "",
    val currentMode: CurrentModeResult? = null,
    val report: SamsungPackageInspector.DiagnosticReport? = null,
    val testState: FullTestState = FullTestState.Idle,
    val isActionInProgress: Boolean = false,
    val lastOperationResult: ModeOperationResult? = null,
    val logs: List<UiLogEntry> = emptyList(),
    
    // Runtime Permissions State
    val permissionStatus: BlePermissionHelper.PermissionStatus = BlePermissionHelper.PermissionStatus(
        allGranted = false,
        hasBluetoothScan = false,
        hasBluetoothConnect = false,
        hasFineLocation = false,
        hasCoarseLocation = false,
        hasNotification = false,
        missingPermissions = emptyList()
    ),

    // Phase 1 BLE & RSSI State
    val scannerState: BleScanner.ScannerState = BleScanner.ScannerState(),
    val inspectedDevice: BleDiscoveredDevice? = null,
    val savedProximityDevice: BleDeviceProfile? = null,
    val activeRssiSnapshot: RssiTracker.RssiSnapshot = RssiTracker.RssiSnapshot(null, 0, null, null, null, null, null, emptyList()),
    val selectedRssiWindow: RssiTracker.HistoryWindow = RssiTracker.HistoryWindow.WINDOW_30S,

    // Phase 2 Calibration State & Per-Device Profiles
    val activeProximityProfile: com.samsungmodes.poc.proximity.model.ProximityProfile? = null,
    val savedProfiles: Map<String, com.samsungmodes.poc.proximity.model.ProximityProfile> = emptyMap(),
    val savedDevices: Map<String, BleDeviceProfile> = emptyMap(),

    // Phase 4 Proximity Automation State
    val automationState: ProximityAutomationController.AutomationState = ProximityAutomationController.AutomationState()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val capabilityDetector = SamsungCapabilityDetector(application.applicationContext)
    private var controller: SamsungModeController
    
    val storageRepository = ProximityStorageRepository(application.applicationContext)
    val bleScanner = BleScanner(application.applicationContext, viewModelScope)
    private val rssiTracker = RssiTracker(maxCapacity = 2000)
    val calibrationEngine = com.samsungmodes.poc.proximity.CalibrationEngine(viewModelScope)
    val proximityEngine = com.samsungmodes.poc.proximity.ProximityEngine(viewModelScope)
    val automationController: ProximityAutomationController
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    init {
        val detection = capabilityDetector.detectAndCreateController()
        controller = detection.controller
        automationController = ProximityAutomationController(viewModelScope, proximityEngine, controller)

        val r = detection.report
        _uiState.value = _uiState.value.copy(
            deviceModel = "${r.manufacturer} ${r.deviceModel}",
            androidVersion = "${r.androidVersion} (API ${r.sdkVersion})",
            sdkVersion = r.sdkVersion,
            oneUiVersion = r.oneUiVersion,
            routinesVersionName = r.packageVersionName ?: "Not Installed",
            routinesVersionCode = r.packageVersionCode ?: 0L,
            selectedBackendName = controller.getBackendName(),
            isSupported = controller.isSupported(),
            report = r
        )

        // Check runtime permissions
        checkPermissions()

        // Load Persistent Data (Saved Devices, Per-Device Profiles, Automation Config)
        loadPersistentStorage()

        log("SYSTEM", "Initialized Samsung Modes Controller POC on ${_uiState.value.deviceModel}")
        log("DETECT", detection.rationale)

        r.summaryLogs.forEach { logLine ->
            log("DIAG", logLine)
        }

        readCurrentMode()

        // Observe BLE Scanner state and update RSSI tracker, Calibration Engine & Proximity Engine
        viewModelScope.launch {
            bleScanner.scannerState.collect { scanState ->
                _uiState.value = _uiState.value.copy(scannerState = scanState)

                val trackedKey = _uiState.value.savedProximityDevice?.deviceId?.primaryKey
                    ?: _uiState.value.inspectedDevice?.deviceId?.primaryKey
                    ?: _uiState.value.activeProximityProfile?.targetDeviceId?.primaryKey

                if (trackedKey != null) {
                    val matchingDevice = scanState.discoveredDevices.find { 
                        it.deviceId.primaryKey == trackedKey || 
                        it.address == trackedKey || 
                        it.name.equals(trackedKey, ignoreCase = true) 
                    }
                    if (matchingDevice != null) {
                        rssiTracker.addSample(matchingDevice.currentRssi)
                        calibrationEngine.feedRssiSample(trackedKey, matchingDevice.currentRssi)
                        proximityEngine.feedRssiSample(matchingDevice.currentRssi)
                        _uiState.value = _uiState.value.copy(
                            activeRssiSnapshot = rssiTracker.getSnapshot(_uiState.value.selectedRssiWindow)
                        )
                    }
                }
            }
        }

        // Observe Proximity Engine transition events for diagnostics log
        viewModelScope.launch {
            proximityEngine.transitionEvents.collect { ev ->
                log("PROX", "[${ev.fromState} → ${ev.toState}] ${ev.reason}")
            }
        }

        // Observe Proximity Engine snapshot to update Foreground Service notification
        viewModelScope.launch {
            proximityEngine.snapshot.collect { snapshot ->
                if (ProximityForegroundService.isRunning()) {
                    val targetName = _uiState.value.savedProximityDevice?.displayName
                        ?: _uiState.value.activeProximityProfile?.targetDisplayName
                        ?: "BLE Proximity Beacon"
                    val isAuto = _uiState.value.automationState.masterEnabled && !_uiState.value.automationState.isPaused
                    val modeUuid = _uiState.value.automationState.targetModeUuid

                    try {
                        val serviceIntent = Intent(application.applicationContext, ProximityForegroundService::class.java)
                        // Trigger status refresh on running service
                        val notifManager = application.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        // The foreground service will update its notification
                    } catch (_: Exception) {
                    }
                }
            }
        }

        // Observe Automation Controller state & audit logs
        viewModelScope.launch {
            automationController.automationState.collect { autoState ->
                _uiState.value = _uiState.value.copy(automationState = autoState)
            }
        }

        viewModelScope.launch {
            automationController.auditEvents.collect { audit ->
                log("AUTO", "[${audit.action}] ${audit.message}")
            }
        }
    }

    fun checkPermissions() {
        val status = BlePermissionHelper.checkPermissionStatus(getApplication<Application>().applicationContext)
        _uiState.value = _uiState.value.copy(permissionStatus = status)
        if (!status.allGranted) {
            log("PERM", "Missing permissions: ${status.missingPermissions.joinToString(", ")}")
        } else {
            log("PERM", "All required Bluetooth and Location permissions GRANTED.")
        }
    }

    private fun loadPersistentStorage() {
        val savedDevs = storageRepository.getAllSavedDevices()
        val savedProfs = storageRepository.getAllProximityProfiles()
        val masterEnabled = storageRepository.isMasterAutomationEnabled()
        val targetModeUuid = storageRepository.getTargetModeUuid()
        val pauseUntil = storageRepository.getPauseUntilMillis()
        val activeDevKey = storageRepository.getActiveDeviceKey()

        val activeProfile = if (activeDevKey != null) savedProfs[activeDevKey] else savedProfs.values.firstOrNull()
        val activeDevice = if (activeDevKey != null) savedDevs[activeDevKey] else savedDevs.values.firstOrNull()

        _uiState.value = _uiState.value.copy(
            savedDevices = savedDevs,
            savedProfiles = savedProfs,
            savedProximityDevice = activeDevice,
            activeProximityProfile = activeProfile,
            modeUuid = if (_uiState.value.modeUuid.isBlank() && targetModeUuid.isNotBlank()) targetModeUuid else _uiState.value.modeUuid
        )

        if (activeProfile != null) {
            proximityEngine.updateProfile(activeProfile)
        }

        if (targetModeUuid.isNotBlank()) {
            automationController.setTargetModeUuid(targetModeUuid, activeProfile?.profileName ?: "Target Mode")
        }
        automationController.setMasterEnabled(masterEnabled)
        if (pauseUntil > System.currentTimeMillis()) {
            val remainMin = ((pauseUntil - System.currentTimeMillis()) / 60000).toInt().coerceAtLeast(1)
            automationController.pauseAutomation(remainMin)
        }

        log("STORAGE", "Loaded ${savedDevs.size} saved BLE devices and ${savedProfs.size} per-device calibration profiles from persistent storage.")
    }

    // --- Phase 4 Automation Operations ---

    fun toggleMasterAutomation(enabled: Boolean) {
        automationController.setMasterEnabled(enabled)
        storageRepository.setMasterAutomationEnabled(enabled)
        log("AUTO", "Master Automation ${if (enabled) "ENABLED" else "DISABLED"}")
    }

    fun setAutomationTargetMode(uuid: String, modeName: String = "Selected Mode") {
        automationController.setTargetModeUuid(uuid, modeName)
        storageRepository.setTargetModeUuid(uuid)
        _uiState.value = _uiState.value.copy(modeUuid = uuid)
        log("AUTO", "Bound target Samsung Mode UUID to: $uuid ($modeName)")
    }

    fun pauseAutomation(durationMinutes: Int = 0) {
        automationController.pauseAutomation(durationMinutes)
        val pauseUntil = if (durationMinutes > 0) System.currentTimeMillis() + (durationMinutes * 60000L) else 0L
        storageRepository.setPauseUntilMillis(pauseUntil)
    }

    fun resumeAutomation() {
        automationController.resumeAutomation()
        storageRepository.setPauseUntilMillis(0L)
    }

    fun emergencyStopAutomation() {
        automationController.emergencyStop()
        storageRepository.setMasterAutomationEnabled(false)
        log("WARN", "EMERGENCY STOP TRIGGERED: Mode stopped and automation disabled.")
    }

    fun reconcileAutomation() {
        automationController.reconcileStateWithCurrentProximity()
        log("AUTO", "Manual state reconcile initiated.")
    }

    fun selectDeviceProfile(deviceKey: String) {
        val prof = _uiState.value.savedProfiles[deviceKey]
        val dev = _uiState.value.savedDevices[deviceKey]
        storageRepository.setActiveDeviceKey(deviceKey)
        _uiState.value = _uiState.value.copy(
            activeProximityProfile = prof,
            savedProximityDevice = dev
        )
        if (prof != null) {
            proximityEngine.updateProfile(prof)
            log("PROFILE", "Activated per-device calibration profile for: ${prof.targetDisplayName} (ENTER: ${prof.enterThresholdRssi} dBm, EXIT: ${prof.exitThresholdRssi} dBm)")
        }
    }

    fun resetAllData() {
        storageRepository.resetAllData()
        bleScanner.clearDevices()
        rssiTracker.clear()
        _uiState.value = _uiState.value.copy(
            savedDevices = emptyMap(),
            savedProfiles = emptyMap(),
            savedProximityDevice = null,
            activeProximityProfile = null,
            inspectedDevice = null,
            activeRssiSnapshot = rssiTracker.getSnapshot(_uiState.value.selectedRssiWindow)
        )
        automationController.setMasterEnabled(false)
        log("STORAGE", "FACTORY RESET COMPLETE: All saved devices, calibration profiles, and automation preferences deleted.")
    }

    // --- Phase 2 Calibration Operations ---

    fun startOutsideCalibration(deviceKey: String, deviceName: String, durationSec: Int = 30) {
        log("CALIB", "Starting STEP 1: OUTSIDE Calibration for '$deviceName' ($durationSec s)...")
        calibrationEngine.startOutsideCalibration(deviceKey, deviceName, durationSec)
    }

    fun startInsideCalibration(durationSec: Int = 30) {
        log("CALIB", "Starting STEP 2: INSIDE Calibration ($durationSec s)...")
        calibrationEngine.startInsideCalibration(durationSec)
    }

    fun saveCalibratedProfile(profile: com.samsungmodes.poc.proximity.model.ProximityProfile) {
        storageRepository.saveProximityProfile(profile)
        storageRepository.setActiveDeviceKey(profile.targetDeviceId.primaryKey)
        val updatedProfiles = _uiState.value.savedProfiles.toMutableMap()
        updatedProfiles[profile.targetDeviceId.primaryKey] = profile

        _uiState.value = _uiState.value.copy(
            activeProximityProfile = profile,
            savedProfiles = updatedProfiles
        )
        proximityEngine.updateProfile(profile)
        log("CALIB", "PER-DEVICE CALIBRATION SAVED: '${profile.profileName}' for device ${profile.targetDisplayName} [ENTER: ${profile.enterThresholdRssi} dBm, EXIT: ${profile.exitThresholdRssi} dBm]")
    }

    // --- Phase 1 BLE & RSSI Operations ---

    fun startBleScan() {
        val status = BlePermissionHelper.checkPermissionStatus(getApplication<Application>().applicationContext)
        if (!status.allGranted) {
            log("WARN", "Cannot start scan: Missing permissions (${status.missingPermissions.joinToString(", ")}). Grant permissions first.")
            return
        }
        log("BLE", "Starting BLE Scan (Mode: ${_uiState.value.scannerState.scanMode.displayName})...")
        val started = bleScanner.startScan()
        if (started) {
            log("BLE", "BLE Scan started successfully.")
        } else {
            val err = bleScanner.scannerState.value.errorMessage ?: "Unknown error"
            log("ERROR", "Failed to start BLE Scan: $err")
        }
    }

    fun stopBleScan() {
        bleScanner.stopScan()
        log("BLE", "BLE Scan stopped.")
    }

    fun setScanMode(mode: BleScanner.ScanModePreference) {
        bleScanner.setScanMode(mode)
        log("BLE", "BLE Scan mode updated to: ${mode.displayName}")
    }

    fun clearDiscoveredDevices() {
        bleScanner.clearDevices()
        rssiTracker.clear()
        _uiState.value = _uiState.value.copy(
            activeRssiSnapshot = rssiTracker.getSnapshot(_uiState.value.selectedRssiWindow)
        )
        log("BLE", "Cleared discovered device list and RSSI history.")
    }

    fun inspectDevice(device: BleDiscoveredDevice) {
        _uiState.value = _uiState.value.copy(inspectedDevice = device)
        rssiTracker.clear()
        rssiTracker.addSample(device.currentRssi)
        _uiState.value = _uiState.value.copy(
            activeRssiSnapshot = rssiTracker.getSnapshot(_uiState.value.selectedRssiWindow)
        )

        // Check if this device already has a saved calibration profile
        val existingProfile = _uiState.value.savedProfiles[device.deviceId.primaryKey]
        if (existingProfile != null) {
            _uiState.value = _uiState.value.copy(activeProximityProfile = existingProfile)
            proximityEngine.updateProfile(existingProfile)
            log("BLE", "Inspecting ${device.name}. Loaded saved per-device calibration profile: [ENTER: ${existingProfile.enterThresholdRssi} dBm, EXIT: ${existingProfile.exitThresholdRssi} dBm]")
        } else {
            log("BLE", "Inspecting BLE Device: ${device.name} (${device.formattedAddress}) [RSSI: ${device.currentRssi} dBm]")
        }
    }

    fun saveAsProximityDevice(device: BleDiscoveredDevice) {
        val deviceType = when {
            device.isSmartTagCandidate -> BleProximityDevice.DeviceType.SAMSUNG_SMARTTAG_1
            device.advertisement.serviceUuids.isNotEmpty() -> BleProximityDevice.DeviceType.GENERIC_BEACON
            else -> BleProximityDevice.DeviceType.CUSTOM_BLE
        }

        val profile = BleDeviceProfile(
            id = UUID.randomUUID().toString(),
            displayName = device.name.ifBlank { "Proximity Beacon" },
            deviceType = deviceType,
            deviceId = device.deviceId,
            targetMacAddress = device.address,
            targetManufacturerId = device.deviceId.manufacturerId
        )

        storageRepository.saveBleDevice(profile)
        storageRepository.setActiveDeviceKey(profile.deviceId.primaryKey)
        val updatedDevices = _uiState.value.savedDevices.toMutableMap()
        updatedDevices[profile.deviceId.primaryKey] = profile

        _uiState.value = _uiState.value.copy(
            savedProximityDevice = profile,
            savedDevices = updatedDevices
        )
        log("BLE", "SAVED PROXIMITY DEVICE: ${profile.displayName} [Type: ${profile.deviceType}, Key: ${profile.deviceId.primaryKey}]")
    }

    fun setRssiHistoryWindow(window: RssiTracker.HistoryWindow) {
        _uiState.value = _uiState.value.copy(
            selectedRssiWindow = window,
            activeRssiSnapshot = rssiTracker.getSnapshot(window)
        )
        log("BLE", "Updated RSSI graph history window to: ${window.label}")
    }

    fun selectBackend(backend: String) {
        val inspector = capabilityDetector.getInspector()
        controller = when (backend) {
            "V85" -> com.samsungmodes.poc.samsung.SamsungModeControllerV85(getApplication<Application>().applicationContext, inspector)
            "COMBINED" -> com.samsungmodes.poc.samsung.SamsungModeControllerCombined(getApplication<Application>().applicationContext, inspector)
            else -> com.samsungmodes.poc.samsung.SamsungModeControllerV8(getApplication<Application>().applicationContext, inspector)
        }
        _uiState.value = _uiState.value.copy(
            selectedBackendName = controller.getBackendName(),
            isSupported = controller.isSupported()
        )
        log("CONFIG", "Switched backend to [${controller.getBackendName()}] (Supported: ${controller.isSupported()})")
    }

    fun onUuidChanged(newUuid: String) {
        _uiState.value = _uiState.value.copy(modeUuid = newUuid)
    }

    fun startMode() {
        val uuid = _uiState.value.modeUuid.trim()
        if (uuid.isEmpty()) {
            log("WARN", "Please enter a valid Samsung Mode UUID before starting.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActionInProgress = true)
            log("ACTION", "Invoking START for Mode UUID: $uuid via [${controller.getBackendName()}]...")

            val result = controller.startMode(uuid)
            _uiState.value = _uiState.value.copy(
                isActionInProgress = false,
                lastOperationResult = result
            )
            handleOperationResult("START", result)
            readCurrentMode()
        }
    }

    fun stopMode() {
        val uuid = _uiState.value.modeUuid.trim()
        if (uuid.isEmpty()) {
            log("WARN", "Please enter a valid Samsung Mode UUID before stopping.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActionInProgress = true)
            log("ACTION", "Invoking STOP for Mode UUID: $uuid via [${controller.getBackendName()}]...")

            val result = controller.stopMode(uuid)
            _uiState.value = _uiState.value.copy(
                isActionInProgress = false,
                lastOperationResult = result
            )
            handleOperationResult("STOP", result)
            readCurrentMode()
        }
    }

    fun toggleMode() {
        val uuid = _uiState.value.modeUuid.trim()
        if (uuid.isEmpty()) {
            log("WARN", "Please enter a valid Samsung Mode UUID before toggling.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActionInProgress = true)
            log("ACTION", "Invoking TOGGLE for Mode UUID: $uuid...")

            val result = controller.toggleMode(uuid)
            _uiState.value = _uiState.value.copy(
                isActionInProgress = false,
                lastOperationResult = result
            )
            handleOperationResult("TOGGLE", result)
            readCurrentMode()
        }
    }

    fun readCurrentMode() {
        viewModelScope.launch {
            log("QUERY", "Querying currently active Samsung Mode state...")
            val result = controller.getCurrentMode()
            _uiState.value = _uiState.value.copy(currentMode = result)

            if (result.isModeActive) {
                log("INFO", "Current active Mode detected: ${result.activeModeUuid ?: "Unknown"} (Source: ${result.source})")
                // Auto populate if field is empty
                if (_uiState.value.modeUuid.isEmpty() && !result.activeModeUuid.isNullOrEmpty()) {
                    _uiState.value = _uiState.value.copy(modeUuid = result.activeModeUuid)
                    log("INFO", "Auto-populated Mode UUID field with active mode: ${result.activeModeUuid}")
                }
            } else {
                log("INFO", "No Samsung Mode currently active according to system observables.")
            }
        }
    }

    fun runFullTest() {
        val uuid = _uiState.value.modeUuid.trim()
        if (uuid.isEmpty()) {
            log("ERROR", "Cannot run full test: Mode UUID is empty. Enter a valid UUID first.")
            return
        }

        viewModelScope.launch {
            log("TEST", "==================================================")
            log("TEST", "STARTING 7-STEP FULL CYCLE VERIFICATION TEST")
            log("TEST", "Target Mode UUID: $uuid | Backend: ${controller.getBackendName()}")
            log("TEST", "==================================================")

            var stepPassedCount = 0
            var partialVerified = false

            // Step 1: Initial state check
            _uiState.value = _uiState.value.copy(testState = FullTestState.Running(1, "1/7: Reading baseline state..."))
            log("TEST", "[Step 1/7] Checking initial mode state...")
            val initialMode = controller.getCurrentMode()
            log("TEST", "Initial state: active=${initialMode.isModeActive}, uuid=${initialMode.activeModeUuid ?: "none"}")

            // Step 2: STOP mode (clean slate)
            _uiState.value = _uiState.value.copy(testState = FullTestState.Running(2, "2/7: Ensuring mode is STOPPED..."))
            log("TEST", "[Step 2/7] Dispatching STOP command...")
            val stop1Res = controller.stopMode(uuid)
            delay(1000)

            // Step 3: Verify OFF
            _uiState.value = _uiState.value.copy(testState = FullTestState.Running(3, "3/7: Verifying mode is OFF..."))
            log("TEST", "[Step 3/7] Verifying mode is OFF...")
            val postStop1 = controller.getCurrentMode()
            if (!postStop1.isModeActive) {
                log("SUCCESS", "[Step 3/7] Verified OFF successfully.")
                stepPassedCount++
            } else {
                log("WARN", "[Step 3/7] Mode state could not be verified as OFF (Observables: ${postStop1.details}).")
            }

            // Step 4: START mode
            _uiState.value = _uiState.value.copy(testState = FullTestState.Running(4, "4/7: Dispatching START command..."))
            log("TEST", "[Step 4/7] Dispatching START command...")
            val startRes = controller.startMode(uuid)
            delay(1500)

            // Step 5: Verify ON
            _uiState.value = _uiState.value.copy(testState = FullTestState.Running(5, "5/7: Verifying mode is ON..."))
            log("TEST", "[Step 5/7] Verifying mode is ON...")
            val postStart = controller.getCurrentMode()
            val startVerified = when (startRes) {
                is ModeOperationResult.Success -> startRes.verified || postStart.isModeActive
                else -> false
            }

            if (startVerified) {
                log("SUCCESS", "[Step 5/7] Verified ON successfully.")
                stepPassedCount++
            } else if (startRes is ModeOperationResult.Success) {
                partialVerified = true
                log("WARN", "[Step 5/7] Invocation dispatched successfully, but state not independently verified in settings.")
            } else {
                log("ERROR", "[Step 5/7] START failed: $startRes")
            }

            // Step 6: STOP mode
            _uiState.value = _uiState.value.copy(testState = FullTestState.Running(6, "6/7: Dispatching STOP command..."))
            log("TEST", "[Step 6/7] Dispatching final STOP command...")
            val stop2Res = controller.stopMode(uuid)
            delay(1500)

            // Step 7: Verify OFF
            _uiState.value = _uiState.value.copy(testState = FullTestState.Running(7, "7/7: Verifying mode is OFF..."))
            log("TEST", "[Step 7/7] Verifying final OFF state...")
            val postStop2 = controller.getCurrentMode()
            val stopVerified = when (stop2Res) {
                is ModeOperationResult.Success -> stop2Res.verified || !postStop2.isModeActive
                else -> false
            }

            if (stopVerified) {
                log("SUCCESS", "[Step 7/7] Verified final OFF state successfully.")
                stepPassedCount++
            } else {
                log("WARN", "[Step 7/7] Final OFF state could not be verified.")
            }

            // Evaluation
            val outcome = when {
                stepPassedCount >= 3 -> "PASS"
                partialVerified -> "PARTIAL PASS"
                else -> "FAIL"
            }

            val summary = when (outcome) {
                "PASS" -> "Full cycle test PASSED. Samsung Mode START and STOP were both invoked and verified on ${controller.getBackendName()}."
                "PARTIAL PASS" -> "Invocation dispatched to Samsung without throwing, but external verification could not confirm setting state."
                else -> "Test FAILED. The Samsung backend rejected invocation or verification failed."
            }

            log("TEST", "==================================================")
            log("TEST", "TEST RESULT: $outcome - $summary")
            log("TEST", "==================================================")

            _uiState.value = _uiState.value.copy(
                testState = FullTestState.Completed(outcome, summary),
                currentMode = postStop2
            )
        }
    }

    fun refreshDiagnostics() {
        val detection = capabilityDetector.detectAndCreateController()
        controller = detection.controller
        val r = detection.report
        _uiState.value = _uiState.value.copy(
            selectedBackendName = controller.getBackendName(),
            isSupported = controller.isSupported(),
            report = r
        )
        log("REFRESH", "Diagnostics refreshed: ${detection.rationale}")
    }

    fun clearLogs() {
        _uiState.value = _uiState.value.copy(logs = emptyList())
    }

    fun exportConfigBackup() {
        try {
            storageRepository.syncBackupToExternalStorage()
            log("STORAGE", "Configuration backup exported to Documents/SamsungModes/samsung_modes_backup.json")
        } catch (e: Exception) {
            log("ERROR", "Failed to export backup: ${e.message}")
        }
    }

    fun restoreConfigBackup(): Boolean {
        return try {
            val restored = storageRepository.restoreFromExternalStorage()
            if (restored) {
                val savedDevices = storageRepository.getAllSavedDevices()
                val activeKey = storageRepository.getActiveDeviceKey()
                val savedDevice = activeKey?.let { savedDevices[it] } ?: savedDevices.values.firstOrNull()
                val profiles = storageRepository.getAllProximityProfiles()
                val profile = activeKey?.let { profiles[it] } ?: profiles.values.firstOrNull()

                _uiState.value = _uiState.value.copy(
                    savedDevices = savedDevices,
                    savedProximityDevice = savedDevice,
                    savedProfiles = profiles,
                    activeProximityProfile = profile,
                    modeUuid = storageRepository.getTargetModeUuid()
                )
                log("SUCCESS", "Configuration restored successfully from Documents/SamsungModes/samsung_modes_backup.json (${savedDevices.size} devices, ${profiles.size} profiles)")
            } else {
                log("WARN", "No backup found in Documents/SamsungModes/samsung_modes_backup.json")
            }
            restored
        } catch (e: Exception) {
            log("ERROR", "Failed to restore backup: ${e.message}")
            false
        }
    }

    private fun handleOperationResult(action: String, result: ModeOperationResult) {
        when (result) {
            is ModeOperationResult.Success -> {
                if (result.verified) {
                    log("SUCCESS", "[$action] MODE STATE VERIFIED: ${result.details}")
                } else {
                    log("INFO", "[$action] INVOCATION SUCCESS (State unverified): ${result.details}")
                }
            }
            is ModeOperationResult.PermissionDenied -> {
                log("ERROR", "[$action] PERMISSION DENIED: ${result.reason}")
            }
            is ModeOperationResult.InvocationFailed -> {
                log("ERROR", "[$action] INVOCATION FAILED: ${result.reason}")
            }
            is ModeOperationResult.NotSupported -> {
                log("ERROR", "[$action] NOT SUPPORTED: ${result.reason}")
            }
            is ModeOperationResult.VerificationFailed -> {
                log("WARN", "[$action] VERIFICATION FAILED: ${result.reason}")
            }
        }
    }

    private fun log(level: String, message: String) {
        val entry = UiLogEntry(
            timestamp = timeFormat.format(Date()),
            level = level,
            message = message
        )
        _uiState.value = _uiState.value.copy(
            logs = _uiState.value.logs + entry
        )
    }
}
