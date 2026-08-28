import React, { useState } from 'react';
import {
  AutomationState,
  AutomationAuditEvent,
  ProximityProfile,
  ProximityState,
  BleDeviceProfile,
  RuntimePermissionStatus,
} from '../types';
import {
  Shield,
  Power,
  Play,
  Pause,
  AlertTriangle,
  RefreshCw,
  Bluetooth,
  Smartphone,
  CheckCircle2,
  XCircle,
  Clock,
  Trash2,
  Edit3,
  Sparkles,
  Zap,
  ArrowRight,
  Layers,
} from 'lucide-react';

interface AutomationControllerViewProps {
  automationState: AutomationState;
  proximityState: ProximityState;
  filteredRssi: number;
  confidencePercent: number;
  activeProfile: ProximityProfile | null;
  savedProfiles: Record<string, ProximityProfile>;
  savedDevices: Record<string, BleDeviceProfile>;
  permissionStatus: RuntimePermissionStatus;
  auditEvents: AutomationAuditEvent[];
  onRequestPermissions: () => void;
  onToggleMaster: (enabled: boolean) => void;
  onSetTargetMode: (uuid: string, name?: string) => void;
  onPause: (minutes: number) => void;
  onResume: () => void;
  onEmergencyStop: () => void;
  onReconcile: () => void;
  onSelectDeviceProfile: (deviceKey: string) => void;
  onResetAllData: () => void;
  onSimulateTransition?: (targetState: ProximityState) => void;
}

