============================================================
NEXT DEVELOPMENT PHASE — BLE PROXIMITY → SAMSUNG MODE
============================================================

IMPORTANT:

The existing Samsung Modes POC is WORKING.

DO NOT rewrite, replace, or break the existing Samsung Modes
integration.

DO NOT modify the working One UI 8.0 / One UI 8.5 Samsung Mode
controllers unless absolutely necessary.

This phase extends the existing project by adding a BLE proximity
system.

The final purpose of the application is:

    BLE beacon / Samsung SmartTag
                ↓
          RSSI measurement
                ↓
        proximity estimation
                ↓
       INSIDE / OUTSIDE
                ↓
       Samsung Mode ON/OFF
                ↓
    Samsung native "Restrict app usage"

The application must support:

    1. Samsung SmartTag 1 as a BLE proximity device
    2. Generic BLE beacons in the future

The SmartTag is NOT the permanent hardware assumption.

The BLE layer must therefore be designed around generic BLE
advertisements/devices rather than Samsung-specific APIs.

============================================================
PHASE 1 — BLE DEVICE DISCOVERY
============================================================

Add a BLE scanner.

Use Android's standard Bluetooth Low Energy APIs.

Required functionality:

    Scan for nearby BLE devices.

Display:

    Device name
    MAC/address when legitimately available
    RSSI
    manufacturer data
    service UUIDs if available
    advertising packet information
    last seen timestamp

IMPORTANT:

Android privacy restrictions may randomize addresses or restrict
access to certain identifying information.

Do not assume the MAC address is a permanent identifier.

Create a stable device identity abstraction.

Example:

    BleDeviceId

The application should attempt to identify a device using the most
reliable available combination of:

    manufacturer ID
    manufacturer payload
    service UUID
    service data
    device address where available
    device name

Do NOT hard-code the Samsung SmartTag.

============================================================
SMARTTAG 1 SUPPORT
============================================================

The Samsung SmartTag 1 has already been observed by the user using
a third-party BLE scanner.

The application must therefore investigate its actual advertisement
packets.

DO NOT assume a particular manufacturer payload format without
inspecting the device.

Create a BLE diagnostic screen.

When scanning, allow the user to tap a device:

    Samsung SmartTag

and inspect:

    RSSI
    manufacturer data
    service UUIDs
    service data
    advertisement flags
    TX power if advertised
    raw advertisement fields

Provide:

    [ SAVE AS PROXIMITY DEVICE ]

The saved device becomes the device used by the proximity engine.

============================================================
GENERIC BEACON SUPPORT
============================================================

The architecture must support adding a dedicated BLE beacon later.

Do NOT write:

    if device == SmartTag

throughout the code.

Instead define:

    interface BleProximityDevice

and a profile such as:

    data class BleDeviceProfile(
        id: String,
        displayName: String,
        identificationRules: ...,
        smoothingConfiguration: ...,
        proximityConfiguration: ...
    )

The eventual supported hardware should include:

    Samsung SmartTag 1
    Generic BLE beacon

Potential generic beacon formats may include:

    iBeacon
    Eddystone
    manufacturer-specific advertisements

Implement only what is necessary now, but keep the architecture
extensible.

============================================================
BLE SCANNER
============================================================

Create:

    BleScanner

Responsibilities:

    startScan()
    stopScan()
    scanResults
    device discovery
    RSSI updates
    advertisement parsing

The scanner must NOT determine:

    inside
    outside

That belongs to the proximity engine.

============================================================
RSSI TRACKING TOOL
============================================================

This is one of the most important features.

Create a dedicated:

    RSSI Monitor

screen.

It must display a live graph of RSSI against time.

Example:

    RSSI MONITOR

    Device:
        Bedroom Beacon

    Current:
        -61 dBm

    Average:
        -64 dBm

    Median:
        -63 dBm

    Minimum:
        -78 dBm

    Maximum:
        -52 dBm

    Samples:
        184

Then show:

    LIVE RSSI GRAPH

The graph should update continuously.

Allow:

    5 seconds
    15 seconds
    30 seconds
    60 seconds
    5 minutes

sampling/history windows.

Do not retain unlimited samples in memory.

