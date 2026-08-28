package com.samsungmodes.poc.samsung

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Diagnostic inspector for Samsung Modes & Routines package, activities,
 * providers, and system state observables.
 */
class SamsungPackageInspector(private val context: Context) {

    companion object {
        const val TAG = "SamsungPackageInspector"
        const val ROUTINES_PACKAGE = "com.samsung.android.app.routines"
        const val SHORTCUT_ACTIVITY = "com.samsung.android.app.routines.ui.shortcut.ShortcutLaunchActivity"
        const val LEGACY_PROVIDER_AUTHORITY = "com.samsung.android.app.routines.externalprovider"
        val LEGACY_PROVIDER_URI: Uri = Uri.parse("content://$LEGACY_PROVIDER_AUTHORITY")
    }

    data class DiagnosticReport(
        val deviceModel: String,
        val manufacturer: String,
        val androidVersion: String,
        val sdkVersion: Int,
        val oneUiVersion: String,
        val packageInstalled: Boolean,
        val packageVersionName: String?,
        val packageVersionCode: Long?,
        val shortcutActivityFound: Boolean,
        val shortcutActivityExported: Boolean,
        val shortcutActivityPermission: String?,
        val resolvedShortcutActivityClass: String?,
        val legacyProviderFound: Boolean,
        val legacyProviderExported: Boolean,
        val legacyProviderAccessible: Boolean,
        val legacyProviderError: String?,
        val systemModeIdValue: String?,
        val globalModeIdValue: String?,
        val secureModeIdValue: String?,
        val availableBackend: String,
        val summaryLogs: List<String>
    )

    fun inspectDevice(): DiagnosticReport {
        val logs = mutableListOf<String>()
        val pm = context.packageManager

        val deviceModel = Build.MODEL ?: "Unknown"
        val manufacturer = Build.MANUFACTURER ?: "Unknown"
        val androidVersion = Build.VERSION.RELEASE ?: "Unknown"
        val sdkVersion = Build.VERSION.SDK_INT
        val oneUiVersion = detectOneUiVersion()

        logs.add("Probing device: $manufacturer $deviceModel (Android $androidVersion, SDK $sdkVersion, One UI: $oneUiVersion)")

        // 1. Inspect package
        var packageInstalled = false
        var versionName: String? = null
        var versionCode: Long? = null

        try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(ROUTINES_PACKAGE, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong() or PackageManager.GET_PROVIDERS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(ROUTINES_PACKAGE, PackageManager.GET_ACTIVITIES or PackageManager.GET_PROVIDERS)
            }
            packageInstalled = true
            versionName = pkgInfo.versionName
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
            logs.add("Package $ROUTINES_PACKAGE found (Version: $versionName, Code: $versionCode)")
        } catch (e: PackageManager.NameNotFoundException) {
            logs.add("Package $ROUTINES_PACKAGE NOT installed on this device.")
        } catch (e: Exception) {
            logs.add("Error querying package $ROUTINES_PACKAGE: ${e.message}")
        }

        // 2. Inspect Activities, Receivers, and Shortcut Candidates
        var shortcutFound = false
        var shortcutExported = false
        var shortcutPerm: String? = null
        var resolvedShortcutActivityClass: String? = null
        val exportedActivities = mutableListOf<String>()
        val exportedReceivers = mutableListOf<String>()

        try {
            val fullPkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    ROUTINES_PACKAGE,
                    PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_ACTIVITIES.toLong() or
                        PackageManager.GET_RECEIVERS.toLong() or
                        PackageManager.GET_PROVIDERS.toLong() or
                        PackageManager.GET_PERMISSIONS.toLong()
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(
                    ROUTINES_PACKAGE,
                    PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS
                )
            }

            // Log declared permissions
            fullPkgInfo.requestedPermissions?.forEach { p ->
                if (p.contains("routine", ignoreCase = true)) {
                    logs.add("Routines declared permission: $p")
                }
            }

            // Scan all activities in package
            fullPkgInfo.activities?.forEach { act ->
                if (act.exported) {
                    exportedActivities.add(act.name)
                    logs.add("Exported Activity in package: ${act.name} (permission: ${act.permission ?: "none"})")
                }
            }

