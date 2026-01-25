# Vitruvian BLE Protocol (Project Phoenix notes)

This document describes the BLE/GATT protocol workflow used by Project Phoenix to talk to Vitruvian V-Form / Trainer+ machines. It is based on reverse-engineering and on the reference implementation in this repo (not an official spec). Firmware variants exist; be prepared to observe and adapt.

Safety note: these devices can generate large forces. Always provide an obvious, immediate “stop” UX, conservative defaults, and robust failure handling.

## Scope

This document covers:

- How to discover and connect to the device over BLE
- Which services/characteristics matter
- How telemetry is delivered (polling + notifications)
- How control commands are written (frame formats + sequences)

Reference implementation files:

- BLE transport + polling: `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/KableBleRepository.kt`
- Frame building: `shared/src/commonMain/kotlin/com/devil/phoenixproject/util/BlePacketFactory.kt`
- UUID list + protocol constants: `shared/src/commonMain/kotlin/com/devil/phoenixproject/util/BleConstants.kt`
- Status bit masks: `shared/src/commonMain/kotlin/com/devil/phoenixproject/domain/model/SampleStatus.kt`

## Device discovery (scanning)

Project Phoenix scans with no hard BLE filter and then filters advertisements in software.

### Name-based filtering

If the advertisement has a name, treat it as Vitruvian if it matches one of:

- `Vee_*` (V-Form family)
- `VIT*` (Trainer+ family)
- `Vitruvian*` (some variants)

See `startScanning()` in `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/KableBleRepository.kt`.

### UUID/service-data filtering

If the advertisement has no useful name, Project Phoenix also checks:

- Service UUID contains `0000fef3-0000-1000-8000-00805f9b34fb` (often present)
- Or the NUS service UUID `6e400001-b5a3-f393-e0a9-e50e24dcca9e`
- Or “FEF3” appears in *service data* (important: on some devices it is in serviceData, not serviceUuids)

## GATT layout (services & characteristics)

Project Phoenix treats the device as “NUS-like service + custom characteristics”.

### Primary service

- **Service (NUS-like)**: `6e400001-b5a3-f393-e0a9-e50e24dcca9e`

### Command write characteristic

- **TX write**: `6e400002-b5a3-f393-e0a9-e50e24dcca9e`
  - The app writes binary “frames” here.
  - Use **Write With Response** (some devices do not support Write Without Response).
  - See `sendWorkoutCommand()` in `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/KableBleRepository.kt`.

Note: some codebases call this UUID “RX” because it is *device RX* (host TX).

### Telemetry characteristics (custom)

All are under the NUS-like service above:

| Characteristic | UUID | Direction | Delivery |
|---|---|---|---|
| Monitor/Sample | `90e991a6-c548-44ed-969b-eb541014eae3` | device → app | **poll via read()** (NOT notifiable) |
| Reps | `8308f2a6-0875-4a94-a86f-5c5c5e1b068a` | device → app | **notify** |
| Diagnostic/Property | `5fa538ec-d041-42f6-bbd6-c30d475387b7` | device → app | **poll via read()** |
| Heuristic | `c7b73007-b245-4503-a1ed-9e4e97eb9802` | device → app | **poll via read()** |
| Version | `74e994ac-0e80-4c02-9cd0-76cb31d3959b` | device → app | notify (best-effort) |
| Mode | `67d0dae0-5bfc-4ea2-acc9-ac784dee7f29` | device → app | notify (best-effort) |

Project Phoenix also keeps references to other known-but-not-required UUIDs in `shared/src/commonMain/kotlin/com/devil/phoenixproject/util/BleConstants.kt` (e.g., per-cable 6-byte characteristics, wifi state, auth-ish characteristic).

### Standard DIS firmware revision (optional)

Project Phoenix tries (best-effort) to read firmware revision from the standard Device Information Service:

- **DIS service**: `0000180a-0000-1000-8000-00805f9b34fb`
- **Firmware Revision String**: `00002a26-0000-1000-8000-00805f9b34fb`

Failure to read is expected on some devices.

## Transport requirements & robustness

### MTU & connection priority (Android)

Project Phoenix requests:

- High connection priority (Android-only)
- MTU `247` (Android-only; iOS negotiates automatically)

See `onDeviceReady()` in `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/KableBleRepository.kt` and the expect/actual implementations in:

- `shared/src/androidMain/kotlin/com/devil/phoenixproject/data/ble/BleExtensions.android.kt`
- `shared/src/iosMain/kotlin/com/devil/phoenixproject/data/ble/BleExtensions.ios.kt`

### Single-flight GATT operations

Do not run multiple reads/writes concurrently. Use a single operation queue (mutex/serial executor). Project Phoenix uses a mutex around the monitor polling loop to avoid overlapping polling sessions.

### Timeouts & “hung BLE stack” handling

Project Phoenix uses timeouts on reads (e.g. `1500ms` for monitor reads). After too many consecutive timeouts, it disconnects and requires reconnection.

### Heartbeat

Project Phoenix keeps the connection alive with a 2-second heartbeat:

1) Try a `read()` on the Monitor characteristic (safe/readable) with timeout.
2) If that fails, write a 4-byte no-op `00 00 00 00` to the TX characteristic using Write With Response.

See `startHeartbeat()` in `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/KableBleRepository.kt`.

## Telemetry workflows

Project Phoenix consumes three main streams:

- **High-rate samples** (position/load) from Monitor via polling
- **Rep events** from Reps characteristic notifications
- **Diagnostics & force telemetry** via low-rate polling (Diagnostic + Heuristic)

### Monitor / Sample characteristic (polled)

Read `90e991a6-c548-44ed-969b-eb541014eae3` in a loop with a timeout. Project Phoenix does **no fixed delay on success**; the BLE response time naturally rate-limits (often ~10–20ms).

Minimum required payload for Project Phoenix parsing is 16 bytes; it also reads an optional 2-byte status word at bytes 16–17. Some firmware variants may return longer payloads (commonly referenced as 28 bytes); ignore trailing bytes until you understand them.

#### Sample format (little-endian, as used by Project Phoenix)

Offsets are 0-based:

- `0..1` `ticks_lo` (u16)
- `2..3` `ticks_hi` (u16)
- `4..5` `posA_raw` (i16, signed) → `posA = posA_raw / 10.0`
- `6..7` (unknown/unused by Project Phoenix; likely velocityA_raw)
- `8..9` `loadA_raw` (u16) → `loadA_kg = loadA_raw / 100.0`
- `10..11` `posB_raw` (i16, signed) → `posB = posB_raw / 10.0`
- `12..13` (unknown/unused by Project Phoenix; likely velocityB_raw)
- `14..15` `loadB_raw` (u16) → `loadB_kg = loadB_raw / 100.0`
- `16..17` `status` (u16) optional (if present)

Derived:

- `ticks = ticks_lo + (ticks_hi << 16)` (u32)

See `parseMonitorData()` in `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/KableBleRepository.kt`.

Note: Many parts of the repo historically label `posA/posB` as “mm”. Empirically (Go spike on macOS), `pos` appears to behave like **centimeters** when using `pos_raw / 10.0` (e.g., pulling >1m yields `pos≈171.6`). Treat units as “position” unless validated for your hardware/firmware.

#### Status flags (Monitor bytes 16–17)

Project Phoenix interprets the 16-bit status word using these masks (see `shared/src/commonMain/kotlin/com/devil/phoenixproject/domain/model/SampleStatus.kt`):

| Mask | Name | Meaning |
|---:|---|---|
| `0x0001` | `REP_TOP_READY` | top-of-rep ready |
| `0x0002` | `REP_BOTTOM_READY` | bottom-of-rep ready |
| `0x0004` | `ROM_OUTSIDE_HIGH` | ROM above allowed range |
| `0x0008` | `ROM_OUTSIDE_LOW` | ROM below allowed range |
| `0x0010` | `ROM_UNLOAD_ACTIVE` | ROM unload active |
| `0x0020` | `SPOTTER_ACTIVE` | spotter active |
| `0x0040` | `DELOAD_WARN` | deload warning |
| `0x8000` | `DELOAD_OCCURRED` | deload occurred |

#### Rep/handle detection logic (optional)

Project Phoenix uses Monitor samples to implement Just Lift “auto start/stop” via a handle state machine. Thresholds used:

- Rest threshold: `pos < 5mm`
- Grab threshold: `pos > 8mm`
- “movement” velocity threshold: `50 mm/s` (normal)
- “auto-start” grab velocity threshold: `20 mm/s` (Just Lift auto-start)

These are not required to “talk to” the device, but are required to replicate Project Phoenix UX.

### Reps characteristic (notify)