export const AutomationControllerView: React.FC<AutomationControllerViewProps> = ({
  automationState,
  proximityState,
  filteredRssi,
  confidencePercent,
  activeProfile,
  savedProfiles,
  savedDevices,
  permissionStatus,
  auditEvents,
  onRequestPermissions,
  onToggleMaster,
  onSetTargetMode,
  onPause,
  onResume,
  onEmergencyStop,
  onReconcile,
  onSelectDeviceProfile,
  onResetAllData,
  onSimulateTransition,
}) => {
  const [showResetModal, setShowResetModal] = useState(false);
  const [showUuidModal, setShowUuidModal] = useState(false);
  const [tempUuid, setTempUuid] = useState(automationState.targetModeUuid);
  const [tempName, setTempName] = useState(automationState.targetModeName);

  const isEnabled = automationState.masterEnabled;
  const isPaused = automationState.isPaused;

  const getStatusColor = () => {
    if (!isEnabled) return 'bg-slate-100 text-slate-700 border-slate-300';
    if (isPaused) return 'bg-amber-50 text-amber-800 border-amber-300';
    switch (automationState.executionState) {
      case 'START_SUCCESS':
      case 'IDLE':
        return 'bg-emerald-50 text-emerald-800 border-emerald-300';
      case 'TRIGGERING_START':
      case 'TRIGGERING_STOP':
      case 'RETRYING':
        return 'bg-amber-50 text-amber-800 border-amber-300';
      case 'STOP_SUCCESS':
        return 'bg-blue-50 text-blue-800 border-blue-300';
      case 'ERROR':
        return 'bg-rose-50 text-rose-800 border-rose-300';
      default:
        return 'bg-slate-100 text-slate-700 border-slate-300';
    }
  };

  const getStatusDotColor = () => {
    if (!isEnabled) return 'bg-slate-400';
    if (isPaused) return 'bg-amber-500 animate-pulse';
    switch (automationState.executionState) {
      case 'START_SUCCESS':
      case 'IDLE':
        return 'bg-emerald-500';
      case 'TRIGGERING_START':
      case 'TRIGGERING_STOP':
      case 'RETRYING':
        return 'bg-amber-500 animate-spin';
      case 'STOP_SUCCESS':
        return 'bg-blue-500';
      case 'ERROR':
        return 'bg-rose-500';
      default:
        return 'bg-slate-400';
    }
  };

  return (
    <div className="space-y-6 max-w-5xl mx-auto pb-12">
      {/* 1. RUNTIME PERMISSIONS BANNER */}
      {!permissionStatus.allGranted && (
        <div className="bg-amber-50 border-2 border-amber-300 rounded-xl p-4 shadow-sm">
          <div className="flex items-start justify-between">
            <div className="flex items-start space-x-3">
              <div className="p-2 bg-amber-100 rounded-lg text-amber-700 mt-0.5">
                <AlertTriangle className="w-5 h-5" />
              </div>
              <div>
                <h4 className="text-sm font-bold text-amber-900">Runtime Permissions Required</h4>
                <p className="text-xs text-amber-700 mt-1">
                  Android 12+ and Android 16 require Bluetooth Scan, Bluetooth Connect, and Fine Location to detect beacons and measure RSSI.
                </p>
                <div className="flex flex-wrap gap-2 mt-2">
                  <span className={`text-[11px] px-2 py-0.5 rounded font-mono ${permissionStatus.hasBluetoothScan ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-200 text-amber-900 font-bold'}`}>
                    {permissionStatus.hasBluetoothScan ? '✓ BLUETOOTH_SCAN' : '✗ BLUETOOTH_SCAN'}
                  </span>
                  <span className={`text-[11px] px-2 py-0.5 rounded font-mono ${permissionStatus.hasBluetoothConnect ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-200 text-amber-900 font-bold'}`}>
                    {permissionStatus.hasBluetoothConnect ? '✓ BLUETOOTH_CONNECT' : '✗ BLUETOOTH_CONNECT'}
                  </span>
                  <span className={`text-[11px] px-2 py-0.5 rounded font-mono ${permissionStatus.hasFineLocation ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-200 text-amber-900 font-bold'}`}>
                    {permissionStatus.hasFineLocation ? '✓ ACCESS_FINE_LOCATION' : '✗ ACCESS_FINE_LOCATION'}
                  </span>
                </div>
              </div>
            </div>
            <button
              onClick={onRequestPermissions}
              className="px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white text-xs font-bold rounded-lg shadow-sm transition-all flex items-center space-x-1 shrink-0"
            >
              <Shield className="w-4 h-4 mr-1" />
              Grant All Permissions
            </button>
          </div>
        </div>
      )}

      {/* 2. MASTER AUTOMATION SWITCH CARD */}
      <div
        className={`rounded-2xl p-6 border-2 transition-all shadow-sm ${
          !isEnabled
            ? 'bg-slate-50 border-slate-200'
            : isPaused
            ? 'bg-amber-50/70 border-amber-200'
            : 'bg-emerald-50/60 border-emerald-300 shadow-emerald-50'
        }`}
      >
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="space-y-1">
            <div className="flex items-center space-x-3">
              <span className={`p-2.5 rounded-xl ${isEnabled ? 'bg-emerald-600 text-white' : 'bg-slate-300 text-slate-600'}`}>
                <Power className="w-6 h-6" />
              </span>
              <div>
                <h2 className="text-lg font-bold text-slate-900">Master Proximity Automation</h2>
                <p className="text-xs text-slate-600">
                  {!isEnabled
                    ? 'Automation is OFF • BLE monitoring is active, but Samsung Mode will not change'
                    : isPaused
                    ? `TEMPORARILY PAUSED • Resume in ${Math.max(0, Math.round((automationState.pauseUntilMillis - Date.now()) / 60000))} mins`
                    : 'ACTIVE • Automatically toggling Samsung Mode when proximity state transitions'}
                </p>
              </div>
            </div>
          </div>

          <div className="flex items-center space-x-3 self-end sm:self-center">
            <button
              id="btn-master-toggle"
              onClick={() => onToggleMaster(!isEnabled)}
              className={`relative inline-flex h-8 w-16 items-center rounded-full transition-colors focus:outline-none ${
                isEnabled ? 'bg-emerald-600' : 'bg-slate-300'
              }`}
            >
              <span
                className={`inline-block h-6 w-6 transform rounded-full bg-white transition-transform shadow-md ${
                  isEnabled ? 'translate-x-9' : 'translate-x-1'
                }`}
              />
            </button>
            <span className="text-xs font-bold uppercase tracking-wider text-slate-700">
              {isEnabled ? 'ENABLED' : 'DISABLED'}
            </span>
          </div>
        </div>

        {/* State Bar */}
        <div className="mt-5 pt-4 border-t border-slate-200/80 flex flex-wrap items-center justify-between gap-3 text-xs">
          <div className="flex items-center space-x-2">
            <span className={`inline-block w-2.5 h-2.5 rounded-full ${getStatusDotColor()}`} />
            <span className="font-semibold text-slate-800">Status:</span>
            <span className={`px-2.5 py-0.5 rounded-md border font-medium text-[11px] ${getStatusColor()}`}>
              {automationState.executionState}
            </span>
          </div>

          <div className="flex items-center space-x-4 text-slate-500 text-[11px]">
            <span>
              Evaluated: <strong className="text-slate-800">{automationState.totalTransitionsHandled}</strong>
            </span>
            <span>
              Success: <strong className="text-emerald-700">{automationState.successfulInvocations}</strong>
            </span>
            <span>
              Failed: <strong className="text-rose-600">{automationState.failedInvocations}</strong>
            </span>
          </div>
        </div>
      </div>

      {/* 3. PROXIMITY -> SAMSUNG MODE FLOW PIPELINE */}
      <div className="bg-white border border-slate-200 rounded-2xl p-6 shadow-sm">
        <h3 className="text-sm font-bold text-slate-900 flex items-center space-x-2 mb-4">
          <Layers className="w-4 h-4 text-blue-600" />
          <span>Real-Time Proximity → Samsung Mode Automation Pipeline</span>
        </h3>

        <div className="grid grid-cols-1 md:grid-cols-5 gap-3 items-center">
          {/* Node 1: Beacon */}
          <div className="bg-slate-50 border border-slate-200 rounded-xl p-3.5 text-center">
            <div className="w-10 h-10 mx-auto rounded-full bg-blue-100 text-blue-700 flex items-center justify-center mb-2">
              <Bluetooth className="w-5 h-5" />
            </div>
            <div className="text-xs font-bold text-slate-900 truncate">
              {activeProfile?.targetDisplayName || 'SmartTag 1'}
            </div>
            <div className="text-[11px] font-mono text-slate-500 mt-0.5">
              RSSI: {Math.round(filteredRssi)} dBm
            </div>
          </div>

          <div className="hidden md:flex justify-center text-slate-400">
            <ArrowRight className="w-5 h-5" />
          </div>

          {/* Node 2: Proximity State Machine */}
          <div
            className={`border rounded-xl p-3.5 text-center transition-all ${
              proximityState === 'INSIDE'
                ? 'bg-emerald-50 border-emerald-300'
                : proximityState === 'OUTSIDE'
                ? 'bg-blue-50 border-blue-300'
                : 'bg-slate-50 border-slate-200'
            }`}
          >
            <div
              className={`w-10 h-10 mx-auto rounded-full flex items-center justify-center font-bold text-sm mb-2 ${
                proximityState === 'INSIDE'
                  ? 'bg-emerald-200 text-emerald-800'
                  : proximityState === 'OUTSIDE'
                  ? 'bg-blue-200 text-blue-800'
                  : 'bg-slate-200 text-slate-700'
              }`}
            >
              {proximityState === 'INSIDE' ? 'IN' : proximityState === 'OUTSIDE' ? 'OUT' : '?'}
            </div>
            <div className="text-xs font-bold text-slate-900">{proximityState}</div>
            <div className="text-[11px] text-slate-500 mt-0.5">{confidencePercent}% confidence</div>
          </div>

          <div className="hidden md:flex justify-center text-slate-400">
            <ArrowRight className="w-5 h-5" />
          </div>

          {/* Node 3: Samsung Mode */}
          <div
            className={`border rounded-xl p-3.5 text-center transition-all ${
              proximityState === 'INSIDE' && isEnabled
                ? 'bg-purple-50 border-purple-300'
                : 'bg-slate-50 border-slate-200'
            }`}
          >
            <div
              className={`w-10 h-10 mx-auto rounded-full flex items-center justify-center mb-2 ${
                proximityState === 'INSIDE' && isEnabled
                  ? 'bg-purple-200 text-purple-800'
                  : 'bg-slate-200 text-slate-600'
              }`}
            >
              <Smartphone className="w-5 h-5" />
            </div>
            <div className="text-xs font-bold text-slate-900 truncate">
              {automationState.targetModeName || 'Bedroom Focus'}
            </div>
            <div
              className={`text-[11px] font-bold mt-0.5 ${
                proximityState === 'INSIDE' && isEnabled ? 'text-purple-700' : 'text-slate-500'
              }`}
            >
              {proximityState === 'INSIDE' && isEnabled ? 'START (ON)' : 'STOP (OFF)'}
            </div>
          </div>
        </div>

        {/* Transition Logic Rules Summary */}
        <div className="mt-4 pt-3 border-t border-slate-100 flex flex-wrap gap-4 text-[11px] text-slate-600">
          <span className="flex items-center space-x-1">
            <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
            <span><strong>OUTSIDE → INSIDE:</strong> Dispatches <code>startMode(uuid)</code></span>
          </span>
          <span className="flex items-center space-x-1">
            <span className="w-2 h-2 rounded-full bg-blue-500"></span>
            <span><strong>INSIDE → OUTSIDE:</strong> Dispatches <code>stopMode(uuid)</code></span>
          </span>
          <span className="flex items-center space-x-1">
            <span className="w-2 h-2 rounded-full bg-slate-400"></span>
            <span><strong>UNKNOWN:</strong> Preserves state (Safety hold)</span>
          </span>
        </div>
      </div>

      {/* 4. TARGET SAMSUNG MODE & SAFETY OVERRIDES GRID */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Target Samsung Mode Binding */}
        <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2">
              <Smartphone className="w-4 h-4 text-purple-600" />
              <h3 className="text-sm font-bold text-slate-900">Target Samsung Mode Binding</h3>
            </div>
            <button
              onClick={() => {
                setTempUuid(automationState.targetModeUuid);
                setTempName(automationState.targetModeName);
                setShowUuidModal(true);
              }}
              className="text-xs text-blue-600 hover:text-blue-700 font-semibold flex items-center space-x-1"
            >
              <Edit3 className="w-3.5 h-3.5" />
              <span>Edit</span>
            </button>
          </div>

          <div className="bg-slate-50 border border-slate-200 rounded-xl p-3.5 flex items-center justify-between">
            <div>
              <div className="text-xs font-bold text-slate-900">{automationState.targetModeName}</div>
              <div className="text-[11px] font-mono text-slate-500 truncate max-w-[240px]">
                {automationState.targetModeUuid || 'No UUID configured'}
              </div>
            </div>
            <span className="px-2 py-1 bg-purple-100 text-purple-800 text-[10px] font-bold rounded-md uppercase">
              Target
            </span>
          </div>

          {/* Quick Presets */}
          <div className="space-y-1.5">
            <label className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">
              Quick Samsung Routines Presets
            </label>
            <div className="flex flex-wrap gap-2">
              {[
                { name: 'Bedroom Focus', uuid: 'routine-bedroom-focus-01' },
                { name: 'Work / Study', uuid: 'routine-work-mode-02' },
                { name: 'Sleep & Relax', uuid: 'routine-sleep-relax-03' },
              ].map((preset) => (
                <button
                  key={preset.uuid}
                  onClick={() => onSetTargetMode(preset.uuid, preset.name)}
                  className={`px-2.5 py-1 rounded-lg text-xs font-medium border transition-colors ${
                    automationState.targetModeUuid === preset.uuid
                      ? 'bg-purple-100 text-purple-800 border-purple-300 font-semibold'
                      : 'bg-slate-50 hover:bg-slate-100 text-slate-700 border-slate-200'
                  }`}
                >
                  {preset.name}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Safety Overrides & Simulation */}
        <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-sm space-y-4">
          <div className="flex items-center space-x-2">
            <Shield className="w-4 h-4 text-emerald-600" />
            <h3 className="text-sm font-bold text-slate-900">Safety Overrides & Diagnostics</h3>
          </div>

          <div className="grid grid-cols-2 gap-2.5">
            {isPaused ? (
              <button
                onClick={onResume}
                className="col-span-2 py-2 px-3 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-bold flex items-center justify-center space-x-1.5 shadow-sm transition-all"
              >
                <Play className="w-4 h-4" />
                <span>Resume Automation</span>
              </button>
            ) : (
              <>
                <button
                  onClick={() => onPause(15)}
                  className="py-2 px-3 bg-slate-50 hover:bg-slate-100 border border-slate-200 text-slate-700 rounded-xl text-xs font-medium flex items-center justify-center space-x-1.5 transition-all"
                >
                  <Pause className="w-3.5 h-3.5 text-amber-600" />
                  <span>Pause 15m</span>
                </button>
                <button
                  onClick={() => onPause(60)}
                  className="py-2 px-3 bg-slate-50 hover:bg-slate-100 border border-slate-200 text-slate-700 rounded-xl text-xs font-medium flex items-center justify-center space-x-1.5 transition-all"
                >
                  <Clock className="w-3.5 h-3.5 text-amber-600" />
                  <span>Pause 1h</span>
                </button>
              </>
            )}

            <button
              onClick={onReconcile}
              className="py-2 px-3 bg-slate-50 hover:bg-slate-100 border border-slate-200 text-slate-700 rounded-xl text-xs font-medium flex items-center justify-center space-x-1.5 transition-all"
            >
              <RefreshCw className="w-3.5 h-3.5 text-blue-600" />
              <span>Reconcile State</span>
            </button>

            <button
              onClick={onEmergencyStop}
              className="py-2 px-3 bg-rose-50 hover:bg-rose-100 border border-rose-200 text-rose-700 rounded-xl text-xs font-bold flex items-center justify-center space-x-1.5 transition-all"
            >
              <AlertTriangle className="w-3.5 h-3.5 text-rose-600" />
              <span>Emergency Stop</span>
            </button>
          </div>

          {/* Scenario Trigger Simulation */}
          {onSimulateTransition && (
            <div className="pt-2 border-t border-slate-100">
              <div className="text-[11px] font-bold text-slate-500 uppercase mb-1.5">
                Simulate Proximity Transition Trigger
              </div>
              <div className="flex gap-2">
                <button
                  onClick={() => onSimulateTransition('INSIDE')}
                  className="flex-1 py-1.5 px-2 bg-emerald-50 hover:bg-emerald-100 text-emerald-800 border border-emerald-200 rounded-lg text-xs font-semibold"
                >
                  Simulate INSIDE
                </button>
                <button
                  onClick={() => onSimulateTransition('OUTSIDE')}
                  className="flex-1 py-1.5 px-2 bg-blue-50 hover:bg-blue-100 text-blue-800 border border-blue-200 rounded-lg text-xs font-semibold"
                >
                  Simulate OUTSIDE
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 5. PER-DEVICE PROFILES & PERSISTENCE MANAGER */}
      <div className="bg-white border border-slate-200 rounded-2xl p-6 shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <Zap className="w-4 h-4 text-amber-500" />
            <h3 className="text-sm font-bold text-slate-900">
              Per-Device Calibration Profiles ({Object.keys(savedProfiles).length})
            </h3>
          </div>
          <span className="text-xs text-emerald-700 bg-emerald-50 border border-emerald-200 px-2.5 py-0.5 rounded-full font-medium">
            Persisted in Storage
          </span>
        </div>

        {Object.keys(savedProfiles).length === 0 ? (
          <div className="text-center py-6 border-2 border-dashed border-slate-200 rounded-xl">
            <p className="text-xs text-slate-500">
              No calibrated profiles saved yet. Calibrate a BLE beacon in the Calibration tab to save its profile.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {(Object.values(savedProfiles) as ProximityProfile[]).map((profile: ProximityProfile) => {
              const isSelected = activeProfile?.targetDeviceKey === profile.targetDeviceKey;
              return (
                <div
                  key={profile.targetDeviceKey}
                  onClick={() => onSelectDeviceProfile(profile.targetDeviceKey)}
                  className={`p-3.5 rounded-xl border cursor-pointer transition-all ${
                    isSelected
                      ? 'bg-blue-50/80 border-blue-400 shadow-sm ring-1 ring-blue-400'
                      : 'bg-slate-50 hover:bg-slate-100/70 border-slate-200'
                  }`}
                >
                  <div className="flex items-start justify-between">
                    <div>
                      <div className="text-xs font-bold text-slate-900">{profile.profileName}</div>
                      <div className="text-[11px] text-slate-600 mt-0.5">
                        Device: <strong>{profile.targetDisplayName}</strong>
                      </div>
                    </div>
                    {isSelected && (
                      <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-blue-600 text-white">
                        ACTIVE
                      </span>
                    )}
                  </div>

                  <div className="mt-2.5 pt-2 border-t border-slate-200/60 flex items-center justify-between text-[11px] font-mono text-slate-600">
                    <span>ENTER: {profile.enterThresholdRssi} dBm</span>
                    <span>EXIT: {profile.exitThresholdRssi} dBm</span>
                    <span>Filter: {profile.filterType}</span>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Reset All Data Button */}
        <div className="pt-4 border-t border-slate-100 flex items-center justify-between">
          <span className="text-xs text-slate-500">Reset saved devices, calibration profiles, and settings</span>
          <button
            onClick={() => setShowResetModal(true)}
            className="px-3 py-1.5 bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 text-xs font-bold rounded-lg flex items-center space-x-1 transition-colors"
          >
            <Trash2 className="w-3.5 h-3.5" />
            <span>Reset All Stored Data</span>
          </button>
        </div>
      </div>

      {/* 6. AUTOMATION AUDIT TRAIL LOG */}
      <div className="bg-white border border-slate-200 rounded-2xl p-6 shadow-sm space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-bold text-slate-900 flex items-center space-x-2">
            <Sparkles className="w-4 h-4 text-purple-600" />
            <span>Real-Time Automation Audit Log</span>
          </h3>
          <span className="text-[11px] font-mono text-slate-500">
            {auditEvents.length} events logged
          </span>
        </div>

        <div className="bg-slate-950 rounded-xl p-4 font-mono text-xs text-slate-300 max-h-64 overflow-y-auto space-y-1.5 border border-slate-800">
          {auditEvents.length === 0 ? (
            <div className="text-slate-600 italic">No automated actions dispatched yet. Enable automation to begin.</div>
          ) : (
            auditEvents.slice().reverse().map((ev) => (
              <div key={ev.id} className="flex items-start space-x-2 text-[11px] leading-relaxed">
                <span className="text-slate-500 shrink-0">
                  [{new Date(ev.timestampMillis).toLocaleTimeString()}]
                </span>
                <span
                  className={`font-bold shrink-0 ${
                    ev.action.includes('SUCCESS') || ev.action.includes('ON')
                      ? 'text-emerald-400'
                      : ev.action.includes('FAILED') || ev.action.includes('EMERGENCY')
                      ? 'text-rose-400'
                      : 'text-amber-400'
                  }`}
                >
                  [{ev.action}]
                </span>
                <span className="text-slate-300">{ev.message}</span>
              </div>
            ))
          )}
        </div>
      </div>

      {/* UUID EDIT MODAL */}
      {showUuidModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl space-y-4">
            <h3 className="text-base font-bold text-slate-900">Configure Samsung Mode Binding</h3>
            <div className="space-y-3">
              <div>
                <label className="text-xs font-semibold text-slate-700 block mb-1">Friendly Name</label>
                <input
                  type="text"
                  value={tempName}
                  onChange={(e) => setTempName(e.target.value)}
                  className="w-full text-xs p-2.5 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                  placeholder="e.g. Bedroom Focus"
                />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-700 block mb-1">Samsung Routine / Mode UUID</label>
                <input
                  type="text"
                  value={tempUuid}
                  onChange={(e) => setTempUuid(e.target.value)}
                  className="w-full text-xs p-2.5 font-mono border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                  placeholder="e.g. routine-bedroom-focus-01"
                />
              </div>
            </div>
            <div className="flex justify-end space-x-2 pt-2">
              <button
                onClick={() => setShowUuidModal(false)}
                className="px-4 py-2 border border-slate-200 text-slate-700 text-xs font-bold rounded-lg hover:bg-slate-50"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  onSetTargetMode(tempUuid, tempName);
                  setShowUuidModal(false);
                }}
                className="px-4 py-2 bg-purple-600 hover:bg-purple-700 text-white text-xs font-bold rounded-lg"
              >
                Save Binding
              </button>
            </div>
          </div>
        </div>
      )}

      {/* RESET CONFIRMATION MODAL */}
      {showResetModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl space-y-4">
            <div className="flex items-center space-x-3 text-rose-600">
              <AlertTriangle className="w-6 h-6" />
              <h3 className="text-base font-bold text-slate-900">Reset All Application Data?</h3>
            </div>
            <p className="text-xs text-slate-600 leading-relaxed">
              This will wipe all saved BLE devices, per-device calibration profiles, custom threshold parameters, and automation settings from storage. The app will return to initial factory state.
            </p>
            <div className="flex justify-end space-x-2 pt-2">
              <button
                onClick={() => setShowResetModal(false)}
                className="px-4 py-2 border border-slate-200 text-slate-700 text-xs font-bold rounded-lg hover:bg-slate-50"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  onResetAllData();
                  setShowResetModal(false);
                }}
                className="px-4 py-2 bg-rose-600 hover:bg-rose-700 text-white text-xs font-bold rounded-lg shadow-sm"
              >
                Confirm Full Reset
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
