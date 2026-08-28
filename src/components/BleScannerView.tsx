import React, { useState, useEffect } from 'react';
import { BleDiscoveredDevice, BleScanMode, BleDeviceProfile, LogEntry } from '../types';
import { Bluetooth, Radio, Search, Play, Square, Shield, Sparkles, CheckCircle2, ChevronRight, Info, RefreshCw, Cpu, Layers } from 'lucide-react';

interface BleScannerViewProps {
  discoveredDevices: BleDiscoveredDevice[];
  isScanning: boolean;
  scanMode: BleScanMode;
  savedProximityDevice: BleDeviceProfile | null;
  inspectedDevice: BleDiscoveredDevice | null;
  onStartScan: () => void;
  onStopScan: () => void;
  onSetScanMode: (mode: BleScanMode) => void;
  onInspectDevice: (device: BleDiscoveredDevice) => void;
  onSaveProximityDevice: (device: BleDiscoveredDevice) => void;
  onAddLog: (level: LogEntry['level'], msg: string) => void;
}

export function BleScannerView({
  discoveredDevices,
  isScanning,
  scanMode,
  savedProximityDevice,
  inspectedDevice,
  onStartScan,
  onStopScan,
  onSetScanMode,
  onInspectDevice,
  onSaveProximityDevice,
  onAddLog,
}: BleScannerViewProps) {
  const [filterText, setFilterText] = useState('');
  const [filterOnlySmartTags, setFilterOnlySmartTags] = useState(false);
  const [selectedDeviceForModal, setSelectedDeviceForModal] = useState<BleDiscoveredDevice | null>(null);

  const filteredDevices = discoveredDevices.filter((dev) => {
    const matchesSearch =
      dev.name.toLowerCase().includes(filterText.toLowerCase()) ||
      dev.address.toLowerCase().includes(filterText.toLowerCase()) ||
      dev.primaryKey.toLowerCase().includes(filterText.toLowerCase());

    if (!matchesSearch) return false;
    if (filterOnlySmartTags && !dev.isSmartTagCandidate) return false;
    return true;
  });

  const handleInspect = (dev: BleDiscoveredDevice) => {
    setSelectedDeviceForModal(dev);
    onInspectDevice(dev);
  };

  const handleSave = (dev: BleDiscoveredDevice) => {
    onSaveProximityDevice(dev);
    onAddLog('SUCCESS', `Saved target proximity device: ${dev.name} (${dev.address || 'Signature ID'})`);
  };

  return (
    <div className="space-y-6">
      {/* Top Banner / Scan Control Bar */}
      <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-4 sm:p-6 shadow-xl flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="flex items-start sm:items-center gap-4">
          <div
            className={`w-12 h-12 rounded-2xl flex items-center justify-center transition-all ${
              isScanning
                ? 'bg-blue-600/20 border border-blue-500/40 text-blue-400 animate-pulse'
                : 'bg-neutral-800 text-neutral-400'
            }`}
          >
            <Radio className="w-6 h-6" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-base font-bold text-white tracking-tight">Bluetooth Low Energy Scanner</h2>
              <span
                className={`px-2 py-0.5 rounded-full text-[10px] font-bold font-mono border ${
                  isScanning
                    ? 'bg-emerald-950/80 text-emerald-300 border-emerald-800'
                    : 'bg-neutral-800 text-neutral-400 border-neutral-700'
                }`}
              >
                {isScanning ? 'SCANNING ACTIVE' : 'SCAN IDLE'}
              </span>
            </div>
            <p className="text-xs text-neutral-400 mt-0.5">
              Android 16 BLE Discovery • Discovers Samsung SmartTag 1 & Generic Beacons
            </p>
          </div>
        </div>

        {/* Action Controls */}
        <div className="flex flex-wrap items-center gap-2.5">
          {/* Scan Mode Selector */}
          <div className="flex items-center bg-neutral-950 border border-neutral-800 rounded-xl p-1 text-xs">
            {(
              [
                { id: 'BALANCED', label: 'Balanced' },
                { id: 'LOW_LATENCY', label: 'Low Latency' },
                { id: 'LOW_POWER', label: 'Battery Saver' },
              ] as const
            ).map((mode) => (
              <button
                key={mode.id}
                onClick={() => onSetScanMode(mode.id)}
                className={`px-2.5 py-1 rounded-lg font-medium transition-all ${
                  scanMode === mode.id
                    ? 'bg-blue-600 text-white shadow-sm'
                    : 'text-neutral-400 hover:text-neutral-200'
                }`}
              >
                {mode.label}
              </button>
            ))}
          </div>

          {/* Start / Stop Button */}
          {isScanning ? (
            <button
              onClick={onStopScan}
              className="flex items-center gap-2 px-4 py-2 bg-red-950/80 hover:bg-red-900/80 text-red-300 border border-red-800 rounded-xl font-semibold text-xs transition-all shadow-sm active:scale-95"
            >
              <Square className="w-3.5 h-3.5 fill-current" />
              Stop Scanning
            </button>
          ) : (
            <button
              onClick={onStartScan}
              className="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-xl font-semibold text-xs transition-all shadow-md active:scale-95 shadow-blue-900/30"
            >
              <Play className="w-3.5 h-3.5 fill-current" />
              Start BLE Scan
            </button>
          )}
        </div>
      </div>

      {/* Active Saved Proximity Device Callout */}
      {savedProximityDevice && (
        <div className="bg-gradient-to-r from-blue-950/40 via-neutral-900 to-neutral-900 border border-blue-800/60 rounded-2xl p-4 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-blue-600/30 border border-blue-500/50 flex items-center justify-center text-blue-400">
              <Sparkles className="w-4 h-4" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-xs text-blue-400 font-semibold tracking-wider uppercase">Active Proximity Target</span>
                <span className="px-1.5 py-0.5 rounded text-[10px] font-mono bg-blue-900 text-blue-200 border border-blue-700">
                  {savedProximityDevice.deviceType}
                </span>
              </div>
              <p className="text-sm font-bold text-white mt-0.5">{savedProximityDevice.displayName}</p>
            </div>
          </div>
          <div className="text-right">
            <span className="text-[11px] text-neutral-400 font-mono block">
              Key: {savedProximityDevice.primaryKey.slice(0, 24)}...
            </span>
          </div>
        </div>
      )}

      {/* Filter and Search Bar */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 bg-neutral-900/60 border border-neutral-800 rounded-xl p-3">
        <div className="relative flex-1">
          <Search className="w-4 h-4 text-neutral-500 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={filterText}
            onChange={(e) => setFilterText(e.target.value)}
            placeholder="Search by device name, MAC, or vendor signature..."
            className="w-full bg-neutral-950 border border-neutral-800 rounded-lg pl-9 pr-3 py-1.5 text-xs text-neutral-200 placeholder-neutral-500 focus:outline-none focus:border-blue-500 font-mono"
          />
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setFilterOnlySmartTags(!filterOnlySmartTags)}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold border transition-all flex items-center gap-1.5 ${
              filterOnlySmartTags
                ? 'bg-blue-950 text-blue-300 border-blue-800'
                : 'bg-neutral-950 text-neutral-400 border-neutral-800 hover:text-neutral-200'
            }`}
          >
            <Shield className="w-3.5 h-3.5" />
            SmartTags Only
          </button>
          <span className="text-xs text-neutral-400 font-mono pl-1">
            {filteredDevices.length} discovered
          </span>
        </div>
      </div>

      {/* Discovered Devices List */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5">
        {filteredDevices.length === 0 ? (
          <div className="col-span-full bg-neutral-900/40 border border-dashed border-neutral-800 rounded-2xl p-12 text-center">
            <Radio className="w-8 h-8 text-neutral-600 mx-auto mb-2 animate-bounce" />
            <p className="text-sm font-semibold text-neutral-300">No BLE Devices Found</p>
            <p className="text-xs text-neutral-500 mt-1 max-w-sm mx-auto">
              {isScanning
                ? 'Listening for BLE advertisement packets nearby...'
                : 'Press "Start BLE Scan" to begin discovering nearby beacons and SmartTags.'}
            </p>
          </div>
        ) : (
          filteredDevices.map((device) => {
            const isSaved = savedProximityDevice?.primaryKey === device.primaryKey;
            const isSelected = inspectedDevice?.primaryKey === device.primaryKey;

            // Signal bar width percentage (-100 dBm to -30 dBm)
            const rssiPercent = Math.min(100, Math.max(10, ((device.currentRssi + 100) / 70) * 100));

            return (
              <div
                key={device.primaryKey}
                className={`bg-neutral-900 border rounded-2xl p-4 transition-all hover:border-neutral-700 flex flex-col justify-between gap-3.5 ${
                  isSelected
                    ? 'border-blue-500 bg-neutral-900/90 shadow-lg shadow-blue-950/20'
                    : isSaved
                    ? 'border-blue-800/80 bg-neutral-900/80'
                    : 'border-neutral-800'
                }`}
              >
                <div>
                  {/* Card Header */}
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex items-start gap-2.5">
                      <div
                        className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 mt-0.5 ${
                          device.isSmartTagCandidate
                            ? 'bg-blue-600/20 border border-blue-500/40 text-blue-400'
                            : 'bg-neutral-800 text-neutral-400 border border-neutral-700'
                        }`}
                      >
                        <Bluetooth className="w-4 h-4" />
                      </div>
                      <div>
                        <div className="flex items-center gap-1.5 flex-wrap">
                          <h3 className="text-sm font-bold text-white tracking-tight">{device.name}</h3>
                          {device.isSmartTagCandidate && (
                            <span className="px-1.5 py-0.2 rounded text-[9px] font-bold font-mono bg-blue-950 text-blue-300 border border-blue-800">
                              Samsung SmartTag
                            </span>
                          )}
                          {isSaved && (
                            <span className="px-1.5 py-0.2 rounded text-[9px] font-bold font-mono bg-emerald-950 text-emerald-300 border border-emerald-800 flex items-center gap-1">
                              <CheckCircle2 className="w-2.5 h-2.5" /> Target
                            </span>
                          )}
                        </div>
                        <p className="text-[11px] text-neutral-400 font-mono mt-0.5">
                          {device.address || 'Address Protected'} • {device.totalSamples} pkts
                        </p>
                      </div>
                    </div>

                    {/* RSSI Badge */}
                    <div className="text-right shrink-0">
                      <div className="text-sm font-bold font-mono text-white flex items-center justify-end gap-1">
                        <span
                          className={`w-2 h-2 rounded-full ${
                            device.currentRssi >= -65
                              ? 'bg-emerald-400 animate-ping'
                              : device.currentRssi >= -80
                              ? 'bg-amber-400'
                              : 'bg-red-400'
                          }`}
                        />
                        {device.currentRssi} dBm
                      </div>
                      <span className="text-[10px] text-neutral-400 font-medium">
                        {device.currentRssi >= -65 ? 'Strong Signal' : device.currentRssi >= -80 ? 'Moderate' : 'Weak Signal'}
                      </span>
                    </div>
                  </div>

                  {/* Signal Strength Bar */}
                  <div className="w-full bg-neutral-950 h-1.5 rounded-full mt-3 overflow-hidden border border-neutral-800">
                    <div
                      className={`h-full rounded-full transition-all duration-300 ${
                        device.currentRssi >= -65
                          ? 'bg-emerald-500'
                          : device.currentRssi >= -80
                          ? 'bg-amber-500'
                          : 'bg-red-500'
                      }`}
                      style={{ width: `${rssiPercent}%` }}
                    />
                  </div>

                  {/* Payload Summary Tags */}
                  <div className="flex flex-wrap gap-1.5 mt-2.5 text-[10px] font-mono text-neutral-400">
                    {device.advertisement.txPowerLevel !== null && (
                      <span className="px-1.5 py-0.5 bg-neutral-950 rounded border border-neutral-800">
                        TX: {device.advertisement.txPowerLevel} dBm
                      </span>
                    )}
                    {Object.keys(device.advertisement.manufacturerDataMap).map((mfgId) => (
                      <span key={mfgId} className="px-1.5 py-0.5 bg-neutral-950 rounded border border-neutral-800 text-blue-300">
                        Mfg: 0x{parseInt(mfgId).toString(16).toUpperCase()}
                      </span>
                    ))}
                    {device.advertisement.serviceUuids.length > 0 && (
                      <span className="px-1.5 py-0.5 bg-neutral-950 rounded border border-neutral-800 text-purple-300">
                        {device.advertisement.serviceUuids.length} UUID(s)
                      </span>
                    )}
                  </div>
                </div>

                {/* Card Actions */}
                <div className="flex items-center justify-between gap-2 pt-2 border-t border-neutral-800/80">
                  <button
                    onClick={() => handleInspect(device)}
                    className="px-3 py-1.5 text-xs font-semibold text-neutral-300 hover:text-white bg-neutral-800/60 hover:bg-neutral-800 rounded-lg flex items-center gap-1.5 transition-all"
                  >
                    <Info className="w-3.5 h-3.5" />
                    Inspect Payload
                  </button>

                  <button
                    onClick={() => handleSave(device)}
                    className={`px-3 py-1.5 text-xs font-semibold rounded-lg flex items-center gap-1.5 transition-all ${
                      isSaved
                        ? 'bg-emerald-950 text-emerald-300 border border-emerald-800'
                        : 'bg-blue-600 hover:bg-blue-500 text-white shadow-sm'
                    }`}
                  >
                    {isSaved ? (
                      <>
                        <CheckCircle2 className="w-3.5 h-3.5" /> Selected
                      </>
                    ) : (
                      <>
                        <Sparkles className="w-3.5 h-3.5" /> Set as Target
                      </>
                    )}
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Raw Payload Inspection Modal */}
      {selectedDeviceForModal && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-neutral-900 border border-neutral-700 rounded-2xl max-w-2xl w-full max-h-[85vh] flex flex-col overflow-hidden shadow-2xl">
            {/* Modal Header */}
            <div className="p-4 sm:p-5 border-b border-neutral-800 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl bg-blue-600/20 border border-blue-500/40 flex items-center justify-center text-blue-400">
                  <Cpu className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-white tracking-tight">
                    {selectedDeviceForModal.name}
                  </h3>
                  <p className="text-xs text-neutral-400 font-mono">
                    {selectedDeviceForModal.address || 'Address Protected'}
                  </p>
                </div>
              </div>
              <button
                onClick={() => setSelectedDeviceForModal(null)}
                className="p-1.5 text-neutral-400 hover:text-white rounded-lg hover:bg-neutral-800"
              >
                ✕
              </button>
            </div>

            {/* Modal Scrollable Body */}
            <div className="p-4 sm:p-5 space-y-4 overflow-y-auto font-mono text-xs">
              {/* Telemetry Metrics */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                <div className="bg-neutral-950 p-2.5 rounded-xl border border-neutral-800">
                  <span className="text-[10px] text-neutral-500 block">LATEST RSSI</span>
                  <span className="text-sm font-bold text-white">{selectedDeviceForModal.currentRssi} dBm</span>
                </div>
                <div className="bg-neutral-950 p-2.5 rounded-xl border border-neutral-800">
                  <span className="text-[10px] text-neutral-500 block">TOTAL PACKETS</span>
                  <span className="text-sm font-bold text-white">{selectedDeviceForModal.totalSamples}</span>
                </div>
                <div className="bg-neutral-950 p-2.5 rounded-xl border border-neutral-800">
                  <span className="text-[10px] text-neutral-500 block">TX POWER</span>
                  <span className="text-sm font-bold text-white">
                    {selectedDeviceForModal.advertisement.txPowerLevel !== null
                      ? `${selectedDeviceForModal.advertisement.txPowerLevel} dBm`
                      : 'N/A'}
                  </span>
                </div>
                <div className="bg-neutral-950 p-2.5 rounded-xl border border-neutral-800">
                  <span className="text-[10px] text-neutral-500 block">FLAGS</span>
                  <span className="text-sm font-bold text-white">
                    0x{selectedDeviceForModal.advertisement.advertiseFlags.toString(16).toUpperCase()}
                  </span>
                </div>
              </div>

              {/* Manufacturer Specific Data */}
              <div className="bg-neutral-950 p-3.5 rounded-xl border border-neutral-800">
                <h4 className="text-neutral-400 font-sans font-semibold mb-2 flex items-center gap-1.5">
                  <Layers className="w-3.5 h-3.5 text-blue-400" /> Manufacturer Data Payloads
                </h4>
                {Object.entries(selectedDeviceForModal.advertisement.manufacturerDataMap).length === 0 ? (
                  <p className="text-neutral-500">None advertised</p>
                ) : (
                  <div className="space-y-2">
                    {Object.entries(selectedDeviceForModal.advertisement.manufacturerDataMap).map(([mfgId, hex]) => {
                      const hexStr = String(hex);
                      return (
                        <div key={mfgId} className="bg-neutral-900 p-2 rounded border border-neutral-800">
                          <div className="flex items-center justify-between text-[11px] mb-1">
                            <span className="text-blue-300 font-bold">
                              Vendor: 0x{parseInt(mfgId).toString(16).toUpperCase()} (
                              {parseInt(mfgId) === 0x0075 ? 'Samsung Electronics' : 'Other'})
                            </span>
                            <span className="text-neutral-500">{hexStr.split(' ').length} bytes</span>
                          </div>
                          <div className="text-emerald-400 break-all select-all font-mono">{hexStr}</div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Service UUIDs */}
              <div className="bg-neutral-950 p-3.5 rounded-xl border border-neutral-800">
                <h4 className="text-neutral-400 font-sans font-semibold mb-2">Advertised Service UUIDs</h4>
                {selectedDeviceForModal.advertisement.serviceUuids.length === 0 ? (
                  <p className="text-neutral-500">None advertised</p>
                ) : (
                  <ul className="space-y-1">
                    {selectedDeviceForModal.advertisement.serviceUuids.map((uuid, i) => (
                      <li key={i} className="text-purple-300 bg-neutral-900 p-1.5 rounded border border-neutral-800">
                        {uuid}
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              {/* Raw Hex Byte Stream */}
              <div className="bg-neutral-950 p-3.5 rounded-xl border border-neutral-800">
                <h4 className="text-neutral-400 font-sans font-semibold mb-1">Raw Packet Byte Stream</h4>
                <div className="text-neutral-300 break-all select-all bg-neutral-900 p-2.5 rounded border border-neutral-800 text-[11px]">
                  {selectedDeviceForModal.advertisement.rawBytesHex || '0201061AFF750001000200030004000500...'}
                </div>
              </div>
            </div>

            {/* Modal Footer */}
            <div className="p-4 border-t border-neutral-800 bg-neutral-950 flex items-center justify-between">
              <button
                onClick={() => setSelectedDeviceForModal(null)}
                className="px-4 py-2 bg-neutral-800 hover:bg-neutral-700 text-neutral-300 rounded-xl text-xs font-semibold"
              >
                Close
              </button>
              <button
                onClick={() => {
                  handleSave(selectedDeviceForModal);
                  setSelectedDeviceForModal(null);
                }}
                className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-xl text-xs font-semibold shadow-md"
              >
                Save as Proximity Device
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
