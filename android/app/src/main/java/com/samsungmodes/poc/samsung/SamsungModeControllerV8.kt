package com.samsungmodes.poc.samsung

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import com.samsungmodes.poc.model.CurrentModeResult
import com.samsungmodes.poc.model.ModeOperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Samsung Mode Controller implementation for One UI 8.0 / Routine ContentProvider.
 *
 * MECHANISM:
 * Uses Samsung Modes & Routines external content provider and background receivers:
 *   Authority: com.samsung.android.app.routines.externalprovider
 *   Methods: start_manual_routine, end_manual_routine, toggle_manual_routine, execute_routine
 *   Receiver: com.samsung.android.app.routines.domainmodel.receiver.RoutineExecutionReceiver
 */
class SamsungModeControllerV8(
    private val context: Context,
    private val inspector: SamsungPackageInspector
) : SamsungModeController {

    companion object {
        const val BACKEND_NAME = "V8 (One UI 8.0 ContentProvider)"
        const val AUTHORITY = "com.samsung.android.app.routines.externalprovider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")
        
        const val METHOD_START = "start_manual_routine"
        const val METHOD_STOP = "end_manual_routine"
        const val METHOD_STOP_ALT = "stop_manual_routine"
        const val METHOD_TOGGLE = "toggle_manual_routine"
        const val METHOD_EXECUTE = "execute_routine"
        const val METHOD_GET_CURRENT = "get_current_manual_routine"
        const val ARG_UUID = "uuid"
        const val VERIFICATION_WAIT_MS = 1200L
    }

    override fun isSupported(): Boolean {
        val report = inspector.inspectDevice()
        return report.legacyProviderFound && report.legacyProviderAccessible
    }

    override fun getBackendName(): String = BACKEND_NAME

    override suspend fun startMode(uuid: String): ModeOperationResult = withContext(Dispatchers.IO) {
        if (uuid.isBlank()) {
            return@withContext ModeOperationResult.InvocationFailed("Mode UUID cannot be blank")
        }

        try {
            val cr = context.contentResolver
            val extras = Bundle().apply {
                putString(ARG_UUID, uuid)
                putString("mode_uuid", uuid)
                putString("routine_id", uuid)
                putString("id", uuid)
                putString("extra_routine_uuid", uuid)
                putString("EXTRA_KEY_ROUTINE_UUID", uuid)
                putString("tag", uuid)
                putString("action", "start")
                putBoolean("is_start", true)
                putBoolean("is_toggle", false)
                putBoolean("from_shortcut", true)
                uuid.toLongOrNull()?.let { numId ->
                    putLong("uuid", numId)
                    putLong("mode_id", numId)
                    putLong("routine_id", numId)
                    putLong("tag", numId)
                }
            }

            val methodsToTry = listOf(
                METHOD_START,
                "start_routine",
                METHOD_EXECUTE,
                "turn_on_mode",
                "run_routine",
                METHOD_TOGGLE
            )

            var resultBundle: Bundle? = null
            var successfulMethod = ""

            for (method in methodsToTry) {
                try {
                    val res = cr.call(CONTENT_URI, method, uuid, extras)
                    if (res != null) {
                        resultBundle = res
                        successfulMethod = method
                        break
                    }
                } catch (e: Exception) {
                    // Try next method
                }
            }

            // Auxiliary: Broadcast trigger to RoutineExecutionReceiver and RoutineBroadcastReceiver
            try {
                val broadcastIntent = Intent("com.samsung.android.app.routines.ACTION_RUN_ROUTINE").apply {
                    setPackage(SamsungPackageInspector.ROUTINES_PACKAGE)
                    putExtras(extras)
                }
                context.sendBroadcast(broadcastIntent)
            } catch (e: Exception) {
                // Ignore broadcast error
            }

            delay(VERIFICATION_WAIT_MS)
            val postState = getCurrentModeInternal()
            val bundleSummary = bundleToString(resultBundle)

            if (postState.isModeActive && (postState.activeModeUuid == null || postState.activeModeUuid.equals(uuid, ignoreCase = true))) {
                ModeOperationResult.Success(
                    verified = true,
                    details = "Provider start succeeded ($successfulMethod: $bundleSummary). Active mode: ${postState.activeModeUuid ?: "YES"} via ${postState.source}."
                )
            } else {
                ModeOperationResult.Success(
                    verified = false,
                    details = "Dispatched start ($successfulMethod: $bundleSummary). Waiting for system mode transition."
                )
            }
        } catch (se: SecurityException) {
            ModeOperationResult.PermissionDenied(
                "Samsung Modes provider rejected invocation: ${se.message}"
            )
        } catch (e: Exception) {
            ModeOperationResult.InvocationFailed(
                "Failed to call provider $METHOD_START: ${e.message}",
                e
            )
        }
    }

    override suspend fun stopMode(uuid: String): ModeOperationResult = withContext(Dispatchers.IO) {
        if (uuid.isBlank()) {
            return@withContext ModeOperationResult.InvocationFailed("Mode UUID cannot be blank")
        }

        try {
            val cr = context.contentResolver
            val extras = Bundle().apply {
                putString(ARG_UUID, uuid)
                putString("mode_uuid", uuid)
                putString("routine_id", uuid)
                putString("id", uuid)
                putString("extra_routine_uuid", uuid)
                putString("EXTRA_KEY_ROUTINE_UUID", uuid)
                putString("tag", uuid)
                putString("action", "stop")
                putBoolean("is_start", false)
                putBoolean("is_toggle", false)
                putBoolean("from_shortcut", true)
                uuid.toLongOrNull()?.let { numId ->
                    putLong("uuid", numId)
                    putLong("mode_id", numId)
                    putLong("routine_id", numId)
                    putLong("tag", numId)
                }
            }

            val stopMethodsToTry = listOf(
                METHOD_STOP,
                METHOD_STOP_ALT,
                "turn_off_mode",
                "cancel_routine",
                "stop_routine",
                METHOD_TOGGLE
            )

            var resultBundle: Bundle? = null
            var successfulMethod = ""

            for (method in stopMethodsToTry) {
                try {
                    val res = cr.call(CONTENT_URI, method, uuid, extras)
                    if (res != null) {
                        resultBundle = res
                        successfulMethod = method
                        break
                    }
                } catch (e: Exception) {
                    // Continue to next method
                }
            }

            // Auxiliary broadcast stop trigger
            try {
                val broadcastIntent = Intent("com.samsung.android.app.routines.ACTION_STOP_MANUAL_ROUTINE").apply {
                    setPackage(SamsungPackageInspector.ROUTINES_PACKAGE)
                    putExtras(extras)
                }
                context.sendBroadcast(broadcastIntent)
            } catch (e: Exception) {
                // Ignore
            }

            delay(VERIFICATION_WAIT_MS)
            val postState = getCurrentModeInternal()
            val bundleSummary = bundleToString(resultBundle)

            if (!postState.isModeActive) {
                ModeOperationResult.Success(
                    verified = true,
                    details = "Provider stop succeeded ($successfulMethod: $bundleSummary). Mode is now OFF."
                )
            } else {
                ModeOperationResult.Success(
                    verified = false,
                    details = "Dispatched stop ($successfulMethod: $bundleSummary). Verification state: ${postState.activeModeUuid ?: "Active"}."
                )
            }
        } catch (se: SecurityException) {
            ModeOperationResult.PermissionDenied(
                "Samsung Modes provider stop rejected: ${se.message}"
            )
        } catch (e: Exception) {
            ModeOperationResult.InvocationFailed(
                "Failed to call provider $METHOD_STOP: ${e.message}",
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
                // Mode is currently ON -> Toggle turns it OFF
                val stopResult = stopMode(uuid)
                if (stopResult is ModeOperationResult.Success) {
                    stopResult.copy(details = "Toggled OFF (Target was active). ${stopResult.details}")
                } else {
                    stopResult
                }
            } else {
                // Mode is currently OFF -> Toggle turns it ON
                val startResult = startMode(uuid)
                if (startResult is ModeOperationResult.Success) {
                    startResult.copy(details = "Toggled ON (Target was inactive). ${startResult.details}")
                } else {
                    startResult
                }
            }
        } catch (e: Exception) {
            ModeOperationResult.InvocationFailed("Failed to toggle mode: ${e.message}", e)
        }
    }

    override suspend fun getCurrentMode(): CurrentModeResult = withContext(Dispatchers.IO) {
        getCurrentModeInternal()
    }

    private fun getCurrentModeInternal(): CurrentModeResult {
        // Try provider query first
        try {
            val cr = context.contentResolver
            val bundle = cr.call(CONTENT_URI, METHOD_GET_CURRENT, null, null)
            val activeUuid = bundle?.getString("active_uuid") ?: bundle?.getString(ARG_UUID)
            if (!activeUuid.isNullOrEmpty() && activeUuid != "none" && activeUuid != "null") {
                return CurrentModeResult(
                    activeModeUuid = activeUuid,
                    modeName = "Active Mode ($activeUuid)",
                    isModeActive = true,
                    source = "Provider[$METHOD_GET_CURRENT]",
                    details = "Mode returned by external provider"
                )
            }
        } catch (e: Exception) {
            // Fall back to settings observables
        }

        // Fallback to Settings.System / Settings.Global mode keys
        val knownKeys = listOf("mode_id", "current_sec_active_mode", "lifestyle_mode_current_id", "active_routine_id")
        for (key in knownKeys) {
            try {
                val sysVal = Settings.System.getString(context.contentResolver, key)
                if (!sysVal.isNullOrEmpty() && sysVal != "0" && sysVal != "null") {
                    return CurrentModeResult(
                        activeModeUuid = sysVal,
                        modeName = "Active Mode ($sysVal)",
                        isModeActive = true,
                        source = "Settings.System[$key]",
                        details = "Observed in system settings"
                    )
                }
            } catch (e: Exception) {
                // Ignored
            }

            try {
                val globVal = Settings.Global.getString(context.contentResolver, key)
                if (!globVal.isNullOrEmpty() && globVal != "0" && globVal != "null") {
                    return CurrentModeResult(
                        activeModeUuid = globVal,
                        modeName = "Active Mode ($globVal)",
                        isModeActive = true,
                        source = "Settings.Global[$key]",
                        details = "Observed in global settings"
                    )
                }
            } catch (e: Exception) {
                // Ignored
            }
        }

        return CurrentModeResult(
            activeModeUuid = null,
            modeName = null,
            isModeActive = false,
            source = "System",
            details = "No active mode observed"
        )
    }

    private fun bundleToString(bundle: Bundle?): String {
        if (bundle == null) return "null"
        return try {
            val keys = bundle.keySet()
            val pairs = keys.map { k -> "$k=${bundle.get(k)}" }
            "Bundle[${pairs.joinToString(", ")}]"
        } catch (e: Exception) {
            bundle.toString()
        }
    }
}

