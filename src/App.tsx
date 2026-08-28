import React, { useState, useEffect } from 'react';
import { DeviceInfo, DeviceProfile, LogEntry, BleDiscoveredDevice, BleScanMode, BleDeviceProfile } from './types';
import { DeviceSimulator } from './components/DeviceSimulator';
import { DiagnosticsInspector } from './components/DiagnosticsInspector';
import { CodeExplorer } from './components/CodeExplorer';
import { ArchitectureView } from './components/ArchitectureView';
import { DocumentationView } from './components/DocumentationView';
import { BleScannerView } from './components/BleScannerView';
import { RssiMonitorView } from './components/RssiMonitorView';
import { PhaseRoadmapView } from './components/PhaseRoadmapView';
import { Smartphone, Activity, Code, GitFork, BookOpen, Radio, BarChart3, Shield, Layers } from 'lucide-react';

const DEVICE_PROFILES: Record<DeviceProfile, DeviceInfo> = {
  'galaxy-s23-oneui85': {
    id: 'galaxy-s23-oneui85',
    name: 'Samsung Galaxy S23 (One UI 8.5)',
    model: 'Samsung Galaxy S23 (SM-S911B)',
    manufacturer: 'Samsung',
    androidVersion: '16 (API 36)',
    sdkInt: 36,
    oneUiVersion: '8.5',
    routinesPackageInstalled: true,
    routinesVersionName: 'v4.3.02.14',
    routinesVersionCode: 430214001,
    shortcutActivityFound: true,
    shortcutActivityExported: true,
    legacyProviderFound: true,
    legacyProviderAccessible: false,
    selectedBackend: 'V8.5 (Shortcut Activity)',
    isSupported: true,
    systemSettingModeId: 'focus-lock-550e8400',
  },
  'galaxy-s23-oneui80': {
    id: 'galaxy-s23-oneui80',
    name: 'Samsung Galaxy S23 (One UI 8.0)',
    model: 'Samsung Galaxy S23 (SM-S911U)',
    manufacturer: 'Samsung',
    androidVersion: '16 (API 36)',
    sdkInt: 36,
    oneUiVersion: '8.0',
    routinesPackageInstalled: true,
    routinesVersionName: 'v4.1.00.82',
    routinesVersionCode: 410082000,
    shortcutActivityFound: false,
    shortcutActivityExported: false,
    legacyProviderFound: true,
    legacyProviderAccessible: true,
    selectedBackend: 'V8 (Legacy External Provider)',
    isSupported: true,
    systemSettingModeId: null,
  },
  'generic-android16': {
    id: 'generic-android16',
    name: 'Pixel 9 / Generic Android 16',
    model: 'Google Pixel 9 Pro',
    manufacturer: 'Google',
    androidVersion: '16 (API 36)',
    sdkInt: 36,
    oneUiVersion: 'Non-Samsung Device',
    routinesPackageInstalled: false,
    routinesVersionName: 'Not Installed',
    routinesVersionCode: 0,
    shortcutActivityFound: false,
    shortcutActivityExported: false,
    legacyProviderFound: false,
    legacyProviderAccessible: false,
    selectedBackend: 'Unsupported',
    isSupported: false,
    systemSettingModeId: null,
  },
};