============================================================
RSSI STATISTICS
============================================================

Calculate at minimum:

    instantaneous RSSI
    moving average
    exponential moving average
    median
    rolling standard deviation
    minimum
    maximum
    sample count

The proximity engine should NOT use raw RSSI directly.

Raw RSSI fluctuates too much.

============================================================
CALIBRATION SYSTEM
============================================================

Create a first-class calibration tool.

The user should be able to create a:

    Proximity Profile

Example:

    Bedroom

A profile contains:

    BLE device
    Inside calibration data
    Outside calibration data
    enter threshold
    exit threshold
    hysteresis
    smoothing parameters
    timing parameters

============================================================
CALIBRATION UX
============================================================

The user creates a profile:

    Profile name:

    [ Bedroom ]

    Beacon:

    [ Samsung SmartTag ]

Then:

    STEP 1 — OUTSIDE CALIBRATION

    Go to the OUTSIDE location.

    Stay there normally.

    Press:

        [ START OUTSIDE CALIBRATION ]

Collect RSSI for a configurable period.

Default:

    30 seconds

Display live:

    samples
    mean
    median
    standard deviation
    min
    max
    RSSI graph

Then:

    [ SAVE OUTSIDE ]

============================================================

STEP 2 — INSIDE CALIBRATION

Tell the user:

    "Go to the location you consider INSIDE."

Then:

    [ START INSIDE CALIBRATION ]

Collect another 30 seconds of RSSI.

Display the same statistics.

Then:

    [ SAVE INSIDE ]

============================================================
THRESHOLD CALCULATION
============================================================

Do NOT simply calculate:

    threshold = average(inside, outside)

RSSI distributions overlap.

Instead calculate useful candidate thresholds from the measured
distributions.

At minimum calculate:

    inside median
    outside median
    inside p10
    inside p25
    inside p75
    inside p90
    outside p10
    outside p25
    outside p75
    outside p90

Then determine a candidate boundary.

Display:

    OUTSIDE RSSI:
        median -72
        p25 -76
        p75 -68

    INSIDE RSSI:
        median -58
        p25 -63
        p75 -53

    Suggested ENTER threshold:
        -64 dBm

    Suggested EXIT threshold:
        -68 dBm

Allow the user to manually modify them.

============================================================
HYSTERESIS
============================================================

This is REQUIRED.

Never use a single threshold.

Use:

    ENTER threshold

and:

    EXIT threshold

Example:

    ENTER:
        -64 dBm

    EXIT:
        -69 dBm

Interpretation:

    RSSI >= -64
        → candidate INSIDE

    RSSI <= -69
        → candidate OUTSIDE

    -69 < RSSI < -64
        → maintain current state

This prevents rapid state flipping.

Make these values configurable.

============================================================
TEMPORAL STABILITY
============================================================

RSSI spikes must NOT immediately activate/deactivate the Mode.

Implement persistence requirements.

Example:

    ENTER requires:
        RSSI to remain inside-compatible for 5 seconds

    EXIT requires:
        RSSI to remain outside-compatible for 10 seconds

Make these configurable.

Default:

    ENTER:
        5 seconds

    EXIT:
        10 seconds

The engine should use filtered RSSI rather than instantaneous RSSI.

============================================================
PROXIMITY STATE MACHINE
============================================================

Implement an explicit state machine:

    UNKNOWN
       |
       +------> INSIDE
       |
       +------> OUTSIDE

States:

    UNKNOWN
    INSIDE
    OUTSIDE

Do NOT expose "transitioning" as a stable state unless useful for UI.

Example:

    OUTSIDE

RSSI enters the ENTER region.

Start:

    enterCandidateTimer

If RSSI remains valid for the entire duration:

    OUTSIDE → INSIDE

If RSSI becomes invalid:

    cancel candidate

Similarly:

    INSIDE

RSSI enters EXIT region.

Start:

    exitCandidateTimer

If RSSI remains valid for the entire duration:

    INSIDE → OUTSIDE

============================================================
UNKNOWN STATE
============================================================

UNKNOWN must NOT automatically mean OUTSIDE.

This is important.

