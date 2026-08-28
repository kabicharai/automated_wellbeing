import React from 'react';
import { BookOpen, AlertTriangle, ShieldCheck, Terminal, Smartphone, HelpCircle } from 'lucide-react';

export const DocumentationView: React.FC = () => {
  return (
    <div className="space-y-6 text-sm text-neutral-300 leading-relaxed">
      {/* Overview Card */}
      <div className="p-6 rounded-2xl bg-neutral-900 border border-neutral-800 space-y-3">
        <div className="flex items-center gap-2">
          <BookOpen className="w-5 h-5 text-blue-400" />
          <h2 className="text-base font-bold text-white">Samsung Modes POC Documentation & Technical Notes</h2>
        </div>
        <p className="text-xs text-neutral-400">
          This single Android APK implements capability-aware programmatic control of user-created Samsung Modes on Android 16 (targeting both One UI 8.0 and One UI 8.5 devices).
        </p>
      </div>

      {/* Step-by-Step Setup Guide */}
      <div className="p-6 rounded-2xl bg-neutral-900 border border-neutral-800 space-y-4">
        <h3 className="text-sm font-bold text-white flex items-center gap-2">
          <Smartphone className="w-4 h-4 text-emerald-400" />
          1. Creating the Test Samsung Mode on Galaxy S23
        </h3>
        <ol className="list-decimal list-inside space-y-2 text-xs text-neutral-300 pl-1">
          <li>On your Samsung Galaxy S23, open <strong>Settings</strong> → <strong>Modes and Routines</strong>.</li>
          <li>Tap <strong>Add mode</strong> (or the <strong>+</strong> button in the top right corner).</li>
          <li>Name the Mode (e.g., <code className="text-blue-400 font-mono">Test Focus</code>).</li>
          <li>Under the <strong>Stay focused</strong> section, tap <strong>Restrict app usage</strong>.</li>
          <li>Select 1 or 2 test apps (such as a browser or calculator) and save the mode.</li>
        </ol>
      </div>

      {/* UUID Discovery Guide */}
      <div className="p-6 rounded-2xl bg-neutral-900 border border-neutral-800 space-y-4">
        <h3 className="text-sm font-bold text-white flex items-center gap-2">
          <HelpCircle className="w-4 h-4 text-purple-400" />
          2. How to Obtain the Mode UUID
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
          <div className="p-4 bg-neutral-950 rounded-xl border border-neutral-800 space-y-1.5">
            <div className="font-bold text-white">Method A: Live Query (App In-Place)</div>
            <p className="text-neutral-400">
              Activate the Mode manually once in Samsung Settings. Open this POC app and tap <strong>[READ CURRENT MODE]</strong>. The app inspects <code className="text-blue-400 font-mono">Settings.System.getString("mode_id")</code> and auto-populates the UUID.
            </p>
          </div>

          <div className="p-4 bg-neutral-950 rounded-xl border border-neutral-800 space-y-1.5">
            <div className="font-bold text-white">Method B: Home Screen Shortcut Inspection</div>
            <p className="text-neutral-400">
              In Modes & Routines, tap the 3-dots menu on your Mode → <strong>Add to Home screen</strong>. Inspect the created shortcut Intent extra <code className="text-blue-400 font-mono">EXTRA_KEY_ROUTINE_UUID</code>.
            </p>
          </div>
        </div>
      </div>

      {/* Undocumented Technical Warning */}
      <div className="p-5 rounded-2xl bg-amber-950/40 border border-amber-800/80 text-amber-200 space-y-2">
        <div className="flex items-center gap-2 text-xs font-bold text-amber-300">
          <AlertTriangle className="w-4 h-4 text-amber-400" />
          UNDOCUMENTED / VERSION-DEPENDENT INTEGRATION WARNING
        </div>
        <p className="text-xs text-amber-200/90 leading-relaxed">
          Samsung does not publish an official 3rd-party developer SDK for Modes & Routines invocation. Both <code className="font-mono text-amber-100">ShortcutLaunchActivity</code> (One UI 8.5) and <code className="font-mono text-amber-100">externalprovider</code> (One UI 8.0) are internal Samsung components. The codebase strictly isolates these mechanisms behind version-specific controllers with zero root, Shizuku, or AccessibilityService hacks.
        </p>
      </div>

      {/* Build Instructions */}
      <div className="p-6 rounded-2xl bg-neutral-900 border border-neutral-800 space-y-3">
        <h3 className="text-sm font-bold text-white flex items-center gap-2">
          <Terminal className="w-4 h-4 text-blue-400" />
          3. Compiling the Android APK
        </h3>
        <p className="text-xs text-neutral-400">
          You can build the APK in <strong>GitHub Actions</strong> (zero setup) or locally:
        </p>
        <div className="space-y-2">
          <div className="text-xs font-semibold text-neutral-300">Option A: GitHub Actions (.github/workflows/build.yml)</div>
          <pre className="p-3 bg-neutral-950 rounded-xl border border-neutral-800 font-mono text-xs text-cyan-300 overflow-x-auto">
{`# Uses gradle/actions/setup-gradle@v3 (no local SDK/jar needed)
cd android && gradle assembleDebug --no-daemon
# Output artifact: app-debug.apk`}
          </pre>

          <div className="text-xs font-semibold text-neutral-300 pt-1">Option B: Android Studio / Local Gradle</div>
          <pre className="p-3 bg-neutral-950 rounded-xl border border-neutral-800 font-mono text-xs text-emerald-400 overflow-x-auto">
{`cd android
./gradlew assembleDebug
# Generated APK: app/build/outputs/apk/debug/app-debug.apk`}
          </pre>
        </div>
      </div>
    </div>
  );
};
