package com.samsungmodes.poc.proximity.automation

import com.samsungmodes.poc.model.ModeOperationResult
import com.samsungmodes.poc.proximity.ProximityEngine
import com.samsungmodes.poc.proximity.model.ProximityState
import com.samsungmodes.poc.proximity.model.ProximityTransitionEvent
import com.samsungmodes.poc.samsung.SamsungModeController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Phase 4 Proximity Automation Controller.
 *
 * Mediates between ProximityEngine state transitions and SamsungModeController APIs:
 * - OUTSIDE → INSIDE : Calls SamsungModeController.startMode(targetModeUuid)
 * - INSIDE → OUTSIDE : Calls SamsungModeController.stopMode(targetModeUuid)
 * - UNKNOWN : No-op (safety rule: preserves current phone mode without false flips)
 *
 * Guarantees:
 * 1. Zero duplicate calls while in steady state.
 * 2. Minimum debounce cooldown (3 seconds) between consecutive mode changes.
 * 3. Master Automation Switch (ON/OFF) & Pause/Resume override.
 * 4. Controlled retry backoff on Samsung Mode invocation failures.
 * 5. Emergency Stop Mode.
 * 6. Audit trail for diagnostics and UI telemetry.
 */
class ProximityAutomationController(
    private val coroutineScope: CoroutineScope,
    private val proximityEngine: ProximityEngine,
    private var samsungModeController: SamsungModeController
) {

    enum class ExecutionState(val displayName: String) {
        DISABLED("AUTOMATION OFF"),
        IDLE("MONITORING (READY)"),
        TRIGGERING_START("STARTING SAMSUNG MODE..."),
        START_SUCCESS("MODE ACTIVE (INSIDE)"),
        TRIGGERING_STOP("STOPPING SAMSUNG MODE..."),
        STOP_SUCCESS("MODE INACTIVE (OUTSIDE)"),
        PAUSED("TEMPORARILY PAUSED"),
        RETRYING("RETRYING INVOCATION..."),
        ERROR("INVOCATION ERROR")
    }

    data class AutomationState(
        val masterEnabled: Boolean = false,
        val targetModeUuid: String = "",
        val targetModeName: String = "Bedroom Focus",
        val isPaused: Boolean = false,
        val pauseUntilMillis: Long = 0L,
        val executionState: ExecutionState = ExecutionState.DISABLED,
        val lastTriggeredTransition: String = "None",
        val lastActionTimestampMillis: Long = 0L,
        val lastResultDetails: String = "",
        val retryCount: Int = 0,
        val totalTransitionsHandled: Int = 0,
        val successfulInvocations: Int = 0,
        val failedInvocations: Int = 0
    ) {
        val isCurrentlyPaused: Boolean
            get() = isPaused && (pauseUntilMillis == 0L || System.currentTimeMillis() < pauseUntilMillis)

        val pauseRemainingSeconds: Long
            get() {
                if (!isPaused || pauseUntilMillis == 0L) return 0L
                val diff = pauseUntilMillis - System.currentTimeMillis()
                return if (diff > 0) diff / 1000 else 0L
            }
    }

    data class AutomationAuditEvent(
        val id: String = UUID.randomUUID().toString(),
        val timestampMillis: Long = System.currentTimeMillis(),
        val action: String, // "START_MODE", "STOP_MODE", "PAUSE", "RESUME", "RETRY", "EMERGENCY_STOP"
        val fromState: ProximityState,
        val toState: ProximityState,
        val targetUuid: String,
        val success: Boolean,
        val message: String
    )

    private val _automationState = MutableStateFlow(AutomationState())
    val automationState: StateFlow<AutomationState> = _automationState.asStateFlow()

    private val _auditEvents = MutableSharedFlow<AutomationAuditEvent>(replay = 50)
    val auditEvents: SharedFlow<AutomationAuditEvent> = _auditEvents.asSharedFlow()

    private var lastInvocationTimestamp: Long = 0L
    private val cooldownMillis: Long = 3000L // 3s anti-chatter debounce

    init {
        // Observe Proximity Engine transition events
        coroutineScope.launch {
            proximityEngine.transitionEvents.collect { transition ->
                handleProximityTransition(transition)
            }
        }

        // Periodic check to auto-resume when pause duration expires
        coroutineScope.launch {
            while (isActive) {
                delay(1000)
                val state = _automationState.value
                if (state.isPaused && state.pauseUntilMillis > 0 && System.currentTimeMillis() >= state.pauseUntilMillis) {
                    resumeAutomation("Pause timer expired")
                }
            }
        }
    }

    fun updateSamsungModeController(controller: SamsungModeController) {
        this.samsungModeController = controller
    }

    fun setMasterEnabled(enabled: Boolean) {
        _automationState.value = _automationState.value.copy(
            masterEnabled = enabled,
            executionState = if (enabled) {
                if (_automationState.value.isCurrentlyPaused) ExecutionState.PAUSED else ExecutionState.IDLE
            } else {
                ExecutionState.DISABLED
            }
        )
        recordAuditEvent(
            action = if (enabled) "MASTER_ON" else "MASTER_OFF",
            fromState = proximityEngine.snapshot.value.state,
            toState = proximityEngine.snapshot.value.state,
            success = true,
            message = if (enabled) "Master Proximity Automation ENABLED" else "Master Proximity Automation DISABLED"
        )
    }

    fun setTargetModeUuid(uuid: String, modeName: String = "Selected Mode") {
        _automationState.value = _automationState.value.copy(
            targetModeUuid = uuid.trim(),
            targetModeName = modeName
        )
    }

    fun pauseAutomation(durationMinutes: Int = 0) {
        val pauseUntil = if (durationMinutes > 0) {
            System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        } else {
            0L // Indefinite pause until manual resume
        }

        _automationState.value = _automationState.value.copy(
            isPaused = true,
            pauseUntilMillis = pauseUntil,
            executionState = ExecutionState.PAUSED
        )

        val durationLabel = if (durationMinutes > 0) "for $durationMinutes minutes" else "indefinitely"
        recordAuditEvent(
            action = "PAUSE",
            fromState = proximityEngine.snapshot.value.state,
            toState = proximityEngine.snapshot.value.state,
            success = true,
            message = "Automation PAUSED $durationLabel"
        )
    }

    fun resumeAutomation(reason: String = "User manual resume") {
        _automationState.value = _automationState.value.copy(
            isPaused = false,
            pauseUntilMillis = 0L,
            executionState = if (_automationState.value.masterEnabled) ExecutionState.IDLE else ExecutionState.DISABLED
        )
        recordAuditEvent(
            action = "RESUME",
            fromState = proximityEngine.snapshot.value.state,
            toState = proximityEngine.snapshot.value.state,
            success = true,
            message = "Automation RESUMED ($reason)"
        )
    }

    fun emergencyStop() {
        val uuid = _automationState.value.targetModeUuid
        coroutineScope.launch {
            _automationState.value = _automationState.value.copy(
                masterEnabled = false,
                isPaused = true,
                executionState = ExecutionState.DISABLED
            )

            if (uuid.isNotBlank()) {
                val res = samsungModeController.stopMode(uuid)
                recordAuditEvent(
                    action = "EMERGENCY_STOP",
                    fromState = proximityEngine.snapshot.value.state,
                    toState = proximityEngine.snapshot.value.state,
                    success = res is ModeOperationResult.Success,
                    message = "EMERGENCY STOP executed: Automation disabled and STOP dispatched for UUID: $uuid"
                )
            }
        }
    }

    fun reconcileStateWithCurrentProximity() {
        val currentState = proximityEngine.snapshot.value.state
        val uuid = _automationState.value.targetModeUuid

        if (!_automationState.value.masterEnabled || _automationState.value.isCurrentlyPaused || uuid.isBlank()) {
            return
        }

        coroutineScope.launch {
            when (currentState) {
                ProximityState.INSIDE -> {
                    recordAuditEvent(
                        action = "RECONCILE",
                        fromState = currentState,
                        toState = currentState,
                        success = true,
                        message = "Reconciling state: In INSIDE zone -> Enforcing Mode START"
                    )
                    dispatchModeAction(isStart = true, uuid = uuid, retryCount = 0)
                }
                ProximityState.OUTSIDE -> {
                    recordAuditEvent(
                        action = "RECONCILE",
                        fromState = currentState,
                        toState = currentState,
                        success = true,
                        message = "Reconciling state: In OUTSIDE zone -> Enforcing Mode STOP"
                    )
                    dispatchModeAction(isStart = false, uuid = uuid, retryCount = 0)
                }
                ProximityState.UNKNOWN -> {
                    // UNKNOWN does nothing
                }
            }
        }
    }

    private fun handleProximityTransition(event: ProximityTransitionEvent) {
        val state = _automationState.value
        val uuid = state.targetModeUuid

        _automationState.value = state.copy(
            totalTransitionsHandled = state.totalTransitionsHandled + 1
        )

        // Rule 1: Master Automation must be enabled
        if (!state.masterEnabled) {
            return
        }

        // Rule 2: Automation must not be paused
        if (state.isCurrentlyPaused) {
            return
        }

        // Rule 3: Target Mode UUID must be configured
        if (uuid.isBlank()) {
            recordAuditEvent(
                action = "SKIPPED",
                fromState = event.fromState,
                toState = event.toState,
                success = false,
                message = "Proximity transition [${event.fromState} → ${event.toState}] skipped: No Samsung Mode UUID configured."
            )
            return
        }

        // Rule 4: Cooldown debounce to prevent rapid flapping
        val now = System.currentTimeMillis()
        if (now - lastInvocationTimestamp < cooldownMillis) {
            recordAuditEvent(
                action = "DEBOUNCED",
                fromState = event.fromState,
                toState = event.toState,
                success = true,
                message = "Proximity transition [${event.fromState} → ${event.toState}] debounced (within ${cooldownMillis}ms cooldown)."
            )
            return
        }

        // Rule 5: Transition Action Routing
        when {
            // OUTSIDE → INSIDE (or UNKNOWN → INSIDE upon stable initial entry)
            event.toState == ProximityState.INSIDE && event.fromState != ProximityState.INSIDE -> {
                coroutineScope.launch {
                    dispatchModeAction(isStart = true, uuid = uuid, retryCount = 0)
                }
            }

            // INSIDE → OUTSIDE
            event.toState == ProximityState.OUTSIDE && event.fromState == ProximityState.INSIDE -> {
                coroutineScope.launch {
                    dispatchModeAction(isStart = false, uuid = uuid, retryCount = 0)
                }
            }

            // UNKNOWN transitions: NEVER toggle mode (safety mandate)
            event.toState == ProximityState.UNKNOWN -> {
                recordAuditEvent(
                    action = "SAFETY_HOLD",
                    fromState = event.fromState,
                    toState = event.toState,
                    success = true,
                    message = "Signal entered UNKNOWN state: Phone mode preserved without changes."
                )
            }
        }
    }

    private suspend fun dispatchModeAction(isStart: Boolean, uuid: String, retryCount: Int) {
        lastInvocationTimestamp = System.currentTimeMillis()
        val actionName = if (isStart) "START" else "STOP"

        _automationState.value = _automationState.value.copy(
            executionState = if (isStart) ExecutionState.TRIGGERING_START else ExecutionState.TRIGGERING_STOP,
            lastTriggeredTransition = if (isStart) "OUTSIDE → INSIDE ($actionName)" else "INSIDE → OUTSIDE ($actionName)",
            lastActionTimestampMillis = System.currentTimeMillis(),
            retryCount = retryCount
        )

        val result = if (isStart) {
            samsungModeController.startMode(uuid)
        } else {
            samsungModeController.stopMode(uuid)
        }

        when (result) {
            is ModeOperationResult.Success -> {
                _automationState.value = _automationState.value.copy(
                    executionState = if (isStart) ExecutionState.START_SUCCESS else ExecutionState.STOP_SUCCESS,
                    successfulInvocations = _automationState.value.successfulInvocations + 1,
                    lastResultDetails = "Verified: ${result.verified} [${result.details}]",
                    retryCount = 0
                )
                recordAuditEvent(
                    action = if (isStart) "START_MODE_SUCCESS" else "STOP_MODE_SUCCESS",
                    fromState = if (isStart) ProximityState.OUTSIDE else ProximityState.INSIDE,
                    toState = if (isStart) ProximityState.INSIDE else ProximityState.OUTSIDE,
                    success = true,
                    message = "Samsung Mode $actionName dispatched successfully. Verified: ${result.verified} (${result.details})"
                )
            }
            else -> {
                val errorReason = when (result) {
                    is ModeOperationResult.PermissionDenied -> result.reason
                    is ModeOperationResult.InvocationFailed -> result.reason
                    is ModeOperationResult.NotSupported -> result.reason
                    is ModeOperationResult.VerificationFailed -> result.reason
                    else -> "Unknown error"
                }

                _automationState.value = _automationState.value.copy(
                    executionState = ExecutionState.ERROR,
                    failedInvocations = _automationState.value.failedInvocations + 1,
                    lastResultDetails = errorReason
                )

                recordAuditEvent(
                    action = if (isStart) "START_MODE_FAILED" else "STOP_MODE_FAILED",
                    fromState = if (isStart) ProximityState.OUTSIDE else ProximityState.INSIDE,
                    toState = if (isStart) ProximityState.INSIDE else ProximityState.OUTSIDE,
                    success = false,
                    message = "Samsung Mode $actionName failed: $errorReason"
                )

                // Safe exponential backoff retry (up to 3 retries: 5s, 15s, 30s)
                if (retryCount < 3 && _automationState.value.masterEnabled && !_automationState.value.isCurrentlyPaused) {
                    val backoffSeconds = when (retryCount) {
                        0 -> 5
                        1 -> 15
                        else -> 30
                    }
                    _automationState.value = _automationState.value.copy(executionState = ExecutionState.RETRYING)
                    recordAuditEvent(
                        action = "RETRY_SCHEDULED",
                        fromState = if (isStart) ProximityState.OUTSIDE else ProximityState.INSIDE,
                        toState = if (isStart) ProximityState.INSIDE else ProximityState.OUTSIDE,
                        success = false,
                        message = "Scheduling retry #${retryCount + 1} for $actionName in ${backoffSeconds}s..."
                    )
                    delay(backoffSeconds * 1000L)
                    if (_automationState.value.masterEnabled && !_automationState.value.isCurrentlyPaused) {
                        dispatchModeAction(isStart, uuid, retryCount + 1)
                    }
                }
            }
        }
    }

    private fun recordAuditEvent(
        action: String,
        fromState: ProximityState,
        toState: ProximityState,
        success: Boolean,
        message: String
    ) {
        val event = AutomationAuditEvent(
            action = action,
            fromState = fromState,
            toState = toState,
            targetUuid = _automationState.value.targetModeUuid,
            success = success,
            message = message
        )
        coroutineScope.launch {
            _auditEvents.emit(event)
        }
    }
}