Subscribe to notifications on `8308f2a6-0875-4a94-a86f-5c5c5e1b068a`.

#### Reps format (official 24-byte, little-endian, no opcode)

Offsets 0-based:

- `0..3` `upCounter` (u32) – concentric completions
- `4..7` `downCounter` (u32) – eccentric completions
- `8..11` `rangeTop` (f32 LE)
- `12..15` `rangeBottom` (f32 LE)
- `16..17` `repsRomCount` (u16) – warmup reps with proper ROM
- `18..19` `repsRomTotal` (u16) – warmup target / total regardless of ROM (varies by firmware)
- `20..21` `repsSetCount` (u16) – working set reps completed
- `22..23` `repsSetTotal` (u16) – working target

See `parseRepsCharacteristicData()` in `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/KableBleRepository.kt`.

#### Legacy reps format (6 bytes)

Some environments produce a legacy 6-byte payload:

- `0..1` `topCounter` (u16)
- `2..3` unused
- `4..5` `completeCounter` (u16)

### Diagnostic characteristic (polled, 500ms)

Read `5fa538ec-d041-42f6-bbd6-c30d475387b7` every `500ms` (Project Phoenix uses this as keep-alive + fault/temperature stream).

Project Phoenix parses:

- bytes `4..11` as 4 little-endian `u16` “fault codes”
- bytes `12..19` as 8 raw temperature bytes

See `parseDiagnosticData()` in `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/KableBleRepository.kt`.

### Heuristic characteristic (polled, 250ms / 4Hz)

Read `c7b73007-b245-4503-a1ed-9e4e97eb9802` every `250ms`.

Project Phoenix expects at least 48 bytes and parses 12 little-endian float32 values:

Concentric (offset 0):

- `0` `kgAvg`
- `4` `kgMax`
- `8` `velAvg`
- `12` `velMax`
- `16` `wattAvg`
- `20` `wattMax`

Eccentric (offset 24):

- `24` `kgAvg`
- `28` `kgMax`
- `32` `velAvg`
- `36` `velMax`
- `40` `wattAvg`
- `44` `wattMax`

See `parseHeuristicData()` in `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/KableBleRepository.kt`.

## Command frames (writes)

All commands in Project Phoenix are written to the TX characteristic `6e400002-...` using **Write With Response**.

Most multi-byte values in frames are **little-endian** unless otherwise noted.

Implementation reference: `shared/src/commonMain/kotlin/com/devil/phoenixproject/util/BlePacketFactory.kt`.

### Command summary

| Purpose | Bytes | Frame |
|---|---:|---|
| Start motors | 4 | `0x03 0x00 0x00 0x00` |
| Reset/Init (“hard stop”) | 4 | `0x0A 0x00 0x00 0x00` |
| Soft stop (“StopPacket”, clears faults, keep polling) | 2 | `0x50 0x00` |
| Program/activation params | 96 | command `0x04` + parameter block |
| Echo control params | 32 | command `0x4E` (u32 LE) + parameters |
| Legacy “regular” command (weight/reps only) | 25 | command `0x4F` + small parameter block |
| Legacy stop (not preferred) | 4 | `0x05 0x00 0x00 0x00` |
| Color scheme | 34 | command `0x11` (u32 LE) + brightness/colors |

Notes:

- Some references call the program/activation frame “97 bytes”; `BleConstants` also documents an “activation command” `0x04` with a 97-byte packet size. Project Phoenix’s working implementation uses a **96-byte** `0x04` frame (`createProgramParams()`).
- Some references document Echo as a 29-byte packet. Project Phoenix uses a **32-byte** `0x4E` frame (`createEchoControl()`).

### Workout start sequence (program modes)

To start a set (Old School / Pump / TUT / TUT Beast / Eccentric Only):

1. **Write program params** frame (96 bytes) built via `createProgramParams(params)`.
2. Wait a short delay (Project Phoenix uses ~`50ms`) to avoid congesting the BLE stack.
3. **Write Start** frame `03 00 00 00` to engage the motors.
4. Begin/continue Monitor polling + subscribe to Reps notifications.

Reference: `startWorkout()` flow in `shared/src/commonMain/kotlin/com/devil/phoenixproject/presentation/viewmodel/MainViewModel.kt`.

### Workout start sequence (Echo mode)

Echo mode uses a distinct control frame (`0x4E`, 32 bytes) built via `createEchoControl(...)`, then the same Start frame.

### Stopping

Project Phoenix uses two “stop” concepts:

