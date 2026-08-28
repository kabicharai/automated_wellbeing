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

### ✅ Phase 1: BLE Scanner, Device Discovery & Live RSSI Monitor (CURRENT PHASE)
- [ ] **Android BLE Scanner**: Standard `BluetoothLeScanner` implementation with Android 16 permissions (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`).
- [ ] **Stable Identity Abstraction (`BleDeviceId`)**: Identify devices flexibly across MAC randomization using manufacturer ID, manufacturer payload, service UUIDs, and device name.
- [ ] **SmartTag 1 & Generic Beacon Diagnostics**: Raw advertisement inspector (RSSI, manufacturer data, service UUIDs, service data, flags, TX power).
- [ ] **Proximity Device Selection**: Ability to tap any scanned device and save it as the active Proximity Target (`BleProximityDevice`).
- [ ] **Live RSSI Monitor & Telemetry**: Real-time graph of RSSI against time with configurable sampling windows (5s, 15s, 30s, 60s, 5m) and sample statistics (Current, Average, Median, Min, Max, Count).
- [ ] **Web Companion Simulation & UI**: Interactive scanner, live graphing, and raw payload decoder mirroring physical Android capabilities.

---

### ⏳ Phase 2: RSSI Filtering, Statistical Engine & Dual-Zone Calibration
- [ ] **Statistical Engine**: Compute median, moving average, exponential moving average (EMA), rolling standard deviation, and percentiles ($p_{10}, p_{25}, p_{75}, p_{90}$).
- [ ] **Guided 2-Step Calibration**:
  1. *Step 1 — Outside Calibration* (30s sample collection + distribution curve)
  2. *Step 2 — Inside Calibration* (30s sample collection + distribution curve)
- [ ] **Threshold Calculator & Separation Analysis**: Calculate candidate ENTER/EXIT thresholds from distributions; display separation quality (Good / Moderate / Poor) and overlap warnings.

---

### ⏳ Phase 3: Proximity State Machine & Anti-Flapping Engine
- [ ] **3-State Machine**: `UNKNOWN` ↔ `INSIDE` ↔ `OUTSIDE`.
- [ ] **Hysteresis Engine**: Distinct ENTER threshold (e.g. $\ge -64\text{ dBm}$) and EXIT threshold (e.g. $\le -69\text{ dBm}$).
- [ ] **Temporal Stability & Candidate Timers**: Configurable candidate duration (e.g. 5s for ENTER, 10s for EXIT) to suppress sudden spikes and body blocking.
- [ ] **Lost-Device Handling**: Configurable timeout (default 30s) — temporary signal drops preserve the current state and do NOT immediately cause an `OUTSIDE` flip.
- [ ] **Confidence Metric**: Real-time calculation based on sample density, variance, and threshold margin.

---

### ⏳ Phase 4: Proximity Automation Controller & Samsung Modes Binding
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