UNKNOWN can occur when:

    BLE device disappears
    Bluetooth is disabled
    scan fails
    insufficient RSSI samples
    app starts and has no previous state
    device cannot be identified
    RSSI confidence is too low

Default safety behavior:

    UNKNOWN
        → DO NOT CHANGE CURRENT SAMSUNG MODE

The app should preserve the last known state rather than
unnecessarily toggling the Mode.

============================================================
STARTUP BEHAVIOR
============================================================

When the application starts:

    do not immediately toggle Samsung Mode.

First:

    start BLE scanning
    collect samples
    establish confidence
    determine state

Only then perform a Mode transition if appropriate.

Example:

    Current Samsung Mode:
        OFF

    Proximity:
        UNKNOWN

    Action:
        NONE

After sufficient data:

    Proximity:
        INSIDE

    Action:
        START MODE

============================================================
MODE PROFILE
============================================================

Create a configuration object:

    ProximityAutomationProfile

containing:

    profileName

    bleDeviceProfile

    samsungModeUuid

    enterThreshold

    exitThreshold

    enterDuration

    exitDuration

    smoothingMethod

    smoothingWindow

    minimumSamples

    unknownTimeout

    enabled

Example:

    Bedroom Focus

    BLE:
        SmartTag Bedroom

    Samsung Mode:
        Bedroom Focus

    ENTER:
        -64 dBm

    EXIT:
        -69 dBm

    ENTER duration:
        5 sec

    EXIT duration:
        10 sec

============================================================
AUTOMATION
============================================================

Add a master switch:

    Automation:
        ON / OFF

When OFF:

    BLE monitoring may continue.

    Samsung Mode MUST NOT be changed automatically.

When ON:

    proximity state transitions can control Samsung Mode.

============================================================
MODE TRANSITION LOGIC
============================================================

This is critical.

When:

    OUTSIDE → INSIDE

call:

    SamsungModeController.startMode(uuid)

When:

    INSIDE → OUTSIDE

call:

    SamsungModeController.stopMode(uuid)

When:

    UNKNOWN

do nothing.

Do NOT call START repeatedly while already INSIDE.

Do NOT call STOP repeatedly while already OUTSIDE.

Only act on actual state transitions.

============================================================
MODE STATE SYNCHRONIZATION
============================================================

At startup and periodically, reconcile:

    proximity state

with:

    Samsung Mode state

Do not blindly assume they match.

Example:

    proximity:
        INSIDE

    Samsung Mode:
        OFF

Then:

    startMode()

Example:

    proximity:
        OUTSIDE

    Samsung Mode:
        ON

Then:

    stopMode()

But avoid fighting the user.

If the user manually changes the Mode, record it in the log.

============================================================
MANUAL OVERRIDE
============================================================

Add:

    [ PAUSE AUTOMATION ]

When automation is paused:

    BLE scanning can continue.

    No automatic Mode changes occur.

Provide:

    [ RESUME AUTOMATION ]

When resumed:

    re-evaluate proximity

    reconcile Samsung Mode

Do not immediately toggle blindly.

============================================================
EMERGENCY CONTROL
============================================================

Provide:

    [ DISABLE AUTOMATION ]

and:

    [ STOP SAMSUNG MODE ]

The latter explicitly attempts to stop the configured Mode.

============================================================
BACKGROUND OPERATION
============================================================

The final application must work while:

    screen is off
    phone is locked
    application is not in foreground

Research and implement the appropriate Android background BLE
architecture for Android 16.

Do NOT rely on a continuously running foreground Activity.

Use an appropriate:

    Foreground Service

where required.

The user must be clearly informed why the service is running.

Example notification:

    Bedroom Proximity
    Monitoring beacon proximity

The notification should show:

    current profile
    current proximity state
    automation state

============================================================
ANDROID PERMISSIONS
============================================================

Implement the correct Android 16 Bluetooth permissions.

Handle:

    BLUETOOTH_SCAN
    BLUETOOTH_CONNECT

and any other permission actually required by the chosen scanning
implementation.

Request permissions gracefully.

Do not request unrelated permissions.

Explain to the user why Bluetooth access is required.

============================================================
BATTERY
============================================================

Battery efficiency is important.

Do NOT continuously scan at maximum intensity forever.

