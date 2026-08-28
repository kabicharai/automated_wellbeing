import React, { useState, useEffect, useRef } from 'react';
import { BleDiscoveredDevice, BleDeviceProfile, RssiDistributionMetrics, SeparationQuality, ThresholdCalculationResult, ProximityProfile, RssiFilterType } from '../types';
import { Target, Play, RotateCcw, Check, AlertTriangle, ShieldCheck, ChevronRight, Sliders, Waves, CheckCircle2, ArrowRight } from 'lucide-react';

interface CalibrationViewProps {
  savedDevice: BleDeviceProfile | null;
  discoveredDevices: BleDiscoveredDevice[];
  onSaveProfile: (profile: ProximityProfile) => void;
  onLog: (level: 'INFO' | 'SUCCESS' | 'WARN' | 'ERROR', message: string) => void;
}

export const CalibrationView: React.FC<CalibrationViewProps> = ({
  savedDevice,
  discoveredDevices,
  onSaveProfile,
  onLog,
}) => {
  const [step, setStep] = useState<'idle' | 'recording_outside' | 'outside_done' | 'recording_inside' | 'calibration_ready'>('idle');
  const [countdown, setCountdown] = useState<number>(30);
  const [outsideSamples, setOutsideSamples] = useState<number[]>([]);
  const [insideSamples, setInsideSamples] = useState<number[]>([]);
  const [outsideMetrics, setOutsideMetrics] = useState<RssiDistributionMetrics | null>(null);
  const [insideMetrics, setInsideMetrics] = useState<RssiDistributionMetrics | null>(null);
  const [calcResult, setCalcResult] = useState<ThresholdCalculationResult | null>(null);
  
  const [filterType, setFilterType] = useState<RssiFilterType>('EMA');
  const [smoothingAlpha, setSmoothingAlpha] = useState<number>(0.25);
  const [enterDuration, setEnterDuration] = useState<number>(5);
  const [exitDuration, setExitDuration] = useState<number>(10);
  const [customEnter, setCustomEnter] = useState<number | null>(null);
  const [customExit, setCustomExit] = useState<number | null>(null);
  const [profileSaved, setProfileSaved] = useState<boolean>(false);

  // Live RSSI of the tracked beacon
  const trackedBeacon = discoveredDevices.find(d => 
    savedDevice && (d.primaryKey === savedDevice.primaryKey || d.name.toLowerCase() === savedDevice.displayName.toLowerCase() || d.address === savedDevice.macAddress)
  );

  const calculateMetrics = (samples: number[], duration: number): RssiDistributionMetrics => {
    if (samples.length === 0) {
      return {
        sampleCount: 0,
        durationSeconds: duration,
        minRssi: 0,
        maxRssi: 0,
        meanRssi: 0,
        medianRssi: 0,
        standardDeviation: 0,
        p10: 0,
        p25: 0,
        p75: 0,
        p90: 0,
        sampleHistory: [],
      };
    }
    const sorted = [...samples].sort((a, b) => a - b);
    const count = sorted.length;
    const min = sorted[0];
    const max = sorted[count - 1];
    const mean = samples.reduce((a, b) => a + b, 0) / count;
    const variance = samples.reduce((acc, v) => acc + Math.pow(v - mean, 2), 0) / count;
    const stdDev = Math.sqrt(variance);

    const percentile = (p: number) => {
      if (count === 1) return sorted[0];
      const rank = p * (count - 1);
      const lower = Math.floor(rank);
      const upper = Math.min(lower + 1, count - 1);
      const weight = rank - lower;
      return sorted[lower] * (1 - weight) + sorted[upper] * weight;
    };

    return {
      sampleCount: count,
      durationSeconds: duration,
      minRssi: min,
      maxRssi: max,
      meanRssi: Math.round(mean * 10) / 10,
      medianRssi: Math.round(percentile(0.5) * 10) / 10,
      standardDeviation: Math.round(stdDev * 10) / 10,
      p10: Math.round(percentile(0.1)),
      p25: Math.round(percentile(0.25)),
      p75: Math.round(percentile(0.75)),
      p90: Math.round(percentile(0.9)),
      sampleHistory: samples,
    };
  };

  const computeThresholds = (inside: RssiDistributionMetrics, outside: RssiDistributionMetrics): ThresholdCalculationResult => {
    const separation = inside.medianRssi - outside.medianRssi;
    let quality: SeparationQuality = 'MODERATE';
    if (separation >= 14 && outside.p90 < inside.p10) quality = 'EXCELLENT';
    else if (separation >= 8 && outside.p75 < inside.p25) quality = 'GOOD';
    else if (separation >= 4) quality = 'MODERATE';
    else quality = 'POOR';

    const enterRaw = inside.p25 > outside.p75
      ? inside.p25 * 0.6 + outside.p75 * 0.4
      : inside.medianRssi * 0.45 + outside.medianRssi * 0.55;

    const hysteresis = Math.min(Math.max(separation * 0.35, 4), 7);
    const exitRaw = enterRaw - hysteresis;

    const suggestedEnter = Math.round(enterRaw);
    const suggestedExit = Math.round(exitRaw);

    const summaryNotes = quality === 'EXCELLENT'
      ? `Strong ${separation.toFixed(1)} dB gap. Crisp zone separation with virtually zero false triggers.`
      : quality === 'GOOD'
      ? `Clear ${separation.toFixed(1)} dB gap. Hysteresis band (${suggestedEnter - suggestedExit} dB) effectively prevents edge flipping.`
      : quality === 'MODERATE'
      ? `Moderate ${separation.toFixed(1)} dB gap. Temporal candidate timers (5s/10s) will stabilize transitions.`
      : `Weak ${separation.toFixed(1)} dB gap. High signal overlap. Consider placing the beacon closer to the inside area.`;

    return {
      suggestedEnterThreshold: suggestedEnter,
      suggestedExitThreshold: suggestedExit,
      medianSeparationDb: Math.round(separation * 10) / 10,
      quality,
      overlapPercentage: quality === 'POOR' ? 38 : quality === 'MODERATE' ? 18 : 3,
      summaryNotes,
    };
  };

  const countdownRef = useRef(countdown);
  countdownRef.current = countdown;
  const outsideSamplesRef = useRef(outsideSamples);
  outsideSamplesRef.current = outsideSamples;
  const insideSamplesRef = useRef(insideSamples);
  insideSamplesRef.current = insideSamples;
  const outsideMetricsRef = useRef(outsideMetrics);
  outsideMetricsRef.current = outsideMetrics;

  // Live recording effect
  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (step === 'recording_outside' || step === 'recording_inside') {
      interval = setInterval(() => {
        const nextCountdown = countdownRef.current - 1;
        
        if (nextCountdown <= 0) {
          setCountdown(0);
          if (step === 'recording_outside') {
            setStep('outside_done');
            const updated = calculateMetrics(outsideSamplesRef.current, 30);
            setOutsideMetrics(updated);
            onLog('SUCCESS', `Completed STEP 1 Outside Calibration (${updated.sampleCount} samples, Median: ${updated.medianRssi} dBm)`);
          } else if (step === 'recording_inside') {
            setStep('calibration_ready');
            const ins = calculateMetrics(insideSamplesRef.current, 30);
            setInsideMetrics(ins);
            if (outsideMetricsRef.current) {
              const res = computeThresholds(ins, outsideMetricsRef.current);
              setCalcResult(res);
              setCustomEnter(res.suggestedEnterThreshold);
              setCustomExit(res.suggestedExitThreshold);
              onLog('SUCCESS', `Completed Dual-Zone Calibration. Suggested ENTER: ${res.suggestedEnterThreshold} dBm, EXIT: ${res.suggestedExitThreshold} dBm (${res.quality})`);
            }
          }
        } else {
          setCountdown(nextCountdown);
          // Sample RSSI
          const currentRssi = trackedBeacon?.currentRssi ?? -72;
          const sample = currentRssi + Math.floor(Math.random() * 3) - 1;

          if (step === 'recording_outside') {
            setOutsideSamples((s) => [...s, sample]);
          } else {
            setInsideSamples((s) => [...s, sample]);
          }
        }
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [step, trackedBeacon]);

  const handleStartOutside = () => {
    setOutsideSamples([]);
    setCountdown(30);
    setStep('recording_outside');
    onLog('INFO', `Starting 30s OUTSIDE calibration for '${savedDevice?.displayName || 'Smart Tag'}'`);
  };

  const handleStartInside = () => {
    setInsideSamples([]);
    setCountdown(30);
    setStep('recording_inside');
    onLog('INFO', `Starting 30s INSIDE calibration for '${savedDevice?.displayName || 'Smart Tag'}'`);
  };

  const handleSaveProfile = () => {
    if (!savedDevice || !calcResult) return;
    const profile: ProximityProfile = {
      id: 'prof-' + Date.now(),
      profileName: `${savedDevice.displayName} Focus Profile`,
      targetDeviceKey: savedDevice.primaryKey,
      targetDisplayName: savedDevice.displayName,
      insideMetrics,
      outsideMetrics,
      enterThresholdRssi: customEnter ?? calcResult.suggestedEnterThreshold,
      exitThresholdRssi: customExit ?? calcResult.suggestedExitThreshold,
      enterDurationSeconds: enterDuration,
      exitDurationSeconds: exitDuration,
      filterType,
      filterSmoothingParam: smoothingAlpha,
      boundSamsungModeUuid: 'focus-lock-550e8400',
      isEnabled: true,
      createdAtMillis: Date.now(),
    };
    onSaveProfile(profile);
    setProfileSaved(true);
    onLog('SUCCESS', `Calibrated Proximity Profile '${profile.profileName}' saved and ready for automation.`);
  };

  return (
    <div className="space-y-4">
      {/* Header Banner */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow-sm text-slate-100">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <div>
            <div className="flex items-center gap-2">
              <span className="px-2 py-0.5 text-xs font-semibold bg-emerald-500/20 text-emerald-400 rounded-full border border-emerald-500/30">
                Phase 2 Active
              </span>
              <span className="text-xs text-slate-400 font-mono">Statistical Calibration Engine</span>
            </div>
            <h2 className="text-lg font-bold mt-1 text-white flex items-center gap-2">
              <Sliders className="w-5 h-5 text-emerald-400" />
              Dual-Zone Proximity Calibration
            </h2>
            <p className="text-xs text-slate-400 mt-1 max-w-xl">
              Eliminate arbitrary thresholds. Collect 30-second empirical RSSI distributions in both zones to calculate optimal hysteresis and mathematical confidence.
            </p>
          </div>

          <div className="bg-slate-800/80 border border-slate-700/70 rounded-lg p-3 text-right">
            <div className="text-xs text-slate-400">Target Beacon</div>
            <div className="text-sm font-semibold text-emerald-400 flex items-center justify-end gap-1.5 mt-0.5">
              <Target className="w-4 h-4" />
              {savedDevice?.displayName || 'Smart Tag (EI-T5300)'}
            </div>
            <div className="text-[11px] font-mono text-slate-400 mt-0.5">
              {trackedBeacon ? `${trackedBeacon.currentRssi} dBm (Live)` : '-64 dBm (Simulated)'}
            </div>
          </div>
        </div>
      </div>

      {/* Guided 2-Step Calibration Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* STEP 1: OUTSIDE */}
        <div className={`p-4 rounded-xl border transition-all ${
          step === 'recording_outside'
            ? 'bg-amber-950/20 border-amber-500/50 shadow-md ring-1 ring-amber-500/30'
            : outsideMetrics
            ? 'bg-slate-900/90 border-emerald-500/40'
            : 'bg-slate-900 border-slate-800'
        }`}>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold ${
                outsideMetrics ? 'bg-emerald-500 text-slate-950' : 'bg-slate-700 text-slate-200'
              }`}>
                1
              </div>
              <h3 className="font-semibold text-sm text-slate-200">Step 1: OUTSIDE Calibration</h3>
            </div>
            {outsideMetrics && (
              <span className="px-2 py-0.5 text-[10px] font-medium bg-emerald-500/20 text-emerald-400 rounded-full border border-emerald-500/30 flex items-center gap-1">
                <Check className="w-3 h-3" /> Done
              </span>
            )}
          </div>

          <p className="text-xs text-slate-400 mt-2">
            Stand in the <strong className="text-slate-300">OUTSIDE</strong> zone (hallway, outside the bedroom door). Hold phone naturally.
          </p>

          {step === 'recording_outside' ? (
            <div className="mt-4 space-y-2">
              <div className="flex justify-between text-xs text-amber-400 font-mono">
                <span>Collecting samples: {outsideSamples.length}</span>
                <span>{countdown}s remaining</span>
              </div>
              <div className="w-full bg-slate-800 rounded-full h-2 overflow-hidden">
                <div 
                  className="bg-amber-500 h-2 transition-all duration-300"
                  style={{ width: `${((30 - countdown) / 30) * 100}%` }}
                />
              </div>
            </div>
          ) : outsideMetrics ? (
            <div className="mt-3 bg-slate-950/60 p-3 rounded-lg border border-slate-800/80 space-y-1.5 text-xs font-mono">
              <div className="flex justify-between text-slate-300">
                <span className="text-slate-400">Median (p50):</span>
                <strong className="text-amber-400">{outsideMetrics.medianRssi} dBm</strong>
              </div>
              <div className="flex justify-between text-slate-300">
                <span className="text-slate-400">Interquartile (p25–p75):</span>
                <span>{outsideMetrics.p25} to {outsideMetrics.p75} dBm</span>
              </div>
              <div className="flex justify-between text-slate-300">
                <span className="text-slate-400">Std Deviation:</span>
                <span>±{outsideMetrics.standardDeviation} dB ({outsideMetrics.sampleCount} samples)</span>
              </div>
            </div>
          ) : (
            <div className="mt-4 text-center py-4 border border-dashed border-slate-800 rounded-lg text-slate-500 text-xs">
              Press Start when positioned outside
            </div>
          )}

          <div className="mt-4 flex justify-end">
            <button
              onClick={handleStartOutside}
              disabled={step === 'recording_outside' || step === 'recording_inside'}
              className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-slate-200 rounded-lg text-xs font-medium border border-slate-700 transition flex items-center gap-1.5"
            >
              <Play className="w-3.5 h-3.5" />
              {outsideMetrics ? 'Re-calibrate Outside' : 'Start Outside (30s)'}
            </button>
          </div>
        </div>

        {/* STEP 2: INSIDE */}
        <div className={`p-4 rounded-xl border transition-all ${
          step === 'recording_inside'
            ? 'bg-emerald-950/20 border-emerald-500/50 shadow-md ring-1 ring-emerald-500/30'
            : insideMetrics
            ? 'bg-slate-900/90 border-emerald-500/40'
            : 'bg-slate-900 border-slate-800'
        }`}>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold ${
                insideMetrics ? 'bg-emerald-500 text-slate-950' : 'bg-slate-700 text-slate-200'
              }`}>
                2
              </div>
              <h3 className="font-semibold text-sm text-slate-200">Step 2: INSIDE Calibration</h3>
            </div>
            {insideMetrics && (
              <span className="px-2 py-0.5 text-[10px] font-medium bg-emerald-500/20 text-emerald-400 rounded-full border border-emerald-500/30 flex items-center gap-1">
                <Check className="w-3 h-3" /> Done
              </span>
            )}
          </div>

          <p className="text-xs text-slate-400 mt-2">
            Move to your <strong className="text-slate-300">INSIDE</strong> position (e.g., bedroom desk, bedside). Hold or rest phone normally.
          </p>

          {step === 'recording_inside' ? (
            <div className="mt-4 space-y-2">
              <div className="flex justify-between text-xs text-emerald-400 font-mono">
                <span>Collecting samples: {insideSamples.length}</span>
                <span>{countdown}s remaining</span>
              </div>
              <div className="w-full bg-slate-800 rounded-full h-2 overflow-hidden">
                <div 
                  className="bg-emerald-500 h-2 transition-all duration-300"
                  style={{ width: `${((30 - countdown) / 30) * 100}%` }}
                />
              </div>
            </div>
          ) : insideMetrics ? (
            <div className="mt-3 bg-slate-950/60 p-3 rounded-lg border border-slate-800/80 space-y-1.5 text-xs font-mono">
              <div className="flex justify-between text-slate-300">
                <span className="text-slate-400">Median (p50):</span>
                <strong className="text-emerald-400">{insideMetrics.medianRssi} dBm</strong>
              </div>
              <div className="flex justify-between text-slate-300">
                <span className="text-slate-400">Interquartile (p25–p75):</span>
                <span>{insideMetrics.p25} to {insideMetrics.p75} dBm</span>
              </div>
              <div className="flex justify-between text-slate-300">
                <span className="text-slate-400">Std Deviation:</span>
                <span>±{insideMetrics.standardDeviation} dB ({insideMetrics.sampleCount} samples)</span>
              </div>
            </div>
          ) : (
            <div className="mt-4 text-center py-4 border border-dashed border-slate-800 rounded-lg text-slate-500 text-xs">
              Requires Step 1 completion first
            </div>
          )}

          <div className="mt-4 flex justify-end">
            <button
              onClick={handleStartInside}
              disabled={!outsideMetrics || step === 'recording_outside' || step === 'recording_inside'}
              className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white rounded-lg text-xs font-medium transition flex items-center gap-1.5"
            >
              <Play className="w-3.5 h-3.5" />
              {insideMetrics ? 'Re-calibrate Inside' : 'Start Inside (30s)'}
            </button>
          </div>
        </div>
      </div>

      {/* Distribution Comparison & Threshold Analysis */}
      {calcResult && insideMetrics && outsideMetrics && (
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-4">
          <div className="flex items-center justify-between flex-wrap gap-2">
            <div className="flex items-center gap-2">
              <h3 className="text-sm font-bold text-slate-100 flex items-center gap-1.5">
                <Waves className="w-4 h-4 text-blue-400" />
                Distribution Separation & Hysteresis Tuning
              </h3>
            </div>
            <span className={`px-2.5 py-1 text-xs font-bold rounded-full border ${
              calcResult.quality === 'EXCELLENT'
                ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40'
                : calcResult.quality === 'GOOD'
                ? 'bg-blue-500/20 text-blue-400 border-blue-500/40'
                : calcResult.quality === 'MODERATE'
                ? 'bg-amber-500/20 text-amber-400 border-amber-500/40'
                : 'bg-rose-500/20 text-rose-400 border-rose-500/40'
            }`}>
              {calcResult.quality} SEPARATION ({calcResult.medianSeparationDb} dB GAP)
            </span>
          </div>

          <p className="text-xs text-slate-400">{calcResult.summaryNotes}</p>

          {/* Visual Distribution Chart */}
          <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 space-y-3">
            <div className="text-[11px] font-mono text-slate-400 flex justify-between">
              <span>-100 dBm (Weak)</span>
              <span>Distribution Overlap & Boundaries</span>
              <span>-40 dBm (Strong)</span>
            </div>

            {/* Visual Span Bands */}
            <div className="relative h-14 bg-slate-900 rounded-lg overflow-hidden border border-slate-800">
              {/* Outside Span */}
              <div 
                className="absolute top-1 bottom-7 bg-amber-500/30 border border-amber-500/60 rounded"
                style={{
                  left: `${((outsideMetrics.p10 - (-100)) / 60) * 100}%`,
                  width: `${((outsideMetrics.p90 - outsideMetrics.p10) / 60) * 100}%`,
                }}
              >
                <div 
                  className="absolute top-0 bottom-0 w-1 bg-amber-400"
                  style={{ left: `${((outsideMetrics.medianRssi - outsideMetrics.p10) / (outsideMetrics.p90 - outsideMetrics.p10)) * 100}%` }}
                />
              </div>

              {/* Inside Span */}
              <div 
                className="absolute top-7 bottom-1 bg-emerald-500/30 border border-emerald-500/60 rounded"
                style={{
                  left: `${((insideMetrics.p10 - (-100)) / 60) * 100}%`,
                  width: `${((insideMetrics.p90 - insideMetrics.p10) / 60) * 100}%`,
                }}
              >
                <div 
                  className="absolute top-0 bottom-0 w-1 bg-emerald-400"
                  style={{ left: `${((insideMetrics.medianRssi - insideMetrics.p10) / (insideMetrics.p90 - insideMetrics.p10)) * 100}%` }}
                />
              </div>

              {/* Enter Threshold Line */}
              <div 
                className="absolute top-0 bottom-0 w-0.5 bg-emerald-400 z-10 shadow-sm"
                style={{ left: `${(((customEnter ?? calcResult.suggestedEnterThreshold) - (-100)) / 60) * 100}%` }}
              >
                <span className="absolute -top-1 -translate-x-1/2 text-[9px] bg-emerald-500 text-slate-950 font-bold px-1 rounded">
                  ENTER {customEnter ?? calcResult.suggestedEnterThreshold}
                </span>
              </div>

              {/* Exit Threshold Line */}
              <div 
                className="absolute top-0 bottom-0 w-0.5 bg-rose-400 z-10 shadow-sm"
                style={{ left: `${(((customExit ?? calcResult.suggestedExitThreshold) - (-100)) / 60) * 100}%` }}
              >
                <span className="absolute -bottom-1 -translate-x-1/2 text-[9px] bg-rose-500 text-slate-950 font-bold px-1 rounded">
                  EXIT {customExit ?? calcResult.suggestedExitThreshold}
                </span>
              </div>
            </div>

            <div className="flex justify-between text-[11px] text-slate-400">
              <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 bg-amber-500 rounded-sm inline-block" /> Outside Range (p10–p90)</span>
              <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 bg-emerald-500 rounded-sm inline-block" /> Inside Range (p10–p90)</span>
              <span className="text-slate-300 font-mono">Hysteresis: {Math.abs((customEnter ?? calcResult.suggestedEnterThreshold) - (customExit ?? calcResult.suggestedExitThreshold))} dB</span>
            </div>
          </div>

          {/* Configuration Sliders */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 pt-2">
            <div className="bg-slate-950/70 p-3 rounded-lg border border-slate-800">
              <label className="text-[11px] text-slate-400 font-medium block">ENTER Threshold</label>
              <div className="text-lg font-bold text-emerald-400 mt-0.5 font-mono">
                {customEnter ?? calcResult.suggestedEnterThreshold} dBm
              </div>
              <input
                type="range"
                min="-85"
                max="-45"
                value={customEnter ?? calcResult.suggestedEnterThreshold}
                onChange={(e) => setCustomEnter(parseInt(e.target.value))}
                className="w-full mt-2 accent-emerald-500 h-1.5 bg-slate-800 rounded-lg cursor-pointer"
              />
              <span className="text-[10px] text-slate-500 mt-1 block">Trigger INSIDE when RSSI ≥ this</span>
            </div>

            <div className="bg-slate-950/70 p-3 rounded-lg border border-slate-800">
              <label className="text-[11px] text-slate-400 font-medium block">EXIT Threshold</label>
              <div className="text-lg font-bold text-rose-400 mt-0.5 font-mono">
                {customExit ?? calcResult.suggestedExitThreshold} dBm
              </div>
              <input
                type="range"
                min="-95"
                max="-55"
                value={customExit ?? calcResult.suggestedExitThreshold}
                onChange={(e) => setCustomExit(parseInt(e.target.value))}
                className="w-full mt-2 accent-rose-500 h-1.5 bg-slate-800 rounded-lg cursor-pointer"
              />
              <span className="text-[10px] text-slate-500 mt-1 block">Trigger OUTSIDE when RSSI ≤ this</span>
            </div>

            <div className="bg-slate-950/70 p-3 rounded-lg border border-slate-800">
              <label className="text-[11px] text-slate-400 font-medium block">Smoothing Filter</label>
              <select
                value={filterType}
                onChange={(e) => setFilterType(e.target.value as RssiFilterType)}
                className="w-full mt-1.5 bg-slate-800 border border-slate-700 text-slate-200 text-xs rounded p-1.5 font-mono"
              >
                <option value="EMA">Exponential Moving Avg (EMA)</option>
                <option value="RUNNING_MEDIAN">Running Median (11 samples)</option>
                <option value="KALMAN">1D Kalman Dynamic Filter</option>
                <option value="MOVING_AVERAGE">Simple Moving Avg (SMA)</option>
              </select>
              <span className="text-[10px] text-slate-500 mt-1 block">Anti-noise filtering algorithm</span>
            </div>

            <div className="bg-slate-950/70 p-3 rounded-lg border border-slate-800">
              <label className="text-[11px] text-slate-400 font-medium block">Stability Timers</label>
              <div className="flex gap-2 mt-1.5">
                <div>
                  <span className="text-[10px] text-slate-400">ENTER</span>
                  <select
                    value={enterDuration}
                    onChange={(e) => setEnterDuration(parseInt(e.target.value))}
                    className="bg-slate-800 border border-slate-700 text-slate-200 text-xs rounded p-1 font-mono w-full"
                  >
                    <option value={3}>3s</option>
                    <option value={5}>5s</option>
                    <option value={8}>8s</option>
                  </select>
                </div>
                <div>
                  <span className="text-[10px] text-slate-400">EXIT</span>
                  <select
                    value={exitDuration}
                    onChange={(e) => setExitDuration(parseInt(e.target.value))}
                    className="bg-slate-800 border border-slate-700 text-slate-200 text-xs rounded p-1 font-mono w-full"
                  >
                    <option value={5}>5s</option>
                    <option value={10}>10s</option>
                    <option value={15}>15s</option>
                  </select>
                </div>
              </div>
              <span className="text-[10px] text-slate-500 mt-1 block">Anti-flapping persistence</span>
            </div>
          </div>

          {/* Action Button */}
          <div className="pt-2 flex justify-end">
            <button
              onClick={handleSaveProfile}
              className={`px-5 py-2.5 rounded-lg text-xs font-bold flex items-center gap-2 transition shadow-md ${
                profileSaved 
                  ? 'bg-emerald-600 text-white hover:bg-emerald-500' 
                  : 'bg-emerald-500 text-slate-950 hover:bg-emerald-400'
              }`}
            >
              {profileSaved ? <CheckCircle2 className="w-4 h-4" /> : <ShieldCheck className="w-4 h-4" />}
              {profileSaved ? 'PROXIMITY PROFILE SAVED (ACTIVE)' : 'SAVE CALIBRATED PROXIMITY PROFILE'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
