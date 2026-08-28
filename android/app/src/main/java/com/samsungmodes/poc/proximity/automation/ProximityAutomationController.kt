package com.samsungmodes.poc.proximity.automation

import com.samsungmodes.poc.model.ModeOperationResult
import com.samsungmodes.poc.proximity.ProximityEngine
import com.samsungmodes.poc.proximity.model.AutomationEntryAction
import com.samsungmodes.poc.proximity.model.AutomationExitAction
import com.samsungmodes.poc.proximity.model.AutomationRule
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * Phase 4 & 5 Proximity Automation Controller.
 *
 * Mediates between ProximityEngine state transitions and SamsungModeController APIs:
 * - Supports Multi-Device and Multi-Mode AutomationRules with custom Entry / Exit triggers:
 *     - Standard: INSIDE -> Turn ON / OUTSIDE -> Turn OFF
 *     - Inverted / Safe-Zone: INSIDE -> Turn OFF / OUTSIDE -> Turn ON or Restore
 *     - One-way: INSIDE -> Turn ON / OUTSIDE -> Do Nothing
 * - Conflict Priority: When multiple rules match, evaluates according to rule priority (1 = highest).
 * - Time & Day Constraints: Active time windows per rule.
 * - Guarantees:
 *     1. Zero duplicate calls while in steady state.
 *     2. Minimum debounce cooldown (3 seconds) between consecutive mode changes.
 *     3. Master Automation Switch (ON/OFF) & Pause/Resume override.
 *     4. Controlled retry backoff on Samsung Mode invocation failures.
 *     5. Emergency Stop Mode.
 *     6. Audit trail for diagnostics and UI telemetry.
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
        START_SUCCESS("MODE ACTIVE"),
        TRIGGERING_STOP("STOPPING SAMSUNG MODE..."),
        STOP_SUCCESS("MODE INACTIVE"),
        PAUSED("TEMPORARILY PAUSED"),
        RETRYING("RETRYING INVOCATION..."),
        ERROR("INVOCATION ERROR")
    }

    data class AutomationState(
        val masterEnabled: Boolean = false,
        val targetModeUuid: String = "",
        val targetModeName: String = "Selected Mode",
        val isPaused: Boolean = false,
        val pauseUntilMillis: Long = 0L,
        val executionState: ExecutionState = ExecutionState.DISABLED,
        val lastTriggeredTransition: String = "None",
        val lastActionTimestampMillis: Long = 0L,
        val lastResultDetails: String = "",
        val retryCount: Int = 0,
        val totalTransitionsHandled: Int = 0,
        val successfulInvocations: Int = 0,
        val failedInvocations: Int = 0,
        val activeRuleId: String? = null,
        val activeRules: List<AutomationRule> = emptyList(),
        val previousModeUuid: String = ""
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
        val message: String,
        val ruleId: String? = null,
        val ruleName: String? = null
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

    fun setRules(rules: List<AutomationRule>) {
        _automationState.value = _automationState.value.copy(
            activeRules = rules.sortedBy { it.priority }
        )
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
        if (!_automationState.value.masterEnabled || _automationState.value.isCurrentlyPaused) {
            return
        }

        val rule = findActiveRuleForCurrentState()
        val uuid = rule?.targetModeUuid?.ifBlank { null }
            ?: _automationState.value.targetModeUuid.ifBlank { null }
            ?: _automationState.value.activeRules.firstOrNull { it.isEnabled && it.targetModeUuid.isNotBlank() }?.targetModeUuid
            ?: ""
        if (uuid.isBlank()) return

        coroutineScope.launch {
            when (currentState) {
                ProximityState.INSIDE -> {
                    val shouldStart = rule?.entryAction != AutomationEntryAction.TURN_OFF
                    recordAuditEvent(
                        action = "RECONCILE",
                        fromState = currentState,
                        toState = currentState,
                        success = true,
                        message = "Reconciling state: In INSIDE zone -> Enforcing Action (${if (shouldStart) "START" else "STOP"})",
                        ruleId = rule?.id,
                        ruleName = rule?.name
                    )
                    dispatchModeAction(isStart = shouldStart, uuid = uuid, retryCount = 0, rule = rule)
                }
                ProximityState.OUTSIDE -> {
                    val shouldStart = rule?.exitAction == AutomationExitAction.TURN_ON
                    recordAuditEvent(
                        action = "RECONCILE",
                        fromState = currentState,
                        toState = currentState,
                        success = true,
                        message = "Reconciling state: In OUTSIDE zone -> Enforcing Action (${if (shouldStart) "START" else "STOP"})",
                        ruleId = rule?.id,
                        ruleName = rule?.name
                    )
                    dispatchModeAction(isStart = shouldStart, uuid = uuid, retryCount = 0, rule = rule)
                }
                ProximityState.UNKNOWN -> {
                    // UNKNOWN does nothing
                }
            }
        }
    }

    private fun findActiveRuleForCurrentState(): AutomationRule? {
        val rules = _automationState.value.activeRules.filter { it.isEnabled }
        if (rules.isEmpty()) return null

        val activeProfile = proximityEngine.activeProfile
        val currentDeviceKey = activeProfile?.targetDeviceId?.primaryKey?.trim()
        val currentMac = activeProfile?.targetDeviceId?.macAddress?.trim()
        val currentName = activeProfile?.targetDisplayName?.trim()

        // 1. First priority: Look for a rule matching the active BLE device
        var matchingRule = rules.firstOrNull { rule ->
            val ruleKey = rule.deviceKey.trim()
            when {
                ruleKey.isBlank() || ruleKey.equals("ANY", ignoreCase = true) || ruleKey.equals("ALL", ignoreCase = true) -> true
                currentDeviceKey != null && ruleKey.equals(currentDeviceKey, ignoreCase = true) -> true
                currentDeviceKey != null && ruleKey.removePrefix("addr:").equals(currentDeviceKey.removePrefix("addr:"), ignoreCase = true) -> true
                !currentMac.isNullOrBlank() && ruleKey.contains(currentMac, ignoreCase = true) -> true
                !currentName.isNullOrBlank() && rule.deviceDisplayName.equals(currentName, ignoreCase = true) -> true
                else -> false
            }
        }

        // 2. Fallback: If no device-specific rule matched, take the highest priority enabled rule with a valid UUID
        if (matchingRule == null) {
            matchingRule = rules.firstOrNull { it.targetModeUuid.isNotBlank() } ?: rules.firstOrNull()
        }

        if (matchingRule != null && matchingRule.timeConstraintEnabled) {
            if (!isWithinTimeWindow(matchingRule.timeStart, matchingRule.timeEnd, matchingRule.daysOfWeek)) {
                return null
            }
        }
        return matchingRule
    }

    private fun isWithinTimeWindow(start: String, end: String, days: List<String>): Boolean {
        try {
            val cal = Calendar.getInstance()
            val dayOfWeekStr = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "MON"
                Calendar.TUESDAY -> "TUE"
                Calendar.WEDNESDAY -> "WED"
                Calendar.THURSDAY -> "THU"
                Calendar.FRIDAY -> "FRI"
                Calendar.SATURDAY -> "SAT"
                Calendar.SUNDAY -> "SUN"
                else -> "MON"
            }
            if (days.isNotEmpty() && !days.contains(dayOfWeekStr)) {
                return false
            }

            val sdf = SimpleDateFormat("HH:mm", Locale.US)
            val nowStr = sdf.format(cal.time)
            val nowTime = sdf.parse(nowStr)?.time ?: return true
            val startTime = sdf.parse(start)?.time ?: return true
            val endTime = sdf.parse(end)?.time ?: return true

            return if (endTime >= startTime) {
                nowTime in startTime..endTime
            } else {
                // Crosses midnight (e.g. 22:00 to 07:00)
                nowTime >= startTime || nowTime <= endTime
            }
        } catch (e: Exception) {
            return true
        }
    }

    private fun handleProximityTransition(event: ProximityTransitionEvent) {
        val state = _automationState.value
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

        // Find active rule or fallback to global targetModeUuid
        val rule = findActiveRuleForCurrentState()
        val uuid = rule?.targetModeUuid?.ifBlank { null }
            ?: state.targetModeUuid.ifBlank { null }
            ?: state.activeRules.firstOrNull { it.isEnabled && it.targetModeUuid.isNotBlank() }?.targetModeUuid
            ?: ""

        // Rule 3: Target Mode UUID must be configured
        if (uuid.isBlank()) {
            recordAuditEvent(
                action = "SKIPPED",
                fromState = event.fromState,
                toState = event.toState,
                success = false,
                message = "Proximity transition [${event.fromState} -> ${event.toState}] skipped: No Samsung Mode UUID configured.",
                ruleId = rule?.id,
                ruleName = rule?.name
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
                message = "Proximity transition [${event.fromState} -> ${event.toState}] debounced (within ${cooldownMillis}ms cooldown).",
                ruleId = rule?.id,
                ruleName = rule?.name
            )
            return
        }

        // Rule 5: Transition Action Routing based on Rule Configuration
        when {
            // Entering Proximity (OUTSIDE -> INSIDE or UNKNOWN -> INSIDE)
            event.toState == ProximityState.INSIDE && event.fromState != ProximityState.INSIDE -> {
                val entryAction = rule?.entryAction ?: AutomationEntryAction.TURN_ON
                when (entryAction) {
                    AutomationEntryAction.TURN_ON -> {
                        coroutineScope.launch {
                            dispatchModeAction(isStart = true, uuid = uuid, retryCount = 0, rule = rule)
                        }
                    }
                    AutomationEntryAction.TURN_OFF -> {
                        // Inverted / Exclusion Zone: INSIDE turns mode OFF
                        coroutineScope.launch {
                            dispatchModeAction(isStart = false, uuid = uuid, retryCount = 0, rule = rule)
                        }
                    }
                    AutomationEntryAction.NONE -> {
                        recordAuditEvent(
                            action = "ENTRY_IGNORED",
                            fromState = event.fromState,
                            toState = event.toState,
                            success = true,
                            message = "Entered range for rule '${rule?.name ?: "Default"}', but Entry Action is set to NONE.",
                            ruleId = rule?.id,
                            ruleName = rule?.name
                        )
                    }
                }
            }

            // Exiting Proximity (INSIDE -> OUTSIDE or UNKNOWN -> OUTSIDE)
            event.toState == ProximityState.OUTSIDE && event.fromState != ProximityState.OUTSIDE -> {
                val exitAction = rule?.exitAction ?: AutomationExitAction.TURN_OFF
                when (exitAction) {
                    AutomationExitAction.TURN_OFF -> {
                        coroutineScope.launch {
                            dispatchModeAction(isStart = false, uuid = uuid, retryCount = 0, rule = rule)
                        }
                    }
                    AutomationExitAction.TURN_ON -> {
                        // Inverted exit: leaving turns mode ON
                        coroutineScope.launch {
                            dispatchModeAction(isStart = true, uuid = uuid, retryCount = 0, rule = rule)
                        }
                    }
                    AutomationExitAction.RESTORE_PREVIOUS -> {
                        val prevMode = state.previousModeUuid
                        if (prevMode.isNotBlank()) {
                            coroutineScope.launch {
                                dispatchModeAction(isStart = true, uuid = prevMode, retryCount = 0, rule = rule)
                            }
                        } else {
                            coroutineScope.launch {
                                dispatchModeAction(isStart = false, uuid = uuid, retryCount = 0, rule = rule)
                            }
                        }
                    }
                    AutomationExitAction.NONE -> {
                        recordAuditEvent(
                            action = "EXIT_IGNORED",
                            fromState = event.fromState,
                            toState = event.toState,
                            success = true,
                            message = "Exited range for rule '${rule?.name ?: "Default"}', but Exit Action is set to NONE.",
                            ruleId = rule?.id,
                            ruleName = rule?.name
                        )
                    }
                }
            }

            // UNKNOWN transitions: NEVER toggle mode (safety mandate)
            event.toState == ProximityState.UNKNOWN -> {
                recordAuditEvent(
                    action = "SAFETY_HOLD",
                    fromState = event.fromState,
                    toState = event.toState,
                    success = true,
                    message = "Signal entered UNKNOWN state: Phone mode preserved without changes.",
                    ruleId = rule?.id,
                    ruleName = rule?.name
                )
            }
        }
    }

    private suspend fun dispatchModeAction(
        isStart: Boolean,
        uuid: String,
        retryCount: Int,
        rule: AutomationRule? = null
    ) {
        lastInvocationTimestamp = System.currentTimeMillis()
        val actionName = if (isStart) "START" else "STOP"

        _automationState.value = _automationState.value.copy(
            executionState = if (isStart) ExecutionState.TRIGGERING_START else ExecutionState.TRIGGERING_STOP,
            lastTriggeredTransition = if (isStart) "START MODE ($actionName)" else "STOP MODE ($actionName)",
            lastActionTimestampMillis = System.currentTimeMillis(),
            retryCount = retryCount,
            activeRuleId = rule?.id
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
                    retryCount = 0,
                    previousModeUuid = if (isStart) uuid else ""
                )
                recordAuditEvent(
                    action = if (isStart) "START_MODE_SUCCESS" else "STOP_MODE_SUCCESS",
                    fromState = if (isStart) ProximityState.OUTSIDE else ProximityState.INSIDE,
                    toState = if (isStart) ProximityState.INSIDE else ProximityState.OUTSIDE,
                    success = true,
                    message = "Samsung Mode $actionName dispatched successfully [${rule?.name ?: "Rule"}]. Verified: ${result.verified} (${result.details})",
                    ruleId = rule?.id,
                    ruleName = rule?.name
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
                    message = "Samsung Mode $actionName failed [${rule?.name ?: "Rule"}]: $errorReason",
                    ruleId = rule?.id,
                    ruleName = rule?.name
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
                        message = "Scheduling retry #${retryCount + 1} for $actionName in ${backoffSeconds}s...",
                        ruleId = rule?.id,
                        ruleName = rule?.name
                    )
                    delay(backoffSeconds * 1000L)
                    if (_automationState.value.masterEnabled && !_automationState.value.isCurrentlyPaused) {
                        dispatchModeAction(isStart, uuid, retryCount + 1, rule)
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
        message: String,
        ruleId: String? = null,
        ruleName: String? = null
    ) {
        val event = AutomationAuditEvent(
            action = action,
            fromState = fromState,
            toState = toState,
            targetUuid = _automationState.value.targetModeUuid,
            success = success,
            message = message,
            ruleId = ruleId,
            ruleName = ruleName
        )
        coroutineScope.launch {
            _auditEvents.emit(event)
        }
    }
}