Research Android BLE scanning best practices.

Use appropriate:

    scan settings
    scan intervals
    batching if appropriate
    filtering where possible

The system should prioritize reliable proximity detection over
extreme battery savings, but avoid wasteful scanning.

Make scanning configuration adjustable for debugging.

Example modes:

    Battery Saver
    Balanced
    High Reliability

Default:

    Balanced

============================================================
BLE LOST DEVICE HANDLING
============================================================

A beacon disappearing does NOT automatically mean OUTSIDE.

Example:

    INSIDE

    SmartTag temporarily disappears for 3 seconds.

Remain:

    INSIDE

Only transition to OUTSIDE after the configured lost-device
timeout AND sufficient evidence exists.

Default:

    30 seconds

Make configurable.

============================================================
RSSI CONFIDENCE
============================================================

Create a confidence metric.

Example factors:

    sample count
    RSSI variance
    recentness
    distance from threshold
    beacon visibility

Display:

    Confidence:
        92%

This is primarily a diagnostic/UI feature initially.

The state machine may use it to reject extremely weak evidence.

============================================================
MULTIPLE BEACONS — FUTURE READY
============================================================

The architecture must support multiple BLE devices eventually.

For now one profile may use one beacon.

Design:

    ProximityProfile
        contains one or more BeaconProfiles

Future example:

    Bedroom:
        SmartTag
        Dedicated BLE beacon

Possible aggregation:

    ANY
    ALL
    WEIGHTED

Do not implement complicated multi-beacon logic unless easy.

Just keep the architecture extensible.

============================================================
SMARTTAG VS DEDICATED BEACON
============================================================

Do NOT treat the SmartTag as a special permanent dependency.

The user should eventually be able to:

    Add BLE device

Then choose:

    Samsung SmartTag
    Generic BLE Beacon

The same proximity engine must work for both.

The hardware-specific layer should only handle identification/parsing.

============================================================
RSSI DATA STORAGE
============================================================

Persist calibration profiles.

Store:

    profile name
    BLE device identity
    RSSI statistics
    thresholds
    hysteresis
    timing values
    Samsung Mode UUID
    automation state

Use an appropriate local persistence mechanism.

Do not store unnecessary personal data.

Calibration data should survive:

    app restart
    phone reboot

============================================================
EVENT LOG
============================================================

Create an automation log.

Example:

    22:41:03
    RSSI: -57
    State: INSIDE candidate

    22:41:08
    Proximity:
        OUTSIDE → INSIDE

    22:41:08
    Samsung Mode:
        START requested

    22:41:09
    Samsung Mode:
        START verified

Then:

    23:18:44
    RSSI: -71

    23:18:54
    Proximity:
        INSIDE → OUTSIDE

    23:18:54
    Samsung Mode:
        STOP requested

This log is extremely important for debugging false triggers.

============================================================
LIVE DEBUG SCREEN
============================================================

Create:

    Proximity Debugger

Show:

    BLE device
    RSSI
    filtered RSSI
    rolling average
    median
    standard deviation
    ENTER threshold
    EXIT threshold
    current state
    candidate state
    candidate timer
    confidence
    last beacon seen
    Samsung Mode state
    automation state

Also show a live graph.

============================================================
SIMULATION MODE
============================================================

Add a developer-only simulation mode.

Allow manually entering RSSI values or replaying recorded RSSI
samples.

Example:

    -55
    -57
    -58
    -60
    -62
    -63
    -65
    -70

This allows testing the state machine without physically moving
around.

Add:

    [ SIMULATE INSIDE ]

    [ SIMULATE OUTSIDE ]

    [ SIMULATE RSSI SEQUENCE ]

The simulation MUST NOT accidentally activate the real Samsung Mode
unless the user explicitly enables:

    "Allow simulation to control Samsung Mode"

Default:

    OFF

============================================================
CALIBRATION QUALITY
============================================================

After calibration, calculate separation quality.

Display something like:

    INSIDE median:
        -57 dBm

    OUTSIDE median:
        -73 dBm

    Median separation:
        16 dB

Also report whether the distributions overlap significantly.

Example:

    GOOD SEPARATION

or:

    MODERATE SEPARATION

