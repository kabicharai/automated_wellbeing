package com.samsungmodes.poc.proximity.model

/**
 * Stable, anti-flapping proximity states for the Bluetooth Low Energy target.
 */
enum class ProximityState(val label: String, val badgeColorHex: Long) {
    UNKNOWN("UNKNOWN", 0xFF64748B),
    INSIDE("INSIDE", 0xFF10B981),
    OUTSIDE("OUTSIDE", 0xFFEF4444)
}

/**
 * Transient candidate status indicating anti-flapping temporal verification in progress.
 */
enum class CandidateStatus(val label: String) {
    NONE("STABLE"),
    ENTERING("VERIFYING ENTER..."),
    EXITING("VERIFYING EXIT...")
}

/**
 * Diagnostic record of a proximity state machine event or transition.
 */
data class ProximityTransitionEvent(
    val timestampMillis: Long = System.currentTimeMillis(),
    val fromState: ProximityState,
    val toState: ProximityState,
    val candidateStatus: CandidateStatus,
    val filteredRssi: Double?,
    val rawRssi: Int?,
    val reason: String
)

/**
 * Comprehensive real-time snapshot of the Proximity State Machine.
 */
data class ProximityStateSnapshot(
    val state: ProximityState = ProximityState.UNKNOWN,
    val candidateStatus: CandidateStatus = CandidateStatus.NONE,
    val candidateProgressPercent: Float = 0f,
    val candidateElapsedSeconds: Int = 0,
    val candidateTotalSeconds: Int = 5,
    val currentFilteredRssi: Double? = null,
    val currentRawRssi: Int? = null,
    val confidencePercent: Int = 0,
    val enterThreshold: Int = -64,
    val exitThreshold: Int = -69,
    val enterDurationSeconds: Int = 5,
    val exitDurationSeconds: Int = 10,
    val isBeaconLost: Boolean = false,
    val secondsSinceLastSample: Int = 0,
    val lostTimeoutSeconds: Int = 30,
    val profileName: String = "Default Beacon Profile",
    val lastTransitionTimestampMillis: Long = System.currentTimeMillis(),
    val recentEvents: List<ProximityTransitionEvent> = emptyList()
)
