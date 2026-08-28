import { AndroidSourceFile } from '../types';

export const ANDROID_FILES: AndroidSourceFile[] = [
  {
    path: 'samsung/SamsungModeController.kt',
    name: 'SamsungModeController.kt',
    category: 'samsung',
    language: 'kotlin',
    description: 'Core abstraction interface for Samsung Mode start, stop, toggle, and current state discovery.',
    content: `package com.samsungmodes.poc.samsung

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
}`
  },
  {
    path: 'samsung/SamsungModeControllerV85.kt',
    name: 'SamsungModeControllerV85.kt',
    category: 'samsung',
    language: 'kotlin',
    description: 'One UI 8.5+ implementation using explicit Intent to ShortcutLaunchActivity with EXTRA_KEY_ROUTINE_UUID.',
    content: `package com.samsungmodes.poc.samsung

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
        const val VERIFICATION_WAIT_MS = 1200L
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
                details = "Mode $uuid is already active (Verified via \${currentState.source})."
            )
        }

        try {
            val intent = Intent().apply {
                component = ComponentName(ROUTINES_PACKAGE, SHORTCUT_ACTIVITY)
                putExtra(EXTRA_KEY_ROUTINE_UUID, uuid)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }

            context.startActivity(intent)

            // State verification phase
            delay(VERIFICATION_WAIT_MS)
            val postState = getCurrentModeInternal()

            if (postState.isModeActive && (postState.activeModeUuid.isNullOrEmpty() || postState.activeModeUuid.equals(uuid, ignoreCase = true))) {
                ModeOperationResult.Success(
                    verified = true,
                    details = "Invocation succeeded and Samsung Mode verified ON via \${postState.source}."
                )
            } else {
                ModeOperationResult.Success(
                    verified = false,
                    details = "Invocation succeeded (Intent dispatched to ShortcutLaunchActivity), but Samsung Mode state could not be independently verified."
                )
            }
        } catch (se: SecurityException) {
            ModeOperationResult.PermissionDenied(
                "ShortcutLaunchActivity rejected invocation: \${se.message} (Requires system permission or exported=false)"
            )
        } catch (e: Exception) {
            ModeOperationResult.InvocationFailed(
                "Failed to dispatch shortcut Intent: \${e.message}",
                e
            )
        }
    }

    override suspend fun stopMode(uuid: String): ModeOperationResult = withContext(Dispatchers.IO) {
        if (uuid.isBlank()) {
            return@withContext ModeOperationResult.InvocationFailed("Mode UUID cannot be blank")
        }

        try {
            val intent = Intent().apply {
                component = ComponentName(ROUTINES_PACKAGE, SHORTCUT_ACTIVITY)
                putExtra(EXTRA_KEY_ROUTINE_UUID, uuid)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }

            context.startActivity(intent)

            delay(VERIFICATION_WAIT_MS)
            val postState = getCurrentModeInternal()

            if (!postState.isModeActive) {
                ModeOperationResult.Success(
                    verified = true,
                    details = "Invocation succeeded and Samsung Mode verified OFF via \${postState.source}."
                )
            } else {
                ModeOperationResult.Success(
                    verified = false,
                    details = "Invocation succeeded, but Samsung Mode state could not be independently verified as OFF."
                )
            }
        } catch (se: SecurityException) {
            ModeOperationResult.PermissionDenied("Stop rejected: \${se.message}")
        } catch (e: Exception) {
            ModeOperationResult.InvocationFailed("Failed to stop mode via shortcut: \${e.message}", e)
        }
    }

    override suspend fun toggleMode(uuid: String): ModeOperationResult = withContext(Dispatchers.IO) {
        if (uuid.isBlank()) {
            return@withContext ModeOperationResult.InvocationFailed("Mode UUID cannot be blank")
        }

        try {
            val intent = Intent().apply {
                component = ComponentName(ROUTINES_PACKAGE, SHORTCUT_ACTIVITY)
                putExtra(EXTRA_KEY_ROUTINE_UUID, uuid)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }

            context.startActivity(intent)
            delay(VERIFICATION_WAIT_MS)
            val postState = getCurrentModeInternal()

            ModeOperationResult.Success(
                verified = postState.isModeActive,
                details = "Shortcut Intent dispatched. State is now: \${if (postState.isModeActive) "ACTIVE" else "INACTIVE"}"
            )
        } catch (e: Exception) {
            ModeOperationResult.InvocationFailed("Toggle failed: \${e.message}", e)
        }
    }

    override suspend fun getCurrentMode(): CurrentModeResult = withContext(Dispatchers.IO) {
        getCurrentModeInternal()
    }

    private fun getCurrentModeInternal(): CurrentModeResult {
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
        } catch (e: Exception) {}

        return CurrentModeResult(
            activeModeUuid = null,
            modeName = null,
            isModeActive = false,
            source = "Settings",
            details = "No active Samsung Mode reported in system observables"
        )
    }
}`
  },
  {
    path: 'samsung/SamsungModeControllerV8.kt',
    name: 'SamsungModeControllerV8.kt',
    category: 'samsung',
    language: 'kotlin',
    description: 'One UI 8.0 / Legacy implementation using ContentResolver.call() to externalprovider.',
    content: `package com.samsungmodes.poc.samsung

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import com.samsungmodes.poc.model.CurrentModeResult
import com.samsungmodes.poc.model.ModeOperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Samsung Mode Controller implementation for One UI 8.0 / Legacy versions.
 *
 * MECHANISM:
 * Uses Samsung Modes & Routines external content provider:
 *   Authority: com.samsung.android.app.routines.externalprovider
 *   Methods: start_manual_routine, end_manual_routine, toggle_manual_routine
 */
class SamsungModeControllerV8(
    private val context: Context,
    private val inspector: SamsungPackageInspector
) : SamsungModeController {

    companion object {
        const val BACKEND_NAME = "V8 (Legacy External Provider)"
        const val AUTHORITY = "com.samsung.android.app.routines.externalprovider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")
        
        const val METHOD_START = "start_manual_routine"
        const val METHOD_STOP = "end_manual_routine"
        const val METHOD_TOGGLE = "toggle_manual_routine"
        const val METHOD_GET_CURRENT = "get_current_manual_routine"
        const val ARG_UUID = "uuid"
        const val VERIFICATION_WAIT_MS = 1000L
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
            }

            val resultBundle = cr.call(CONTENT_URI, METHOD_START, uuid, extras)

            delay(VERIFICATION_WAIT_MS)
            val postState = getCurrentModeInternal()

            if (postState.isModeActive && (postState.activeModeUuid == null || postState.activeModeUuid.equals(uuid, ignoreCase = true))) {
                ModeOperationResult.Success(
                    verified = true,
                    details = "Provider call ($METHOD_START) returned $resultBundle. State verified ON via \${postState.source}."
                )
            } else {
                ModeOperationResult.Success(
                    verified = false,
                    details = "Provider call ($METHOD_START) dispatched, but Samsung Mode state could not be independently verified."
                )
            }
        } catch (se: SecurityException) {
            ModeOperationResult.PermissionDenied(
                "Legacy Samsung Modes provider is not accessible to third-party apps: \${se.message}"
            )
        } catch (e: Exception) {
            ModeOperationResult.InvocationFailed(
                "Failed to call legacy provider $METHOD_START: \${e.message}",
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
            }

            val resultBundle = cr.call(CONTENT_URI, METHOD_STOP, uuid, extras)

            delay(VERIFICATION_WAIT_MS)
            val postState = getCurrentModeInternal()

            if (!postState.isModeActive) {
                ModeOperationResult.Success(
                    verified = true,
                    details = "Provider call ($METHOD_STOP) completed. State verified OFF."
                )
            } else {
                ModeOperationResult.Success(
                    verified = false,
                    details = "Provider call ($METHOD_STOP) completed, but Samsung Mode state could not be verified as OFF."
                )
            }
        } catch (se: SecurityException) {
            ModeOperationResult.PermissionDenied(
                "Legacy Samsung Modes provider stop rejected: \${se.message}"
            )
        } catch (e: Exception) {
            ModeOperationResult.InvocationFailed(
                "Failed to call legacy provider $METHOD_STOP: \${e.message}",
                e
            )
        }
    }

    override suspend fun toggleMode(uuid: String): ModeOperationResult = withContext(Dispatchers.IO) {
        if (uuid.isBlank()) {
            return@withContext ModeOperationResult.InvocationFailed("Mode UUID cannot be blank")
        }

        try {
            val cr = context.contentResolver
            val extras = Bundle().apply {
                putString(ARG_UUID, uuid)
            }

            val resultBundle = cr.call(CONTENT_URI, METHOD_TOGGLE, uuid, extras)
            delay(VERIFICATION_WAIT_MS)
            val postState = getCurrentModeInternal()

            ModeOperationResult.Success(
                verified = postState.isModeActive,
                details = "Legacy toggle dispatched ($resultBundle). Current state is \${if (postState.isModeActive) "ACTIVE" else "INACTIVE"}."
            )
        } catch (se: SecurityException) {
            ModeOperationResult.PermissionDenied(
                "Legacy provider toggle rejected: \${se.message}"
            )
        } catch (e: Exception) {
            ModeOperationResult.InvocationFailed("Legacy toggle failed: \${e.message}", e)
        }
    }

    override suspend fun getCurrentMode(): CurrentModeResult = withContext(Dispatchers.IO) {
        getCurrentModeInternal()
    }

    private fun getCurrentModeInternal(): CurrentModeResult {
        try {
            val sysMode = Settings.System.getString(context.contentResolver, "mode_id")
            if (!sysMode.isNullOrEmpty() && sysMode != "0") {
                return CurrentModeResult(
                    activeModeUuid = sysMode,
                    modeName = "Active Mode ($sysMode)",
                    isModeActive = true,
                    source = "Settings.System[mode_id]",
                    details = "Observed in system settings"
                )
            }
        } catch (e: Exception) {}

        return CurrentModeResult(
            activeModeUuid = null,
            modeName = null,
            isModeActive = false,
            source = "System",
            details = "No active mode observed"
        )
    }
}`
  },
  {
    path: 'samsung/SamsungCapabilityDetector.kt',
    name: 'SamsungCapabilityDetector.kt',
    category: 'samsung',
    language: 'kotlin',
    description: 'Runtime detector that inspects PackageManager and selects the appropriate controller without hardcoding Android version.',
    content: `package com.samsungmodes.poc.samsung

import android.content.Context
import android.util.Log

/**
 * Runtime capability detector that inspects the target device and selects
 * the appropriate [SamsungModeController] implementation.
 */
class SamsungCapabilityDetector(private val context: Context) {

    private val inspector = SamsungPackageInspector(context)

    data class DetectionResult(
        val controller: SamsungModeController,
        val report: SamsungPackageInspector.DiagnosticReport,
        val rationale: String
    )

    fun detectAndCreateController(): DetectionResult {
        val report = inspector.inspectDevice()

        // Check 1: One UI 8.5 Shortcut Activity Mechanism
        if (report.shortcutActivityFound && report.shortcutActivityExported) {
            val v85 = SamsungModeControllerV85(context, inspector)
            return DetectionResult(
                controller = v85,
                report = report,
                rationale = "ShortcutLaunchActivity detected and exported. Selected Backend: V8.5 (One UI 8.5+)"
            )
        }

        // Check 2: One UI 8.0 Legacy External Content Provider Mechanism
        if (report.legacyProviderFound && report.legacyProviderAccessible) {
            val v8 = SamsungModeControllerV8(context, inspector)
            return DetectionResult(
                controller = v8,
                report = report,
                rationale = "Legacy external provider detected and accessible. Selected Backend: V8 (One UI 8.0)"
            )
        }

        // Check 3: Unsupported Fallbacks with precise diagnosis
        val failureReason = when {
            !report.packageInstalled ->
                "Samsung Modes & Routines package is not installed on this device."

            report.shortcutActivityFound && !report.shortcutActivityExported ->
                "ShortcutLaunchActivity is present but NOT exported. Third-party apps cannot invoke it."

            report.legacyProviderFound && !report.legacyProviderAccessible ->
                "Legacy provider is present but rejected third-party access."

            else ->
                "No accessible Samsung Modes & Routines invocation endpoint found on this device."
        }

        val unsupported = UnsupportedSamsungModeController(failureReason)
        return DetectionResult(
            controller = unsupported,
            report = report,
            rationale = "Fallback: $failureReason"
        )
    }

    fun getInspector(): SamsungPackageInspector = inspector
}`
  },
  {
    path: 'restriction/RestrictionController.kt',
    name: 'RestrictionController.kt',
    category: 'restriction',
    language: 'kotlin',
    description: 'High-level restriction abstraction preparing for future BLE beacon & SmartTag proximity engines.',
    content: `package com.samsungmodes.poc.restriction

import com.samsungmodes.poc.model.RestrictionProfile
import com.samsungmodes.poc.model.RestrictionState

/**
 * High-level restriction abstraction layer.
 * Designed so the future BLE beacon / Samsung SmartTag proximity engine
 * can seamlessly trigger restrictions without coupling to Samsung Mode internals.
 */
interface RestrictionController {
    suspend fun enable(profile: RestrictionProfile): RestrictionState
    suspend fun disable(profile: RestrictionProfile): RestrictionState
    suspend fun currentState(): RestrictionState
}`
  },
  {
    path: 'restriction/SamsungModesRestrictionController.kt',
    name: 'SamsungModesRestrictionController.kt',
    category: 'restriction',
    language: 'kotlin',
    description: 'Implementation that bridges RestrictionController to SamsungModeController.',
    content: `package com.samsungmodes.poc.restriction

import com.samsungmodes.poc.model.ModeOperationResult
import com.samsungmodes.poc.model.RestrictionProfile
import com.samsungmodes.poc.model.RestrictionState
import com.samsungmodes.poc.samsung.SamsungModeController

class SamsungModesRestrictionController(
    private val modeController: SamsungModeController
) : RestrictionController {

    private var activeProfile: RestrictionProfile? = null

    override suspend fun enable(profile: RestrictionProfile): RestrictionState {
        if (!modeController.isSupported()) {
            return RestrictionState.Error("SamsungModeController is not supported on this device.")
        }

        val result = modeController.startMode(profile.samsungModeUuid)
        return when (result) {
            is ModeOperationResult.Success -> {
                activeProfile = profile
                RestrictionState.Active(
                    profileId = profile.id,
                    verified = result.verified
                )
            }
            else -> RestrictionState.Error("Failed to enable restriction: $result")
        }
    }

    override suspend fun disable(profile: RestrictionProfile): RestrictionState {
        val result = modeController.stopMode(profile.samsungModeUuid)
        return when (result) {
            is ModeOperationResult.Success -> {
                activeProfile = null
                RestrictionState.Inactive
            }
            else -> RestrictionState.Error("Failed to disable restriction: $result")
        }
    }

    override suspend fun currentState(): RestrictionState {
        val currentMode = modeController.getCurrentMode()
        return if (currentMode.isModeActive) {
            RestrictionState.Active(
                profileId = currentMode.activeModeUuid ?: activeProfile?.id ?: "unknown",
                verified = true
            )
        } else {
            RestrictionState.Inactive
        }
    }
}`
  },
  {
    path: 'AndroidManifest.xml',
    name: 'AndroidManifest.xml',
    category: 'config',
    language: 'xml',
    description: 'Android manifest with Package Visibility <queries> tags for Android 11-16.',
    content: `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Package Visibility Queries for Android 11+ (API 30+) / Android 16 -->
    <queries>
        <package android:name="com.samsung.android.app.routines" />
        <provider android:authorities="com.samsung.android.app.routines.externalprovider" />
        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent>
        <intent>
            <action android:name="com.samsung.android.app.routines.SHORTCUT" />
        </intent>
    </queries>

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:icon="@android:drawable/ic_dialog_info"
        android:label="Samsung Modes POC"
        android:theme="@style/Theme.SamsungModesPOC">
        
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>`
  },
  {
    path: 'app/build.gradle.kts',
    name: 'build.gradle.kts (app)',
    category: 'config',
    language: 'gradle',
    description: 'Gradle build script targeting Android 16 (compileSdk 36) with Jetpack Compose BOM.',
    content: `plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.samsungmodes.poc"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.samsungmodes.poc"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0-poc"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}`
  }
];