or:

    POOR SEPARATION

If poor:

    "The measured RSSI ranges overlap heavily. Consider moving the
     beacon or using a dedicated BLE beacon."

Do not pretend RSSI can always provide reliable room-level
localization.

============================================================
ANTI-FLAPPING
============================================================

The system must be resistant to:

    RSSI spikes
    human body blocking
    phone orientation changes
    temporary beacon loss
    multipath reflections
    walking near the boundary

Use:

    filtering
    hysteresis
    temporal persistence
    lost-device timeout

Do not solve this simply by increasing the threshold.

============================================================
IMPORTANT: DO NOT USE BLUETOOTH CLASSIC
============================================================

This system is BLE-based.

Use:

    BluetoothLeScanner

and BLE advertisements.

Do not implement Bluetooth Classic discovery.

============================================================
ARCHITECTURE
============================================================

Recommended:

    ble/
        BleScanner
        BleAdvertisement
        BleDeviceIdentifier
        BleDeviceProfile

    proximity/
        ProximityEngine
        ProximityState
        RssiFilter
        RssiStatistics
        CalibrationEngine
        ThresholdCalculator
        ProximityProfile

    automation/
        ProximityAutomationService
        RestrictionController

    samsung/
        EXISTING WORKING IMPLEMENTATION
        DO NOT BREAK

    storage/
        ProfileRepository

    ui/
        DeviceScannerScreen
        CalibrationScreen
        RssiMonitorScreen
        ProximityScreen
        AutomationScreen
        DiagnosticsScreen
        SettingsScreen

============================================================
CRITICAL SEPARATION OF CONCERNS
============================================================

BLE scanner must NOT know about Samsung Modes.

ProximityEngine must NOT know about Samsung Modes.

SamsungModeController must NOT know about BLE.

Automation layer connects them.

Correct:

    BLE
     ↓
    ProximityEngine
     ↓
    AutomationController
     ↓
    SamsungModeController

Incorrect:

    BLE scanner → Samsung API

============================================================
AUTOMATION CONTROLLER
============================================================

Implement:

    ProximityAutomationController

Responsibilities:

    observe ProximityState
    determine whether a state transition occurred
    start/stop the configured Samsung Mode
    prevent duplicate calls
    handle errors
    retry safely
    log transitions

Example:

    OUTSIDE → INSIDE

        startMode()

    INSIDE → OUTSIDE

        stopMode()

No action for:

    INSIDE → INSIDE
    OUTSIDE → OUTSIDE
    UNKNOWN → UNKNOWN
    UNKNOWN → INSIDE

unless startup synchronization explicitly requires it.

============================================================
FAILURE HANDLING
============================================================

If Samsung Mode START fails:

    do NOT continuously retry every RSSI sample.

Use controlled retries.

Example:

    retry after 5 sec
    retry after 15 sec
    retry after 30 sec

Then stop retrying temporarily and report the failure.

If STOP fails:

    use the same controlled retry mechanism.

Never cause a retry storm.

============================================================
USER EXPERIENCE
============================================================

Main screen should eventually look approximately like:

    BEDROOM FOCUS

    ● Monitoring

    Beacon:
        Bedroom SmartTag

    RSSI:
        -58 dBm

    Proximity:
        INSIDE

    Samsung Mode:
        ACTIVE

    Automation:
        ON

--------------------------------

    [ PAUSE ]

    [ CALIBRATE ]

    [ RSSI MONITOR ]

    [ SETTINGS ]

--------------------------------

    Last transition:
        OUTSIDE → INSIDE
        22:41:08

============================================================
SETUP WIZARD
============================================================

Create a guided setup:

    1. Add BLE device

    2. Name it

    3. Create profile

    4. Calibrate OUTSIDE

    5. Calibrate INSIDE

    6. Review RSSI separation

    7. Choose Samsung Mode

    8. Configure thresholds

    9. Test

    10. Enable automation

At the end:

    "Setup complete."

============================================================
TEST MODE
============================================================

Before enabling real automation, provide:

    TEST PROXIMITY

This allows the user to move between inside/outside and observe:

    RSSI
    filtered RSSI
    state
    timers
    expected Samsung Mode action

without actually controlling the Mode.

