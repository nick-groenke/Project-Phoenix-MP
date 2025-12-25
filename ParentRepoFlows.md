Based on a code review of the `vitruvianprojectphoenix` repository (specifically `MainViewModel.kt`, `BleRepositoryImpl.kt`, `ProtocolBuilder.kt`, and `RepCounterFromMachine.kt`), here is a comprehensive list of every flow within the app that invokes BLE protocols.

### 1. Device Connection Flow

**Description:** The sequence initiated to scan for, identify, and connect to a Vitruvian device using the standard Android BLE stack (via `VitruvianBleManager`).

* **Step 1: Initiation**
* `MainViewModel.ensureConnection()` is called.
* It checks current state; if not connected, calls `startScanning()`.


* **Step 2: Scanning**
* `BleRepositoryImpl.startScanning()` initializes the Android `BluetoothLeScanner`.
* **Code:** `scanner.startScan(null, scanSettings, scanCallback)`
* **Filter:** The `scanCallback` filters results for devices starting with "Vee_" or "VIT" (`BleConstants.DEVICE_NAME_PREFIX`). Matches are emitted to `_scannedDevices`.


* **Step 3: Connection Trigger**
* `MainViewModel` picks the first found device and calls `connectToDevice(address)`.


* **Step 4: Connection & Cleanup**
* `BleRepositoryImpl.connectToDevice` executes a critical cleanup first.
* **Code:** `bleManager?.stopPolling()`, `cleanup()`, `close()` are called on any existing manager to prevent "dangling GATT connections" on Android 16/Pixel 7.
* A new `VitruvianBleManager` is instantiated.


* **Step 5: GATT Connection**
* `bleManager.connect(device)` is called with a 15-second timeout and retry logic.
* The flow waits for `ConnectionStatus.Ready`, indicating service discovery is complete.



### 2. Just Lift: Preparation (Handle Detection) Flow

**Description:** The passive monitoring state entered when the user selects "Just Lift" but has not started the workout. This allows the app to detect when handles are grabbed without starting the motor.

* **Step 1: Enable Detection**
* `MainViewModel` calls `bleRepository.enableHandleDetection()` (or `enableJustLiftWaitingMode`).


* **Step 2: Monitor Polling**
* `BleRepositoryImpl` starts a polling loop on the `MONITOR_CHAR_UUID` (28 bytes).


* **Step 3: Baseline Construction**
* `MainViewModel` collects `bleRepository.monitorData`.
* **Code:** `repCounter.updatePositionRangesContinuously(metric.positionA, metric.positionB)`
* **Purpose:** Since no rep events fire in this state, the app manually tracks min/max positions to establish a "meaningful range" baseline for later auto-stop logic.


* **Step 4: State Analysis**
* `BleRepository` (or Manager) analyzes position/velocity to update `handleState` (WaitingForRest -> Released -> Grabbed).



### 3. Just Lift: Auto-Start Trigger Flow

**Description:** The transition from Idle to Active triggered by user movement.

* **Step 1: Grab Detection**
* `BleRepository` emits `HandleState.Grabbed` based on velocity/position thresholds.


* **Step 2: UI Countdown**
* `MainViewModel` observes the state change.
* **Code:** `startAutoStartTimer()` initiates a 5-second coroutine countdown (`_autoStartCountdown` 5..1).


* **Step 3: Launch**
* If the timer completes without handles being released, `startWorkout(isJustLiftMode = true)` is called.



### 4. Workout Start Flow

**Description:** The command sequence constructed by `ProtocolBuilder` to configure the machine and engage the motors.

* **Step 1: Init Command**
* `MainViewModel` calls `bleRepository.startWorkout`.
* `BleRepositoryImpl` calls `sendInitSequence`.
* **Command:** `0x0A` (RESET) built by `ProtocolBuilder.buildInitCommand()`.


