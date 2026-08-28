import React, { useState, useEffect, useRef } from 'react';
import { BleDeviceProfile, ProximityProfile, ProximityState, CandidateStatus, ProximityTransitionEvent, ProximityEngineSnapshot } from '../types';
import { Radar, Activity, ShieldCheck, Play, RotateCcw, AlertTriangle, ArrowRight, ArrowLeft, CheckCircle2, Zap, Clock, Radio, Info } from 'lucide-react';

interface ProximityStateViewProps {
  activeProfile: ProximityProfile | null;
  savedDevice: BleDeviceProfile | null;
  onLog: (level: 'INFO' | 'SUCCESS' | 'WARN' | 'ERROR', message: string) => void;
}

export const ProximityStateView: React.FC<ProximityStateViewProps> = ({
  activeProfile,
  savedDevice,
  onLog,
}) => {
  // Engine configuration (calibrated or defaults)
  const enterThreshold = activeProfile?.enterThresholdRssi ?? -64;
  const exitThreshold = activeProfile?.exitThresholdRssi ?? -69;
  const enterDuration = activeProfile?.enterDurationSeconds ?? 5;
  const exitDuration = activeProfile?.exitDurationSeconds ?? 10;
  const lostTimeout = 30;

  // Real-time State Machine state
  const [currentState, setCurrentState] = useState<ProximityState>('UNKNOWN');
  const [candidateStatus, setCandidateStatus] = useState<CandidateStatus>('NONE');
  const [candidateElapsed, setCandidateElapsed] = useState<number>(0);
  const [currentRssi, setCurrentRssi] = useState<number>(-75);
  const [filteredRssi, setFilteredRssi] = useState<number>(-75.0);
  const [confidence, setConfidence] = useState<number>(85);
  const [secondsSinceLastPacket, setSecondsSinceLastPacket] = useState<number>(0);
  const [isPacketLossActive, setIsPacketLossActive] = useState<boolean>(false);
  const [events, setEvents] = useState<ProximityTransitionEvent[]>([
    {
      id: 'ev-init',
      timestampMillis: Date.now() - 60000,
      fromState: 'UNKNOWN',
      toState: 'UNKNOWN',
      candidateStatus: 'NONE',
      filteredRssi: null,
      rawRssi: null,
      reason: 'Proximity Engine initialized with calibrated thresholds [ENTER: -64 dBm, EXIT: -69 dBm]',
    },
  ]);

  // Timers & candidate tracking refs
  const stateRef = useRef(currentState);
  stateRef.current = currentState;
  const candidateStatusRef = useRef(candidateStatus);
  candidateStatusRef.current = candidateStatus;
  const candidateStartRef = useRef<number>(0);
  const filteredRssiRef = useRef<number>(filteredRssi);
  const secondsSinceLastPacketRef = useRef<number>(0);

  // EMA Filter calculation
  const alpha = 0.25;

  const recordEvent = (from: ProximityState, to: ProximityState, status: CandidateStatus, reason: string, filtVal?: number) => {
    const effectiveFilt = filtVal !== undefined ? filtVal : filteredRssiRef.current;
    const ev: ProximityTransitionEvent = {
      id: 'ev-' + Date.now() + '-' + Math.random().toString(36).substr(2, 4),
      timestampMillis: Date.now(),
      fromState: from,
      toState: to,
      candidateStatus: status,
      filteredRssi: Math.round(effectiveFilt * 10) / 10,
      rawRssi: currentRssi,
      reason,
    };
    setEvents((prev) => [ev, ...prev.slice(0, 19)]);
    onLog(to === 'INSIDE' ? 'SUCCESS' : to === 'OUTSIDE' ? 'WARN' : 'INFO', `[PROX STATE] ${from} → ${to} : ${reason}`);
  };

  // High resolution tick (200ms) for candidate timer & filtering
  useEffect(() => {
    const interval = setInterval(() => {
      if (isPacketLossActive) {
        secondsSinceLastPacketRef.current += 0.2;
        const currentLostSec = secondsSinceLastPacketRef.current;
        setSecondsSinceLastPacket(currentLostSec);

        if (currentLostSec >= lostTimeout && stateRef.current !== 'UNKNOWN') {
          const prevSt = stateRef.current;
          setCurrentState('UNKNOWN');
          setCandidateStatus('NONE');
          recordEvent(prevSt, 'UNKNOWN', 'NONE', `Beacon signal timeout (${lostTimeout}s without packets). Transitioned to UNKNOWN.`);
        }
        return;
      }

      secondsSinceLastPacketRef.current = 0;
      setSecondsSinceLastPacket(0);

      // Natural sensor jitter
      const jitter = (Math.random() - 0.5) * 1.5;
      const effectiveRaw = currentRssi + jitter;

      // Update Filtered RSSI via EMA
      const prevFilt = filteredRssiRef.current;
      const nextFilt = prevFilt + alpha * (effectiveRaw - prevFilt);
      const roundedFilt = Math.round(nextFilt * 10) / 10;
      filteredRssiRef.current = roundedFilt;
      setFilteredRssi(roundedFilt);

      evaluateProximity(roundedFilt, currentRssi);
    }, 200);

    return () => clearInterval(interval);
  }, [currentRssi, isPacketLossActive, enterThreshold, exitThreshold, enterDuration, exitDuration]);

  const evaluateProximity = (filt: number, raw: number) => {
    const st = stateRef.current;
    const cand = candidateStatusRef.current;
    const now = Date.now();

    if (st === 'UNKNOWN') {
      if (filt >= enterThreshold) {
        if (cand !== 'ENTERING') {
          setCandidateStatus('ENTERING');
          candidateStartRef.current = now;
          recordEvent('UNKNOWN', 'UNKNOWN', 'ENTERING', `Signal entered INSIDE zone (${filt} dBm ≥ ${enterThreshold} dBm). Starting ENTER candidate (${enterDuration}s)...`);
        }
      } else if (filt <= exitThreshold) {
        if (cand !== 'EXITING') {
          setCandidateStatus('EXITING');
          candidateStartRef.current = now;
          recordEvent('UNKNOWN', 'UNKNOWN', 'EXITING', `Signal entered OUTSIDE zone (${filt} dBm ≤ ${exitThreshold} dBm). Starting EXIT candidate (${exitDuration}s)...`);
        }
      }
    } else if (st === 'OUTSIDE') {
      if (filt >= enterThreshold) {
        if (cand !== 'ENTERING') {
          setCandidateStatus('ENTERING');
          candidateStartRef.current = now;
          recordEvent('OUTSIDE', 'OUTSIDE', 'ENTERING', `Filtered RSSI (${filt} dBm) crossed ENTER threshold (${enterThreshold} dBm). Verifying temporal stability (${enterDuration}s)...`);
        }
      } else {
        if (cand === 'ENTERING') {
          const elapsedSec = ((now - candidateStartRef.current) / 1000).toFixed(1);
          setCandidateStatus('NONE');
          setCandidateElapsed(0);
          recordEvent('OUTSIDE', 'OUTSIDE', 'NONE', `ENTER candidate aborted after ${elapsedSec}s: Signal dropped to ${filt} dBm (< ${enterThreshold} dBm)`);
        }
      }
    } else if (st === 'INSIDE') {
      if (filt <= exitThreshold) {
        if (cand !== 'EXITING') {
          setCandidateStatus('EXITING');
          candidateStartRef.current = now;
          recordEvent('INSIDE', 'INSIDE', 'EXITING', `Filtered RSSI (${filt} dBm) crossed EXIT threshold (${exitThreshold} dBm). Verifying temporal stability (${exitDuration}s)...`);
        }
      } else {
        if (cand === 'EXITING') {
          const elapsedSec = ((now - candidateStartRef.current) / 1000).toFixed(1);
          setCandidateStatus('NONE');
          setCandidateElapsed(0);
          recordEvent('INSIDE', 'INSIDE', 'NONE', `EXIT candidate aborted after ${elapsedSec}s: Signal recovered to ${filt} dBm (> ${exitThreshold} dBm)`);
        }
      }
    }

    // Check candidate completion
    if (candidateStatusRef.current !== 'NONE') {
      const elapsedMs = now - candidateStartRef.current;
      const targetSec = candidateStatusRef.current === 'ENTERING' ? enterDuration : exitDuration;
      const targetMs = targetSec * 1000;
      setCandidateElapsed(Math.min(targetSec, Math.floor(elapsedMs / 1000)));

      if (elapsedMs >= targetMs) {
        const prevSt = stateRef.current;
        if (candidateStatusRef.current === 'ENTERING') {
          setCurrentState('INSIDE');
          setCandidateStatus('NONE');
          setCandidateElapsed(0);
          recordEvent(prevSt, 'INSIDE', 'NONE', `ENTER verified: Signal sustained above ${enterThreshold} dBm for ${enterDuration}s. [Transition: ${prevSt} → INSIDE]`);
        } else if (candidateStatusRef.current === 'EXITING') {
          setCurrentState('OUTSIDE');
          setCandidateStatus('NONE');
          setCandidateElapsed(0);
          recordEvent(prevSt, 'OUTSIDE', 'NONE', `EXIT verified: Signal sustained below ${exitThreshold} dBm for ${exitDuration}s. [Transition: ${prevSt} → OUTSIDE]`);
        }
      }
    }
  };

  // Test Presets
  const triggerWalkInside = () => {
    setIsPacketLossActive(false);
    setCurrentRssi(-54);
    onLog('INFO', 'Test Preset: User walked INSIDE (Beacon RSSI ~ -54 dBm)');
  };

  const triggerWalkOutside = () => {
    setIsPacketLossActive(false);
    setCurrentRssi(-78);
    onLog('INFO', 'Test Preset: User walked OUTSIDE (Beacon RSSI ~ -78 dBm)');
  };

  const triggerTransientSpike = () => {
    setIsPacketLossActive(false);
    onLog('INFO', 'Test Preset: Simulating transient 2s spike (-56 dBm) to test anti-flapping abort...');
    setCurrentRssi(-56);
    setTimeout(() => {
      setCurrentRssi(-78);
      onLog('INFO', 'Test Preset: Spike subsided back to -78 dBm. Candidate should abort.');
    }, 2000);
  };

  const togglePacketLoss = () => {
    setIsPacketLossActive((prev) => {
      const next = !prev;
      onLog(next ? 'WARN' : 'SUCCESS', next ? 'Test Preset: Packet loss simulated (beacon disconnected)' : 'Test Preset: Beacon signals resumed');
      return next;
    });
  };

  const resetEngine = () => {
    setCurrentState('UNKNOWN');
    setCandidateStatus('NONE');
    setCandidateElapsed(0);
    setFilteredRssi(-75);
    setCurrentRssi(-75);
    setIsPacketLossActive(false);
    setSecondsSinceLastPacket(0);
    recordEvent('UNKNOWN', 'UNKNOWN', 'NONE', 'Proximity State Machine manually reset');
  };

  // Gauge coordinate calculations (-100 to -40 dBm span)
  const minRssi = -100;
  const maxRssi = -40;
  const span = maxRssi - minRssi;
  const exitPercent = ((exitThreshold - minRssi) / span) * 100;
  const enterPercent = ((enterThreshold - minRssi) / span) * 100;
  const needlePercent = Math.min(100, Math.max(0, ((filteredRssi - minRssi) / span) * 100));

  return (
    <div className="space-y-4">
      {/* Header Banner */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow-sm text-slate-100">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <div>
            <div className="flex items-center gap-2">
              <span className="px-2 py-0.5 text-xs font-semibold bg-blue-500/20 text-blue-400 rounded-full border border-blue-500/30">
                Phase 3 Active
              </span>
              <span className="text-xs text-slate-400 font-mono">Anti-Flapping State Machine</span>
            </div>
            <h2 className="text-lg font-bold mt-1 text-white flex items-center gap-2">
              <Radar className="w-5 h-5 text-blue-400" />
              Live Proximity Engine Monitor
            </h2>
            <p className="text-xs text-slate-400 mt-1 max-w-xl">
              Deterministic 3-state machine executing temporal hysteresis timers. Zero false triggers from signal fading, multi-path spikes, or body-blocking.
            </p>
          </div>

          <div className="flex items-center gap-3">
            {/* Active State Badge */}
            <div className={`px-4 py-2 rounded-xl font-mono font-bold text-sm flex items-center gap-2 border shadow-sm ${
              currentState === 'INSIDE'
                ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/50'
                : currentState === 'OUTSIDE'
                ? 'bg-rose-500/20 text-rose-400 border-rose-500/50'
                : 'bg-slate-800 text-slate-300 border-slate-700'
            }`}>
              <span className={`w-2.5 h-2.5 rounded-full ${
                currentState === 'INSIDE' ? 'bg-emerald-400 animate-pulse' : currentState === 'OUTSIDE' ? 'bg-rose-400' : 'bg-slate-400'
              }`} />
              STATE: {currentState}
            </div>

            <button
              onClick={resetEngine}
              className="p-2 bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-300 rounded-lg text-xs transition"
              title="Reset State Machine"
            >
              <RotateCcw className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>

      {/* Candidate Timer Alert Banner */}
      {candidateStatus !== 'NONE' && (
        <div className={`p-3.5 rounded-xl border flex items-center justify-between gap-4 transition-all ${
          candidateStatus === 'ENTERING'
            ? 'bg-emerald-950/40 border-emerald-500/60 text-emerald-200'
            : 'bg-rose-950/40 border-rose-500/60 text-rose-200'
        }`}>
          <div className="flex items-center gap-3">
            <Clock className={`w-5 h-5 animate-spin ${candidateStatus === 'ENTERING' ? 'text-emerald-400' : 'text-rose-400'}`} />
            <div>
              <div className="text-xs font-bold font-mono">
                {candidateStatus === 'ENTERING' ? 'VERIFYING ENTER CANDIDATE (TEMPORAL STABILITY)' : 'VERIFYING EXIT CANDIDATE (TEMPORAL STABILITY)'}
              </div>
              <div className="text-[11px] opacity-80">
                Signal must sustain {candidateStatus === 'ENTERING' ? `≥ ${enterThreshold} dBm` : `≤ ${exitThreshold} dBm`} for continuous confirmation.
              </div>
            </div>
          </div>

          <div className="text-right">
            <div className="text-xs font-bold font-mono">
              {candidateElapsed}s / {candidateStatus === 'ENTERING' ? enterDuration : exitDuration}s
            </div>
            <div className="w-28 bg-slate-950/80 rounded-full h-1.5 mt-1 overflow-hidden">
              <div
                className={`h-1.5 transition-all ${candidateStatus === 'ENTERING' ? 'bg-emerald-400' : 'bg-rose-400'}`}
                style={{
                  width: `${(candidateElapsed / (candidateStatus === 'ENTERING' ? enterDuration : exitDuration)) * 100}%`,
                }}
              />
            </div>
          </div>
        </div>
      )}

      {/* 3-State Machine Interactive Flow Visualizer */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-xs font-bold text-slate-300 uppercase tracking-wider font-mono">
            State Machine Architecture & Active Transition Path
          </h3>
          <span className="text-xs text-slate-400 font-mono">
            Active Profile: <strong className="text-slate-200">{activeProfile?.profileName || 'Bedroom SmartTag'}</strong>
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-3 items-center">
          {/* OUTSIDE State Node */}
          <div className={`p-4 rounded-xl border text-center transition-all ${
            currentState === 'OUTSIDE'
              ? 'bg-rose-950/40 border-rose-500 shadow-md ring-2 ring-rose-500/30'
              : 'bg-slate-950 border-slate-800 opacity-60'
          }`}>
            <div className="text-xs font-bold text-slate-400">OUTSIDE ZONE</div>
            <div className="text-lg font-bold text-rose-400 mt-1 font-mono">OUTSIDE</div>
            <div className="text-[11px] text-slate-400 mt-1 font-mono">RSSI ≤ {exitThreshold} dBm</div>
            <div className="mt-2 text-[10px] bg-slate-900 px-2 py-0.5 rounded text-slate-400 font-mono">
              Mode Action: OFF
            </div>
          </div>

          {/* Central Hysteresis Bridge */}
          <div className="p-3 bg-slate-950/80 border border-slate-800 rounded-xl space-y-2 text-center">
            <div className="text-[11px] font-bold text-slate-300">HYSTERESIS DEADBAND</div>
            <div className="text-xs font-mono text-amber-400 bg-amber-500/10 py-1 rounded border border-amber-500/20">
              {exitThreshold} dBm &lt; RSSI &lt; {enterThreshold} dBm
            </div>
            <div className="text-[10px] text-slate-400 leading-tight">
              Maintains current state. Rejects noise jitter.
            </div>

            <div className="pt-1 flex justify-center gap-4 text-[10px] font-mono">
              <span className="text-emerald-400 flex items-center gap-1">
                <ArrowRight className="w-3 h-3" /> ENTER: {enterDuration}s
              </span>
              <span className="text-rose-400 flex items-center gap-1">
                <ArrowLeft className="w-3 h-3" /> EXIT: {exitDuration}s
              </span>
            </div>
          </div>

          {/* INSIDE State Node */}
          <div className={`p-4 rounded-xl border text-center transition-all ${
            currentState === 'INSIDE'
              ? 'bg-emerald-950/40 border-emerald-500 shadow-md ring-2 ring-emerald-500/30'
              : 'bg-slate-950 border-slate-800 opacity-60'
          }`}>
            <div className="text-xs font-bold text-slate-400">INSIDE ZONE</div>
            <div className="text-lg font-bold text-emerald-400 mt-1 font-mono">INSIDE</div>
            <div className="text-[11px] text-slate-400 mt-1 font-mono">RSSI ≥ {enterThreshold} dBm</div>
            <div className="mt-2 text-[10px] bg-slate-900 px-2 py-0.5 rounded text-emerald-400 font-mono">
              Mode Action: ON
            </div>
          </div>
        </div>

        {/* Live Signal Needle & Hysteresis Gauge */}
        <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 space-y-3">
          <div className="flex justify-between items-center text-xs">
            <span className="text-slate-400 font-mono">SIGNAL GAUGE & HYSTERESIS BANDS</span>
            <span className="text-slate-300 font-mono">
              Confidence: <strong className="text-emerald-400">{isPacketLossActive ? 0 : confidence}%</strong>
            </span>
          </div>

          {/* Gauge Bar */}
          <div className="relative h-12 bg-slate-900 rounded-lg overflow-hidden border border-slate-800">
            {/* OUTSIDE Band */}
            <div
              className="absolute top-0 bottom-0 bg-rose-500/20 border-r border-rose-500/60"
              style={{ left: '0%', width: `${exitPercent}%` }}
            >
              <span className="absolute top-1 left-2 text-[9px] text-rose-400 font-mono">OUTSIDE ZONE</span>
            </div>

            {/* DEADBAND */}
            <div
              className="absolute top-0 bottom-0 bg-slate-700/20 border-r border-emerald-500/60"
              style={{ left: `${exitPercent}%`, width: `${enterPercent - exitPercent}%` }}
            >
              <span className="absolute top-1 left-2 text-[9px] text-slate-400 font-mono">DEADBAND</span>
            </div>

            {/* INSIDE Band */}
            <div
              className="absolute top-0 bottom-0 bg-emerald-500/20"
              style={{ left: `${enterPercent}%`, right: '0%' }}
            >
              <span className="absolute top-1 right-2 text-[9px] text-emerald-400 font-mono">INSIDE ZONE</span>
            </div>

            {/* EXIT Threshold Marker */}
            <div
              className="absolute top-0 bottom-0 w-0.5 bg-rose-500 z-10"
              style={{ left: `${exitPercent}%` }}
            >
              <span className="absolute bottom-1 -translate-x-1/2 text-[9px] bg-rose-500 text-slate-950 font-bold px-1 rounded">
                EXIT {exitThreshold}
              </span>
            </div>

            {/* ENTER Threshold Marker */}
            <div
              className="absolute top-0 bottom-0 w-0.5 bg-emerald-500 z-10"
              style={{ left: `${enterPercent}%` }}
            >
              <span className="absolute bottom-1 -translate-x-1/2 text-[9px] bg-emerald-500 text-slate-950 font-bold px-1 rounded">
                ENTER {enterThreshold}
              </span>
            </div>

            {/* Filtered RSSI Needle */}
            <div
              className="absolute top-0 bottom-0 w-1 bg-cyan-400 z-20 transition-all duration-200 shadow-md"
              style={{ left: `${needlePercent}%` }}
            >
              <div className="w-3 h-3 bg-cyan-400 rounded-full -translate-x-1 -mt-1 shadow-lg ring-2 ring-cyan-200" />
            </div>
          </div>

          <div className="flex justify-between text-xs font-mono text-slate-400">
            <div>
              <span className="text-slate-500">Raw RSSI: </span>
              <strong className="text-slate-300">{isPacketLossActive ? 'LOST' : `${currentRssi} dBm`}</strong>
            </div>
            <div>
              <span className="text-slate-500">Filtered (EMA): </span>
              <strong className="text-cyan-400">{isPacketLossActive ? 'LOST' : `${filteredRssi} dBm`}</strong>
            </div>
            <div>
              <span className="text-slate-500">Packet Loss: </span>
              <strong className={isPacketLossActive ? 'text-rose-400' : 'text-emerald-400'}>
                {isPacketLossActive ? `${secondsSinceLastPacket.toFixed(1)}s (LOST)` : '0.0s (HEALTHY)'}
              </strong>
            </div>
          </div>
        </div>
      </div>

      {/* Interactive Simulation & Test Suite */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-3">
        <h3 className="text-xs font-bold text-slate-300 uppercase tracking-wider font-mono flex items-center gap-2">
          <Zap className="w-4 h-4 text-amber-400" />
          Proximity Engine Simulation Playground
        </h3>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2.5">
          <button
            onClick={triggerWalkInside}
            className="p-3 bg-emerald-950/30 hover:bg-emerald-900/40 border border-emerald-500/40 rounded-lg text-left transition"
          >
            <div className="text-xs font-bold text-emerald-400 flex items-center justify-between">
              <span>Walk INSIDE</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </div>
            <div className="text-[11px] text-slate-400 mt-1">Set RSSI to -54 dBm (triggers 5s ENTER timer)</div>
          </button>

          <button
            onClick={triggerWalkOutside}
            className="p-3 bg-rose-950/30 hover:bg-rose-900/40 border border-rose-500/40 rounded-lg text-left transition"
          >
            <div className="text-xs font-bold text-rose-400 flex items-center justify-between">
              <span>Walk OUTSIDE</span>
              <ArrowLeft className="w-3.5 h-3.5" />
            </div>
            <div className="text-[11px] text-slate-400 mt-1">Set RSSI to -78 dBm (triggers 10s EXIT timer)</div>
          </button>

          <button
            onClick={triggerTransientSpike}
            className="p-3 bg-amber-950/30 hover:bg-amber-900/40 border border-amber-500/40 rounded-lg text-left transition"
          >
            <div className="text-xs font-bold text-amber-400 flex items-center justify-between">
              <span>Transient Spike (2s)</span>
              <Activity className="w-3.5 h-3.5" />
            </div>
            <div className="text-[11px] text-slate-400 mt-1">Tests anti-flapping abort on temporary bounce</div>
          </button>

          <button
            onClick={togglePacketLoss}
            className={`p-3 border rounded-lg text-left transition ${
              isPacketLossActive
                ? 'bg-rose-900/50 border-rose-500 text-rose-200'
                : 'bg-slate-800 hover:bg-slate-700/80 border-slate-700 text-slate-300'
            }`}
          >
            <div className="text-xs font-bold flex items-center justify-between">
              <span>{isPacketLossActive ? 'Resume Beacon Signal' : 'Simulate Packet Loss'}</span>
              <Radio className="w-3.5 h-3.5" />
            </div>
            <div className="text-[11px] text-slate-400 mt-1">
              {isPacketLossActive ? 'Beacon packets restored' : 'Timeout after 30s -> UNKNOWN'}
            </div>
          </button>
        </div>

        {/* Manual Slider */}
        <div className="bg-slate-950 p-3 rounded-lg border border-slate-800/80 mt-2">
          <div className="flex justify-between text-xs font-mono text-slate-400">
            <span>Manual RSSI Slider:</span>
            <strong className="text-cyan-400">{currentRssi} dBm</strong>
          </div>
          <input
            type="range"
            min="-95"
            max="-45"
            value={currentRssi}
            onChange={(e) => {
              setIsPacketLossActive(false);
              setCurrentRssi(parseInt(e.target.value));
            }}
            className="w-full mt-2 accent-cyan-400 h-1.5 bg-slate-800 rounded-lg cursor-pointer"
          />
        </div>
      </div>

      {/* State Machine Transition Timeline Log */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="text-xs font-bold text-slate-300 uppercase tracking-wider font-mono">
            State Machine Transition & Candidate Timeline
          </h3>
          <span className="text-xs text-slate-500 font-mono">{events.length} records</span>
        </div>

        <div className="space-y-1.5 max-h-64 overflow-y-auto pr-1">
          {events.map((ev) => (
            <div
              key={ev.id}
              className="p-2.5 bg-slate-950 rounded-lg border border-slate-800/80 text-xs font-mono flex items-start justify-between gap-2"
            >
              <div className="space-y-0.5">
                <div className="flex items-center gap-2">
                  <span className="text-slate-500">{new Date(ev.timestampMillis).toLocaleTimeString()}</span>
                  <span className={`px-1.5 py-0.2 rounded font-bold text-[10px] ${
                    ev.toState === 'INSIDE'
                      ? 'bg-emerald-500/20 text-emerald-400'
                      : ev.toState === 'OUTSIDE'
                      ? 'bg-rose-500/20 text-rose-400'
                      : 'bg-slate-800 text-slate-400'
                  }`}>
                    {ev.fromState} → {ev.toState}
                  </span>
                  {ev.candidateStatus !== 'NONE' && (
                    <span className="text-amber-400 text-[10px]">[{ev.candidateStatus}]</span>
                  )}
                </div>
                <div className="text-slate-300 text-[11px] mt-0.5">{ev.reason}</div>
              </div>

              <div className="text-right text-slate-500 text-[10px] shrink-0">
                {ev.filteredRssi !== null && <div>filt: {ev.filteredRssi} dBm</div>}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
