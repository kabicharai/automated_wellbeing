package com.samsungmodes.poc.restriction

import com.samsungmodes.poc.model.RestrictionProfile
import com.samsungmodes.poc.model.RestrictionState

/**
 * High-level restriction abstraction layer.
 * Designed so the future BLE beacon / Samsung SmartTag proximity engine
 * can seamlessly trigger restrictions without coupling to Samsung Mode internals.
 */
interface RestrictionController {
    /**
     * Enables restriction for the given profile (e.g. when entering designated zone).
     */
    suspend fun enable(profile: RestrictionProfile): RestrictionState

    /**
     * Disables restriction for the given profile (e.g. when exiting designated zone).
     */
    suspend fun disable(profile: RestrictionProfile): RestrictionState

    /**
     * Queries the current restriction state.
     */
    suspend fun currentState(): RestrictionState
}
