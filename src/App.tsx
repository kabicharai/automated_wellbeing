import React, { useState } from 'react';
import { DeviceInfo, DeviceProfile, LogEntry } from './types';
import { DeviceSimulator } from './components/DeviceSimulator';
import { DiagnosticsInspector } from './components/DiagnosticsInspector';
import { CodeExplorer } from './components/CodeExplorer';
import { ArchitectureView } from './components/ArchitectureView';
import { DocumentationView } from './components/DocumentationView';
import { Smartphone, Activity, Code, GitFork, BookOpen, Layers, CheckCircle2, Shield } from 'lucide-react';

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
    systemSettingModeId: 'focus-lock-550e8400'
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
    systemSettingModeId: null
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
    systemSettingModeId: null
  }
};

export default function App() {
  const [activeTab, setActiveTab] = useState<'simulator' | 'diagnostics' | 'code' | 'architecture' | 'docs'>('simulator');
  const [currentDeviceId, setCurrentDeviceId] = useState<DeviceProfile>('galaxy-s23-oneui85');
  const [logs, setLogs] = useState<LogEntry[]>([
    {
      id: '1',
      timestamp: '18:43:29.102',
      level: 'SYSTEM',
      message: 'Initialized Samsung Modes Controller POC on Samsung Galaxy S23 (Android 16, SDK 36)'
    },
    {
      id: '2',
      timestamp: '18:43:29.115',
      level: 'DETECT',
      message: 'SamsungCapabilityDetector: Probed ShortcutLaunchActivity and externalprovider. Selected Backend: V8.5 (One UI 8.5+)'
    },
    {
      id: '3',
      timestamp: '18:43:29.128',
      level: 'DIAG',
      message: 'Package com.samsung.android.app.routines found (v4.3.02.14, exported=true)'
    }
  ]);

  const currentDevice = DEVICE_PROFILES[currentDeviceId];

  const handleAddLog = (level: LogEntry['level'], message: string) => {
    const newEntry: LogEntry = {
      id: Math.random().toString(36).substring(2, 9),
      timestamp: new Date().toLocaleTimeString('en-US', { hour12: false }) + '.' + Math.floor(Math.random() * 900 + 100),
      level,
      message
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

  return (
    <div className="min-h-screen bg-neutral-950 text-neutral-100 flex flex-col font-sans selection:bg-blue-600 selection:text-white">
      {/* Top App Header */}
      <header className="border-b border-neutral-800 bg-neutral-900/90 backdrop-blur sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-3.5 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-blue-600 flex items-center justify-center shadow-md">
              <Shield className="w-5 h-5 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-sm font-bold text-white tracking-tight">Samsung Modes & Routines POC</h1>
                <span className="px-2 py-0.5 rounded-full text-[10px] font-bold font-mono bg-blue-950 text-blue-300 border border-blue-800">
                  Android 16 • One UI 8.0 & 8.5
                </span>
              </div>
              <p className="text-xs text-neutral-400">Single APK • Runtime Capability Probing • Restrict App Usage</p>
            </div>
          </div>

          {/* Test Phone Profile Selector */}
          <div className="flex items-center gap-2 bg-neutral-950 p-1.5 rounded-xl border border-neutral-800">
            <span className="text-xs font-semibold text-neutral-400 pl-2 pr-1 flex items-center gap-1.5">
              <Smartphone className="w-3.5 h-3.5 text-blue-400" /> Target Phone:
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
        <div className="max-w-7xl mx-auto px-4 sm:px-6 flex gap-2 overflow-x-auto border-t border-neutral-800/80 pt-1">
          {[
            { id: 'simulator', label: 'Live POC Applet', icon: Smartphone },
            { id: 'diagnostics', label: 'Diagnostics Matrix', icon: Activity },
            { id: 'code', label: 'Kotlin Source & APK Build', icon: Code },
            { id: 'architecture', label: 'Architecture & BLE Plan', icon: GitFork },
            { id: 'docs', label: 'Documentation & Guide', icon: BookOpen },
          ].map((tab) => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id as any)}
                className={`px-3.5 py-2 text-xs font-semibold rounded-t-lg flex items-center gap-2 transition-all border-b-2 ${
                  isActive
                    ? 'text-blue-400 border-blue-500 bg-neutral-800/60'
                    : 'text-neutral-400 border-transparent hover:text-neutral-200 hover:bg-neutral-800/30'
                }`}
              >
                <Icon className="w-3.5 h-3.5" />
                {tab.label}
              </button>
            );
          })}
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6">
        {activeTab === 'simulator' && (
          <DeviceSimulator
            device={currentDevice}
            onDeviceChange={() => {}}
            logs={logs}
            onAddLog={handleAddLog}
            onClearLogs={handleClearLogs}
          />
        )}

        {activeTab === 'diagnostics' && (
          <DiagnosticsInspector device={currentDevice} />
        )}

        {activeTab === 'code' && (
          <CodeExplorer />
        )}

        {activeTab === 'architecture' && (
          <ArchitectureView />
        )}

        {activeTab === 'docs' && (
          <DocumentationView />
        )}
      </main>

      {/* Footer */}
      <footer className="border-t border-neutral-800/80 py-4 px-6 text-center text-xs text-neutral-500">
        Samsung Modes & Routines Controller POC • Android 16 (API 36) • Designed for Galaxy S23 (One UI 8.0 & 8.5)
      </footer>
    </div>
  );
}
