package com.samsungmodes.poc.proximity

import com.samsungmodes.poc.proximity.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic, anti-flapping Proximity State Machine.
 *
 * Implements:
 * 1. Dual-threshold hysteresis (ENTER vs EXIT).
 * 2. Temporal candidate stability timers (5s ENTER / 10s EXIT default).
 * 3. Graceful beacon signal loss handling (preserves state during short fades; transitions to UNKNOWN after timeout).
 * 4. Real-time confidence score estimation.
 * 5. Strict decoupling: Emits pure state transitions without direct Samsung Modes API calls.
 */
class ProximityEngine(
    private val coroutineScope: CoroutineScope,
    initialProfile: ProximityProfile? = null
) {
    private val _snapshot = MutableStateFlow(
        ProximityStateSnapshot(
            enterThreshold = initialProfile?.enterThresholdRssi ?: -64,
            exitThreshold = initialProfile?.exitThresholdRssi ?: -69,
            enterDurationSeconds = initialProfile?.enterDurationSeconds ?: 5,
            exitDurationSeconds = initialProfile?.exitDurationSeconds ?: 10,
            lostTimeoutSeconds = initialProfile?.lostDeviceTimeoutSeconds ?: 30,
            profileName = initialProfile?.profileName ?: "SmartTag Proximity"
        )
    )
    val snapshot: StateFlow<ProximityStateSnapshot> = _snapshot.asStateFlow()

    private val _transitionEvents = MutableSharedFlow<ProximityTransitionEvent>(replay = 20)
    val transitionEvents: SharedFlow<ProximityTransitionEvent> = _transitionEvents.asSharedFlow()

    // Internal state machine variables
    private var currentState: ProximityState = ProximityState.UNKNOWN
    private var candidateStatus: CandidateStatus = CandidateStatus.NONE
    private var candidateStartTimeMillis: Long = 0L
    private var candidateRequiredDurationMillis: Long = 0L

    private var activeProfile: ProximityProfile? = initialProfile
    private var rssiFilter: RssiFilter = RssiFilterFactory.create(
        type = initialProfile?.filterType ?: RssiFilterType.EMA,
        smoothingParam = initialProfile?.filterSmoothingParam ?: 0.25,
        windowSize = initialProfile?.windowSampleSize ?: 15
    )

    private var lastSampleTimestampMillis: Long = 0L
    private var lastStateChangeTimestampMillis: Long = System.currentTimeMillis()
    private val recentSamplesWindow = ArrayDeque<Int>(20)
    private val recentEventsList = mutableListOf<ProximityTransitionEvent>()

    private var tickerJob: Job? = null

    init {
        startEngineTicker()
    }

    /**
     * Updates the engine configuration with a new ProximityProfile.
     */
    fun updateProfile(profile: ProximityProfile) {
        activeProfile = profile
        rssiFilter = RssiFilterFactory.create(
            type = profile.filterType,
            smoothingParam = profile.filterSmoothingParam,
            windowSize = profile.windowSampleSize
        )

        _snapshot.value = _snapshot.value.copy(
            enterThreshold = profile.enterThresholdRssi,
            exitThreshold = profile.exitThresholdRssi,
            enterDurationSeconds = profile.enterDurationSeconds,
            exitDurationSeconds = profile.exitDurationSeconds,
            lostTimeoutSeconds = profile.lostDeviceTimeoutSeconds,
            profileName = profile.profileName
        )
        recordEvent(
            from = currentState,
            to = currentState,
            status = candidateStatus,
            reason = "Profile updated: '${profile.profileName}' [ENTER: ${profile.enterThresholdRssi} dBm, EXIT: ${profile.exitThresholdRssi} dBm]"
        )
    }

    /**
     * Feeds a raw RSSI sample from the BLE scanner for the target beacon.
     */
    fun feedRssiSample(rawRssi: Int) {
        val now = System.currentTimeMillis()
        lastSampleTimestampMillis = now

        if (recentSamplesWindow.size >= 20) {
            recentSamplesWindow.removeFirst()
        }
        recentSamplesWindow.addLast(rawRssi)

        val filtered = rssiFilter.addSample(rawRssi)
        evaluateState(filtered, rawRssi, now)
    }

    /**
     * Resets the proximity state machine back to UNKNOWN.
     */
    fun reset() {
        val prev = currentState
        currentState = ProximityState.UNKNOWN
        candidateStatus = CandidateStatus.NONE
        candidateStartTimeMillis = 0L
        rssiFilter.reset()
        recentSamplesWindow.clear()
        lastSampleTimestampMillis = 0L
        lastStateChangeTimestampMillis = System.currentTimeMillis()

        recordEvent(
            from = prev,
            to = ProximityState.UNKNOWN,
            status = CandidateStatus.NONE,
            reason = "Proximity Engine manually reset"
        )
        updateSnapshot()
    }

    private fun evaluateState(filteredRssi: Double, rawRssi: Int, now: Long) {
        val enterThreshold = _snapshot.value.enterThreshold
        val exitThreshold = _snapshot.value.exitThreshold
        val enterDurationMillis = _snapshot.value.enterDurationSeconds * 1000L
        val exitDurationMillis = _snapshot.value.exitDurationSeconds * 1000L

        when (currentState) {
            ProximityState.UNKNOWN -> {
                // Immediate candidate evaluation from UNKNOWN
                if (filteredRssi >= enterThreshold) {
                    if (candidateStatus != CandidateStatus.ENTERING) {
                        candidateStatus = CandidateStatus.ENTERING
                        candidateStartTimeMillis = now
                        candidateRequiredDurationMillis = enterDurationMillis
                        recordEvent(currentState, currentState, candidateStatus, "Signal detected above ENTER threshold (${"%.1f".format(filteredRssi)} >= $enterThreshold dBm). Verifying...")
                    }
                } else if (filteredRssi <= exitThreshold) {
                    if (candidateStatus != CandidateStatus.EXITING) {
                        candidateStatus = CandidateStatus.EXITING
                        candidateStartTimeMillis = now
                        candidateRequiredDurationMillis = exitDurationMillis
                        recordEvent(currentState, currentState, candidateStatus, "Signal detected below EXIT threshold (${"%.1f".format(filteredRssi)} <= $exitThreshold dBm). Verifying...")
                    }
                } else {
                    // Deadband between exit and enter while UNKNOWN: default candidate to OUTSIDE with relaxed timer
                    if (candidateStatus == CandidateStatus.NONE) {
                        candidateStatus = CandidateStatus.EXITING
                        candidateStartTimeMillis = now
                        candidateRequiredDurationMillis = exitDurationMillis
                    }
                }
            }

            ProximityState.OUTSIDE -> {
                if (filteredRssi >= enterThreshold) {
                    if (candidateStatus != CandidateStatus.ENTERING) {
                        // Start candidate entering verification
                        candidateStatus = CandidateStatus.ENTERING
                        candidateStartTimeMillis = now
                        candidateRequiredDurationMillis = enterDurationMillis
                        recordEvent(currentState, currentState, candidateStatus, "Filtered RSSI (${"%.1f".format(filteredRssi)} dBm) entered INSIDE zone. Starting ENTER verification (${_snapshot.value.enterDurationSeconds}s)...")
                    }
                } else {
                    if (candidateStatus == CandidateStatus.ENTERING) {
                        // Signal dropped back below ENTER threshold before timer elapsed: cancel candidate
                        val elapsedSec = ((now - candidateStartTimeMillis) / 1000.0)
                        candidateStatus = CandidateStatus.NONE
                        recordEvent(currentState, currentState, candidateStatus, "ENTER candidate aborted after ${"%.1f".format(elapsedSec)}s: Signal dropped to ${"%.1f".format(filteredRssi)} dBm (< $enterThreshold dBm)")
                    }
                }
            }

            ProximityState.INSIDE -> {
                if (filteredRssi <= exitThreshold) {
                    if (candidateStatus != CandidateStatus.EXITING) {
                        // Start candidate exiting verification
                        candidateStatus = CandidateStatus.EXITING
                        candidateStartTimeMillis = now
                        candidateRequiredDurationMillis = exitDurationMillis
                        recordEvent(currentState, currentState, candidateStatus, "Filtered RSSI (${"%.1f".format(filteredRssi)} dBm) dropped into OUTSIDE zone. Starting EXIT verification (${_snapshot.value.exitDurationSeconds}s)...")
                    }
                } else {
                    if (candidateStatus == CandidateStatus.EXITING) {
                        // Signal recovered back above EXIT threshold before timer elapsed: cancel candidate
                        val elapsedSec = ((now - candidateStartTimeMillis) / 1000.0)
                        candidateStatus = CandidateStatus.NONE
                        recordEvent(currentState, currentState, candidateStatus, "EXIT candidate aborted after ${"%.1f".format(elapsedSec)}s: Signal recovered to ${"%.1f".format(filteredRssi)} dBm (> $exitThreshold dBm)")
                    }
                }
            }
        }

        checkCandidateCompletion(now, filteredRssi, rawRssi)
        updateSnapshot()
    }

    private fun checkCandidateCompletion(now: Long, filteredRssi: Double?, rawRssi: Int?) {
        if (candidateStatus == CandidateStatus.NONE) return

        val elapsed = now - candidateStartTimeMillis
        if (elapsed >= candidateRequiredDurationMillis && candidateRequiredDurationMillis > 0) {
            val previousState = currentState
            when (candidateStatus) {
                CandidateStatus.ENTERING -> {
                    currentState = ProximityState.INSIDE
                    candidateStatus = CandidateStatus.NONE
                    lastStateChangeTimestampMillis = now
                    recordEvent(previousState, ProximityState.INSIDE, CandidateStatus.NONE, "ENTER verified: Signal sustained above ${_snapshot.value.enterThreshold} dBm for ${_snapshot.value.enterDurationSeconds}s (Transition: $previousState -> INSIDE)")
                }
                CandidateStatus.EXITING -> {
                    currentState = ProximityState.OUTSIDE
                    candidateStatus = CandidateStatus.NONE
                    lastStateChangeTimestampMillis = now
                    recordEvent(previousState, ProximityState.OUTSIDE, CandidateStatus.NONE, "EXIT verified: Signal sustained below ${_snapshot.value.exitThreshold} dBm for ${_snapshot.value.exitDurationSeconds}s (Transition: $previousState -> OUTSIDE)")
                }
                CandidateStatus.NONE -> {}
            }
        }
    }

    private fun startEngineTicker() {
        tickerJob?.cancel()
        tickerJob = coroutineScope.launch {
            while (isActive) {
                delay(200L)
                val now = System.currentTimeMillis()
                val filtered = rssiFilter.getCurrentFilteredValue()
                val raw = recentSamplesWindow.lastOrNull()

                // Check for beacon signal timeout (Lost Beacon / Out of Range)
                val secondsSinceLast = if (lastSampleTimestampMillis > 0) {
                    ((now - lastSampleTimestampMillis) / 1000).toInt()
                } else 999

                val isLost = secondsSinceLast >= _snapshot.value.lostTimeoutSeconds

                if (isLost) {
                    if (currentState == ProximityState.INSIDE) {
                        // User was INSIDE and beacon signal disappeared (moved out of range quickly).
                        // Transition directly to OUTSIDE to safely turn off active mode.
                        val prev = currentState
                        currentState = ProximityState.OUTSIDE
                        candidateStatus = CandidateStatus.NONE
                        lastStateChangeTimestampMillis = now
                        recordEvent(
                            prev,
                            ProximityState.OUTSIDE,
                            CandidateStatus.NONE,
                            "Beacon out of range (Signal lost for ${secondsSinceLast}s >= timeout ${_snapshot.value.lostTimeoutSeconds}s). Exited zone to OUTSIDE."
                        )
                    } else if (candidateStatus == CandidateStatus.EXITING) {
                        // Exiting candidate was underway and signal vanished completely: complete exit immediately
                        val prev = currentState
                        currentState = ProximityState.OUTSIDE
                        candidateStatus = CandidateStatus.NONE
                        lastStateChangeTimestampMillis = now
                        recordEvent(
                            prev,
                            ProximityState.OUTSIDE,
                            CandidateStatus.NONE,
                            "Signal dropped completely during exit verification. Exited zone to OUTSIDE."
                        )
                    } else if (candidateStatus == CandidateStatus.ENTERING) {
                        // Signal lost while entering: cancel candidate
                        candidateStatus = CandidateStatus.NONE
                    }
                } else {
                    checkCandidateCompletion(now, filtered, raw)
                }

                updateSnapshot()
            }
        }
    }

    private fun updateSnapshot() {
        val now = System.currentTimeMillis()
        val filtered = rssiFilter.getCurrentFilteredValue()
        val raw = recentSamplesWindow.lastOrNull()

        val secondsSinceLast = if (lastSampleTimestampMillis > 0) {
            ((now - lastSampleTimestampMillis) / 1000).toInt()
        } else 0

        val (progress, elapsedSec, totalSec) = if (candidateStatus != CandidateStatus.NONE && candidateRequiredDurationMillis > 0) {
            val elapsedMs = (now - candidateStartTimeMillis).coerceAtLeast(0)
            val progressFrac = (elapsedMs.toFloat() / candidateRequiredDurationMillis.toFloat()).coerceIn(0f, 1f)
            Triple(progressFrac, (elapsedMs / 1000).toInt(), (candidateRequiredDurationMillis / 1000).toInt())
        } else {
            Triple(0f, 0, if (candidateStatus == CandidateStatus.ENTERING) _snapshot.value.enterDurationSeconds else _snapshot.value.exitDurationSeconds)
        }

        val confidence = calculateConfidence(filtered, secondsSinceLast)

        _snapshot.value = _snapshot.value.copy(
            state = currentState,
            candidateStatus = candidateStatus,
            candidateProgressPercent = progress,
            candidateElapsedSeconds = elapsedSec,
            candidateTotalSeconds = totalSec,
            currentFilteredRssi = filtered,
            currentRawRssi = raw,
            confidencePercent = confidence,
            isBeaconLost = secondsSinceLast >= _snapshot.value.lostTimeoutSeconds,
            secondsSinceLastSample = secondsSinceLast,
            lastTransitionTimestampMillis = lastStateChangeTimestampMillis,
            recentEvents = recentEventsList.takeLast(15).reversed()
        )
    }

    private fun calculateConfidence(filtered: Double?, secondsSinceLast: Int): Int {
        if (filtered == null || secondsSinceLast >= _snapshot.value.lostTimeoutSeconds) return 0
        if (recentSamplesWindow.size < 3) return 30

        var score = 100

        // 1. Freshness penalty
        if (secondsSinceLast > 3) {
            score -= min(60, (secondsSinceLast - 3) * 10)
        }

        // 2. Variance penalty
        val mean = recentSamplesWindow.average()
        val variance = recentSamplesWindow.map { (it - mean) * (it - mean) }.average()
        val stdDev = Math.sqrt(variance)
        if (stdDev > 4.0) {
            score -= min(30, ((stdDev - 4.0) * 5).toInt())
        }

        // 3. Proximity to threshold margin bonus/penalty
        val enterThresh = _snapshot.value.enterThreshold
        val exitThresh = _snapshot.value.exitThreshold
        val midPoint = (enterThresh + exitThresh) / 2.0
        val distToBoundary = abs(filtered - midPoint)
        if (distToBoundary < 1.5) {
            score -= 15 // In deadband center
        }

        return score.coerceIn(5, 100)
    }

    private fun recordEvent(from: ProximityState, to: ProximityState, status: CandidateStatus, reason: String) {
        val event = ProximityTransitionEvent(
            timestampMillis = System.currentTimeMillis(),
            fromState = from,
            toState = to,
            candidateStatus = status,
            filteredRssi = rssiFilter.getCurrentFilteredValue(),
            rawRssi = recentSamplesWindow.lastOrNull(),
            reason = reason
        )
        recentEventsList.add(event)
        coroutineScope.launch {
            _transitionEvents.emit(event)
        }
    }
}