Then:

    TEST AUTOMATION

allows real Mode control.

============================================================
SAFETY
============================================================

Never automatically modify Samsung Mode configuration.

Only:

    start
    stop
    toggle if absolutely necessary

using the EXISTING working SamsungModeController.

The user explicitly chooses which Mode is controlled.

Do not create or modify Samsung Modes automatically.

============================================================
MULTIPLE PROFILES
============================================================

Allow multiple profiles.

Example:

    Bedroom
    Office
    Study

Each profile can have:

    BLE device
    calibration
    thresholds
    Samsung Mode UUID

Only one automation profile needs to be active initially.

Architecture should support multiple profiles later.

============================================================
PERSISTENCE
============================================================

Persist:

    selected profile
    automation enabled/disabled
    calibration data
    thresholds
    Samsung Mode UUID
    beacon identity

Do NOT persist large raw RSSI histories indefinitely.

Optionally allow:

    Export calibration data

for debugging.

============================================================
EXPORT / DEBUG
============================================================

Add:

    Export diagnostic report

containing:

    device information
    Android version
    One UI version
    Modes & Routines version
    BLE device information
    calibration statistics
    thresholds
    state transitions
    Samsung Mode operations
    errors

Do NOT include sensitive information unnecessarily.

============================================================
BACKGROUND SERVICE
============================================================

When automation is enabled:

    the proximity monitoring system must remain functional when:

        app is backgrounded
        screen is off
        phone is locked

Implement the appropriate Android 16 architecture.

Do not rely on an Activity remaining alive.

Handle:

    service lifecycle
    Bluetooth disabled
    permission revoked
    app process restart
    phone reboot if appropriate

============================================================
REBOOT BEHAVIOR
============================================================

Eventually support restoring automation after reboot.

At minimum:

    persist automation state.

If automation was ON before reboot:

    restore monitoring after required Android permissions/services
    are available.

Do not immediately toggle Samsung Mode before proximity is known.

Start in:

    UNKNOWN

collect evidence first.

============================================================
BLUETOOTH OFF
============================================================

If Bluetooth is disabled:

    state = UNKNOWN

Display:

    "Bluetooth is disabled."

Do NOT automatically stop the Samsung Mode merely because Bluetooth
is temporarily unavailable.

Do NOT assume UNKNOWN = OUTSIDE.

============================================================
APP FORCE STOP
============================================================

Document Android limitations around force-stopping the app.

Do not attempt to circumvent Android's force-stop behavior.

============================================================
PERFORMANCE
============================================================

Avoid unnecessary recomposition or object allocation.

Use:

    Kotlin coroutines
    Flow / StateFlow

for reactive state.

The BLE scanner should expose data through a clean stream.

Example:

    Flow<BleObservation>

The ProximityEngine consumes observations.

============================================================
TESTS
============================================================

Write unit tests for:

    RSSI smoothing
    median
    moving average
    standard deviation
    percentile calculation
    threshold calculation
    hysteresis
    enter timer
    exit timer
    lost beacon timeout
    UNKNOWN behavior
    duplicate transition suppression
    Samsung Mode retry logic

Create deterministic tests such as:

    OUTSIDE RSSI:
        -72, -73, -71, -75, -70

    INSIDE RSSI:
        -56, -58, -57, -60, -55

Verify the expected state transitions.

============================================================
END-TO-END TEST
============================================================

Provide a manual test procedure:

    1. Create Samsung Mode "Bedroom Focus".
    2. Configure Restrict app usage.
    3. Select SmartTag.
    4. Calibrate OUTSIDE.
    5. Calibrate INSIDE.
    6. Review separation.
    7. Enable TEST mode.
    8. Walk outside.
    9. Walk inside.
    10. Observe RSSI.
    11. Observe state transition.
    12. Enable automation.
    13. Walk inside.
    14. Verify Samsung Mode activates.
    15. Walk outside.
    16. Wait for exit timer.
    17. Verify Samsung Mode deactivates.

============================================================
IMPORTANT: DON'T OVERFIT THE RSSI
============================================================

Do not assume:

    -60 dBm = inside

or:

    -70 dBm = outside

Those values must come from calibration.

