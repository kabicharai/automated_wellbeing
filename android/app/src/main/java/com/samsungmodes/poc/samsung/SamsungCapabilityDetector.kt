package com.samsungmodes.poc.samsung

import android.content.Context
import android.util.Log

/**
 * Runtime capability detector that inspects the target device and selects
 * the appropriate [SamsungModeController] implementation.
 *
 * Does NOT hardcode Android version alone (e.g. does not assume Android >= 16 is always V8.5),
 * but probes actual installed package components and export statuses.
 */
class SamsungCapabilityDetector(private val context: Context) {

    companion object {
        const val TAG = "SamsungCapDetector"
    }

    private val inspector = SamsungPackageInspector(context)

    data class DetectionResult(
        val controller: SamsungModeController,
        val report: SamsungPackageInspector.DiagnosticReport,
        val rationale: String
    )

    fun detectAndCreateController(): DetectionResult {
        val report = inspector.inspectDevice()

        Log.d(TAG, "Detecting Samsung Modes capabilities on ${report.manufacturer} ${report.deviceModel}")

        // Check 1: One UI 8.0 External Content Provider Mechanism (Silent background execution)
        if (report.legacyProviderFound && report.legacyProviderAccessible) {
            val v8 = SamsungModeControllerV8(context, inspector)
            return DetectionResult(
                controller = v8,
                report = report,
                rationale = "External Routine ContentProvider detected and accessible. Selected Backend: V8 (One UI 8.0 ContentProvider)"
            )
        }

        // Check 2: One UI 8.5 Shortcut Activity Mechanism
        if (report.shortcutActivityFound && report.shortcutActivityExported) {
            val v85 = SamsungModeControllerV85(context, inspector)
            return DetectionResult(
                controller = v85,
                report = report,
                rationale = "ShortcutLaunchActivity detected and exported. Selected Backend: V8.5 (One UI 8.5+)"
            )
        }

        // Check 3: Unsupported Fallbacks with precise diagnosis
        val failureReason = when {
            !report.packageInstalled ->
                "Samsung Modes & Routines package (${SamsungPackageInspector.ROUTINES_PACKAGE}) is not installed on this device."

            report.shortcutActivityFound && !report.shortcutActivityExported ->
                "ShortcutLaunchActivity is present but NOT exported (android:exported=false). Third-party apps cannot invoke it."

            report.legacyProviderFound && !report.legacyProviderAccessible ->
                "Legacy provider is present but rejected third-party access (${report.legacyProviderError ?: "Permission Denied"})."

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
}
