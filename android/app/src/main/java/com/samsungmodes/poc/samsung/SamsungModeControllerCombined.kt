package com.samsungmodes.poc.samsung

import android.content.Context
import com.samsungmodes.poc.model.CurrentModeResult
import com.samsungmodes.poc.model.ModeOperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Multi-Engine Combined Controller.
 * Dispatches both the ContentProvider call and Shortcut Activity in sequence,
 * ensuring maximum compatibility on locked-down One UI revisions.
 */
class SamsungModeControllerCombined(
    private val context: Context,
    private val inspector: SamsungPackageInspector
) : SamsungModeController {

    private val v8 = SamsungModeControllerV8(context, inspector)
    private val v85 = SamsungModeControllerV85(context, inspector)

    companion object {
        const val BACKEND_NAME = "Multi-Engine (Combined Pulse)"
    }

    override fun isSupported(): Boolean = v8.isSupported() || v85.isSupported()

    override fun getBackendName(): String = BACKEND_NAME

    private fun getSummary(res: ModeOperationResult): String {
        return when (res) {
            is ModeOperationResult.Success -> res.details
            is ModeOperationResult.PermissionDenied -> "PermissionDenied: ${res.reason}"
            is ModeOperationResult.InvocationFailed -> "InvocationFailed: ${res.reason}"
            is ModeOperationResult.NotSupported -> "NotSupported: ${res.reason}"
            is ModeOperationResult.VerificationFailed -> "VerificationFailed: ${res.reason}"
        }
    }

    override suspend fun startMode(uuid: String): ModeOperationResult = withContext(Dispatchers.IO) {
        val res1 = v8.startMode(uuid)
        if (res1 is ModeOperationResult.Success && res1.verified) {
            return@withContext res1
        }
        val res2 = v85.startMode(uuid)
        if (res2 is ModeOperationResult.Success && res2.verified) {
            return@withContext res2
        }
        ModeOperationResult.Success(
            verified = false,
            details = "Dispatched via Provider & Shortcut. Provider: ${getSummary(res1)}, Shortcut: ${getSummary(res2)}"
        )
    }

    override suspend fun stopMode(uuid: String): ModeOperationResult = withContext(Dispatchers.IO) {
        val res1 = v8.stopMode(uuid)
        if (res1 is ModeOperationResult.Success && res1.verified) {
            return@withContext res1
        }
        val res2 = v85.stopMode(uuid)
        if (res2 is ModeOperationResult.Success && res2.verified) {
            return@withContext res2
        }
        ModeOperationResult.Success(
            verified = false,
            details = "Dispatched STOP via Provider & Shortcut. Provider: ${getSummary(res1)}, Shortcut: ${getSummary(res2)}"
        )
    }

    override suspend fun toggleMode(uuid: String): ModeOperationResult = withContext(Dispatchers.IO) {
        val res1 = v8.toggleMode(uuid)
        if (res1 is ModeOperationResult.Success && res1.verified) {
            return@withContext res1
        }
        val res2 = v85.toggleMode(uuid)
        if (res2 is ModeOperationResult.Success && res2.verified) {
            return@withContext res2
        }
        ModeOperationResult.Success(
            verified = false,
            details = "Dispatched Toggle via Provider & Shortcut. Provider: ${getSummary(res1)}, Shortcut: ${getSummary(res2)}"
        )
    }

    override suspend fun getCurrentMode(): CurrentModeResult = withContext(Dispatchers.IO) {
        v8.getCurrentMode()
    }
}
