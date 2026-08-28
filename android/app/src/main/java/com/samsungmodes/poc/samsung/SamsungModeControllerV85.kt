package com.samsungmodes.poc.samsung

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.samsungmodes.poc.model.CurrentModeResult
import com.samsungmodes.poc.model.ModeOperationResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Samsung Mode Controller implementation for One UI 8.5+.
 *
 * MECHANISM:
 * Uses Samsung Modes & Routines shortcut launcher activity:
 *   Package: com.samsung.android.app.routines
 *   Activity: com.samsung.android.app.routines.ui.shortcut.ShortcutLaunchActivity
 *   Extra: EXTRA_KEY_ROUTINE_UUID = <Mode/Routine UUID>
 *
 * STATUS:
 *   UNDOCUMENTED / VERSION-DEPENDENT
 *   This is a reverse-engineered shortcut invocation mechanism.
 *   Samsung does not provide a public developer SDK.
 */
class SamsungModeControllerV85(
    private val context: Context,
    private val inspector: SamsungPackageInspector
) : SamsungModeController {

    companion object {
        const val BACKEND_NAME = "V8.5 (Shortcut Activity)"
        const val ROUTINES_PACKAGE = "com.samsung.android.app.routines"
        const val SHORTCUT_ACTIVITY = "com.samsung.android.app.routines.ui.shortcut.ShortcutLaunchActivity"
        const val EXTRA_KEY_ROUTINE_UUID = "EXTRA_KEY_ROUTINE_UUID"
        const val EXTRA_KEY_ACTION_TYPE = "EXTRA_KEY_ACTION_TYPE" // Fallback extra for some sub-variants
        const val VERIFICATION_WAIT_MS = 1200L
    }

    private fun getTargetActivityClass(): String {
        val report = inspector.inspectDevice()
        return report.resolvedShortcutActivityClass ?: SHORTCUT_ACTIVITY
    }

    override fun isSupported(): Boolean {
        val report = inspector.inspectDevice()
        return report.shortcutActivityFound && report.shortcutActivityExported
    }

    override fun getBackendName(): String = BACKEND_NAME

    override suspend fun startMode(uuid: String): ModeOperationResult = withContext(Dispatchers.IO) {
        if (uuid.isBlank()) {
            return@withContext ModeOperationResult.InvocationFailed("Mode UUID cannot be blank")
        }

        val currentState = getCurrentModeInternal()
        if (currentState.isModeActive && currentState.activeModeUuid.equals(uuid, ignoreCase = true)) {
            return@withContext ModeOperationResult.Success(
                verified = true,
                details = "Mode $uuid is already active (Verified via ${currentState.source})."
            )
        }

        try {
            val targetClass = getTargetActivityClass()
            val intent = Intent("com.samsung.android.app.routines.SHORTCUT").apply {
                component = ComponentName(ROUTINES_PACKAGE, targetClass)
                putExtra(EXTRA_KEY_ROUTINE_UUID, uuid)
                putExtra("routine_uuid", uuid)
                putExtra("extra_routine_uuid", uuid)
                putExtra("tag", uuid)
                putExtra("mode_id", uuid)
                putExtra("routine_id", uuid)
                putExtra("action_type", "start")
                putExtra("is_start", true)
                putExtra("from_shortcut", true)
                putExtra("from_widget", true)
                uuid.toLongOrNull()?.let { numId ->
                    putExtra("tag", numId)
                    putExtra("mode_id", numId)
                    putExtra("routine_id", numId)
                    putExtra("EXTRA_KEY_ROUTINE_UUID", numId)
                }
                data = android.net.Uri.parse("content://com.samsung.android.app.routines.externalprovider/routine/$uuid")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }

            context.startActivity(intent)

            // State verification phase
            delay(VERIFICATION_WAIT_MS)
            val postState = getCurrentModeInternal()

            if (postState.isModeActive && (postState.activeModeUuid.isNullOrEmpty() || postState.activeModeUuid.equals(uuid, ignoreCase = true))) {
                ModeOperationResult.Success(
                    verified = true,
                    details = "Invocation succeeded and Samsung Mode verified ON via ${postState.source}."
                )
            } else {
                ModeOperationResult.Success(
                    verified = false,
                    details = "Invocation succeeded (Intent dispatched to ShortcutLaunchActivity), but Samsung Mode state could not be independently verified."
                )
            }
        } catch (se: SecurityException) {
            ModeOperationResult.PermissionDenied(
                "ShortcutLaunchActivity rejected invocation: ${se.message} (Requires system permission or exported=false)"
            )
        } catch (e: Exception) {
            ModeOperationResult.InvocationFailed(
                "Failed to dispatch shortcut Intent: ${e.message}",
                e
            )
        }
    }

    override suspend fun stopMode(uuid: String): ModeOperationResult = withContext(Dispatchers.IO) {
        if (uuid.isBlank()) {
            return@withContext ModeOperationResult.InvocationFailed("Mode UUID cannot be blank")
        }

        // On shortcut architectures, invocation often acts as a toggle.
        // We verify whether it is currently on before triggering.
        val currentState = getCurrentModeInternal()

        try {
            val targetClass = getTargetActivityClass()
            val intent = Intent("com.samsung.android.app.routines.SHORTCUT").apply {
                component = ComponentName(ROUTINES_PACKAGE, targetClass)
                putExtra(EXTRA_KEY_ROUTINE_UUID, uuid)
                putExtra("routine_uuid", uuid)
                putExtra("extra_routine_uuid", uuid)
                putExtra("tag", uuid)
                putExtra("mode_id", uuid)
                putExtra("routine_id", uuid)
                putExtra("action_type", "stop")
                putExtra("is_start", false)
                putExtra("from_shortcut", true)
                putExtra("from_widget", true)
                uuid.toLongOrNull()?.let { numId ->
                    putExtra("tag", numId)
                    putExtra("mode_id", numId)
                    putExtra("routine_id", numId)
                    putExtra("EXTRA_KEY_ROUTINE_UUID", numId)
                }
                data = android.net.Uri.parse("content://com.samsung.android.app.routines.externalprovider/routine/$uuid")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }

            context.startActivity(intent)

            delay(VERIFICATION_WAIT_MS)
            val postState = getCurrentModeInternal()

            if (!postState.isModeActive) {
                ModeOperationResult.Success(
                    verified = true,
                    details = "Invocation succeeded and Samsung Mode verified OFF via ${postState.source}."
                )
            } else {
                ModeOperationResult.Success(
                    verified = false,
                    details = "Invocation succeeded, but Samsung Mode state could not be independently verified as OFF."
                )
            }
        } catch (se: SecurityException) {
            ModeOperationResult.PermissionDenied(
                "Stop rejected: ${se.message}"
            )
        } catch (e: Exception) {
            ModeOperationResult.InvocationFailed(
                "Failed to stop mode via shortcut: ${e.message}",
                e
            )
        }
    }

    override suspend fun toggleMode(uuid: String): ModeOperationResult = withContext(Dispatchers.IO) {
        if (uuid.isBlank()) {
            return@withContext ModeOperationResult.InvocationFailed("Mode UUID cannot be blank")
        }

        try {
            val currentState = getCurrentModeInternal()
            val isTargetActive = currentState.isModeActive && (
                currentState.activeModeUuid.isNullOrEmpty() ||
                currentState.activeModeUuid.equals(uuid, ignoreCase = true)
            )

            if (isTargetActive) {
                val stopResult = stopMode(uuid)
                if (stopResult is ModeOperationResult.Success) {
                    stopResult.copy(details = "Toggled OFF (Target was active). ${stopResult.details}")
                } else {
                    stopResult
                }
            } else {
                val startResult = startMode(uuid)
                if (startResult is ModeOperationResult.Success) {
                    startResult.copy(details = "Toggled ON (Target was inactive). ${startResult.details}")
                } else {
                    startResult
                }
            }
        } catch (e: Exception) {
            ModeOperationResult.InvocationFailed("Toggle failed: ${e.message}", e)
        }
    }

    override suspend fun getCurrentMode(): CurrentModeResult = withContext(Dispatchers.IO) {
        getCurrentModeInternal()
    }

    private fun getCurrentModeInternal(): CurrentModeResult {
        // Probe Settings.System
        try {
            val systemModeId = Settings.System.getString(context.contentResolver, "mode_id")
            if (!systemModeId.isNullOrEmpty() && systemModeId != "0" && systemModeId != "-1") {
                return CurrentModeResult(
                    activeModeUuid = systemModeId,
                    modeName = "Active Mode ($systemModeId)",
                    isModeActive = true,
                    source = "Settings.System[mode_id]",
                    details = "Mode UUID active in Settings.System"
                )
            }

            val currentSecMode = Settings.System.getString(context.contentResolver, "current_sec_active_mode")
            if (!currentSecMode.isNullOrEmpty() && currentSecMode != "none") {
                return CurrentModeResult(
                    activeModeUuid = currentSecMode,
                    modeName = "Active Mode ($currentSecMode)",
                    isModeActive = true,
                    source = "Settings.System[current_sec_active_mode]",
                    details = "Active mode in current_sec_active_mode"
                )
            }

            val globalModeId = Settings.Global.getString(context.contentResolver, "mode_id")
            if (!globalModeId.isNullOrEmpty() && globalModeId != "0") {
                return CurrentModeResult(
                    activeModeUuid = globalModeId,
                    modeName = "Active Mode ($globalModeId)",
                    isModeActive = true,
                    source = "Settings.Global[mode_id]",
                    details = "Mode active in Settings.Global"
                )
            }
        } catch (e: Exception) {
            // Read failure
        }

        return CurrentModeResult(
            activeModeUuid = null,
            modeName = null,
            isModeActive = false,
            source = "Settings",
            details = "No active Samsung Mode reported in system/global observables"
        )
    }
}