const INITIAL_BLE_DEVICES: BleDiscoveredDevice[] = [
  {
    primaryKey: 'mfg:0x75:010042',
    name: 'Samsung Galaxy SmartTag (Bedroom)',
    address: 'E4:7B:A2:18:42:01',
    currentRssi: -58,
    firstSeenMillis: Date.now() - 45000,
    lastSeenMillis: Date.now() - 500,
    totalSamples: 112,
    isSmartTagCandidate: true,
    advertisement: {
      advertiseFlags: 0x06,
      txPowerLevel: -4,
      manufacturerDataMap: {
        0x0075: '01 00 42 18 A2 7B E4 02 FF 80 10 04',
      },
      serviceUuids: ['0000fd5a-0000-1000-8000-00805f9b34fb'],
      serviceDataMap: {
        '0000fd5a-0000-1000-8000-00805f9b34fb': 'A0 12 04',
      },
      rawBytesHex: '0201061AFF750001004218A27BE402FF80100403035AFD05165AFD00A012',
      isConnectable: true,
      primaryPhy: 'LE 1M',
      timestampNanos: Date.now() * 1000000,
    },
  },
  {
    primaryKey: 'mfg:0x75:010088',
    name: 'Galaxy SmartTag 1 (Office)',
    address: 'F0:19:AF:88:12:33',
    currentRssi: -73,
    firstSeenMillis: Date.now() - 80000,
    lastSeenMillis: Date.now() - 1200,
    totalSamples: 84,
    isSmartTagCandidate: true,
    advertisement: {
      advertiseFlags: 0x06,
      txPowerLevel: -4,
      manufacturerDataMap: {
        0x0075: '01 00 88 12 AF 19 F0 01 EE 70 08 02',
      },
      serviceUuids: ['0000fd5a-0000-1000-8000-00805f9b34fb'],
      serviceDataMap: {},
      rawBytesHex: '0201061AFF750001008812AF19F001EE70080203035AFD',
      isConnectable: true,
      primaryPhy: 'LE 1M',
      timestampNanos: Date.now() * 1000000,
    },
  },
  {
    primaryKey: 'svc:0000feaa-ibeacon',
    name: 'Generic BLE Beacon (Desk Proximity)',
    address: 'C8:2B:96:34:55:10',
    currentRssi: -64,
    firstSeenMillis: Date.now() - 120000,
    lastSeenMillis: Date.now() - 300,
    totalSamples: 198,
    isSmartTagCandidate: false,
    advertisement: {
      advertiseFlags: 0x04,
      txPowerLevel: 0,
      manufacturerDataMap: {
        0x004c: '02 15 E2 C5 6D B5 DF FB 48 D2 B0 60 D0 F5 A7 10 96 E0 00 01 00 01 C5',
      },
      serviceUuids: ['0000feaa-0000-1000-8000-00805f9b34fb'],
      serviceDataMap: {},
      rawBytesHex: '0201041AFF4C000215E2C56DB5DFFB48D2B060D0F5A71096E000010001C5',
      isConnectable: false,
      primaryPhy: 'LE 1M',
      timestampNanos: Date.now() * 1000000,
    },
  },
  {
    primaryKey: 'name:Eddystone-UID',
    name: 'Eddystone-UID Beacon (Hallway)',
    address: 'D4:8A:22:90:11:77',
    currentRssi: -82,
    firstSeenMillis: Date.now() - 60000,
    lastSeenMillis: Date.now() - 2500,
    totalSamples: 42,
    isSmartTagCandidate: false,
    advertisement: {
      advertiseFlags: 0x06,
      txPowerLevel: -10,
      manufacturerDataMap: {},
      serviceUuids: ['0000feaa-0000-1000-8000-00805f9b34fb'],
      serviceDataMap: {
        '0000feaa-0000-1000-8000-00805f9b34fb': '00 EE 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F 00 00',
      },
      rawBytesHex: '0201060303AAFE1716AAFE00EE0102030405060708090A0B0C0D0E0F0000',
      isConnectable: false,
      primaryPhy: 'LE 1M',
      timestampNanos: Date.now() * 1000000,
    },
  },
];

