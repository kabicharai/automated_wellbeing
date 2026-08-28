import React, { useState, useEffect } from 'react';
import {
  DeviceInfo,
  DeviceProfile,
  LogEntry,
  BleDiscoveredDevice,
  BleScanMode,
  BleDeviceProfile,
  ProximityProfile,
  AutomationState,
  AutomationAuditEvent,
  RuntimePermissionStatus,
  ProximityState,
} from './types';
import { DeviceSimulator } from './components/DeviceSimulator';
import { DiagnosticsInspector } from './components/DiagnosticsInspector';
import { CodeExplorer } from './components/CodeExplorer';
import { ArchitectureView } from './components/ArchitectureView';
import { DocumentationView } from './components/DocumentationView';
import { BleScannerView } from './components/BleScannerView';
import { RssiMonitorView } from './components/RssiMonitorView';
import { CalibrationView } from './components/CalibrationView';
import { ProximityStateView } from './components/ProximityStateView';
import { AutomationControllerView } from './components/AutomationControllerView';
import { PhaseRoadmapView } from './components/PhaseRoadmapView';
import {
  Smartphone,
  Activity,
  Code,
  GitFork,
  BookOpen,
  Radio,
  BarChart3,
  Shield,
  Layers,
  Sliders,
  Radar,
  Zap,
} from 'lucide-react';

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
];

const STORAGE_KEYS = {
  PROFILES: 'samsung_modes_proximity_profiles_v1',
  DEVICES: 'samsung_modes_saved_devices_v1',
  AUTOMATION: 'samsung_modes_automation_state_v1',
  PERMISSIONS: 'samsung_modes_permissions_v1',
};