            // Scan receivers
            fullPkgInfo.receivers?.forEach { rec ->
                if (rec.exported) {
                    exportedReceivers.add(rec.name)
                    logs.add("Exported Receiver in package: ${rec.name} (permission: ${rec.permission ?: "none"})")
                }
            }

            // Check primary shortcut activity candidate
            val primaryIntent = Intent().apply {
                setClassName(ROUTINES_PACKAGE, SHORTCUT_ACTIVITY)
            }
            val primaryResolve = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(primaryIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(primaryIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }

            if (primaryResolve != null && primaryResolve.activityInfo.exported) {
                shortcutFound = true
                shortcutExported = true
                shortcutPerm = primaryResolve.activityInfo.permission
                resolvedShortcutActivityClass = SHORTCUT_ACTIVITY
                logs.add("Primary ShortcutLaunchActivity verified: exported=true, class=$SHORTCUT_ACTIVITY")
            } else {
                // Search for genuine shortcut activity (NOT main navigation tabs like MainModeTabLaunchActivity)
                val candidate = exportedActivities.firstOrNull { name ->
                    val isNavigationTab = name.contains("MainModeTab", ignoreCase = true) ||
                            name.contains("MainRoutineTab", ignoreCase = true) ||
                            name.contains("RoutineMainTab", ignoreCase = true) ||
                            name.contains("Settings", ignoreCase = true) ||
                            name.contains("Search", ignoreCase = true) ||
                            name.contains("DetailActivity", ignoreCase = true) ||
                            name.contains("About", ignoreCase = true)
                    
                    !isNavigationTab && (
                        name.contains("shortcut", ignoreCase = true) ||
                        name.contains("trampoline", ignoreCase = true) ||
                        name.endsWith(".RoutineLaunchActivity")
                    )
                }

                if (candidate != null) {
                    shortcutFound = true
                    shortcutExported = true
                    resolvedShortcutActivityClass = candidate
                    logs.add("Discovered exported shortcut candidate: $candidate")
                } else {
                    logs.add("ShortcutLaunchActivity not found or not exported for 3rd-party background invocation.")
                }
            }
        } catch (e: Exception) {
            logs.add("Exception while inspecting components in $ROUTINES_PACKAGE: ${e.message}")
        }

        // 3. Inspect Legacy External Provider (One UI 8.0 target)
        var legacyFound = false
        var legacyExported = false
        var legacyAccessible = false
        var legacyError: String? = null

        try {
            val providerInfo: ProviderInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveContentProvider(LEGACY_PROVIDER_AUTHORITY, PackageManager.ComponentInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveContentProvider(LEGACY_PROVIDER_AUTHORITY, 0)
            }

            if (providerInfo != null) {
                legacyFound = true
                legacyExported = providerInfo.exported
                logs.add("Legacy Provider found ($LEGACY_PROVIDER_AUTHORITY): exported=$legacyExported, readPerm=${providerInfo.readPermission}, writePerm=${providerInfo.writePermission}")

                // Probe accessibility without mutating
                try {
                    val cr = context.contentResolver
                    // First try call() method probe
                    val testBundle = cr.call(LEGACY_PROVIDER_URI, "get_current_manual_routine", null, null)
                    legacyAccessible = true
                    logs.add("Legacy Provider call() succeeded: active=${testBundle?.getString("active_uuid") ?: "none"}")
                } catch (se1: SecurityException) {
                    try {
                        val cr = context.contentResolver
                        val cursor = cr.query(LEGACY_PROVIDER_URI, null, null, null, null)
                        cursor?.close()
                        legacyAccessible = true
                        logs.add("Legacy Provider query() succeeded.")
                    } catch (se2: SecurityException) {
                        legacyAccessible = false
                        legacyError = "SecurityException: ${se2.message}"
                        logs.add("Legacy Provider rejected by system: ${se2.message}")
                    } catch (e2: Exception) {
                        legacyAccessible = false
                        legacyError = "${e2.javaClass.simpleName}: ${e2.message}"
                        logs.add("Legacy Provider query returned: ${e2.message}")
                    }
                } catch (e: Exception) {
                    legacyAccessible = false
                    legacyError = "${e.javaClass.simpleName}: ${e.message}"
                    logs.add("Legacy Provider probe returned: ${e.message}")
                }
            } else {
                logs.add("Legacy Provider $LEGACY_PROVIDER_AUTHORITY NOT found.")
            }
        } catch (e: Exception) {
            legacyError = e.message
            logs.add("Exception inspecting legacy provider: ${e.message}")
        }