export default function App() {
  const [activeTab, setActiveTab] = useState<
    'ble-scanner' | 'rssi-monitor' | 'phases' | 'simulator' | 'diagnostics' | 'code' | 'architecture' | 'docs'
  >('ble-scanner');
  
  const [currentDeviceId, setCurrentDeviceId] = useState<DeviceProfile>('galaxy-s23-oneui85');
  
  // Phase 1 BLE state
  const [isScanning, setIsScanning] = useState<boolean>(true);
  const [scanMode, setScanMode] = useState<BleScanMode>('BALANCED');
  const [discoveredDevices, setDiscoveredDevices] = useState<BleDiscoveredDevice[]>(INITIAL_BLE_DEVICES);
  const [inspectedDevice, setInspectedDevice] = useState<BleDiscoveredDevice | null>(INITIAL_BLE_DEVICES[0]);
  const [savedProximityDevice, setSavedProximityDevice] = useState<BleDeviceProfile | null>({
    id: 'saved-smarttag-bedroom',
    displayName: 'Samsung Galaxy SmartTag (Bedroom)',
    deviceType: 'SAMSUNG_SMARTTAG_1',
    primaryKey: 'mfg:0x75:010042',
    macAddress: 'E4:7B:A2:18:42:01',
    targetManufacturerId: 0x0075,
    createdAtMillis: Date.now() - 3600000,
    notes: 'Primary BLE beacon located in master bedroom for focus mode automation',
  });

  const [logs, setLogs] = useState<LogEntry[]>([
    {
      id: '1',
      timestamp: '00:04:12.102',
      level: 'SYSTEM',
      message: 'Initialized Samsung Modes & BLE Proximity System (Android 16 API 36)',
    },
    {
      id: '2',
      timestamp: '00:04:12.115',
      level: 'BLE',
      message: 'BleScanner: Initialized BluetoothLeScanner with mode [Balanced]',
    },
    {
      id: '3',
      timestamp: '00:04:12.128',
      level: 'BLE',
      message: 'BleDeviceIdentifier: Identified Samsung SmartTag 1 (Vendor: 0x0075 Samsung Electronics)',
    },
  ]);

  const currentDevice = DEVICE_PROFILES[currentDeviceId];

  const handleAddLog = (level: LogEntry['level'], message: string) => {
    const newEntry: LogEntry = {
      id: Math.random().toString(36).substring(2, 9),
      timestamp: new Date().toLocaleTimeString('en-US', { hour12: false }) + '.' + Math.floor(Math.random() * 900 + 100),
      level,
      message,
    };
    setLogs((prev) => [...prev, newEntry]);
  };

  const handleClearLogs = () => {
    setLogs([]);
  };

  const handleDeviceSwitch = (devId: DeviceProfile) => {
    setCurrentDeviceId(devId);
    const dev = DEVICE_PROFILES[devId];
    handleAddLog('SYSTEM', `Switched target test device to: ${dev.name}`);
    handleAddLog('DETECT', `Selected Backend resolved to: [${dev.selectedBackend}] (isSupported=${dev.isSupported})`);
  };

  const handleStartScan = () => {
    setIsScanning(true);
    handleAddLog('BLE', `Started BLE Scanner (Mode: ${scanMode})`);
  };

  const handleStopScan = () => {
    setIsScanning(false);
    handleAddLog('BLE', 'Stopped BLE Scanner');
  };

  const handleSaveProximityDevice = (dev: BleDiscoveredDevice) => {
    const profile: BleDeviceProfile = {
      id: Math.random().toString(36).substring(2, 9),
      displayName: dev.name,
      deviceType: dev.isSmartTagCandidate ? 'SAMSUNG_SMARTTAG_1' : 'GENERIC_BEACON',
      primaryKey: dev.primaryKey,
      macAddress: dev.address,
      targetManufacturerId: dev.advertisement.manufacturerDataMap[0x0075] ? 0x0075 : null,
      createdAtMillis: Date.now(),
      notes: 'Saved from Phase 1 BLE Scanner',
    };
    setSavedProximityDevice(profile);
    setInspectedDevice(dev);
  };

  return (
    <div className="min-h-screen bg-neutral-950 text-neutral-100 flex flex-col font-sans selection:bg-blue-600 selection:text-white">
      {/* Top App Header */}
      <header className="border-b border-neutral-800 bg-neutral-900/90 backdrop-blur sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-3 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-blue-600 flex items-center justify-center shadow-md shadow-blue-900/30">
              <Shield className="w-5 h-5 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-sm font-bold text-white tracking-tight">Samsung Modes • BLE Proximity</h1>
                <span className="px-2 py-0.5 rounded-full text-[10px] font-bold font-mono bg-blue-950 text-blue-300 border border-blue-800">
                  Phase 1 Active
                </span>
              </div>
              <p className="text-xs text-neutral-400">
                Samsung SmartTag 1 & Generic Beacons • Continuous RSSI • One UI 8.0 & 8.5
              </p>
            </div>
          </div>

          {/* Test Phone Profile Selector */}
          <div className="flex items-center gap-2 bg-neutral-950 p-1.5 rounded-xl border border-neutral-800">
            <span className="text-xs font-semibold text-neutral-400 pl-2 pr-1 flex items-center gap-1.5">
              <Smartphone className="w-3.5 h-3.5 text-blue-400" /> Phone:
            </span>
            <select
              value={currentDeviceId}
              onChange={(e) => handleDeviceSwitch(e.target.value as DeviceProfile)}
              className="bg-neutral-900 border border-neutral-700 text-neutral-200 text-xs font-semibold rounded-lg px-2.5 py-1 focus:outline-none focus:border-blue-500 cursor-pointer"
            >
              <option value="galaxy-s23-oneui85">Galaxy S23 (One UI 8.5 / V85 Backend)</option>
              <option value="galaxy-s23-oneui80">Galaxy S23 (One UI 8.0 / V8 Backend)</option>
              <option value="generic-android16">Generic Phone (Unsupported Fallback)</option>
            </select>
          </div>
        </div>

        {/* Tab Navigation */}
        <div className="max-w-7xl mx-auto px-4 sm:px-6 flex gap-1.5 overflow-x-auto border-t border-neutral-800/80 pt-1">
          {[
            { id: 'ble-scanner', label: 'BLE Scanner (P1)', icon: Radio, highlight: true },
            { id: 'rssi-monitor', label: 'RSSI Monitor (P1)', icon: BarChart3, highlight: true },
            { id: 'phases', label: 'Roadmap & Blueprint', icon: Layers },
            { id: 'simulator', label: 'Samsung Modes POC', icon: Smartphone },
            { id: 'diagnostics', label: 'Diagnostics Matrix', icon: Activity },
            { id: 'code', label: 'Kotlin Source & APK', icon: Code },
            { id: 'docs', label: 'Documentation & Guide', icon: BookOpen },
          ].map((tab) => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id as any)}
                className={`px-3 py-2 text-xs font-semibold rounded-t-lg flex items-center gap-2 transition-all whitespace-nowrap border-b-2 ${
                  isActive
                    ? 'text-blue-400 border-blue-500 bg-neutral-800/60'
                    : 'text-neutral-400 border-transparent hover:text-neutral-200 hover:bg-neutral-800/30'
                }`}
              >
                <Icon className={`w-3.5 h-3.5 ${tab.highlight && !isActive ? 'text-blue-400' : ''}`} />
                {tab.label}
              </button>
            );
          })}
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6">
        {activeTab === 'ble-scanner' && (
          <BleScannerView
            discoveredDevices={discoveredDevices}
            isScanning={isScanning}
            scanMode={scanMode}
            savedProximityDevice={savedProximityDevice}
            inspectedDevice={inspectedDevice}
            onStartScan={handleStartScan}
            onStopScan={handleStopScan}
            onSetScanMode={(mode) => {
              setScanMode(mode);
              handleAddLog('BLE', `Switched BLE scan mode to: ${mode}`);
            }}
            onInspectDevice={(dev) => {
              setInspectedDevice(dev);
              handleAddLog('BLE', `Inspecting BLE payload for ${dev.name}`);
            }}
            onSaveProximityDevice={handleSaveProximityDevice}
            onAddLog={handleAddLog}
          />
        )}

        {activeTab === 'rssi-monitor' && (
          <RssiMonitorView
            activeDevice={inspectedDevice}
            savedProfile={savedProximityDevice}
            allDevices={discoveredDevices}
            onSelectDevice={(dev) => setInspectedDevice(dev)}
          />
        )}

        {activeTab === 'phases' && <PhaseRoadmapView />}

        {activeTab === 'simulator' && (
          <DeviceSimulator
            device={currentDevice}
            onDeviceChange={() => {}}
            logs={logs}
            onAddLog={handleAddLog}
            onClearLogs={handleClearLogs}
          />
        )}

        {activeTab === 'diagnostics' && <DiagnosticsInspector device={currentDevice} />}

        {activeTab === 'code' && <CodeExplorer />}

        {activeTab === 'docs' && <DocumentationView />}
      </main>

      {/* Footer */}
      <footer className="border-t border-neutral-800/80 py-3 px-6 text-center text-xs text-neutral-500 flex flex-col sm:flex-row items-center justify-between max-w-7xl mx-auto w-full gap-2">
        <span>Samsung Modes POC & BLE Proximity Engine • Android 16 (API 36)</span>
        <span className="font-mono text-neutral-400">Phase 1: BLE Scanner & RSSI Monitor</span>
      </footer>
    </div>
  );
}
