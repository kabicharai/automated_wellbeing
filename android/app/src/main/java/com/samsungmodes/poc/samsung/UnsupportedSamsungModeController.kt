package com.samsungmodes.poc.samsung

import com.samsungmodes.poc.model.CurrentModeResult
import com.samsungmodes.poc.model.ModeOperationResult

/**
 * Fallback controller used when neither One UI 8.5 nor One UI 8.0 invocation endpoints
 * are accessible on the current device.
 */
class UnsupportedSamsungModeController(
    private val reason: String = "Samsung Modes & Routines is not supported or accessible on this device."
) : SamsungModeController {

    override fun isSupported(): Boolean = false

    override fun getBackendName(): String = "Unsupported"

    override suspend fun startMode(uuid: String): ModeOperationResult {
        return ModeOperationResult.NotSupported(reason)
    }

    override suspend fun stopMode(uuid: String): ModeOperationResult {
        return ModeOperationResult.NotSupported(reason)
    }

    override suspend fun toggleMode(uuid: String): ModeOperationResult {
        return ModeOperationResult.NotSupported(reason)
    }

    override suspend fun getCurrentMode(): CurrentModeResult {
        return CurrentModeResult(
            activeModeUuid = null,
            modeName = null,
            isModeActive = false,
            source = "None",
            details = "Device is unsupported: $reason"
        )
    }
}
