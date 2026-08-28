package com.samsungmodes.poc.samsung

import com.samsungmodes.poc.model.CurrentModeResult
import com.samsungmodes.poc.model.ModeOperationResult

/**
 * Common abstraction for Samsung Mode Controllers across One UI versions.
 */
interface SamsungModeController {
    /**
     * Attempts to activate the specified Samsung Mode by UUID.
     */
    suspend fun startMode(uuid: String): ModeOperationResult

    /**
     * Attempts to deactivate the specified Samsung Mode by UUID.
     */
    suspend fun stopMode(uuid: String): ModeOperationResult

    /**
     * Attempts to toggle the specified Samsung Mode by UUID.
     */
    suspend fun toggleMode(uuid: String): ModeOperationResult

    /**
     * Queries the currently active Samsung Mode state via legitimate system observables.
     */
    suspend fun getCurrentMode(): CurrentModeResult

    /**
     * Returns whether this controller is supported on the current device.
     */
    fun isSupported(): Boolean

    /**
     * Returns a user-friendly identifier of this controller backend.
     */
    fun getBackendName(): String
}
