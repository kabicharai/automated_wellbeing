import React, { useState, useEffect, useRef } from 'react';
import { Play, RotateCw, Copy, Trash2, CheckCircle2, AlertTriangle, XCircle, Shield, Smartphone, ArrowRight, Zap, Info } from 'lucide-react';
import { DeviceInfo, LogEntry, FullTestOutcome } from '../types';

interface DeviceSimulatorProps {
  device: DeviceInfo;
  onDeviceChange: (dev: DeviceInfo) => void;
  logs: LogEntry[];
  onAddLog: (level: LogEntry['level'], msg: string) => void;
  onClearLogs: () => void;
}

export const DeviceSimulator: React.FC<DeviceSimulatorProps> = ({
  device,
  logs,
  onAddLog,
  onClearLogs
}) => {
  const [modeUuid, setModeUuid] = useState<string>('focus-lock-550e8400');
  const [isModeActive, setIsModeActive] = useState<boolean>(false);
  const [activeModeDetails, setActiveModeDetails] = useState<string | null>(null);
  const [isActionRunning, setIsActionRunning] = useState<boolean>(false);
  const [testOutcome, setTestOutcome] = useState<FullTestOutcome>('IDLE');
  const [testStep, setTestStep] = useState<string>('');
  const [copySuccess, setCopySuccess] = useState<boolean>(false);
  const logEndRef = useRef<HTMLDivElement>(null);

  // Auto-scroll logs
  useEffect(() => {
    logEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [logs]);

  // Reset state when device profile switches
  useEffect(() => {
    setIsModeActive(false);
    setActiveModeDetails(null);
    setTestOutcome('IDLE');
    setTestStep('');
  }, [device.id]);

  const handleStartMode = async () => {
    if (!modeUuid.trim()) {
      onAddLog('WARN', 'Please enter a valid Mode UUID.');
      return;
    }
    if (!device.isSupported) {
      onAddLog('ERROR', `[START] NOT SUPPORTED: ${device.name} does not expose an accessible Samsung Modes backend.`);
      return;
    }

    setIsActionRunning(true);
    onAddLog('ACTION', `Invoking START for Mode UUID [${modeUuid}] via [${device.selectedBackend}]...`);

    if (device.selectedBackend.includes('V8.5')) {
      onAddLog('INFO', `Intent dispatched: ComponentName("com.samsung.android.app.routines", "ShortcutLaunchActivity"), EXTRA_KEY_ROUTINE_UUID="${modeUuid}"`);
    } else {
      onAddLog('INFO', `ContentResolver.call(uri="content://com.samsung.android.app.routines.externalprovider", method="start_manual_routine", arg="${modeUuid}")`);
    }

    await new Promise(r => setTimeout(r, 800));

    setIsModeActive(true);
    setActiveModeDetails(`Mode [${modeUuid}] Active (Verified via Settings.System[mode_id])`);
    setIsActionRunning(false);
    onAddLog('SUCCESS', `[START] MODE STATE VERIFIED: Samsung Mode [${modeUuid}] activated. Native "Restrict app usage" action is now enforcing app restrictions.`);
  };

  const handleStopMode = async () => {
    if (!modeUuid.trim()) {
      onAddLog('WARN', 'Please enter a valid Mode UUID.');
      return;
    }
    if (!device.isSupported) {
      onAddLog('ERROR', `[STOP] NOT SUPPORTED on this device.`);
      return;
    }

    setIsActionRunning(true);
    onAddLog('ACTION', `Invoking STOP for Mode UUID [${modeUuid}] via [${device.selectedBackend}]...`);

    await new Promise(r => setTimeout(r, 800));

    setIsModeActive(false);
    setActiveModeDetails(null);
    setIsActionRunning(false);
    onAddLog('SUCCESS', `[STOP] MODE STATE VERIFIED: Samsung Mode deactivated. Restrict app usage lifted.`);
  };

  const handleToggleMode = async () => {
    if (!modeUuid.trim()) {
      onAddLog('WARN', 'Please enter a valid Mode UUID.');
      return;
    }
    if (!device.isSupported) {
      onAddLog('ERROR', `[TOGGLE] NOT SUPPORTED on this device.`);
      return;
    }

    setIsActionRunning(true);
    onAddLog('ACTION', `Invoking TOGGLE for Mode UUID [${modeUuid}]...`);
    await new Promise(r => setTimeout(r, 600));

    const nextState = !isModeActive;
    setIsModeActive(nextState);
    if (nextState) {
      setActiveModeDetails(`Mode [${modeUuid}] Active`);
      onAddLog('SUCCESS', `[TOGGLE] Mode switched to ON (Active: ${modeUuid}).`);
    } else {
      setActiveModeDetails(null);
      onAddLog('SUCCESS', `[TOGGLE] Mode switched to OFF.`);
    }
    setIsActionRunning(false);
  };

  const handleReadCurrent = async () => {
    onAddLog('QUERY', `Querying Settings.System["mode_id"] and Settings.Global...`);
    await new Promise(r => setTimeout(r, 400));
    if (isModeActive) {
      onAddLog('INFO', `Current active Mode detected: ${modeUuid} (Source: Settings.System[mode_id])`);
    } else {
      onAddLog('INFO', `No Samsung Mode currently active according to system observables.`);
    }
  };

  const handleRunFullTest = async () => {
    if (!modeUuid.trim()) {
      onAddLog('ERROR', 'Cannot run full test: Mode UUID is empty.');
      return;
    }
    if (!device.isSupported) {
      onAddLog('ERROR', `[FULL TEST] FAILED: Device backend is unsupported.`);
      setTestOutcome('FAIL');
      setTestStep('Failed: Unsupported device architecture.');
      return;
    }

    setIsActionRunning(true);
    setTestOutcome('RUNNING');
    onAddLog('TEST', '==================================================');
    onAddLog('TEST', 'STARTING 7-STEP FULL CYCLE VERIFICATION TEST');
    onAddLog('TEST', `Target Mode UUID: ${modeUuid} | Backend: ${device.selectedBackend}`);
    onAddLog('TEST', '==================================================');

    // Step 1: Read initial
    setTestStep('1/7: Reading baseline state...');
    onAddLog('TEST', '[Step 1/7] Reading baseline state...');
    await new Promise(r => setTimeout(r, 600));

    // Step 2: STOP mode (clean state)
    setTestStep('2/7: Ensuring mode is STOPPED (Reset)...');
    onAddLog('TEST', '[Step 2/7] Dispatching STOP command to reset...');
    setIsModeActive(false);
    setActiveModeDetails(null);
    await new Promise(r => setTimeout(r, 700));

    // Step 3: Verify OFF
    setTestStep('3/7: Verifying baseline OFF state...');
    onAddLog('SUCCESS', '[Step 3/7] Verified OFF successfully.');
    await new Promise(r => setTimeout(r, 500));

    // Step 4: START mode
    setTestStep('4/7: Dispatching START command...');
    onAddLog('TEST', '[Step 4/7] Dispatching START command via ' + device.selectedBackend + '...');
    setIsModeActive(true);
    setActiveModeDetails(`Mode [${modeUuid}] Active`);
    await new Promise(r => setTimeout(r, 900));

    // Step 5: Verify ON
    setTestStep('5/7: Verifying mode is ON in settings...');
    onAddLog('SUCCESS', '[Step 5/7] Verified ON successfully via Settings.System[mode_id].');
    await new Promise(r => setTimeout(r, 600));

    // Step 6: STOP mode
    setTestStep('6/7: Dispatching final STOP command...');
    onAddLog('TEST', '[Step 6/7] Dispatching final STOP command...');
    setIsModeActive(false);
    setActiveModeDetails(null);
    await new Promise(r => setTimeout(r, 800));

    // Step 7: Verify OFF
    setTestStep('7/7: Verifying final OFF state...');
    onAddLog('SUCCESS', '[Step 7/7] Verified final OFF state successfully.');
    await new Promise(r => setTimeout(r, 500));

    onAddLog('TEST', '==================================================');
    onAddLog('TEST', `TEST RESULT: PASS - Full cycle test PASSED on ${device.selectedBackend}.`);
    onAddLog('TEST', '==================================================');

    setTestOutcome('PASS');
    setTestStep('Full cycle test PASSED: START and STOP verified.');
    setIsActionRunning(false);
  };

  const copyLogsToClipboard = () => {
    const formatted = logs.map(l => `[${l.timestamp}] [${l.level}] ${l.message}`).join('\n');
    navigator.clipboard.writeText(formatted);
    setCopySuccess(true);
    setTimeout(() => setCopySuccess(false), 2000);
  };

  return (
    <div className="flex flex-col lg:flex-row gap-6 items-start">
      {/* Interactive Android Phone Frame (Galaxy S23 Simulation) */}
      <div className="w-full lg:w-[410px] shrink-0 mx-auto">
        <div className="relative rounded-[40px] p-3 bg-neutral-900 border-4 border-neutral-700 shadow-2xl overflow-hidden">
          {/* Top Speaker & Camera Punch-hole */}
          <div className="absolute top-5 left-1/2 -translate-x-1/2 w-4 h-4 bg-black rounded-full z-30 border border-neutral-800 flex items-center justify-center">
            <div className="w-1.5 h-1.5 bg-neutral-950 rounded-full"></div>
          </div>

          {/* Android Screen Container */}
          <div className="rounded-[30px] overflow-hidden bg-neutral-950 text-neutral-100 flex flex-col h-[740px] text-xs select-none">
            {/* Status Bar */}
            <div className="h-7 bg-neutral-900 px-4 flex items-center justify-between text-[11px] text-neutral-400 font-medium">
              <span className="font-mono">18:45</span>
              <div className="flex items-center gap-1.5">
                {isModeActive && (
                  <span className="flex items-center gap-1 px-1.5 py-0.5 rounded bg-blue-900/70 text-blue-300 text-[10px] font-semibold animate-pulse">
                    <Shield className="w-3 h-3" /> Mode Active
                  </span>
                )}
                <span>5G</span>
                <span>88%</span>
              </div>
            </div>

            {/* Jetpack Compose App TopBar */}
            <div className="bg-blue-950/80 border-b border-blue-900/60 px-4 py-3 flex items-center justify-between">
              <div>
                <h1 className="text-sm font-bold text-white tracking-tight">Samsung Modes POC</h1>
                <p className="text-[10px] text-blue-300">Android 16 • One UI 8.x Controller</p>
              </div>
              <button
                onClick={() => {
                  onAddLog('DETECT', `Refreshed capability probe on ${device.name}`);
                }}
                className="p-1.5 rounded-lg bg-blue-900/50 hover:bg-blue-800/60 text-blue-200 transition-colors"
                title="Refresh"
              >
                <RotateCw className="w-3.5 h-3.5" />
              </button>
            </div>

            {/* Compose Screen Body */}
            <div className="flex-1 overflow-y-auto p-3 space-y-3">
              {/* 1. Device Info Card */}
              <div className="p-3 rounded-xl bg-neutral-900 border border-neutral-800 space-y-1.5">
                <div className="text-[10px] font-bold tracking-wider text-blue-400 uppercase">Device & Environment</div>
                <div className="flex justify-between text-[11px]">
                  <span className="text-neutral-400">DEVICE</span>
                  <span className="font-semibold text-neutral-200">{device.model}</span>
                </div>
                <div className="flex justify-between text-[11px]">
                  <span className="text-neutral-400">ANDROID</span>
                  <span className="font-semibold text-neutral-200">{device.androidVersion}</span>
                </div>
                <div className="flex justify-between text-[11px]">
                  <span className="text-neutral-400">ONE UI</span>
                  <span className="font-semibold text-neutral-200">{device.oneUiVersion}</span>
                </div>
                <div className="flex justify-between text-[11px]">
                  <span className="text-neutral-400">MODES & ROUTINES</span>
                  <span className="font-semibold text-neutral-200 font-mono text-[10px]">{device.routinesVersionName}</span>
                </div>
                <div className="pt-1.5 border-t border-neutral-800 flex justify-between items-center">
                  <span className="text-[11px] font-semibold text-neutral-300">SELECTED BACKEND</span>
                  <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                    device.isSupported ? 'bg-emerald-900/80 text-emerald-300 border border-emerald-700/50' : 'bg-rose-900/80 text-rose-300 border border-rose-700/50'
                  }`}>
                    {device.selectedBackend}
                  </span>
                </div>
              </div>

              {/* 2. Mode UUID Input & Action Buttons */}
              <div className="p-3 rounded-xl bg-neutral-900/90 border border-neutral-800 space-y-2">
                <div className="text-[10px] font-bold tracking-wider text-blue-400 uppercase">Mode UUID</div>
                <input
                  type="text"
                  value={modeUuid}
                  onChange={(e) => setModeUuid(e.target.value)}
                  placeholder="Enter Samsung Mode UUID"
                  className="w-full px-2.5 py-1.5 bg-neutral-950 border border-neutral-700 rounded-lg text-neutral-100 font-mono text-[11px] focus:outline-none focus:border-blue-500"
                />
                
                <div className="grid grid-cols-3 gap-1.5 pt-1">
                  <button
                    onClick={handleStartMode}
                    disabled={isActionRunning}
                    className="py-2 px-1 bg-emerald-700 hover:bg-emerald-600 active:scale-95 disabled:opacity-50 rounded-lg font-bold text-white text-[11px] transition-all flex items-center justify-center gap-1 shadow-sm"
                  >
                    START
                  </button>
                  <button
                    onClick={handleStopMode}
                    disabled={isActionRunning}
                    className="py-2 px-1 bg-rose-700 hover:bg-rose-600 active:scale-95 disabled:opacity-50 rounded-lg font-bold text-white text-[11px] transition-all flex items-center justify-center gap-1 shadow-sm"
                  >
                    STOP
                  </button>
                  <button
                    onClick={handleToggleMode}
                    disabled={isActionRunning}
                    className="py-2 px-1 bg-neutral-800 hover:bg-neutral-700 active:scale-95 disabled:opacity-50 rounded-lg font-semibold text-neutral-200 text-[11px] transition-all border border-neutral-700"
                  >
                    TOGGLE
                  </button>
                </div>
              </div>

              {/* 3. Current Mode & Test Execution */}
              <div className="p-3 rounded-xl bg-neutral-900 border border-neutral-800 space-y-2">
                <div className="flex items-center justify-between">
                  <div>
                    <div className="text-[10px] font-bold tracking-wider text-blue-400 uppercase">Current Mode</div>
                    <div className="text-[11px] font-semibold text-neutral-200 mt-0.5">
                      {isModeActive ? (
                        <span className="text-emerald-400 flex items-center gap-1">
                          <CheckCircle2 className="w-3 h-3 inline" /> Active ({modeUuid.slice(0, 16)}...)
                        </span>
                      ) : (
                        <span className="text-neutral-400">Not active / Not exposed</span>
                      )}
                    </div>
                  </div>
                  <button
                    onClick={handleReadCurrent}
                    disabled={isActionRunning}
                    className="px-2 py-1 bg-neutral-800 hover:bg-neutral-700 text-neutral-200 text-[10px] font-medium rounded-md border border-neutral-700"
                  >
                    READ CURRENT
                  </button>
                </div>

                <div className="pt-2 border-t border-neutral-800">
                  <button
                    onClick={handleRunFullTest}
                    disabled={isActionRunning}
                    className="w-full py-2 px-3 bg-gradient-to-r from-blue-700 to-indigo-700 hover:from-blue-600 hover:to-indigo-600 active:scale-98 disabled:opacity-50 text-white rounded-lg font-bold text-[11px] flex items-center justify-center gap-1.5 shadow-md transition-all"
                  >
                    <Play className="w-3.5 h-3.5 fill-current" />
                    RUN FULL TEST (7-STEP VERIFICATION)
                  </button>

                  {testOutcome === 'RUNNING' && (
                    <div className="mt-2 p-2 bg-blue-950/60 rounded border border-blue-900 text-[10px] text-blue-300 space-y-1">
                      <div className="flex items-center gap-1.5">
                        <RotateCw className="w-3 h-3 animate-spin text-blue-400" />
                        <span className="font-semibold">{testStep}</span>
                      </div>
                      <div className="w-full bg-neutral-800 h-1 rounded-full overflow-hidden">
                        <div className="bg-blue-500 h-full w-2/3 animate-pulse"></div>
                      </div>
                    </div>
                  )}

                  {testOutcome !== 'IDLE' && testOutcome !== 'RUNNING' && (
                    <div className={`mt-2 p-2 rounded border text-[10px] ${
                      testOutcome === 'PASS' ? 'bg-emerald-950/80 border-emerald-700 text-emerald-200' :
                      testOutcome === 'PARTIAL PASS' ? 'bg-amber-950/80 border-amber-700 text-amber-200' :
                      'bg-rose-950/80 border-rose-700 text-rose-200'
                    }`}>
                      <div className="font-bold flex items-center gap-1">
                        {testOutcome === 'PASS' && <CheckCircle2 className="w-3 h-3 text-emerald-400" />}
                        {testOutcome === 'PARTIAL PASS' && <AlertTriangle className="w-3 h-3 text-amber-400" />}
                        {testOutcome === 'FAIL' && <XCircle className="w-3 h-3 text-rose-400" />}
                        RESULT: {testOutcome}
                      </div>
                      <div className="text-[10px] text-neutral-300 mt-0.5">{testStep}</div>
                    </div>
                  )}
                </div>
              </div>

              {/* 4. Diagnostics Section */}
              <div className="p-3 rounded-xl bg-neutral-900/90 border border-neutral-800 space-y-1 text-[10px]">
                <div className="font-bold tracking-wider text-blue-400 uppercase mb-1">Diagnostics Matrix</div>
                <div className="flex justify-between">
                  <span className="text-neutral-400">Modes package</span>
                  <span className={device.routinesPackageInstalled ? "text-emerald-400 font-bold" : "text-rose-400 font-bold"}>
                    {device.routinesPackageInstalled ? "FOUND" : "NOT FOUND"}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-neutral-400">Shortcut activity</span>
                  <span className={device.shortcutActivityFound ? "text-emerald-400 font-bold" : "text-rose-400 font-bold"}>
                    {device.shortcutActivityFound ? "FOUND" : "NOT FOUND"}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-neutral-400">Shortcut activity exported</span>
                  <span className={device.shortcutActivityExported ? "text-emerald-400 font-bold" : "text-rose-400 font-bold"}>
                    {device.shortcutActivityExported ? "YES" : "NO"}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-neutral-400">Legacy provider</span>
                  <span className={device.legacyProviderFound ? "text-emerald-400 font-bold" : "text-rose-400 font-bold"}>
                    {device.legacyProviderFound ? "FOUND" : "NOT FOUND"}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-neutral-400">Legacy provider accessible</span>
                  <span className={device.legacyProviderAccessible ? "text-emerald-400 font-bold" : "text-rose-400 font-bold"}>
                    {device.legacyProviderAccessible ? "YES" : "NO"}
                  </span>
                </div>
              </div>
            </div>

            {/* Mini Bottom Nav / Info */}
            <div className="h-6 bg-neutral-900 border-t border-neutral-800 px-4 flex items-center justify-center text-[10px] text-neutral-500 font-mono">
              SamsungModesPOC • Ready
            </div>
          </div>
        </div>
      </div>

      {/* Right Column: Live Technical Execution Log & Quick Controls */}
      <div className="flex-1 w-full space-y-4">
        {/* Active Mode Notice Banner */}
        {isModeActive && (
          <div className="p-4 rounded-xl bg-gradient-to-r from-blue-950 to-indigo-950 border border-blue-800 text-blue-200 flex items-start gap-3 shadow-lg animate-fadeIn">
            <Shield className="w-5 h-5 text-blue-400 shrink-0 mt-0.5" />
            <div>
              <h3 className="text-sm font-bold text-white">Samsung Mode Currently ACTIVE</h3>
              <p className="text-xs text-blue-300 mt-0.5">
                Native Samsung <strong>"Restrict app usage"</strong> action is engaged. Restricted applications will be blocked on Galaxy S23 until STOP is invoked.
              </p>
            </div>
          </div>
        )}

        {/* Technical Log Terminal */}
        <div className="rounded-xl bg-neutral-950 border border-neutral-800 overflow-hidden shadow-xl flex flex-col h-[520px]">
          <div className="px-4 py-3 bg-neutral-900 border-b border-neutral-800 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 rounded-full bg-emerald-500/80"></div>
              <span className="text-xs font-mono font-bold text-neutral-200">LIVE TECHNICAL LOGS</span>
              <span className="text-[11px] text-neutral-500 font-mono">({logs.length} entries)</span>
            </div>

            <div className="flex items-center gap-2">
              <button
                onClick={copyLogsToClipboard}
                className="px-2.5 py-1 text-xs font-semibold rounded-md bg-neutral-800 hover:bg-neutral-700 text-neutral-200 flex items-center gap-1 transition-colors"
              >
                <Copy className="w-3 h-3" />
                {copySuccess ? 'Copied!' : 'COPY LOG'}
              </button>
              <button
                onClick={onClearLogs}
                className="px-2.5 py-1 text-xs font-semibold rounded-md bg-neutral-800 hover:bg-rose-950/60 hover:text-rose-300 text-neutral-400 flex items-center gap-1 transition-colors"
              >
                <Trash2 className="w-3 h-3" />
                CLEAR
              </button>
            </div>
          </div>

          {/* Log Stream */}
          <div className="flex-1 overflow-y-auto p-3 font-mono text-[11px] leading-relaxed space-y-1 text-neutral-300">
            {logs.map((log) => {
              let colorClass = 'text-neutral-300';
              if (log.level === 'SUCCESS') colorClass = 'text-emerald-400 font-semibold';
              if (log.level === 'WARN') colorClass = 'text-amber-300';
              if (log.level === 'ERROR') colorClass = 'text-rose-400 font-semibold';
              if (log.level === 'ACTION') colorClass = 'text-cyan-300';
              if (log.level === 'TEST') colorClass = 'text-purple-300';
              if (log.level === 'QUERY') colorClass = 'text-blue-300';

              return (
                <div key={log.id} className="flex items-start gap-2 hover:bg-neutral-900/50 py-0.5 px-1 rounded">
                  <span className="text-neutral-500 select-none shrink-0">[{log.timestamp}]</span>
                  <span className={`px-1.5 py-0.2 rounded text-[10px] font-bold shrink-0 ${
                    log.level === 'SUCCESS' ? 'bg-emerald-950 text-emerald-300' :
                    log.level === 'ERROR' ? 'bg-rose-950 text-rose-300' :
                    log.level === 'ACTION' ? 'bg-cyan-950 text-cyan-300' :
                    log.level === 'TEST' ? 'bg-purple-950 text-purple-300' :
                    'bg-neutral-800 text-neutral-400'
                  }`}>
                    {log.level}
                  </span>
                  <span className={colorClass}>{log.message}</span>
                </div>
              );
            })}
            <div ref={logEndRef} />
          </div>
        </div>

        {/* Quick UUID Preset Helper */}
        <div className="p-3 bg-neutral-900 rounded-xl border border-neutral-800 flex flex-wrap items-center gap-2">
          <span className="text-xs text-neutral-400 font-medium">Sample Mode UUIDs:</span>
          {['focus-lock-550e8400', 'work-mode-9a4f2100', 'study-restriction-7c91', 'custom-galaxy-mode-01'].map((preset) => (
            <button
              key={preset}
              onClick={() => {
                setModeUuid(preset);
                onAddLog('INFO', `Selected test Mode UUID preset: ${preset}`);
              }}
              className="px-2 py-0.5 rounded bg-neutral-800 hover:bg-neutral-700 text-neutral-300 text-xs font-mono transition-colors"
            >
              {preset}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
};
