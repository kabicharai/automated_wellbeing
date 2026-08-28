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
  level: 'SYSTEM' | 'DETECT' | 'DIAG' | 'ACTION' | 'INFO' | 'SUCCESS' | 'WARN' | 'ERROR' | 'TEST' | 'QUERY' | 'BLE' | 'AUTO';
  message: string;
}

export type FullTestOutcome = 'IDLE' | 'RUNNING' | 'PASS' | 'PARTIAL PASS' | 'FAIL';

export interface AndroidSourceFile {
  path: string;
  name: string;
  category: 'samsung' | 'ble' | 'ui' | 'restriction' | 'model' | 'config' | 'doc';
  language: 'kotlin' | 'xml' | 'gradle' | 'markdown';
  content: string;
  description: string;
}

// --- Phase 1: BLE & RSSI Types ---

export type BleScanMode = 'BALANCED' | 'LOW_LATENCY' | 'LOW_POWER';

export interface BleRawAdvertisement {
  advertiseFlags: number;
  txPowerLevel: number | null;
  manufacturerDataMap: Record<number, string>; // mfgId -> hex payload
  serviceUuids: string[];
  serviceDataMap: Record<string, string>;
  rawBytesHex: string;
  isConnectable: boolean;
  primaryPhy: string;
  timestampNanos: number;
}

export interface BleDiscoveredDevice {
  primaryKey: string;
  name: string;
  address: string;
  currentRssi: number;
  firstSeenMillis: number;
  lastSeenMillis: number;
  totalSamples: number;
  isSmartTagCandidate: boolean;
  advertisement: BleRawAdvertisement;
}

export interface RssiSample {
  timestampMillis: number;
  rssi: number;
}

export type RssiHistoryWindow = '5s' | '15s' | '30s' | '60s' | '5m';

export interface RssiSnapshot {
  currentRssi: number | null;
  sampleCount: number;
  average: number | null;
  median: number | null;
  min: number | null;
  max: number | null;
  standardDeviation: number | null;
  historySamples: RssiSample[];
}

export interface BleDeviceProfile {
  id: string;
  displayName: string;
  deviceType: 'SAMSUNG_SMARTTAG_1' | 'GENERIC_BEACON' | 'IBEACON' | 'EDDYSTONE' | 'CUSTOM_BLE';
  primaryKey: string;
  macAddress: string;
  targetManufacturerId: number | null;
  createdAtMillis: number;
  notes: string;
}

export type RssiFilterType = 'EMA' | 'RUNNING_MEDIAN' | 'KALMAN' | 'MOVING_AVERAGE';

export interface RssiDistributionMetrics {
  sampleCount: number;
  durationSeconds: number;
  minRssi: number;
  maxRssi: number;
  meanRssi: number;
  medianRssi: number;
  standardDeviation: number;
  p10: number;
  p25: number;
  p75: number;
  p90: number;
  sampleHistory: number[];
}

export type SeparationQuality = 'EXCELLENT' | 'GOOD' | 'MODERATE' | 'POOR';

export interface ThresholdCalculationResult {
  suggestedEnterThreshold: number;
  suggestedExitThreshold: number;
  medianSeparationDb: number;
  quality: SeparationQuality;
  overlapPercentage: number;
  summaryNotes: string;
}

export interface ProximityProfile {
  id: string;
  profileName: string;
  targetDeviceKey: string;
  targetDisplayName: string;
  insideMetrics: RssiDistributionMetrics | null;
  outsideMetrics: RssiDistributionMetrics | null;
  enterThresholdRssi: number;
  exitThresholdRssi: number;
  enterDurationSeconds: number;
  exitDurationSeconds: number;
  filterType: RssiFilterType;
  filterSmoothingParam: number;
  boundSamsungModeUuid: string;
  isEnabled: boolean;
  createdAtMillis: number;
}

export type ProximityState = 'UNKNOWN' | 'INSIDE' | 'OUTSIDE';
export type CandidateStatus = 'NONE' | 'ENTERING' | 'EXITING';

export interface ProximityTransitionEvent {
  id: string;
  timestampMillis: number;
  fromState: ProximityState;
  toState: ProximityState;
  candidateStatus: CandidateStatus;
  filteredRssi: number | null;
  rawRssi: number | null;
  reason: string;
}

export interface ProximityEngineSnapshot {
  state: ProximityState;
  candidateStatus: CandidateStatus;
  candidateProgressPercent: number;
  candidateElapsedSeconds: number;
  candidateTotalSeconds: number;
  currentFilteredRssi: number | null;
  currentRawRssi: number | null;
  confidencePercent: number;
  enterThreshold: number;
  exitThreshold: number;
  enterDurationSeconds: number;
  exitDurationSeconds: number;
  isBeaconLost: boolean;
  secondsSinceLastSample: number;
  lostTimeoutSeconds: number;
  profileName: string;
  recentEvents: ProximityTransitionEvent[];
}

// --- Phase 4 & 5: Multi-Device & Multi-Mode Proximity Automation Engine ---

export type AutomationEntryAction = 'TURN_ON' | 'TURN_OFF' | 'NONE';
export type AutomationExitAction = 'TURN_OFF' | 'TURN_ON' | 'RESTORE_PREVIOUS' | 'NONE';

export interface AutomationRule {
  id: string;
  name: string;
  deviceKey: string;
  deviceDisplayName: string;
  targetModeUuid: string;
  targetModeName: string;
  entryAction: AutomationEntryAction;
  exitAction: AutomationExitAction;
  priority: number; // 1 = highest priority
  isEnabled: boolean;
  timeConstraintEnabled: boolean;
  timeStart?: string; // "HH:mm" e.g. "22:00"
  timeEnd?: string;   // "HH:mm" e.g. "07:00"
  daysOfWeek?: string[]; // ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']
  notes?: string;
  createdAtMillis: number;
}

export interface RuntimePermissionStatus {
  allGranted: boolean;
  hasBluetoothScan: boolean;
  hasBluetoothConnect: boolean;
  hasFineLocation: boolean;
  hasCoarseLocation: boolean;
  hasNotification: boolean;
  missingPermissions: string[];
}

export type AutomationExecutionState =
  | 'DISABLED'
  | 'IDLE'
  | 'TRIGGERING_START'
  | 'START_SUCCESS'
  | 'TRIGGERING_STOP'
  | 'STOP_SUCCESS'
  | 'PAUSED'
  | 'RETRYING'
  | 'ERROR';

export interface AutomationState {
  masterEnabled: boolean;
  targetModeUuid: string;
  targetModeName: string;
  isPaused: boolean;
  pauseUntilMillis: number;
  executionState: AutomationExecutionState;
  lastTriggeredTransition: string;
  lastActionTimestampMillis: number;
  lastResultDetails: string;
  retryCount: number;
  totalTransitionsHandled: number;
  successfulInvocations: number;
  failedInvocations: number;
  activeRuleId?: string | null;
  rules?: AutomationRule[];
}

export interface AutomationAuditEvent {
  id: string;
  timestampMillis: number;
  action: string;
  fromState: ProximityState;
  toState: ProximityState;
  targetUuid: string;
  success: boolean;
  message: string;
  ruleId?: string;
  ruleName?: string;
}



