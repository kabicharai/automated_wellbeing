package com.samsungmodes.poc.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samsungmodes.poc.model.CurrentModeResult
import com.samsungmodes.poc.model.ModeOperationResult
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
    val logs: List<UiLogEntry> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val capabilityDetector = SamsungCapabilityDetector(application.applicationContext)
    private var controller: SamsungModeController
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    init {
        val detection = capabilityDetector.detectAndCreateController()
        controller = detection.controller

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

        log("SYSTEM", "Initialized Samsung Modes Controller POC on ${_uiState.value.deviceModel}")
        log("DETECT", detection.rationale)

        r.summaryLogs.forEach { logLine ->
            log("DIAG", logLine)
        }

        readCurrentMode()
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
