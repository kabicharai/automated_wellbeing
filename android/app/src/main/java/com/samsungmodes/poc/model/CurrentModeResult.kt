package com.samsungmodes.poc.model

/**
 * Result representing current active Samsung Mode query.
 */
data class CurrentModeResult(
    val activeModeUuid: String?,
    val modeName: String?,
    val isModeActive: Boolean,
    val source: String,
    val details: String
)