export default function App() {
  const [activeTab, setActiveTab] = useState<
    'automation' | 'proximity' | 'calibration' | 'ble-scanner' | 'rssi-monitor' | 'phases' | 'simulator' | 'diagnostics' | 'code' | 'docs'
  >('automation');
  
  const [currentDeviceId, setCurrentDeviceId] = useState<DeviceProfile>('galaxy-s23-oneui85');
  
  // Runtime Permissions State
  const [permissionStatus, setPermissionStatus] = useState<RuntimePermissionStatus>(() => {
    const saved = localStorage.getItem(STORAGE_KEYS.PERMISSIONS);
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch (e) {}
    }
    return {
      allGranted: true,
      hasBluetoothScan: true,
      hasBluetoothConnect: true,
      hasFineLocation: true,
      hasCoarseLocation: true,
      hasNotification: true,
      missingPermissions: [],
    };
  });

  // Persistent Saved Devices
  const [savedDevices, setSavedDevices] = useState<Record<string, BleDeviceProfile>>(() => {
    const saved = localStorage.getItem(STORAGE_KEYS.DEVICES);
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch (e) {}
    }
    return {
      'mfg:0x75:010042': {
        id: 'dev-smarttag-bedroom',
        displayName: 'Samsung SmartTag 1 (Bedroom)',
        deviceType: 'SAMSUNG_SMARTTAG_1',
        primaryKey: 'mfg:0x75:010042',
        macAddress: 'E4:7B:A2:18:42:01',
        targetManufacturerId: 0x0075,
        createdAtMillis: Date.now() - 3600000,
        notes: 'Primary room proximity beacon',
      },
    };
  });

  // Persistent Per-Device Calibration Profiles
  const [savedProfiles, setSavedProfiles] = useState<Record<string, ProximityProfile>>(() => {
    const saved = localStorage.getItem(STORAGE_KEYS.PROFILES);
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch (e) {}
    }
    return {
      'mfg:0x75:010042': {
        id: 'prof-bedroom-tag',
        profileName: 'Bedroom Focus Tag Profile',
        targetDeviceKey: 'mfg:0x75:010042',
        targetDisplayName: 'Samsung SmartTag 1 (Bedroom)',
        insideMetrics: null,
        outsideMetrics: null,
        enterThresholdRssi: -62,
        exitThresholdRssi: -72,
        enterDurationSeconds: 2,
        exitDurationSeconds: 4,
        filterType: 'EMA',
        filterSmoothingParam: 0.25,
        boundSamsungModeUuid: 'routine-bedroom-focus-01',
        isEnabled: true,
        createdAtMillis: Date.now() - 3600000,
      },
    };
  });

  // Active Selected Profile & Device
  const [activeProfileKey, setActiveProfileKey] = useState<string>('mfg:0x75:010042');
  const activeProximityProfile = savedProfiles[activeProfileKey] || Object.values(savedProfiles)[0] || null;
  const savedProximityDevice = savedDevices[activeProfileKey] || Object.values(savedDevices)[0] || null;

  // Phase 4 Automation State
  const [automationState, setAutomationState] = useState<AutomationState>(() => {
    const saved = localStorage.getItem(STORAGE_KEYS.AUTOMATION);
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch (e) {}
    }
    return {
      masterEnabled: true,
      targetModeUuid: 'routine-bedroom-focus-01',
      targetModeName: 'Bedroom Focus',
      isPaused: false,
      pauseUntilMillis: 0,
      executionState: 'IDLE',
      lastTriggeredTransition: 'None',
      lastActionTimestampMillis: 0,
      lastResultDetails: 'Ready for proximity events',
      retryCount: 0,
      totalTransitionsHandled: 0,
      successfulInvocations: 0,
      failedInvocations: 0,
    };
  });

  const [auditEvents, setAuditEvents] = useState<AutomationAuditEvent[]>([
    {
      id: 'audit-1',
      timestampMillis: Date.now() - 15000,
      action: 'SYSTEM_READY',
      fromState: 'UNKNOWN',
      toState: 'OUTSIDE',
      targetUuid: 'routine-bedroom-focus-01',
      success: true,
      message: 'Automation controller initialized with bound routine: Bedroom Focus',
    },
  ]);

  // Phase 1 & 2 Scanning State
  const [isScanning, setIsScanning] = useState<boolean>(true);
  const [scanMode, setScanMode] = useState<BleScanMode>('BALANCED');
  const [discoveredDevices, setDiscoveredDevices] = useState<BleDiscoveredDevice[]>(INITIAL_BLE_DEVICES);
  const [inspectedDevice, setInspectedDevice] = useState<BleDiscoveredDevice | null>(INITIAL_BLE_DEVICES[0]);
  const [simulatedProximityState, setSimulatedProximityState] = useState<ProximityState>('OUTSIDE');

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
    {
      id: '4',
      timestamp: '00:04:12.140',
      level: 'AUTO',
      message: 'ProximityAutomationController: Master Automation ENABLED for target routine-bedroom-focus-01',
    },
  ]);

  const currentDevice = DEVICE_PROFILES[currentDeviceId];

  // Save to persistent storage whenever state changes
  useEffect(() => {
    localStorage.setItem(STORAGE_KEYS.PROFILES, JSON.stringify(savedProfiles));
  }, [savedProfiles]);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEYS.DEVICES, JSON.stringify(savedDevices));
  }, [savedDevices]);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEYS.AUTOMATION, JSON.stringify(automationState));
  }, [automationState]);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEYS.PERMISSIONS, JSON.stringify(permissionStatus));
  }, [permissionStatus]);

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
    setSavedDevices((prev) => ({ ...prev, [dev.primaryKey]: profile }));
    setActiveProfileKey(dev.primaryKey);
    setInspectedDevice(dev);
    handleAddLog('BLE', `Saved Proximity Device: ${dev.name} (${dev.primaryKey})`);
  };

  const handleSaveCalibratedProfile = (prof: ProximityProfile) => {
    setSavedProfiles((prev) => ({ ...prev, [prof.targetDeviceKey]: prof }));
    setActiveProfileKey(prof.targetDeviceKey);
    handleAddLog('SUCCESS', `Saved Per-Device Calibration for '${prof.targetDisplayName}' [ENTER: ${prof.enterThresholdRssi} dBm, EXIT: ${prof.exitThresholdRssi} dBm]`);
  };

  const handleGrantPermissions = () => {
    const granted: RuntimePermissionStatus = {
      allGranted: true,
      hasBluetoothScan: true,
      hasBluetoothConnect: true,
      hasFineLocation: true,
      hasCoarseLocation: true,
      hasNotification: true,
      missingPermissions: [],
    };
    setPermissionStatus(granted);
    handleAddLog('SUCCESS', 'All required Bluetooth & Location permissions granted interactively in-app.');
  };

  // Phase 4 Automation Dispatch Simulator
  const handleSimulateTransition = (newState: ProximityState) => {
    setSimulatedProximityState(newState);
    if (!automationState.masterEnabled) {
      handleAddLog('WARN', `Proximity state changed to ${newState}, but Master Automation is DISABLED.`);
      return;
    }
    if (automationState.isPaused) {
      handleAddLog('WARN', `Proximity state changed to ${newState}, but Automation is currently PAUSED.`);
      return;
    }

    if (newState === 'INSIDE') {
      handleAddLog('AUTO', `[OUTSIDE → INSIDE] Dispatching startMode(${automationState.targetModeUuid}) to Samsung Modes Backend [${currentDevice.selectedBackend}]`);
      setAutomationState((prev) => ({
        ...prev,
        executionState: 'START_SUCCESS',
        lastTriggeredTransition: 'OUTSIDE → INSIDE',
        lastActionTimestampMillis: Date.now(),
        lastResultDetails: `SUCCESS: Activated mode '${prev.targetModeName}'`,
        totalTransitionsHandled: prev.totalTransitionsHandled + 1,
        successfulInvocations: prev.successfulInvocations + 1,
      }));
      setAuditEvents((prev) => [
        ...prev,
        {
          id: Math.random().toString(36).substring(2, 9),
          timestampMillis: Date.now(),
          action: 'START_MODE_SUCCESS',
          fromState: 'OUTSIDE',
          toState: 'INSIDE',
          targetUuid: automationState.targetModeUuid,
          success: true,
          message: `Dispatched startMode('${automationState.targetModeUuid}') via ${currentDevice.selectedBackend}`,
        },
      ]);
    } else if (newState === 'OUTSIDE') {
      handleAddLog('AUTO', `[INSIDE → OUTSIDE] Dispatching stopMode(${automationState.targetModeUuid}) to Samsung Modes Backend [${currentDevice.selectedBackend}]`);
      setAutomationState((prev) => ({
        ...prev,
        executionState: 'STOP_SUCCESS',
        lastTriggeredTransition: 'INSIDE → OUTSIDE',
        lastActionTimestampMillis: Date.now(),
        lastResultDetails: `SUCCESS: Stopped mode '${prev.targetModeName}'`,
        totalTransitionsHandled: prev.totalTransitionsHandled + 1,
        successfulInvocations: prev.successfulInvocations + 1,
      }));
      setAuditEvents((prev) => [
        ...prev,
        {
          id: Math.random().toString(36).substring(2, 9),
          timestampMillis: Date.now(),
          action: 'STOP_MODE_SUCCESS',
          fromState: 'INSIDE',
          toState: 'OUTSIDE',
          targetUuid: automationState.targetModeUuid,
          success: true,
          message: `Dispatched stopMode('${automationState.targetModeUuid}') via ${currentDevice.selectedBackend}`,
        },
      ]);
    }
  };

  const handleResetAllData = () => {
    localStorage.removeItem(STORAGE_KEYS.PROFILES);
    localStorage.removeItem(STORAGE_KEYS.DEVICES);
    localStorage.removeItem(STORAGE_KEYS.AUTOMATION);
    setSavedProfiles({});
    setSavedDevices({});
    setActiveProfileKey('');
    setAutomationState({
      masterEnabled: false,
      targetModeUuid: '',
      targetModeName: '',
      isPaused: false,
      pauseUntilMillis: 0,
      executionState: 'DISABLED',
      lastTriggeredTransition: 'Reset',
      lastActionTimestampMillis: Date.now(),
      lastResultDetails: 'Storage cleared',
      retryCount: 0,
      totalTransitionsHandled: 0,
      successfulInvocations: 0,
      failedInvocations: 0,
    });
    handleAddLog('WARN', 'FACTORY RESET COMPLETE: Cleared all persistent profiles and configurations.');
  };

  return (
    <div className="min-h-screen bg-neutral-950 text-neutral-100 flex flex-col font-sans selection:bg-blue-600 selection:text-white">
      {/* Top App Header */}
      <header className="border-b border-neutral-800 bg-neutral-900/90 backdrop-blur sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-3 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-purple-600 flex items-center justify-center shadow-md shadow-purple-900/30">
              <Zap className="w-5 h-5 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-sm font-bold text-white tracking-tight">Samsung Modes • Proximity Automation</h1>
                <span className="px-2 py-0.5 rounded-full text-[10px] font-bold font-mono bg-purple-950 text-purple-300 border border-purple-800">
                  Phase 4 Active
                </span>
              </div>
              <p className="text-xs text-neutral-400">
                BLE RSSI Proximity State Machine → Samsung Modes Automation Engine
              </p>
            </div>
          </div>

          {/* Test Phone Profile Selector */}
          <div className="flex items-center gap-2 bg-neutral-950 p-1.5 rounded-xl border border-neutral-800">
            <span className="text-xs font-semibold text-neutral-400 pl-2 pr-1 flex items-center gap-1.5">
              <Smartphone className="w-3.5 h-3.5 text-purple-400" /> Phone:
            </span>
            <select
              value={currentDeviceId}
              onChange={(e) => handleDeviceSwitch(e.target.value as DeviceProfile)}
              className="bg-neutral-900 border border-neutral-700 text-neutral-200 text-xs font-semibold rounded-lg px-2.5 py-1 focus:outline-none focus:border-purple-500 cursor-pointer"
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
            { id: 'automation', label: 'Automation (P4)', icon: Zap, highlight: true },
            { id: 'proximity', label: 'Proximity Live (P3)', icon: Radar },
            { id: 'calibration', label: 'Calibration (P2)', icon: Sliders },
            { id: 'ble-scanner', label: 'BLE Scanner (P1)', icon: Radio },
            { id: 'rssi-monitor', label: 'RSSI Monitor (P1)', icon: BarChart3 },
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
                    ? 'text-purple-400 border-purple-500 bg-neutral-800/60'
                    : 'text-neutral-400 border-transparent hover:text-neutral-200 hover:bg-neutral-800/30'
                }`}
              >
                <Icon className={`w-3.5 h-3.5 ${tab.highlight && !isActive ? 'text-purple-400' : ''}`} />
                {tab.label}
              </button>
            );
          })}
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6">
        {activeTab === 'automation' && (
          <AutomationControllerView
            automationState={automationState}
            proximityState={simulatedProximityState}
            filteredRssi={-60}
            confidencePercent={94}
            activeProfile={activeProximityProfile}
            savedProfiles={savedProfiles}
            savedDevices={savedDevices}
            permissionStatus={permissionStatus}
            auditEvents={auditEvents}
            onRequestPermissions={handleGrantPermissions}
            onToggleMaster={(en) => {
              setAutomationState((p) => ({ ...p, masterEnabled: en }));
              handleAddLog('AUTO', `Master Automation ${en ? 'ENABLED' : 'DISABLED'}`);
            }}
            onSetTargetMode={(uuid, name) => {
              setAutomationState((p) => ({
                ...p,
                targetModeUuid: uuid,
                targetModeName: name || 'Selected Mode',
              }));
              handleAddLog('AUTO', `Bound target mode: ${name || uuid}`);
            }}
            onPause={(min) => {
              setAutomationState((p) => ({
                ...p,
                isPaused: true,
                pauseUntilMillis: Date.now() + min * 60000,
                executionState: 'PAUSED',
              }));
              handleAddLog('WARN', `Automation paused for ${min} minutes.`);
            }}
            onResume={() => {
              setAutomationState((p) => ({
                ...p,
                isPaused: false,
                pauseUntilMillis: 0,
                executionState: 'IDLE',
              }));
              handleAddLog('AUTO', 'Automation resumed.');
            }}
            onEmergencyStop={() => {
              setAutomationState((p) => ({
                ...p,
                masterEnabled: false,
                executionState: 'DISABLED',
                lastResultDetails: 'EMERGENCY STOPPED',
              }));
              handleAddLog('ERROR', 'EMERGENCY STOP TRIGGERED: Mode stopped and automation disabled.');
            }}
            onReconcile={() => {
              handleSimulateTransition(simulatedProximityState);
              handleAddLog('AUTO', 'State reconciled with current proximity state.');
            }}
            onSelectDeviceProfile={(devKey) => {
              setActiveProfileKey(devKey);
              handleAddLog('BLE', `Switched active per-device profile to: ${savedProfiles[devKey]?.targetDisplayName || devKey}`);
            }}
            onResetAllData={handleResetAllData}
            onSimulateTransition={handleSimulateTransition}
          />
        )}

        {activeTab === 'proximity' && (
          <ProximityStateView
            activeProfile={activeProximityProfile}
            savedDevice={savedProximityDevice}
            onLog={handleAddLog}
          />
        )}

        {activeTab === 'calibration' && (
          <CalibrationView
            savedDevice={savedProximityDevice}
            discoveredDevices={discoveredDevices}
            onSaveProfile={handleSaveCalibratedProfile}
            onLog={handleAddLog}
          />
        )}

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
        <span>Samsung Modes POC & BLE Proximity Automation • Android 16 (API 36)</span>
        <span className="font-mono text-neutral-400">Phase 4: Proximity Automation & Samsung Modes Dispatcher</span>
      </footer>
    </div>
  );
}