        // 4. Inspect Settings observables for active Mode UUID
        val sysMode = readSetting(Settings.System.CONTENT_URI, "mode_id")
            ?: readSetting(Settings.System.CONTENT_URI, "current_sec_active_mode")
        val globMode = readSetting(Settings.Global.CONTENT_URI, "mode_id")
        val secMode = readSetting(Settings.Secure.CONTENT_URI, "mode_id")

        if (sysMode != null) logs.add("Settings.System mode_id observed: $sysMode")
        if (globMode != null) logs.add("Settings.Global mode_id observed: $globMode")
        if (secMode != null) logs.add("Settings.Secure mode_id observed: $secMode")

        // 5. Compute Selected Backend
        val selectedBackend = when {
            shortcutFound && shortcutExported -> "V85 (One UI 8.5+ ShortcutLaunchActivity)"
            legacyFound && legacyAccessible -> "V8 (One UI 8.0 ExternalProvider)"
            packageInstalled -> "Unsupported (Package found, but no exposed invocation endpoint accessible)"
            else -> "Unsupported (Samsung Modes & Routines not detected)"
        }
        logs.add("Final capability resolution: $selectedBackend")

        return DiagnosticReport(
            deviceModel = deviceModel,
            manufacturer = manufacturer,
            androidVersion = androidVersion,
            sdkVersion = sdkVersion,
            oneUiVersion = oneUiVersion,
            packageInstalled = packageInstalled,
            packageVersionName = versionName,
            packageVersionCode = versionCode,
            shortcutActivityFound = shortcutFound,
            shortcutActivityExported = shortcutExported,
            shortcutActivityPermission = shortcutPerm,
            resolvedShortcutActivityClass = resolvedShortcutActivityClass,
            legacyProviderFound = legacyFound,
            legacyProviderExported = legacyExported,
            legacyProviderAccessible = legacyAccessible,
            legacyProviderError = legacyError,
            systemModeIdValue = sysMode,
            globalModeIdValue = globMode,
            secureModeIdValue = secMode,
            availableBackend = selectedBackend,
            summaryLogs = logs
        )
    }

    private fun readSetting(baseUri: Uri, key: String): String? {
        return try {
            when (baseUri) {
                Settings.System.CONTENT_URI -> Settings.System.getString(context.contentResolver, key)
                Settings.Global.CONTENT_URI -> Settings.Global.getString(context.contentResolver, key)
                Settings.Secure.CONTENT_URI -> Settings.Secure.getString(context.contentResolver, key)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reads Samsung One UI version using system property reflection if available safely.
     */
    private fun detectOneUiVersion(): String {
        return try {
            val semPlatformVerField = Class.forName("com.samsung.android.feature.SemFloatingFeature")
                .getDeclaredMethod("getString", String::class.java)
            val result = semPlatformVerField.invoke(null, "SEC_FLOATING_FEATURE_COMMON_CONFIG_ONEUI_VERSION") as? String
            if (!result.isNullOrBlank()) return result
            
            // System property fallback
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getDeclaredMethod("get", String::class.java, String::class.java)
            val oneUiProp = getMethod.invoke(null, "ro.build.version.oneui", "") as? String
            if (!oneUiProp.isNullOrBlank()) {
                val num = oneUiProp.toIntOrNull()
                if (num != null) {
                    val major = num / 10000
                    val minor = (num % 10000) / 100
                    return "$major.$minor"
                }
                return oneUiProp
            }
            "Unknown (Non-Samsung or Protected)"
        } catch (e: Exception) {
            if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
                // Infer probable One UI version based on Android 16 (SDK 36)
                if (Build.VERSION.SDK_INT >= 36) "8.0 / 8.5 (Inferred Android 16)" else "7.x (Inferred)"
            } else {
                "Non-Samsung Device"
            }
        }
    }
}