* **Step 2: Configuration Packet Construction**
* `BleRepositoryImpl` calls `ProtocolBuilder.buildProgramParams(params)`.
* **Packet Structure:** A 96-byte byte array.
* **Command Header:** `0x04` (PROGRAM mode).
* **Reps Field:** Set to `0xFF` (Unlimited) for Just Lift/AMRAP, or specific count otherwise.
* **Profile Mode:** Maps specific modes (e.g., OldSchool, TimeUnderTension) to the protocol bytes.
* **Weight Calculation:** Adjusts `weightPerCableKg` by subtracting `progressionRegressionKg` to handle firmware quirks regarding "rep 0" progression application.


* **Step 3: Send Configuration**
* `bleManager.sendCommand(command)` writes the 96-byte packet to `NUS_RX_CHAR_UUID`.


* **Step 4: Start Command**
* **Command:** `0x03` (START).
* `bleManager.sendCommand` sends this to engage the motors.


* **Step 5: Polling**
* `bleManager.startMonitorPolling()` is called immediately to begin the data loop.



### 5. Active Monitoring & Rep Counting Flow

**Description:** The runtime loop handling metrics and rep logic.

* **Step 1: Monitor Polling**
* The `VitruvianBleManager` polls `MONITOR_CHAR_UUID` continuously.
* Data flows to `MainViewModel` -> `handleMonitorMetric`.


* **Step 2: Rep Notifications (Primary)**
* The machine sends notifications on `REPS_CHAR_UUID` (24 bytes).
* `MainViewModel` collects `bleRepository.repEvents`.
* **Code:** `handleRepNotification(repNotification)` calls `repCounter.process`.
* **Logic:** It supports both "Modern" (using `repsSetCount` from packet) and "Legacy" (using `topCounter` increments) counting methods.


* **Step 3: Haptic Feedback**
* `repCounter.onRepEvent` triggers `_hapticEvents.emit` for events like `REP_COMPLETED` or `WORKOUT_COMPLETE`.



### 6. Auto-Stop Flow (Velocity & Position)

**Description:** A dual-check system to stop the machine when the user struggles or finishes.

* **Step 1: Velocity Stall Detection (Primary)**
* `MainViewModel.checkAutoStop` calculates `maxVelocity`.
* **Thresholds:** Stalled < 2.5 (`STALL_VELOCITY_LOW`), Moving > 10.0 (`STALL_VELOCITY_HIGH`).
* **Logic:** A hysteresis timer starts if velocity < 2.5. If it remains low for 5 seconds (`STALL_DURATION_SECONDS`), `triggerAutoStop()` is called.


* **Step 2: Position-Based Detection (Secondary)**
* Checks `repCounter.isInDangerZone` (bottom 5% of ROM).
* Checks if cables are "released" (Position < 2.5mm or near min range).
* **Logic:** If both are true for 2.5 seconds, auto-stop triggers.



### 7. Workout Stop Flow

**Description:** Disengaging the machine while ensuring it resets correctly for the next set.

* **Step 1: Command Construction**
* `MainViewModel` calls `bleRepository.stopWorkout()` (or `sendStopCommand` for Just Lift).
* **Command:** `ProtocolBuilder.buildOfficialStopPacket()` generates the `0x03` (STOP) command.


* **Step 2: Send & Poll (Just Lift Special Case)**
* If in Just Lift mode, the app uses `sendStopCommand`.
* **Critical Detail:** It sends the stop packet *without* stopping the monitor polling loop.
* **Reasoning:** "StopPacket sent - polling still active for quick machine response." The machine requires active polling to process the stop command and exit the fault state (red lights) quickly.


* **Step 3: Standard Stop**
* For other modes, `stopWorkout()` stops polling via `bleManager.stopPolling()` after sending the command.



### 8. Echo Mode Force Feedback Flow

**Description:** Real-time visualization of force data specific to "Echo" (Isokinetic) mode.

* **Step 1: Heuristic Polling**
* `VitruvianBleManager` subscribes/polls `HEURISTIC_CHAR_UUID`.


* **Step 2: Data Parsing**
* The app extracts `concentric.kgMax` and `eccentric.kgMax` from the 48-byte Little Endian payload.


* **Step 3: Live Update**
* `MainViewModel` collects `bleRepository.heuristicData`.
* **Code:** `_currentHeuristicKgMax.value = currentMax`.
* This updates the live "Force" gauge in the UI, as Echo mode resistance varies by user effort.