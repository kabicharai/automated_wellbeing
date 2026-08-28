import React, { useState, useEffect, useRef } from 'react';
import { BleDiscoveredDevice, BleDeviceProfile, RssiHistoryWindow, RssiSnapshot, RssiSample } from '../types';
import { Activity, Radio, BarChart3, Clock, Zap, ArrowUpRight, ArrowDownRight, Compass, Sliders, Shield, Sparkles } from 'lucide-react';

interface RssiMonitorViewProps {
  activeDevice: BleDiscoveredDevice | null;
  savedProfile: BleDeviceProfile | null;
  allDevices: BleDiscoveredDevice[];
  onSelectDevice: (device: BleDiscoveredDevice) => void;
}

export function RssiMonitorView({
  activeDevice,
  savedProfile,
  allDevices,
  onSelectDevice,
}: RssiMonitorViewProps) {
  const [selectedWindow, setSelectedWindow] = useState<RssiHistoryWindow>('30s');
  const [samples, setSamples] = useState<RssiSample[]>([]);
  const [currentRssi, setCurrentRssi] = useState<number>(activeDevice?.currentRssi ?? -64);
  const [simulationMode, setSimulationMode] = useState<boolean>(true);
  const [distanceSim, setDistanceSim] = useState<number>(2.5); // meters

  // Simulation timer for live continuous RSSI updating
  useEffect(() => {
    if (!simulationMode) return;

    const interval = setInterval(() => {
      // Calculate realistic base RSSI from distance: RSSI = -58 - 20 * log10(distance) + noise
      const base = -58 - 22 * Math.log10(Math.max(0.5, distanceSim));
      const noise = (Math.random() - 0.5) * 5; // ±2.5 dBm jitter
      const nextRssi = Math.round(base + noise);

      setCurrentRssi(nextRssi);

      setSamples((prev) => {
        const now = Date.now();
        const updated = [...prev, { timestampMillis: now, rssi: nextRssi }];
        // Keep last 300 seconds (5 min) max
        return updated.filter((s) => s.timestampMillis >= now - 300000);
      });
    }, 400); // 2.5 Hz BLE beacon rate

    return () => clearInterval(interval);
  }, [simulationMode, distanceSim]);

  // Window duration in seconds
  const windowSeconds = {
    '5s': 5,
    '15s': 15,
    '30s': 30,
    '60s': 60,
    '5m': 300,
  }[selectedWindow];

  const now = Date.now();
  const visibleSamples = samples.filter((s) => s.timestampMillis >= now - windowSeconds * 1000);

  // Statistics calculation
  const count = visibleSamples.length;
  const rssiValues = visibleSamples.map((s) => s.rssi);
  const avg = count > 0 ? (rssiValues.reduce((a, b) => a + b, 0) / count).toFixed(1) : null;
  const min = count > 0 ? Math.min(...rssiValues) : null;
  const max = count > 0 ? Math.max(...rssiValues) : null;

  const sorted = [...rssiValues].sort((a, b) => a - b);
  const median =
    count > 0
      ? count % 2 === 1
        ? sorted[Math.floor(count / 2)]
        : ((sorted[count / 2 - 1] + sorted[count / 2]) / 2).toFixed(1)
      : null;

  const variance =
    count > 1 && avg
      ? rssiValues.reduce((acc, val) => acc + Math.pow(val - parseFloat(avg), 2), 0) / count
      : 0;
  const stdDev = count > 1 ? Math.sqrt(variance).toFixed(1) : '0.0';

  // SVG Chart Dimensions
  const chartHeight = 220;
  const chartWidth = 700;
  const minY = -95; // dBm
  const maxY = -35; // dBm

  const getYCoord = (rssi: number) => {
    const clamped = Math.max(minY, Math.min(maxY, rssi));
    const norm = (clamped - minY) / (maxY - minY);
    return chartHeight - norm * (chartHeight - 30) - 15;
  };

  const getXCoord = (timestamp: number) => {
    const startTime = now - windowSeconds * 1000;
    const progress = (timestamp - startTime) / (windowSeconds * 1000);
    return Math.max(10, Math.min(chartWidth - 10, progress * (chartWidth - 20) + 10));
  };

  const pointsString = visibleSamples
    .map((s) => `${getXCoord(s.timestampMillis)},${getYCoord(s.rssi)}`)
    .join(' ');

  return (
    <div className="space-y-6">
      {/* Top Monitor Header Card */}
      <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-4 sm:p-6 shadow-xl flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="flex items-start sm:items-center gap-4">
          <div className="w-12 h-12 rounded-2xl bg-blue-600/20 border border-blue-500/40 text-blue-400 flex items-center justify-center">
            <Activity className="w-6 h-6 animate-pulse" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-base font-bold text-white tracking-tight">Live RSSI Telemetry Monitor</h2>
              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold font-mono bg-blue-950 text-blue-300 border border-blue-800">
                Phase 1 Core
              </span>
            </div>
            <p className="text-xs text-neutral-400 mt-0.5">
              Continuous Decibel Stream • Statistical Modeling • Real-time Waveform
            </p>
          </div>
        </div>

        {/* Target Device Selector */}
        <div className="flex items-center gap-2 bg-neutral-950 p-1.5 rounded-xl border border-neutral-800">
          <span className="text-xs font-semibold text-neutral-400 pl-2 flex items-center gap-1.5">
            <Radio className="w-3.5 h-3.5 text-blue-400" /> Target:
          </span>
          <select
            value={activeDevice?.primaryKey ?? (allDevices[0]?.primaryKey || '')}
            onChange={(e) => {
              const dev = allDevices.find((d) => d.primaryKey === e.target.value);
              if (dev) onSelectDevice(dev);
            }}
            className="bg-neutral-900 border border-neutral-700 text-neutral-200 text-xs font-semibold rounded-lg px-2.5 py-1 focus:outline-none focus:border-blue-500 cursor-pointer"
          >
            {allDevices.map((d) => (
              <option key={d.primaryKey} value={d.primaryKey}>
                {d.name} ({d.address || 'SmartTag'})
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* 6 High-Contrast Stat Cards */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        {/* Current RSSI */}
        <div className="bg-neutral-900 border border-neutral-800 p-3.5 rounded-2xl">
          <span className="text-[10px] text-neutral-500 font-mono block">CURRENT RSSI</span>
          <div className="text-xl font-bold font-mono text-white mt-1 flex items-baseline gap-1">
            {currentRssi} <span className="text-xs text-neutral-400 font-normal">dBm</span>
          </div>
          <span className="text-[10px] text-emerald-400 font-medium mt-0.5 block">
            {currentRssi >= -65 ? 'Strong Signal' : currentRssi >= -80 ? 'Moderate' : 'Weak Signal'}
          </span>
        </div>

        {/* Average */}
        <div className="bg-neutral-900 border border-neutral-800 p-3.5 rounded-2xl">
          <span className="text-[10px] text-neutral-500 font-mono block">WINDOW AVERAGE</span>
          <div className="text-xl font-bold font-mono text-neutral-200 mt-1 flex items-baseline gap-1">
            {avg ?? '—'} <span className="text-xs text-neutral-400 font-normal">dBm</span>
          </div>
          <span className="text-[10px] text-neutral-400 mt-0.5 block">Moving Mean</span>
        </div>

        {/* Median */}
        <div className="bg-neutral-900 border border-neutral-800 p-3.5 rounded-2xl">
          <span className="text-[10px] text-neutral-500 font-mono block">MEDIAN (P50)</span>
          <div className="text-xl font-bold font-mono text-blue-400 mt-1 flex items-baseline gap-1">
            {median ?? '—'} <span className="text-xs text-neutral-400 font-normal">dBm</span>
          </div>
          <span className="text-[10px] text-blue-400/80 mt-0.5 block">Outlier Resistant</span>
        </div>

        {/* Std Dev */}
        <div className="bg-neutral-900 border border-neutral-800 p-3.5 rounded-2xl">
          <span className="text-[10px] text-neutral-500 font-mono block">STD DEVIATION (σ)</span>
          <div className="text-xl font-bold font-mono text-neutral-200 mt-1 flex items-baseline gap-1">
            ±{stdDev} <span className="text-xs text-neutral-400 font-normal">dB</span>
          </div>
          <span className="text-[10px] text-neutral-400 mt-0.5 block">Jitter Variance</span>
        </div>

        {/* Min / Max Range */}
        <div className="bg-neutral-900 border border-neutral-800 p-3.5 rounded-2xl">
          <span className="text-[10px] text-neutral-500 font-mono block">MIN / MAX RANGE</span>
          <div className="text-sm font-bold font-mono text-neutral-200 mt-1 flex items-center gap-1.5">
            <span className="text-red-400">{min ?? '—'}</span> / <span className="text-emerald-400">{max ?? '—'}</span>
          </div>
          <span className="text-[10px] text-neutral-400 mt-0.5 block">Spread: {max && min ? max - min : 0} dB</span>
        </div>

        {/* Samples Count */}
        <div className="bg-neutral-900 border border-neutral-800 p-3.5 rounded-2xl">
          <span className="text-[10px] text-neutral-500 font-mono block">WINDOW SAMPLES</span>
          <div className="text-xl font-bold font-mono text-purple-400 mt-1">
            {count}
          </div>
          <span className="text-[10px] text-purple-400/80 mt-0.5 block">@ ~2.5 pkts/sec</span>
        </div>
      </div>

      {/* Live RSSI Waveform Graph Container */}
      <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-4 sm:p-6 shadow-xl space-y-4">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <BarChart3 className="w-4 h-4 text-blue-400" />
            <h3 className="text-sm font-bold text-white tracking-tight">Real-Time RSSI vs. Time Waveform</h3>
          </div>

          {/* Time Window Buttons */}
          <div className="flex items-center bg-neutral-950 border border-neutral-800 rounded-xl p-1 text-xs">
            {(['5s', '15s', '30s', '60s', '5m'] as RssiHistoryWindow[]).map((w) => (
              <button
                key={w}
                onClick={() => setSelectedWindow(w)}
                className={`px-2.5 py-1 rounded-lg font-mono font-medium transition-all ${
                  selectedWindow === w
                    ? 'bg-blue-600 text-white shadow-sm'
                    : 'text-neutral-400 hover:text-neutral-200'
                }`}
              >
                {w}
              </button>
            ))}
          </div>
        </div>

        {/* SVG Waveform Canvas */}
        <div className="w-full bg-neutral-950 border border-neutral-800/80 rounded-xl p-3 overflow-hidden relative">
          <svg viewBox={`0 0 ${chartWidth} ${chartHeight}`} className="w-full h-52 overflow-visible">
            <defs>
              <linearGradient id="rssiGradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#3b82f6" stopOpacity="0.35" />
                <stop offset="100%" stopColor="#3b82f6" stopOpacity="0.0" />
              </linearGradient>
            </defs>

            {/* Horizontal Grid lines (-40, -55, -70, -85 dBm) */}
            {[-40, -55, -70, -85].map((db) => {
              const y = getYCoord(db);
              return (
                <g key={db}>
                  <line
                    x1="40"
                    y1={y}
                    x2={chartWidth - 10}
                    y2={y}
                    stroke="#262626"
                    strokeDasharray="4 4"
                  />
                  <text x="5" y={y + 4} fill="#737373" fontSize="10" fontFamily="monospace">
                    {db} dB
                  </text>
                </g>
              );
            })}

            {/* Threshold Reference Lines (Phase 2 Preview) */}
            <line
              x1="40"
              y1={getYCoord(-64)}
              x2={chartWidth - 10}
              y2={getYCoord(-64)}
              stroke="#10b981"
              strokeWidth="1.5"
              strokeDasharray="2 2"
              opacity="0.6"
            />
            <text x={chartWidth - 130} y={getYCoord(-64) - 4} fill="#10b981" fontSize="9" fontFamily="monospace">
              ENTER Target (-64 dBm)
            </text>

            <line
              x1="40"
              y1={getYCoord(-69)}
              x2={chartWidth - 10}
              y2={getYCoord(-69)}
              stroke="#ef4444"
              strokeWidth="1.5"
              strokeDasharray="2 2"
              opacity="0.6"
            />
            <text x={chartWidth - 125} y={getYCoord(-69) + 12} fill="#ef4444" fontSize="9" fontFamily="monospace">
              EXIT Target (-69 dBm)
            </text>

            {/* Area Fill */}
            {visibleSamples.length > 1 && (
              <polygon
                points={`40,${chartHeight - 15} ${pointsString} ${getXCoord(
                  visibleSamples[visibleSamples.length - 1].timestampMillis
                )},${chartHeight - 15}`}
                fill="url(#rssiGradient)"
              />
            )}

            {/* Main Waveform Polyline */}
            {visibleSamples.length > 1 && (
              <polyline
                fill="none"
                stroke="#3b82f6"
                strokeWidth="2.5"
                strokeLinecap="round"
                strokeLinejoin="round"
                points={pointsString}
              />
            )}

            {/* Latest Point Pulse */}
            {visibleSamples.length > 0 && (
              <g>
                <circle
                  cx={getXCoord(visibleSamples[visibleSamples.length - 1].timestampMillis)}
                  cy={getYCoord(visibleSamples[visibleSamples.length - 1].rssi)}
                  r="5"
                  fill="#3b82f6"
                  stroke="#ffffff"
                  strokeWidth="2"
                />
              </g>
            )}
          </svg>

          {/* Time axis label */}
          <div className="flex justify-between items-center text-[10px] font-mono text-neutral-500 px-2 mt-1">
            <span>-{windowSeconds}s ago</span>
            <span>-{(windowSeconds / 2).toFixed(0)}s</span>
            <span>Now (Live 2.5Hz)</span>
          </div>
        </div>
      </div>

      {/* Interactive Simulation / Testing Controls */}
      <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-4 sm:p-6 shadow-xl space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Sliders className="w-4 h-4 text-blue-400" />
            <h3 className="text-sm font-bold text-white tracking-tight">Phase 1 Distance & RSSI Simulator</h3>
          </div>
          <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-blue-950 text-blue-300 border border-blue-800">
            Simulated Distance: {distanceSim.toFixed(1)}m
          </span>
        </div>

        {/* Distance Slider */}
        <div className="space-y-2">
          <div className="flex justify-between text-xs font-semibold text-neutral-400">
            <span>Close Proximity (0.5m / -55 dBm)</span>
            <span>Room Boundary (3.0m / -68 dBm)</span>
            <span>Outside / Far (8.0m / -85 dBm)</span>
          </div>
          <input
            type="range"
            min="0.5"
            max="8.0"
            step="0.1"
            value={distanceSim}
            onChange={(e) => setDistanceSim(parseFloat(e.target.value))}
            className="w-full h-2 bg-neutral-950 rounded-lg appearance-none cursor-pointer accent-blue-500"
          />
        </div>

        {/* Quick Scenario Triggers */}
        <div className="flex flex-wrap gap-2 pt-2">
          <button
            onClick={() => setDistanceSim(1.0)}
            className="px-3 py-1.5 text-xs font-semibold bg-emerald-950/80 hover:bg-emerald-900/80 text-emerald-300 border border-emerald-800 rounded-xl transition-all"
          >
            Walk Inside (1.0m ~ -58 dBm)
          </button>
          <button
            onClick={() => setDistanceSim(4.5)}
            className="px-3 py-1.5 text-xs font-semibold bg-red-950/80 hover:bg-red-900/80 text-red-300 border border-red-800 rounded-xl transition-all"
          >
            Walk Outside (4.5m ~ -74 dBm)
          </button>
          <button
            onClick={() => {
              // Sudden spike (body blocking or quick turn)
              setCurrentRssi((prev) => prev - 15);
              setSamples((prev) => [...prev, { timestampMillis: Date.now(), rssi: currentRssi - 15 }]);
            }}
            className="px-3 py-1.5 text-xs font-semibold bg-neutral-800 hover:bg-neutral-700 text-neutral-200 rounded-xl transition-all"
          >
            Simulate Body Blocking (-15 dB Spike)
          </button>
        </div>
      </div>
    </div>
  );
}
