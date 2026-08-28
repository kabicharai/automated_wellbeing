import React from 'react';
import { Layers, CheckCircle2, Circle, Clock, GitFork, Shield, Radio, BarChart3, Sliders, Activity, Terminal } from 'lucide-react';

export function PhaseRoadmapView() {
  const phases = [
    {
      number: '1',
      title: 'BLE Device Discovery & Live RSSI Monitor',
      status: 'COMPLETED',
      description:
        'Standard Android 16 BLE scanner, stable identity abstraction (BleDeviceId), raw advertisement inspector, and continuous RSSI telemetry waveform.',
      deliverables: [
        'Android BluetoothLeScanner with Android 16 permissions',
        'Stable identity abstraction across MAC randomization',
        'Samsung SmartTag 1 & Generic BLE Beacon payload inspector',
        'Live RSSI Monitor with 5s / 15s / 30s / 60s / 5m rolling windows',
        'Real-time mean, median, min, max, std dev calculations',
      ],
      icon: Radio,
    },
    {
      number: '2',
      title: 'RSSI Filtering & Dual-Zone Calibration Engine',
      status: 'COMPLETED',
      description:
        'Guided 2-step calibration UX (30s Outside + 30s Inside), statistical distribution modeling, percentiles (p10, p25, p75, p90), and separation quality evaluation.',
      deliverables: [
        'Step 1: 30s Outside Calibration sample collection',
        'Step 2: 30s Inside Calibration sample collection',
        'Distribution overlap analysis & separation rating (Good/Moderate/Poor)',
        'Suggested ENTER & EXIT candidate threshold generation',
      ],
      icon: Sliders,
    },
    {
      number: '3',
      title: 'Proximity State Machine & Anti-Flapping Engine',
      status: 'COMPLETED',
      description:
        'Deterministic 3-state machine (UNKNOWN, INSIDE, OUTSIDE) with dual hysteresis thresholds, configurable temporal persistence timers, and lost-beacon protection.',
      deliverables: [
        'Hysteresis band (e.g. ENTER >= -64 dBm, EXIT <= -69 dBm)',
        'Temporal candidate duration timers (5s enter / 10s exit)',
        'Lost device 30s timeout (prevents false outside transitions)',
        'Confidence score metric calculation',
        'Anti-flapping candidate verification & instant abort logic',
      ],
      icon: Activity,
    },
    {
      number: '4',
      title: 'Automation Controller & Samsung Modes Integration',
      status: 'IN_PROGRESS',
      description:
        'Zero-churn state transition mediator invoking the working One UI 8.0/8.5 controller APIs on confirmed INSIDE/OUTSIDE boundary events.',
      deliverables: [
        'OUTSIDE → INSIDE: Invoke startMode(uuid)',
        'INSIDE → OUTSIDE: Invoke stopMode(uuid)',
        'Duplicate invocation suppression & UNKNOWN state safety',
        'Controlled exponential backoff retries on failure',
        'Master Automation ON/OFF and Emergency Stop',
      ],
      icon: Shield,
    },
    {
      number: '5',
      title: 'Background Monitoring & Foreground Service',
      status: 'PLANNED',
      description:
        'Android 16 Foreground Service with live notification status, continuous background BLE observation when screen is off, and battery scan profiles.',
      deliverables: [
        'Persistent Android Foreground Service',
        'Live notification with state, RSSI, and profile name',
        'Battery optimization (Battery Saver, Balanced, High Reliability)',
      ],
      icon: Layers,
    },
    {
      number: '6',
      title: 'Multi-Profile Storage, Setup Wizard & Export',
      status: 'PLANNED',
      description:
        'Profile persistence (Room, Beacon, Thresholds, Samsung UUID), 10-step guided onboarding wizard, developer simulation mode, and diagnostic export.',
      deliverables: [
        'Local room profile persistence (Bedroom, Office, etc.)',
        '10-Step Setup Wizard',
        'Developer simulation & sequence replay',
        'Comprehensive JSON diagnostic export bundle',
      ],
      icon: GitFork,
    },
  ];

  return (
    <div className="space-y-6">
      {/* Top Architecture Card */}
      <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6 shadow-xl">
        <div className="flex items-center gap-3 mb-4">
          <div className="w-10 h-10 rounded-xl bg-blue-600/20 border border-blue-500/40 flex items-center justify-center text-blue-400">
            <GitFork className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-white tracking-tight">
              Decoupled Two-System Architecture
            </h2>
            <p className="text-xs text-neutral-400">
              Strict separation between BLE Proximity Engine and Samsung Mode Controllers
            </p>
          </div>
        </div>

        {/* Architecture Flow Diagram */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-2">
          <div className="bg-neutral-950 p-4 rounded-xl border border-neutral-800">
            <div className="flex items-center justify-between text-xs font-bold text-blue-400 mb-2">
              <span>SYSTEM A: BLE Engine</span>
              <Radio className="w-4 h-4" />
            </div>
            <ul className="text-[11px] text-neutral-400 space-y-1 font-mono">
              <li>• BleScanner</li>
              <li>• BleDeviceIdentifier</li>
              <li>• RssiTracker & Filter</li>
              <li>• ProximityStateMachine</li>
            </ul>
          </div>

          <div className="bg-neutral-950 p-4 rounded-xl border border-blue-900/60 flex flex-col justify-between">
            <div className="flex items-center justify-between text-xs font-bold text-purple-400 mb-2">
              <span>MEDIATOR: Automation</span>
              <Layers className="w-4 h-4" />
            </div>
            <p className="text-[11px] text-neutral-300 font-sans">
              Reconciles proximity transitions and coordinates Mode start/stop without direct coupling.
            </p>
            <div className="text-[10px] text-purple-300 font-mono mt-2 bg-purple-950/60 p-1.5 rounded border border-purple-800">
              OUTSIDE ↔ INSIDE Transitions
            </div>
          </div>

          <div className="bg-neutral-950 p-4 rounded-xl border border-neutral-800">
            <div className="flex items-center justify-between text-xs font-bold text-emerald-400 mb-2">
              <span>SYSTEM B: Samsung Modes</span>
              <Shield className="w-4 h-4" />
            </div>
            <ul className="text-[11px] text-neutral-400 space-y-1 font-mono">
              <li>• Working POC Controllers</li>
              <li>• One UI 8.5 Shortcut Activity</li>
              <li>• One UI 8.0 External Provider</li>
              <li>• Restrict App Usage</li>
            </ul>
          </div>
        </div>
      </div>

      {/* 6 Phases Timeline Cards */}
      <div className="space-y-4">
        <h3 className="text-sm font-bold text-white tracking-tight flex items-center gap-2">
          <Clock className="w-4 h-4 text-blue-400" /> Sequential Development Phases
        </h3>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {phases.map((p) => {
            const Icon = p.icon;
            const isInProgress = p.status === 'IN_PROGRESS';

            return (
              <div
                key={p.number}
                className={`bg-neutral-900 border rounded-2xl p-5 flex flex-col justify-between transition-all ${
                  isInProgress
                    ? 'border-blue-500 bg-neutral-900/90 shadow-lg shadow-blue-950/30'
                    : 'border-neutral-800 hover:border-neutral-700'
                }`}
              >
                <div>
                  <div className="flex items-start justify-between gap-3 mb-3">
                    <div className="flex items-center gap-3">
                      <div
                        className={`w-8 h-8 rounded-lg flex items-center justify-center font-bold text-xs ${
                          isInProgress
                            ? 'bg-blue-600 text-white'
                            : 'bg-neutral-800 text-neutral-400'
                        }`}
                      >
                        P{p.number}
                      </div>
                      <div>
                        <h4 className="text-sm font-bold text-white">{p.title}</h4>
                        <span
                          className={`inline-block text-[9px] font-bold font-mono px-1.5 py-0.2 rounded mt-0.5 ${
                            isInProgress
                              ? 'bg-blue-950 text-blue-300 border border-blue-800 animate-pulse'
                              : 'bg-neutral-800 text-neutral-400'
                          }`}
                        >
                          {isInProgress ? 'CURRENT ACTIVE PHASE' : 'UPCOMING'}
                        </span>
                      </div>
                    </div>
                    <Icon className="w-4 h-4 text-neutral-500" />
                  </div>

                  <p className="text-xs text-neutral-400 mb-3">{p.description}</p>

                  <div className="space-y-1.5 border-t border-neutral-800 pt-3">
                    {p.deliverables.map((d, i) => (
                      <div key={i} className="flex items-start gap-2 text-xs text-neutral-300">
                        {isInProgress ? (
                          <CheckCircle2 className="w-3.5 h-3.5 text-blue-400 shrink-0 mt-0.5" />
                        ) : (
                          <Circle className="w-3.5 h-3.5 text-neutral-600 shrink-0 mt-0.5" />
                        )}
                        <span>{d}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
