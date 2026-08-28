import React from 'react';
import { DeviceInfo } from '../types';
import { ShieldCheck, ShieldAlert, Cpu, Layers, Box, Terminal, CheckCircle2, XCircle, Info } from 'lucide-react';

interface DiagnosticsInspectorProps {
  device: DeviceInfo;
}

export const DiagnosticsInspector: React.FC<DiagnosticsInspectorProps> = ({ device }) => {
  return (
    <div className="space-y-6">
      {/* Capability Overview Banner */}
      <div className="p-5 rounded-2xl bg-neutral-900 border border-neutral-800 flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-bold text-white tracking-tight">Active Device Inspection</h2>
            <span className={`px-2.5 py-0.5 rounded-full text-xs font-bold ${
              device.isSupported ? 'bg-emerald-950 text-emerald-300 border border-emerald-800' : 'bg-rose-950 text-rose-300 border border-rose-800'
            }`}>
              {device.selectedBackend}
            </span>
          </div>
          <p className="text-xs text-neutral-400 mt-1">
            Probed via <code className="text-blue-400 font-mono">PackageManager</code> and <code className="text-blue-400 font-mono">ContentResolver</code> queries on {device.model}
          </p>
        </div>

        <div className="flex items-center gap-3">
          <div className="text-right">
            <div className="text-xs text-neutral-400">Android Build</div>
            <div className="text-sm font-bold text-white font-mono">{device.androidVersion}</div>
          </div>
          <div className="h-8 w-px bg-neutral-800"></div>
          <div className="text-right">
            <div className="text-xs text-neutral-400">One UI</div>
            <div className="text-sm font-bold text-white font-mono">{device.oneUiVersion}</div>
          </div>
        </div>
      </div>

      {/* 2-Column Inspection Matrix */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Backend 1: One UI 8.5 Shortcut Activity Probe */}
        <div className="p-5 rounded-2xl bg-neutral-900 border border-neutral-800 space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Layers className="w-4 h-4 text-blue-400" />
              <h3 className="text-sm font-bold text-white">One UI 8.5 Shortcut Activity Endpoint</h3>
            </div>
            {device.shortcutActivityFound && device.shortcutActivityExported ? (
              <span className="flex items-center gap-1 text-xs font-bold text-emerald-400 bg-emerald-950/60 px-2 py-0.5 rounded border border-emerald-800">
                <CheckCircle2 className="w-3.5 h-3.5" /> READY
              </span>
            ) : (
              <span className="flex items-center gap-1 text-xs font-bold text-rose-400 bg-rose-950/60 px-2 py-0.5 rounded border border-rose-800">
                <XCircle className="w-3.5 h-3.5" /> UNAVAILABLE
              </span>
            )}
          </div>

          <div className="space-y-2 text-xs">
            <div className="p-2.5 rounded-lg bg-neutral-950 border border-neutral-800 font-mono text-[11px] text-neutral-300">
              <span className="text-neutral-500">// Target Component</span><br />
              <span className="text-blue-400">Package:</span> com.samsung.android.app.routines<br />
              <span className="text-blue-400">Class:</span> com.samsung.android.app.routines.ui.shortcut.ShortcutLaunchActivity<br />
              <span className="text-blue-400">Extra:</span> EXTRA_KEY_ROUTINE_UUID
            </div>

            <div className="space-y-1.5 pt-1">
              <div className="flex justify-between py-1 border-b border-neutral-800/80">
                <span className="text-neutral-400">Activity Found:</span>
                <span className={device.shortcutActivityFound ? "text-emerald-400 font-bold" : "text-rose-400 font-bold"}>
                  {device.shortcutActivityFound ? "YES (PackageManager resolved)" : "NO"}
                </span>
              </div>
              <div className="flex justify-between py-1 border-b border-neutral-800/80">
                <span className="text-neutral-400">android:exported Status:</span>
                <span className={device.shortcutActivityExported ? "text-emerald-400 font-bold" : "text-rose-400 font-bold"}>
                  {device.shortcutActivityExported ? "TRUE (Accessible to 3rd-party)" : "FALSE (Private)"}
                </span>
              </div>
              <div className="flex justify-between py-1">
                <span className="text-neutral-400">Invocation Dispatch:</span>
                <span className="text-neutral-200 font-semibold">Explicit Intent with FLAG_ACTIVITY_NEW_TASK</span>
              </div>
            </div>
          </div>
        </div>

        {/* Backend 2: One UI 8.0 External Provider Probe */}
        <div className="p-5 rounded-2xl bg-neutral-900 border border-neutral-800 space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Box className="w-4 h-4 text-purple-400" />
              <h3 className="text-sm font-bold text-white">One UI 8.0 Legacy Provider Endpoint</h3>
            </div>
            {device.legacyProviderFound && device.legacyProviderAccessible ? (
              <span className="flex items-center gap-1 text-xs font-bold text-emerald-400 bg-emerald-950/60 px-2 py-0.5 rounded border border-emerald-800">
                <CheckCircle2 className="w-3.5 h-3.5" /> READY
              </span>
            ) : (
              <span className="flex items-center gap-1 text-xs font-bold text-rose-400 bg-rose-950/60 px-2 py-0.5 rounded border border-rose-800">
                <XCircle className="w-3.5 h-3.5" /> UNAVAILABLE
              </span>
            )}
          </div>

          <div className="space-y-2 text-xs">
            <div className="p-2.5 rounded-lg bg-neutral-950 border border-neutral-800 font-mono text-[11px] text-neutral-300">
              <span className="text-neutral-500">// Target Provider Authority</span><br />
              <span className="text-purple-400">Authority:</span> com.samsung.android.app.routines.externalprovider<br />
              <span className="text-purple-400">URI:</span> content://com.samsung.android.app.routines.externalprovider<br />
              <span className="text-purple-400">Methods:</span> start_manual_routine, end_manual_routine
            </div>

            <div className="space-y-1.5 pt-1">
              <div className="flex justify-between py-1 border-b border-neutral-800/80">
                <span className="text-neutral-400">Provider Found:</span>
                <span className={device.legacyProviderFound ? "text-emerald-400 font-bold" : "text-rose-400 font-bold"}>
                  {device.legacyProviderFound ? "YES (ContentResolver authority registered)" : "NO"}
                </span>
              </div>
              <div className="flex justify-between py-1 border-b border-neutral-800/80">
                <span className="text-neutral-400">Provider Accessibility:</span>
                <span className={device.legacyProviderAccessible ? "text-emerald-400 font-bold" : "text-rose-400 font-bold"}>
                  {device.legacyProviderAccessible ? "YES (Callable)" : "REJECTED (SecurityException)"}
                </span>
              </div>
              <div className="flex justify-between py-1">
                <span className="text-neutral-400">Invocation Dispatch:</span>
                <span className="text-neutral-200 font-semibold">ContentResolver.call(uri, method, uuid, extras)</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* System Observables Matrix */}
      <div className="p-5 rounded-2xl bg-neutral-900 border border-neutral-800 space-y-3">
        <div className="flex items-center gap-2">
          <Terminal className="w-4 h-4 text-emerald-400" />
          <h3 className="text-sm font-bold text-white">State Verification Observables</h3>
        </div>
        <p className="text-xs text-neutral-400">
          To satisfy the rule of distinguishing <strong>INVOCATION SUCCESS</strong> from <strong>MODE STATE VERIFIED</strong>, the controller inspects legitimate system keys:
        </p>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-3 pt-2">
          <div className="p-3 bg-neutral-950 rounded-xl border border-neutral-800 text-xs">
            <div className="font-mono text-neutral-400">Settings.System</div>
            <div className="font-bold text-white mt-1">mode_id / current_sec_active_mode</div>
            <div className="text-[11px] text-neutral-500 mt-1">Stores active Samsung Mode UUID when user mode is triggered.</div>
          </div>

          <div className="p-3 bg-neutral-950 rounded-xl border border-neutral-800 text-xs">
            <div className="font-mono text-neutral-400">Settings.Global</div>
            <div className="font-bold text-white mt-1">mode_id</div>
            <div className="text-[11px] text-neutral-500 mt-1">Global device mode identifier observable by third-party apps.</div>
          </div>

          <div className="p-3 bg-neutral-950 rounded-xl border border-neutral-800 text-xs">
            <div className="font-mono text-neutral-400">External Provider</div>
            <div className="font-bold text-white mt-1">get_current_manual_routine</div>
            <div className="text-[11px] text-neutral-500 mt-1">Queries current routine state on One UI 8.0 provider endpoints.</div>
          </div>
        </div>
      </div>
    </div>
  );
};
