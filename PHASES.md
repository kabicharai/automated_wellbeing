# Samsung Modes BLE Proximity Automation — Development Phases

This document outlines the sequential phases for building the BLE Proximity → Samsung Modes & Routines automation system. Refer to `BLUEPRINT.md` for full verbatim requirements.

---

## 🎯 Architecture Principle: Separation of Concerns

```
┌────────────────────────────────────────────────────────┐
│               SYSTEM A: BLE Proximity Engine           │
│  [BleScanner] → [BleDeviceIdentifier] → [RssiFilter]   │
│                 → [ProximityStateMachine]              │
└───────────────────────────┬────────────────────────────┘
                            │ (State transitions: INSIDE / OUTSIDE)
                            ▼
┌────────────────────────────────────────────────────────┐
│             ProximityAutomationController              │
│  (State reconciliation, debounce, duplicate suppression)│
└───────────────────────────┬────────────────────────────┘
                            ▼
┌────────────────────────────────────────────────────────┐
│             SYSTEM B: Samsung Mode Controller          │
│       (WORKING POC: One UI 8.0 & 8.5 Controllers)      │
│            startMode(uuid) / stopMode(uuid)            │
└────────────────────────────────────────────────────────┘
```

> **CRITICAL RULE**: The working Samsung Mode Controller POC (`SamsungModeControllerCombined`, `SamsungModeControllerV8`, `SamsungModeControllerV85`) MUST NOT be broken, rewritten, or tightly coupled to BLE.

---

## 📋 Phase Roadmap

### ✅ Phase 1: BLE Scanner, Device Discovery & Live RSSI Monitor (COMPLETED)
- [x] **Android BLE Scanner**: Standard `BluetoothLeScanner` implementation with Android 16 permissions (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`).
- [x] **Stable Identity Abstraction (`BleDeviceId`)**: Identify devices flexibly across MAC randomization using manufacturer ID, manufacturer payload, service UUIDs, and advertised name latching ("Smart Tag" / "SmartTag").
- [x] **SmartTag 1 & Generic Beacon Diagnostics**: Raw advertisement inspector (RSSI, manufacturer data, service UUIDs, service data, flags, TX power).
- [x] **Proximity Device Selection**: Ability to tap any scanned device and save it as the active Proximity Target (`BleProximityDevice`).
- [x] **Live RSSI Monitor & Telemetry**: Real-time graph of RSSI against time with configurable sampling windows (5s, 15s, 30s, 60s, 5m) and sample statistics (Current, Average, Median, Min, Max, Count).
- [x] **Web Companion Simulation & UI**: Interactive scanner, live graphing, and raw payload decoder mirroring physical Android capabilities.

---

### ✅ Phase 2: RSSI Filtering, Statistical Engine & Dual-Zone Calibration (CURRENT PHASE)
- [x] **Statistical Engine**: Compute median, moving average, exponential moving average (EMA), rolling standard deviation, and percentiles ($p_{10}, p_{25}, p_{75}, p_{90}$).
- [x] **RSSI Filtering Suite**: Configurable filters (`EmaRssiFilter`, `RunningMedianRssiFilter`, `KalmanRssiFilter`, and `MovingAverage`).
- [x] **Guided 2-Step Calibration Engine (`CalibrationEngine`)**:
  1. *Step 1 — Outside Calibration* (30s sample collection + distribution curve calculation)
  2. *Step 2 — Inside Calibration* (30s sample collection + distribution curve calculation)
- [x] **Threshold Calculator & Separation Analysis (`ThresholdCalculator`)**: Calculate candidate ENTER/EXIT thresholds from distributions; display separation quality (Good / Moderate / Poor) and overlap warnings.
- [x] **Dual-Zone Calibration UI (`CalibrationTab`)**: Visual distribution comparison canvas, countdown timer, percentile tables, and profile persistence.

---

### ✅ Phase 3: Proximity State Machine & Anti-Flapping Engine (COMPLETED)
- [x] **3-State Machine (`ProximityEngine`)**: `UNKNOWN` ↔ `INSIDE` ↔ `OUTSIDE`.
- [x] **Hysteresis Engine**: Dual-threshold boundary (ENTER e.g. $\ge -64\text{ dBm}$ vs EXIT e.g. $\le -69\text{ dBm}$) with central deadband.
- [x] **Temporal Stability & Candidate Timers**: Anti-flapping temporal verification with high-resolution countdown timer (5s for ENTER, 10s for EXIT) and instant candidate abort if signal leaves target region.
- [x] **Lost-Device Handling**: Configurable timeout (default 30s) — temporary signal dropouts and multi-path fades preserve current state and do not trigger false flips; graceful fallback to `UNKNOWN` after timeout.
- [x] **Confidence Metric**: Real-time confidence score (0-100%) computed from packet freshness, sample variance ($\sigma$), and threshold distance.
- [x] **Live State Visualizer (`ProximityStateTab.kt` & `ProximityStateView.tsx`)**: Real-time signal needle gauge, state transition flow nodes, candidate progress bar, interactive scenario simulator, and transition event log.

---

### ⏳ Phase 4: Proximity Automation Controller & Samsung Modes Binding (NEXT PHASE)
- [ ] **Automation Dispatcher**: Triggers `startMode(uuid)` on `OUTSIDE → INSIDE` and `stopMode(uuid)` on `INSIDE → OUTSIDE`.
- [ ] **Transition Integrity**: Zero duplicate calls, ignores `UNKNOWN` transitions, handles startup synchronization without unrequested toggles.
- [ ] **Safety & Overrides**: Master Automation ON/OFF switch, Pause/Resume controls, and Emergency Stop Mode.
- [ ] **Controlled Retries**: Safe backoff retry logic (5s, 15s, 30s) on Samsung Mode failure.

---

### ⏳ Phase 5: Android Background Monitoring & Power Optimization
- [ ] **Android 16 Foreground Service**: Persistent background BLE monitoring when screen is off or app is backgrounded.
- [ ] **Ongoing Notification**: Live display of current profile, proximity state, RSSI, and automation status.
- [ ] **Power Management**: Configurable scan settings (*Battery Saver*, *Balanced*, *High Reliability*).

---

### ⏳ Phase 6: Multi-Profile Persistence, Setup Wizard & Diagnostic Export
- [ ] **Local Storage**: Save/load profiles (Beacon ID, calibration statistics, thresholds, Samsung Mode UUID, automation state).
- [ ] **10-Step Setup Wizard**: End-to-end guided setup from discovery to active automation.
- [ ] **Simulation Playground**: Developer RSSI slider, mock sequence replay, and state-machine verification.
- [ ] **Diagnostic Export**: Full system report bundle for field debugging.