- **Soft stop**: write `0x50 0x00` to release tension / clear fault state while keeping polling alive. Used heavily for Just Lift mode and fault clearing.
- **Hard stop/reset**: write `0x0A 00 00 00` to fully stop a workout; polling jobs are typically stopped afterwards.

There is also a legacy 4-byte stop command `0x05 00 00 00` in `BlePacketFactory.createStopCommand()`, but Project Phoenix prefers the official StopPacket `0x50 0x00` for “soft stop” behavior.

### Program params frame (96 bytes, command `0x04`)

Project Phoenix builds a 96-byte activation/program frame. Only a subset is “known variable”; most bytes are fixed constants plus a 32-byte “mode profile”.

#### Fixed fields (as implemented by Project Phoenix)

Outside of the mode profile and the weight/progression fields, Project Phoenix writes these fixed values:

- `0x00..0x03`: `04 00 00 00`
- `0x05..0x07`: `03 03 00`
- float32 LE `5.0` at `0x08`, `0x0C`, `0x1C`
- `0x14..0x1B`: `FA 00 FA 00 C8 00 1E 00`
- `0x24..0x2B`: `FA 00 FA 00 C8 00 1E 00`
- `0x2C..0x2F`: `FA 00 50 00`

Everything not explicitly written by the builder remains `0x00`.

These values come directly from `createProgramParams()` in `shared/src/commonMain/kotlin/com/devil/phoenixproject/util/BlePacketFactory.kt`.

Key offsets (0-based):

- `0x00..0x03`: header, with `0x04` at `0x00`
- `0x04`: reps byte
  - `0xFF` means “infinite” / Just Lift / AMRAP
  - otherwise `reps + warmupReps`