The app must learn the user's actual environment.

RSSI is affected by:

    walls
    doors
    human bodies
    phone orientation
    beacon orientation
    furniture
    reflections
    Wi-Fi interference
    device hardware

Therefore calibration data is more important than universal thresholds.

============================================================
DEDICATED BEACON FUTURE
============================================================

Make it easy to replace the SmartTag later.

I intend to potentially purchase a dedicated BLE beacon after the
prototype works.

Therefore I should be able to:

    Add Device
        ↓
    Scan
        ↓
    Select beacon
        ↓
    Save
        ↓
    Calibrate
        ↓
    Use exactly the same proximity engine

No rewrite should be required.

============================================================
DO NOT BREAK EXISTING SAMSUNG POC
============================================================

The existing Samsung Mode POC is already working.

Before modifying anything:

    inspect the existing code.

Do not rewrite the Samsung controller.

Do not change the existing One UI 8.0 implementation unless required.

Do not change the existing One UI 8.5 implementation unless required.

The new automation layer should call the existing abstraction.

============================================================
DEVELOPMENT ORDER
============================================================

Implement in this exact order:

    1. BLE scanner

    2. BLE diagnostics

    3. SmartTag identification/profile

    4. RSSI monitor

    5. RSSI statistics

    6. Calibration engine

    7. Threshold/hysteresis engine

    8. Proximity state machine

    9. Persistence

    10. Automation controller

    11. Connect automation controller to EXISTING
        SamsungModeController

    12. Background monitoring

    13. Setup wizard

    14. Simulation/testing tools

    15. Final UI polish

After each major step:

    compile
    run tests
    verify on the physical phone

============================================================
FIRST DELIVERABLE
============================================================

Do not attempt to build everything in one step.

Start by implementing:

    BLE scanner
    BLE diagnostic screen
    RSSI monitor

Then verify that the Samsung SmartTag 1 appears and RSSI updates
continuously.

After that is confirmed, proceed to calibration.

============================================================
FINAL SUCCESS CRITERIA
============================================================

The finished application must allow me to do this:

    1. Select my Samsung SmartTag 1.

    2. Create a profile called "Bedroom".

    3. Stand outside the room.

    4. Press:
           "I'm OUTSIDE"

       and have the app collect RSSI.

    5. Enter the room.

    6. Press:
           "I'm INSIDE"

       and have the app collect RSSI.

    7. App calculates the RSSI distributions.

    8. App proposes ENTER/EXIT thresholds.

    9. I can manually adjust them.

    10. I select my existing Samsung Mode.

    11. I enable automation.

    12. When I enter the room:

            BLE RSSI
              ↓
            filtering
              ↓
            proximity engine
              ↓
            INSIDE
              ↓
            Samsung Mode START
              ↓
            Samsung "Restrict app usage"

    13. When I leave:

            BLE RSSI
              ↓
            filtering
              ↓
            hysteresis
              ↓
            exit timer
              ↓
            OUTSIDE
              ↓
            Samsung Mode STOP

    14. Temporary RSSI fluctuations do NOT cause mode flapping.

    15. Temporary BLE loss does NOT immediately trigger OUTSIDE.

    16. Screen-off/background operation continues.

    17. The same application works with a future dedicated BLE beacon.

============================================================
MOST IMPORTANT DESIGN PRINCIPLE
============================================================

Think of this application as TWO completely separate systems:

SYSTEM A:

    BLE PROXIMITY ENGINE

    BLE
      ↓
    RSSI
      ↓
    calibration
      ↓
    filtering
      ↓
    hysteresis
      ↓
    INSIDE / OUTSIDE

SYSTEM B:

    SAMSUNG MODE CONTROLLER

    START MODE
    STOP MODE

The only connection between them is:

    ProximityAutomationController

Do not tightly couple them.

This separation is essential because the BLE hardware may change
from Samsung SmartTag 1 to a dedicated beacon later, while the
Samsung Mode implementation should remain unchanged.

============================================================
BEGIN
============================================================

Inspect the existing project first.

Preserve the working Samsung Modes POC.

Then implement ONLY the BLE scanner + diagnostic RSSI monitor as
the first increment.

Compile and test before proceeding to calibration.
