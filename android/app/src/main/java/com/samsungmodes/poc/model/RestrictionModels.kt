package com.samsungmodes.poc.model

/**
 * Data structures for the future restriction abstraction layer.
 * Prepares the codebase for plug-and-play BLE and RSSI proximity engines.
 */
data class RestrictionProfile(
    val id: String,
    val name: String,
    val samsungModeUuid: String,
    val targetApps: List<String> = emptyList(),
    val description: String = ""
)

sealed class RestrictionState {
    data class Active(
        val profileId: String,
        val timestampMs: Long = System.currentTimeMillis(),
        val verified: Boolean
    ) : RestrictionState()

    object Inactive : RestrictionState()

    data class Transitioning(
        val targetState: Boolean,
        val message: String
    ) : RestrictionState()

    data class Error(
        val reason: String
    ) : RestrictionState()
}