- `0x30..0x4F`: 32-byte “mode profile” block (varies by program mode)
- `0x54..0x57`: float32 LE “effectiveKg” (implementation uses `weightPerCable + 10.0`)
- `0x58..0x5B`: float32 LE “totalWeightKg” (implementation uses `weightPerCable`)
- `0x5C..0x5F`: float32 LE “progressionRegressionKg`

See `createProgramParams()` in `shared/src/commonMain/kotlin/com/devil/phoenixproject/util/BlePacketFactory.kt`.

#### Mode profile blocks

Project Phoenix encodes each program mode into a 32-byte profile with values written as little-endian shorts/floats at fixed offsets.

Offsets within the 32-byte profile:

- `0x00` `s16`
- `0x02` `s16`
- `0x04` `f32`
- `0x08` `s16`
- `0x0A` `s16`
- `0x0C` `f32`
- `0x10` `s16`
- `0x12` `s16`
- `0x14` `f32`
- `0x18` `s16`
- `0x1A` `s16`
- `0x1C` `f32`

Concrete values per mode are defined in `getModeProfile()` in `shared/src/commonMain/kotlin/com/devil/phoenixproject/util/BlePacketFactory.kt`.

For convenience, Project Phoenix’s current profile values are:

**Old School**

| Offset | Type | Value |
|---:|---|---:|
| `0x00` | s16 | `0` |
| `0x02` | s16 | `20` |
| `0x04` | f32 | `3.0` |
| `0x08` | s16 | `75` |
| `0x0A` | s16 | `600` |
| `0x0C` | f32 | `50.0` |
| `0x10` | s16 | `-1300` |
| `0x12` | s16 | `-1200` |
| `0x14` | f32 | `100.0` |
| `0x18` | s16 | `-260` |
| `0x1A` | s16 | `-110` |
| `0x1C` | f32 | `0.0` |

**Pump**

| Offset | Type | Value |
|---:|---|---:|
| `0x00` | s16 | `50` |
| `0x02` | s16 | `450` |
| `0x04` | f32 | `10.0` |
| `0x08` | s16 | `500` |
| `0x0A` | s16 | `600` |
| `0x0C` | f32 | `50.0` |
| `0x10` | s16 | `-700` |
| `0x12` | s16 | `-550` |
| `0x14` | f32 | `1.0` |
| `0x18` | s16 | `-100` |
| `0x1A` | s16 | `-50` |
| `0x1C` | f32 | `1.0` |

**TUT**

| Offset | Type | Value |
|---:|---|---:|
| `0x00` | s16 | `250` |
| `0x02` | s16 | `350` |
| `0x04` | f32 | `7.0` |
| `0x08` | s16 | `450` |
| `0x0A` | s16 | `600` |
| `0x0C` | f32 | `50.0` |
| `0x10` | s16 | `-900` |
| `0x12` | s16 | `-700` |
| `0x14` | f32 | `70.0` |
| `0x18` | s16 | `-100` |
| `0x1A` | s16 | `-50` |
| `0x1C` | f32 | `14.0` |

**TUT Beast**

| Offset | Type | Value |
|---:|---|---:|
| `0x00` | s16 | `150` |
| `0x02` | s16 | `250` |
| `0x04` | f32 | `7.0` |
| `0x08` | s16 | `350` |
| `0x0A` | s16 | `450` |
| `0x0C` | f32 | `50.0` |
| `0x10` | s16 | `-900` |
| `0x12` | s16 | `-700` |
| `0x14` | f32 | `70.0` |
| `0x18` | s16 | `-100` |
| `0x1A` | s16 | `-50` |
| `0x1C` | f32 | `28.0` |

**Eccentric Only**

| Offset | Type | Value |
|---:|---|---:|
| `0x00` | s16 | `50` |
| `0x02` | s16 | `550` |
| `0x04` | f32 | `50.0` |
| `0x08` | s16 | `650` |
| `0x0A` | s16 | `750` |
| `0x0C` | f32 | `10.0` |
| `0x10` | s16 | `-900` |
| `0x12` | s16 | `-700` |
| `0x14` | f32 | `70.0` |
| `0x18` | s16 | `-100` |
| `0x1A` | s16 | `-50` |
| `0x1C` | f32 | `20.0` |

### Echo control frame (32 bytes, command `0x4E`)

Echo control is built as:

- `0x00..0x03`: u32 LE = `0x0000004E`
- `0x04`: warmup reps
- `0x05`: target reps (`0xFF` for Just Lift / AMRAP)
- `0x08`: eccentric percent (u16 LE), clamped to `0..150`
- `0x0A`: concentric percent (u16 LE)
- `0x0C`: smoothing (f32 LE)
- `0x10`: gain (f32 LE)
- `0x14`: cap (f32 LE)
- `0x18`: floor (f32 LE)
- `0x1C`: negLimit (f32 LE)

See `createEchoControl()` in `shared/src/commonMain/kotlin/com/devil/phoenixproject/util/BlePacketFactory.kt`.

### Legacy “regular” command (25 bytes, command `0x4F`)

Project Phoenix keeps an older/simplified command that sets:

- program mode (byte)
- weight per cable scaled as `kg * 100` (u16 LE)
- target reps (byte)

This is used for some “weight update” flows but does not provide full parity with program params.

See `createWorkoutCommand()` in `shared/src/commonMain/kotlin/com/devil/phoenixproject/util/BlePacketFactory.kt`.

### Color scheme frame (34 bytes, command `0x11`)

Project Phoenix can set LED color schemes via a 34-byte frame:

- `0x00..0x03`: u32 LE = `0x00000011`
- `0x0C..0x0F`: brightness (f32 LE)
- Then 2× copies of 3 RGB triplets (for 3 colors), for a total of 18 RGB bytes

See `createColorScheme()` and `createColorSchemeCommand()` in `shared/src/commonMain/kotlin/com/devil/phoenixproject/util/BlePacketFactory.kt`.

## Endianness gotchas

What Project Phoenix sees in practice:

- Monitor reads: **little-endian**
- Reps notifications: **little-endian**
- Some “RX metrics notifications” (opcode `0x01`) are parsed as **big-endian** in `parseMetricsPacket()`; Project Phoenix does not rely on these for core operation.

If you encounter a metric/rep stream that begins with an opcode, treat it separately from characteristic-specific notify payloads.

## Minimal implementation checklist

1. Scan and select device by name and/or FEF3 service data.
2. Connect; on Android request high priority + MTU 247.
3. Subscribe to REPS notifications.
4. Start polling:
   - Monitor as fast as possible (sequential read loop with timeout)
   - Diagnostic every 500ms
   - Heuristic every 250ms (optional unless you need Echo telemetry)
5. To start a set:
   - Write program params (96 bytes) or Echo control (32 bytes)
   - Delay ~50ms
   - Write Start (4 bytes)
6. To stop:
   - Soft stop: write `50 00` (keep polling)
   - Hard stop: write `0A 00 00 00` and stop polling if ending session
7. Add heartbeat + timeouts + auto-reconnect safety.
