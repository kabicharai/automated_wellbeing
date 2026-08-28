# Samsung Modes & Routines Controller POC (Android 16 / One UI 8.0 & 8.5)

A single Android application (APK) acting as a proof-of-concept controller for Samsung Modes & Routines on Samsung Galaxy phones running Android 16 (specifically targeting both **One UI 8.0** and **One UI 8.5** within the exact same binary).

---

## 1. Overview & Objectives

### Target Devices
- **Hardware:** Samsung Galaxy S23 (and compatible Galaxy S-series / Z-series)
- **OS:** Android 16 (API Level 36)
- **Software Variants Tested:**
  - Phone A: One UI 8.0
  - Phone B: One UI 8.5

### Core Goal
Prove that an unprivileged, non-root 3rd-party Android app can programmatically:
1. **START** a user-created Samsung Mode configured with native **"Restrict app usage"**
2. **VERIFY** the state change through legitimate system/settings observables
3. **STOP** the Mode to restore normal app access
4. **TOGGLE** the Mode safely when the current state is known

> ⚠️ **Zero Privileged Hacks**:
> - NO AccessibilityService screen-tapping or UI automation
> - NO Root, Shizuku, or ADB wireless debugging requirements
> - NO hidden reflection into private framework classes
> - NO signature bypasses

---

## 2. Eventual Architecture (Future Roadmap)

```
  BLE Beacon / Samsung SmartTag 1
              ↓
      RSSI Observations
              ↓
       Proximity Engine (INSIDE / OUTSIDE Calibrations)
              ↓
     RestrictionController (Abstraction Layer)
              ↓
  SamsungModeController (V8 / V8.5 Runtime Selection)
              ↓
    Samsung "Restrict app usage" Native Enforcer
```

*Note: BLE, SmartTag integration, RSSI calibration, and custom app blockers are intentionally deferred to future milestones.*

---

## 3. Runtime Capability Detection & Backend Matrix

Instead of hardcoding `Build.VERSION.SDK_INT >= 36 -> One UI 8.5`, the app uses **dynamic capability probing** via `SamsungCapabilityDetector` and `SamsungPackageInspector`.

| Feature / Probe | One UI 8.0 Legacy Backend (`V8`) | One UI 8.5 Shortcut Backend (`V85`) | Unsupported Fallback |
| :--- | :--- | :--- | :--- |
| **Package** | `com.samsung.android.app.routines` | `com.samsung.android.app.routines` | Not installed / Non-Samsung |
| **Invocation Mechanism** | `ContentResolver.call()` / query to `content://com.samsung.android.app.routines.externalprovider` | Explicit Intent to `com.samsung.android.app.routines.ui.shortcut.ShortcutLaunchActivity` | None |
| **Parameters** | `start_manual_routine`, `end_manual_routine`, `toggle_manual_routine` + UUID arg | Extra `EXTRA_KEY_ROUTINE_UUID` (String) | None |
| **Exported Status** | Checked via `PackageManager.getProviderInfo()` | Checked via `PackageManager.getActivityInfo()` | N/A |
| **Integration Type** | Undocumented / Provider-Based | Undocumented / Shortcut Activity | Safe No-Op |

### Controller Abstraction
```kotlin
interface SamsungModeController {
    suspend fun startMode(uuid: String): ModeOperationResult
    suspend fun stopMode(uuid: String): ModeOperationResult
    suspend fun toggleMode(uuid: String): ModeOperationResult
    suspend fun getCurrentMode(): CurrentModeResult
    fun isSupported(): Boolean
}
```

---

## 4. How to Configure the Test Samsung Mode

To verify that the native "Restrict app usage" action works:

1. On your Samsung Galaxy S23, open **Settings** → **Modes and Routines**.
2. Tap **Add mode** (or the **+** button) and name it (e.g. `Focus Test`).
3. Under **Stay focused**, select **Restrict app usage**.
4. Choose 1 or 2 test apps you want to restrict (e.g., YouTube, Calculator, or a sample game).
5. Save the Mode.

### Obtaining the Mode UUID
- **Method 1 (Samsung Settings Read)**: Tap **[READ CURRENT MODE]** in the app while the Mode is active in the background. The app reads `Settings.System.getString(cr, "mode_id")` or `Settings.Global`.
- **Method 2 (Routine Shortcut Inspection)**: In Samsung Modes & Routines, tap the 3-dots menu on your Mode/Routine → **Add to Home screen**. Inspect the created shortcut Intent using an intent viewer or ADB logcat (`ActivityTaskManager: START u0 ... EXTRA_KEY_ROUTINE_UUID=...`).
- **Method 3 (Manual Entry)**: Paste the discovered UUID string into the **Mode UUID** field in the app UI.

---

## 5. Running the POC Application

1. Open the app on the test device.
2. Verify the **Diagnostics** card:
   - Device: `Samsung Galaxy S23`
   - Android Version: `16 (API 36)`
   - One UI: `8.0` or `8.5`
   - Selected Backend: `V8` or `V85`
3. Enter your **Mode UUID**.
4. Tap **[START MODE]**:
   - Check log for invocation status.
   - Observe if the Mode icon appears in the Samsung status bar and restricted apps are locked.
5. Tap **[STOP MODE]**:
   - Verify that the restriction is lifted and apps can open.
6. Tap **[RUN FULL TEST]**:
   - Automatically executes the 7-step test sequence (Off → Start → Verify On → Stop → Verify Off).

---

## 6. Structured Operation Results & State Verification

The app strictly distinguishes **Invocation Success** (Intent fired without crash) from **Mode State Verified** (observable state change detected):

```kotlin
sealed class ModeOperationResult {
    data class Success(val verified: Boolean, val details: String)
    data class NotSupported(val reason: String)
    data class PermissionDenied(val reason: String)
    data class InvocationFailed(val reason: String, val exception: Throwable?)
    data class VerificationFailed(val reason: String)
}
```

---

## 7. Known Limitations & Technical Warnings

1. **Undocumented API Warning**: Samsung does not provide a public SDK for 3rd-party Mode toggling. The Shortcut Activity and ContentProvider mechanisms are reverse-engineered internal endpoints.
2. **Permission Boundary**: If future One UI minor updates enforce `signatureOrSystem` permissions on `ShortcutLaunchActivity` or drop `android:exported="true"`, third-party applications without system signatures will receive a clean `PermissionDenied` result.
3. **Toggle vs Direct Action**: Certain shortcut implementations trigger toggle behavior rather than strict idempotent Start/Stop. The app queries state before invoking toggles to prevent inverted state transitions.

---

## 8. Building the Android APK

### Prerequisites
- Android Studio Ladybug (2024.2+) or newer
- JDK 17+
- Android SDK 36 (Android 16 Developer Preview / Release)

### Command Line Build
```bash
cd android
./gradlew assembleDebug
# Output APK: app/build/outputs/apk/debug/app-debug.apk
```
