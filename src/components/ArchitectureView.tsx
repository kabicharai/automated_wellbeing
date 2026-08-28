import React from 'react';
import { ArrowDown, Smartphone, Radio, Cpu, Layers, ShieldCheck, CheckCircle2 } from 'lucide-react';

export const ArchitectureView: React.FC = () => {
  return (
    <div className="space-y-8">
      {/* POC Architecture (Current Milestone) */}
      <div className="p-6 rounded-2xl bg-neutral-900 border border-neutral-800 space-y-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="px-2 py-0.5 rounded bg-blue-900 text-blue-300 text-xs font-bold font-mono">CURRENT MILESTONE</span>
            <h2 className="text-base font-bold text-white">Samsung Modes Controller POC Architecture</h2>
          </div>
          <p className="text-xs text-neutral-400 mt-1">
            Strict unprivileged Android application controlling native Samsung Modes with runtime capability probing.
          </p>
        </div>

        <div className="max-w-2xl mx-auto space-y-2 font-mono text-xs">
          <div className="p-3.5 bg-neutral-950 rounded-xl border border-blue-900/60 text-center text-blue-300 font-bold flex items-center justify-center gap-2">
            <Smartphone className="w-4 h-4 text-blue-400" />
            Jetpack Compose UI (MainActivity / SamsungModesScreen)
          </div>

          <div className="flex justify-center"><ArrowDown className="w-4 h-4 text-neutral-600" /></div>

          <div className="p-3.5 bg-neutral-950 rounded-xl border border-neutral-700 text-center text-white font-bold flex items-center justify-center gap-2">
            <Cpu className="w-4 h-4 text-indigo-400" />
            SamsungCapabilityDetector (Probes PackageManager & Resolvers)
          </div>

          <div className="flex justify-center"><ArrowDown className="w-4 h-4 text-neutral-600" /></div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div className="p-3.5 bg-neutral-950 rounded-xl border border-emerald-800/80 text-emerald-300 space-y-1">
              <div className="font-bold flex items-center gap-1.5">
                <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                SamsungModeControllerV85
              </div>
              <div className="text-[11px] text-neutral-400 font-sans">
                Targets One UI 8.5+ via ShortcutLaunchActivity & EXTRA_KEY_ROUTINE_UUID
              </div>
            </div>

            <div className="p-3.5 bg-neutral-950 rounded-xl border border-purple-800/80 text-purple-300 space-y-1">
              <div className="font-bold flex items-center gap-1.5">
                <CheckCircle2 className="w-4 h-4 text-purple-400" />
                SamsungModeControllerV8
              </div>
              <div className="text-[11px] text-neutral-400 font-sans">
                Targets One UI 8.0 via externalprovider ContentResolver.call()
              </div>
            </div>
          </div>

          <div className="flex justify-center"><ArrowDown className="w-4 h-4 text-neutral-600" /></div>

          <div className="p-3.5 bg-gradient-to-r from-blue-950 to-indigo-950 rounded-xl border border-blue-700 text-center text-blue-200 font-bold flex items-center justify-center gap-2">
            <ShieldCheck className="w-4 h-4 text-blue-400" />
            Samsung Modes "Restrict app usage" Native Enforcer
          </div>
        </div>
      </div>

      {/* Future Roadmap (BLE & RSSI Proximity Engine) */}
      <div className="p-6 rounded-2xl bg-neutral-900 border border-neutral-800 space-y-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="px-2 py-0.5 rounded bg-purple-900 text-purple-300 text-xs font-bold font-mono">FUTURE MILESTONES</span>
            <h2 className="text-base font-bold text-white">Eventual Proximity & Restriction Engine</h2>
          </div>
          <p className="text-xs text-neutral-400 mt-1">
            Pluggable architecture: RestrictionController abstraction ensures BLE Beacon & SmartTag engines connect cleanly without modifying Samsung Mode logic.
          </p>
        </div>

        <div className="max-w-2xl mx-auto p-4 rounded-xl bg-neutral-950 border border-neutral-800 font-mono text-xs text-neutral-300 space-y-3">
          <div className="flex items-center justify-center gap-3">
            <span className="px-3 py-1 bg-neutral-900 rounded border border-neutral-700 text-purple-300">Samsung SmartTag 1</span>
            <span className="text-neutral-500 font-sans">OR</span>
            <span className="px-3 py-1 bg-neutral-900 rounded border border-neutral-700 text-cyan-300">Dedicated BLE Beacon</span>
          </div>

          <div className="text-center text-neutral-600">↓</div>

          <div className="p-2.5 bg-neutral-900 rounded border border-neutral-800 text-center text-amber-300">
            RSSI Observations (Signal Strengths)
          </div>

          <div className="text-center text-neutral-600">↓</div>

          <div className="p-2.5 bg-neutral-900 rounded border border-neutral-800 text-center text-indigo-300">
            Proximity Engine (Calibrated INSIDE / OUTSIDE Classifier)
          </div>

          <div className="text-center text-neutral-600">↓</div>

          <div className="p-2.5 bg-neutral-900 rounded border border-neutral-800 text-center text-white font-bold">
            RestrictionController (SamsungModesRestrictionController)
          </div>

          <div className="text-center text-neutral-600">↓</div>

          <div className="p-2.5 bg-blue-950 rounded border border-blue-800 text-center text-blue-300 font-bold">
            Samsung Modes → "Restrict app usage" Action
          </div>
        </div>
      </div>
    </div>
  );
};
