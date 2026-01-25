## ADDED Requirements

### Requirement: Explicit arming before motor start
The system SHALL require an explicit user action to arm a session before it sends any motor-start command to the device.

#### Scenario: User runs in safe observe mode
- **WHEN** the user has not armed a session
- **THEN** the system does not send motor-start (`0x03`) even if handle detection conditions are met

### Requirement: Always-available stop actions
The system SHALL provide an immediate soft stop action and an immediate reset/hard stop action that are available in all session states.

#### Scenario: User triggers an emergency stop
- **WHEN** the user triggers soft stop
- **THEN** the system sends the soft stop command (`0x50 0x00`) without waiting on session transitions

### Requirement: Stop behavior on telemetry loss
When the session is armed, the system SHALL automatically send a soft stop command on BLE disconnect or telemetry stall, and SHALL return to a safe non-armed state.

#### Scenario: Telemetry stalls mid-set
- **WHEN** the session is armed and monitor telemetry stalls or the device disconnects
- **THEN** the system sends soft stop (`0x50 0x00`) and disarms the session

### Requirement: Hands-free session progression after pickup
After the session is armed, the system SHALL allow the user to complete the “Just Lift / Old School” workflow without requiring further keypresses after handle pickup.

#### Scenario: User starts and lifts hands-free
- **WHEN** the user arms the session before picking up the handles
- **THEN** the system progresses through ready/calibration/active/ending automatically based on telemetry

### Requirement: BLE reliability constraints
The system SHALL enforce strict single-flight GATT operations and SHALL apply timeouts to reads/writes/notification configuration.

#### Scenario: Repeated monitor read timeouts
- **WHEN** monitor reads exceed the timeout repeatedly
- **THEN** the system disconnects cleanly and returns to a safe non-armed state

### Requirement: “Just Lift” program parameters for AMRAP
When starting a “Just Lift” set, the system SHALL configure the program parameters frame (`0x04`) so that reps are set to infinite/AMRAP (`0xFF`), and SHALL apply the configured per-cable weight.

#### Scenario: User starts an AMRAP set
- **WHEN** the user starts the set while armed
- **THEN** the system writes program parameters (`0x04`) with reps set to `0xFF` and per-cable weight configured

### Requirement: Telemetry parsing and event emission
The system SHALL parse monitor telemetry into typed fields (position and load per cable) and SHALL emit timestamped events suitable for unit testing of the session controller.

#### Scenario: Valid monitor frame received
- **WHEN** a valid monitor frame is received from the device
- **THEN** the system emits a telemetry event containing parsed pos/load fields

### Requirement: Dry-run mode
The system SHALL provide a dry-run mode that never sends motor-start (`0x03`) commands, regardless of session state.

#### Scenario: Developer runs dry-run
- **WHEN** dry-run mode is enabled
- **THEN** all attempted motor-start actions are blocked and recorded as “suppressed”

#### Scenario: User triggers start while in dry-run
- **WHEN** dry-run mode is enabled and the session would otherwise start motors
- **THEN** the system does not send motor-start (`0x03`) and indicates “suppressed start” in the UI/logs

### Requirement: Per-cable weight constraints
The system SHALL constrain user-configurable per-cable weight to the range `10–100 lb` in increments of `1 lb`.

#### Scenario: User enters an out-of-range weight
- **WHEN** the user enters a weight below `10 lb` or above `100 lb`
- **THEN** the system rejects the value and preserves the last valid weight
