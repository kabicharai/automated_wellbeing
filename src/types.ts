export type DeviceProfile = 'galaxy-s23-oneui85' | 'galaxy-s23-oneui80' | 'generic-android16';

export interface DeviceInfo {
  id: DeviceProfile;
  name: string;
  model: string;
  manufacturer: string;
  androidVersion: string;
  sdkInt: number;
  oneUiVersion: string;
  routinesPackageInstalled: boolean;
  routinesVersionName: string;
  routinesVersionCode: number;
  shortcutActivityFound: boolean;
  shortcutActivityExported: boolean;
  legacyProviderFound: boolean;
  legacyProviderAccessible: boolean;
  selectedBackend: 'V8.5 (Shortcut Activity)' | 'V8 (Legacy External Provider)' | 'Unsupported';
  isSupported: boolean;
  systemSettingModeId: string | null;
}

export interface LogEntry {
  id: string;
  timestamp: string;
  level: 'SYSTEM' | 'DETECT' | 'DIAG' | 'ACTION' | 'INFO' | 'SUCCESS' | 'WARN' | 'ERROR' | 'TEST' | 'QUERY';
  message: string;
}

export type FullTestOutcome = 'IDLE' | 'RUNNING' | 'PASS' | 'PARTIAL PASS' | 'FAIL';

export interface AndroidSourceFile {
  path: string;
  name: string;
  category: 'samsung' | 'ui' | 'restriction' | 'model' | 'config' | 'doc';
  language: 'kotlin' | 'xml' | 'gradle' | 'markdown';
  content: string;
  description: string;
}
