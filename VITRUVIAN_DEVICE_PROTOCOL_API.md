# Vitruvian Device Protocol API

## 1. Device API Overview

This document specifies the BLE/GATT protocol for communicating with Vitruvian V-Form / Trainer+ resistance training machines. All information is derived from:
- Kotlin implementation: `shared/src/commonMain/kotlin/com/devil/phoenixproject/`
- Go reverse-engineering: `gocode/vitruvian-ble-protocol.md`, `gocode/protocol/`, `gocode/telemetry/`

**Safety Note**: These devices generate significant forces. All control implementations must provide immediate stop capability.

---

## 2. GATT Map

### 2.1 Primary Service

| Service | UUID | Evidence |
|---------|------|----------|
| NUS-like (Primary) | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` | `BleConstants.kt:11`, `KableBleRepository.kt:64` |

### 2.2 Characteristics

| Characteristic | UUID | Direction | Access | Min Size | Evidence |
|----------------|------|-----------|--------|----------|----------|
| TX Write (RX on device) | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` | host→device | Write With Response | varies | `BleConstants.kt:14`, `KableBleRepository.kt:67`, `uuid.go:9` |
| Monitor/Sample | `90e991a6-c548-44ed-969b-eb541014eae3` | device→host | Read (poll) | 16 bytes | `BleConstants.kt:15-16`, `KableBleRepository.kt:71,159-162`, `uuid.go:11` |
| Reps | `8308f2a6-0875-4a94-a86f-5c5c5e1b068a` | device→host | Notify | 6 or 24 bytes | `BleConstants.kt:19-20`, `KableBleRepository.kt:72,163-166`, `uuid.go:12` |
| Diagnostic/Property | `5fa538ec-d041-42f6-bbd6-c30d475387b7` | device→host | Read (poll) | 20 bytes | `BleConstants.kt:27-28`, `KableBleRepository.kt:75,169-172` |
| Heuristic | `c7b73007-b245-4503-a1ed-9e4e97eb9802` | device→host | Read (poll)* | 48 bytes | `BleConstants.kt:26`, `KableBleRepository.kt:76,173-176` |
| Version | `74e994ac-0e80-4c02-9cd0-76cb31d3959b` | device→host | Notify | variable | `BleConstants.kt:22`, `KableBleRepository.kt:77,177-180` |
| Mode | `67d0dae0-5bfc-4ea2-acc9-ac784dee7f29` | device→host | Notify | 4 bytes | `BleConstants.kt:21`, `KableBleRepository.kt:78,181-184` |
| Cable Left | `bc4344e9-8d63-4c89-8263-951e2d74f744` | device→host | Read | 6 bytes | `BleConstants.kt:17` |
| Cable Right | `92ef83d6-8916-4921-8172-a9919bc82566` | device→host | Read | 6 bytes | `BleConstants.kt:18` |
| WiFi State | `a7d06ce0-2e84-485f-9c25-3d4ba6fe7319` | device→host | Read | 74 bytes | `BleConstants.kt:23` |
| Update State | `383f7276-49af-4335-9072-f01b0f8acad6` | device→host | Notify | variable | `BleConstants.kt:24`, `KableBleRepository.kt:80` |
| BLE Update Request | `ef0e485a-8749-4314-b1be-01e57cd1712e` | device→host | Notify | 5 bytes | `BleConstants.kt:25`, `KableBleRepository.kt:82` |
| Unknown/Auth | `36e6c2ee-21c7-404e-aa9b-f74ca4728ad4` | device→host | Notify | UNKNOWN | `BleConstants.kt:32`, `KableBleRepository.kt:84` |

**\* Heuristic Note**: Despite `BleConstants.NOTIFY_CHAR_UUID_STRINGS` (`BleConstants.kt:34-42`) including the Heuristic UUID, the implementation polls this characteristic at 4Hz via `read()` rather than subscribing to notifications. This appears to be a constants list inconsistency; the actual transport is polling.

### 2.3 DIS Service (Optional)

| Service | UUID | Evidence |
|---------|------|----------|
| Device Information Service | `0000180a-0000-1000-8000-00805f9b34fb` | `KableBleRepository.kt:87` |
| Firmware Revision | `00002a26-0000-1000-8000-00805f9b34fb` | `KableBleRepository.kt:88` |

---

## 3. Command Frames

All commands are written to TX characteristic (`6e400002-...`) using **Write With Response**.
All multi-byte values are **little-endian** unless noted.

### 3.1 Command Summary Table

| Command | Opcode | Length | Purpose | Builder | Call Sites |
|---------|--------|--------|---------|---------|------------|
| Start | `0x03` | 4 | Engage motors | `BlePacketFactory.createStartCommand():67-69` | `vitruvian-ble-protocol.md:255,275-276` |
| Stop (Legacy) | `0x05` | 4 | Stop workout (legacy) | `BlePacketFactory.createStopCommand():75-77` | `BlePacketFactory.kt:74` |
| Soft Stop (StopPacket) | `0x50` | 2 | Release tension, clear faults | `BlePacketFactory.createOfficialStopPacket():85-87` | `KableBleRepository.kt:1607-1609` |
| Reset/Init | `0x0A` | 4 | Full stop / initialize | `BlePacketFactory.createResetCommand():94-96` | `KableBleRepository.kt:1586-1588` |
| Program Params | `0x04` | 96 | Configure workout mode | `BlePacketFactory.createProgramParams():127-200` | `vitruvian-ble-protocol.md:258,273-274` |
| Echo Control | `0x4E` | 32 | Configure Echo mode | `BlePacketFactory.createEchoControl():219-261` | `BlePacketFactory.kt:208-211` |
| Legacy Workout | `0x4F` | 25 | Simple mode/weight/reps | `BlePacketFactory.createWorkoutCommand():104-119` | `BlePacketFactory.kt:101-102` |
| Init Preset/Color | `0x11` | 34 | LED color scheme | `BlePacketFactory.createColorScheme():268-287` | `KableBleRepository.kt:1455-1456` |
| Heartbeat No-op | `0x00` | 4 | Keep-alive | inline constant `:125` | `KableBleRepository.kt:962` |
| Init Sequence | `0x01` | 4 | Prepare machine for workout | inline `KableBleRepository.kt:1545` | `KableBleRepository.kt:1546` |
| Legacy Start | `0x02` | 4 | Simplified workout start | inline `KableBleRepository.kt:1566` | `KableBleRepository.kt:1567` |

---

### 3.2 Start Command (4 bytes)

**Evidence**: `BlePacketFactory.kt:67-69`, `commands.go:3`

```
Offset  Type     Value      Meaning
0x00    u32 LE   0x00000003 Opcode = Start
```

Byte sequence: `03 00 00 00`

---

### 3.3 Soft Stop / StopPacket (2 bytes)

**Evidence**: `BlePacketFactory.kt:85-87`, `commands.go:5`

```
Offset  Type     Value      Meaning
0x00    u16 LE   0x0050     Opcode = Soft Stop
```

Byte sequence: `50 00`

**Usage**: Releases tension, clears fault state (blinking red light). Keeps polling alive for Just Lift.

---

### 3.4 Reset/Init Command (4 bytes)

**Evidence**: `BlePacketFactory.kt:41-43,94-96`, `commands.go:7`

```
Offset  Type     Value      Meaning
0x00    u32 LE   0x0000000A Opcode = Reset/Init
```

Byte sequence: `0A 00 00 00`

**Usage**: Full workout stop. Used by web apps. Recovery fallback.

---

### 3.5 Legacy Stop Command (4 bytes)

**Evidence**: `BlePacketFactory.kt:75-77`

```
Offset  Type     Value      Meaning
0x00    u32 LE   0x00000005 Opcode = Legacy Stop
```

Byte sequence: `05 00 00 00`

**Note**: Deprecated. Prefer `0x50` (StopPacket) for soft stop.

---

### 3.6 Program Params (96 bytes, opcode `0x04`)

**Evidence**: `BlePacketFactory.kt:127-200`, `program_params.go:30-92`, `program_params_test.go:9-40`

#### Frame Layout

| Offset | Size | Type | Endian | Meaning | Source |
|--------|------|------|--------|---------|--------|
| 0x00-0x03 | 4 | u32 | LE | Opcode `0x04` | Fixed |
| 0x04 | 1 | u8 | - | Reps: `0xFF`=infinite, else `reps+warmupReps` | `params.reps + params.warmupReps` |
| 0x05 | 1 | u8 | - | Fixed `0x03` | Fixed |
| 0x06 | 1 | u8 | - | Fixed `0x03` | Fixed |
| 0x07 | 1 | u8 | - | Fixed `0x00` | Fixed |
| 0x08-0x0B | 4 | f32 | LE | Fixed `5.0` | Fixed |
| 0x0C-0x0F | 4 | f32 | LE | Fixed `5.0` | Fixed |
| 0x10-0x13 | 4 | - | - | (zeros) | - |
| 0x14-0x15 | 2 | u16 | LE | Fixed `0x00FA` (250) | Fixed |
| 0x16-0x17 | 2 | u16 | LE | Fixed `0x00FA` (250) | Fixed |
| 0x18-0x19 | 2 | u16 | LE | Fixed `0x00C8` (200) | Fixed |
| 0x1A-0x1B | 2 | u16 | LE | Fixed `0x001E` (30) | Fixed |
| 0x1C-0x1F | 4 | f32 | LE | Fixed `5.0` | Fixed |
| 0x20-0x23 | 4 | - | - | (zeros) | - |
| 0x24-0x25 | 2 | u16 | LE | Fixed `0x00FA` | Fixed |
| 0x26-0x27 | 2 | u16 | LE | Fixed `0x00FA` | Fixed |
| 0x28-0x29 | 2 | u16 | LE | Fixed `0x00C8` | Fixed |
| 0x2A-0x2B | 2 | u16 | LE | Fixed `0x001E` | Fixed |
| 0x2C-0x2D | 2 | u16 | LE | Fixed `0x00FA` | Fixed |
| 0x2E-0x2F | 2 | u16 | LE | Fixed `0x0050` (80) | Fixed |
| 0x30-0x4F | 32 | block | LE | Mode Profile (see below) | `getModeProfile()` |
| 0x50-0x53 | 4 | - | - | (zeros) | - |
| 0x54-0x57 | 4 | f32 | LE | `effectiveKg` = `weightPerCable + 10.0` | Param |
| 0x58-0x5B | 4 | f32 | LE | `totalWeightKg` = `weightPerCable` | Param |
| 0x5C-0x5F | 4 | f32 | LE | `progressionRegressionKg` | Param |

#### Mode Profile Block (32 bytes at offset 0x30)

**Evidence**: `BlePacketFactory.kt:300-394`, `program_params.go:94-169`

Profile offset layout (within the 32-byte block):

| Offset | Type | Meaning |
|--------|------|---------|
| 0x00 | i16 LE | Parameter 1 |
| 0x02 | i16 LE | Parameter 2 |
| 0x04 | f32 LE | Parameter 3 |
| 0x08 | i16 LE | Parameter 4 |
| 0x0A | i16 LE | Parameter 5 |
| 0x0C | f32 LE | Parameter 6 |
| 0x10 | i16 LE | Parameter 7 |
| 0x12 | i16 LE | Parameter 8 |
| 0x14 | f32 LE | Parameter 9 |
| 0x18 | i16 LE | Parameter 10 |
| 0x1A | i16 LE | Parameter 11 |
| 0x1C | f32 LE | Parameter 12 |

**OldSchool**: `0, 20, 3.0, 75, 600, 50.0, -1300, -1200, 100.0, -260, -110, 0.0`
**Pump**: `50, 450, 10.0, 500, 600, 50.0, -700, -550, 1.0, -100, -50, 1.0`
**TUT**: `250, 350, 7.0, 450, 600, 50.0, -900, -700, 70.0, -100, -50, 14.0`
**TUTBeast**: `150, 250, 7.0, 350, 450, 50.0, -900, -700, 70.0, -100, -50, 28.0`
**EccentricOnly**: `50, 550, 50.0, 650, 750, 10.0, -900, -700, 70.0, -100, -50, 20.0`

---

### 3.7 Echo Control (32 bytes, opcode `0x4E`)

**Evidence**: `BlePacketFactory.kt:219-261`, `vitruvian-ble-protocol.md:433-448`

| Offset | Size | Type | Endian | Meaning | Source |
|--------|------|------|--------|---------|--------|
| 0x00-0x03 | 4 | u32 | LE | Opcode `0x4E` (78) | Fixed |
| 0x04 | 1 | u8 | - | Warmup reps | Param |
| 0x05 | 1 | u8 | - | Target reps (`0xFF`=infinite) | Param |
| 0x06-0x07 | 2 | u16 | LE | Fixed `0x0000` | Fixed |
| 0x08-0x09 | 2 | u16 | LE | Eccentric % (clamped 0-150) | Param |
| 0x0A-0x0B | 2 | u16 | LE | Concentric % | Param (default 50) |
| 0x0C-0x0F | 4 | f32 | LE | Smoothing | Param (default 0.1) |
| 0x10-0x13 | 4 | f32 | LE | Gain | Level-dependent |
| 0x14-0x17 | 4 | f32 | LE | Cap | Level-dependent |
| 0x18-0x1B | 4 | f32 | LE | Floor | Param (default 0.0) |
| 0x1C-0x1F | 4 | f32 | LE | NegLimit | Param (default -100.0) |

**Echo Levels** (`EchoParams`, `BlePacketFactory.kt:398-415`):
- **HARD**: gain=1.0, cap=50.0
- **HARDER**: gain=1.25, cap=40.0
- **HARDEST**: gain=1.667, cap=30.0
- **EPIC**: gain=3.333, cap=15.0

---

### 3.8 Color Scheme (34 bytes, opcode `0x11`)

**Evidence**: `BlePacketFactory.kt:268-296`, `ColorScheme.kt:1-101`

| Offset | Size | Type | Endian | Meaning |
|--------|------|------|--------|---------|
| 0x00-0x03 | 4 | u32 | LE | Opcode `0x11` |
| 0x04-0x07 | 4 | u32 | LE | Fixed `0x00000000` |
| 0x08-0x0B | 4 | u32 | LE | Fixed `0x00000000` |
| 0x0C-0x0F | 4 | f32 | LE | Brightness (0.0-1.0) |
| 0x10-0x21 | 18 | bytes | - | 2x (3 RGB triplets) |

RGB triplet: 3 bytes (R, G, B), each 0-255.

---

### 3.9 Legacy Workout Command (25 bytes, opcode `0x4F`)

**Evidence**: `BlePacketFactory.kt:104-119`, `BleConstants.kt:52`

| Offset | Size | Type | Meaning |
|--------|------|------|---------|
| 0x00 | 1 | u8 | Opcode `0x4F` (79) |
| 0x01 | 1 | u8 | Program mode value |
| 0x02-0x03 | 2 | u16 LE | Weight x 100 |
| 0x04 | 1 | u8 | Target reps |
| 0x05-0x18 | 20 | - | (zeros) |

---

### 3.10 Init Preset (34 bytes, opcode `0x11`)

**Evidence**: `BlePacketFactory.kt:48-59`, `BlePacketFactoryTest.kt:31-36`

Used for LED initialization with coefficient table. Same opcode as color scheme.

Byte sequence (fixed): `11 00 00 00 00 00 00 00 00 00 00 00 CD CC CC 3E FF 00 4C FF 23 8C FF 8C 8C FF 00 4C FF 23 8C FF 8C 8C`

---

### 3.11 Init Sequence Command (4 bytes, opcode `0x01`)

**Evidence**: `KableBleRepository.kt:1540-1551`

```
Offset  Type     Value      Meaning
0x00    u32 LE   0x00000001 Opcode = Init Sequence
```

Byte sequence: `01 00 00 00`

**Usage**: Prepares machine for workout. Called via `sendInitSequence()` before workout start in some code paths.

**Note**: This command is used in the production code path but its exact device-side behavior is not fully documented. It appears to be a pre-workout initialization signal.

---

### 3.12 Legacy Start Command (4 bytes, opcode `0x02`)

**Evidence**: `KableBleRepository.kt:1553-1578`

```
Offset  Type     Value      Meaning
0x00    u8       0x02       Opcode = Legacy Start
0x01    u8       mode       Program mode value (see ProgramMode.modeValue)
0x02    u8       weight_lo  Weight × 100, low byte
0x03    u8       weight_hi  Weight × 100, high byte
```

| Offset | Size | Type | Meaning |
|--------|------|------|---------|
| 0x00 | 1 | u8 | Opcode `0x02` |
| 0x01 | 1 | u8 | Program mode value |
| 0x02-0x03 | 2 | u16 LE | Weight per cable × 100 (hectograms) |

**Usage**: Simplified workout start that bypasses the full 96-byte Program Params frame. Used by `startWorkout()` function for quick mode+weight configuration.

**Note**: This is an alternative to the standard sequence (Program Params → Start). It combines mode selection and weight setting into a single compact command.

---

## 4. Telemetry Formats

### 4.1 Monitor/Sample (polled, 16-18+ bytes)

**Evidence**: `KableBleRepository.kt:1845-2034`, `monitor.go:64-92`, `monitor_test.go:8-50`

**Delivery**: Poll via `read()` on characteristic `90e991a6-...`. NOT notifiable.

**IMPORTANT**: This characteristic does NOT support notifications. The device will not send data via notify/indicate; you MUST poll via `read()`. Despite `BleConstants.NOTIFY_CHAR_UUID_STRINGS` including similar characteristics, the Monitor/Sample characteristic requires active polling.

**Polling rate**: As fast as BLE allows (~10-20ms natural rate-limit). No fixed delay on success.

| Offset | Size | Type | Endian | Scale | Meaning |
|--------|------|------|--------|-------|---------|
| 0-1 | 2 | u16 | LE | - | ticks_lo |
| 2-3 | 2 | u16 | LE | - | ticks_hi |
| 4-5 | 2 | i16 | LE | /10 | posA (mm) |
| 6-7 | 2 | i16 | LE | /10 | velocityA_raw (UNKNOWN/unused) |
| 8-9 | 2 | u16 | LE | /100 | loadA (kg) |
| 10-11 | 2 | i16 | LE | /10 | posB (mm) |
| 12-13 | 2 | i16 | LE | /10 | velocityB_raw (UNKNOWN/unused) |
| 14-15 | 2 | u16 | LE | /100 | loadB (kg) |
| 16-17 | 2 | u16 | LE | - | status flags (optional) |

**Derived**: `ticks = ticks_lo + (ticks_hi << 16)`

**Unit Note** (`vitruvian-ble-protocol.md:151`): Position empirically behaves like **centimeters** in Go spike (pos~171.6 at >1m pull). Kotlin treats as mm. Consider units as "position" until validated.

---

### 4.2 Reps (notify, 6 or 24 bytes)

**Evidence**: `KableBleRepository.kt:2480-2563`, `reps.go:29-56`, `reps_test.go:9-33`

**Delivery**: Subscribe to notifications on `8308f2a6-...`

#### Official 24-byte Format

| Offset | Size | Type | Endian | Meaning |
|--------|------|------|--------|---------|
| 0-3 | 4 | u32 | LE | upCounter (concentric) |
| 4-7 | 4 | u32 | LE | downCounter (eccentric) |
| 8-11 | 4 | f32 | LE | rangeTop |
| 12-15 | 4 | f32 | LE | rangeBottom |
| 16-17 | 2 | u16 | LE | repsRomCount (warmup done) |
| 18-19 | 2 | u16 | LE | repsRomTotal (warmup target) |
| 20-21 | 2 | u16 | LE | repsSetCount (working done) |
| 22-23 | 2 | u16 | LE | repsSetTotal (working target) |

#### Legacy 6-byte Format

| Offset | Size | Type | Endian | Meaning |
|--------|------|------|--------|---------|
| 0-1 | 2 | u16 | LE | topCounter |
| 2-3 | 2 | - | - | unused |
| 4-5 | 2 | u16 | LE | completeCounter |

---

### 4.3 Diagnostic (polled, 20+ bytes)

**Evidence**: `KableBleRepository.kt:1215-1248`, `vitruvian-ble-protocol.md:206-215`

**Delivery**: Poll every 500ms on `5fa538ec-...`

| Offset | Size | Type | Meaning |
|--------|------|------|---------|
| 0-3 | 4 | u32 LE | Uptime seconds (reserved) |
| 4-5 | 2 | i16 LE | Fault code 1 |
| 6-7 | 2 | i16 LE | Fault code 2 |
| 8-9 | 2 | i16 LE | Fault code 3 |
| 10-11 | 2 | i16 LE | Fault code 4 |
| 12-19 | 8 | bytes | Temperature readings (raw) |

**Type Note**: `vitruvian-ble-protocol.md:212` documents fault codes as `u16` (unsigned), but the Kotlin implementation (`KableBleRepository.kt:1227-1229`) parses them as `Short` (signed i16). This spec uses `i16` to match the actual implementation. The signedness may matter for interpreting specific fault code values.

---

### 4.4 Heuristic (polled, 48 bytes)

**Evidence**: `KableBleRepository.kt:1255-1290`, `vitruvian-ble-protocol.md:217-241`

**Delivery**: Poll every 250ms (4Hz) on `c7b73007-...`

| Offset | Size | Type | Meaning |
|--------|------|------|---------|
| 0-3 | 4 | f32 LE | Concentric kgAvg |
| 4-7 | 4 | f32 LE | Concentric kgMax |
| 8-11 | 4 | f32 LE | Concentric velAvg |
| 12-15 | 4 | f32 LE | Concentric velMax |
| 16-19 | 4 | f32 LE | Concentric wattAvg |
| 20-23 | 4 | f32 LE | Concentric wattMax |
| 24-27 | 4 | f32 LE | Eccentric kgAvg |
| 28-31 | 4 | f32 LE | Eccentric kgMax |
| 32-35 | 4 | f32 LE | Eccentric velAvg |
| 36-39 | 4 | f32 LE | Eccentric velMax |
| 40-43 | 4 | f32 LE | Eccentric wattAvg |
| 44-47 | 4 | f32 LE | Eccentric wattMax |

---

## 5. Status Flags

**Evidence**: `SampleStatus.kt:33-46`, `monitor.go:24-32`

Extracted from Monitor characteristic bytes 16-17 as u16 LE.

| Mask | Name | Meaning |
|------|------|---------|
| `0x0001` | REP_TOP_READY | Top-of-rep ready |
| `0x0002` | REP_BOTTOM_READY | Bottom-of-rep ready |
| `0x0004` | ROM_OUTSIDE_HIGH | ROM above allowed range |
| `0x0008` | ROM_OUTSIDE_LOW | ROM below allowed range |
| `0x0010` | ROM_UNLOAD_ACTIVE | ROM unload active |
| `0x0020` | SPOTTER_ACTIVE | Spotter active |
| `0x0040` | DELOAD_WARN | Deload warning |
| `0x8000` | DELOAD_OCCURRED | Deload occurred |

---

## 6. Required Sequences

### 6.1 Workout Start (Program Modes)

**Evidence**: `vitruvian-ble-protocol.md:269-277`

1. Write Program Params (96 bytes, `0x04`)
2. Delay ~50ms
3. Write Start (`03 00 00 00`)
4. Begin Monitor polling + subscribe to Reps notifications

### 6.2 Workout Start (Echo Mode)

**Evidence**: `vitruvian-ble-protocol.md:280-281`

1. Write Echo Control (32 bytes, `0x4E`)
2. Delay ~50ms
3. Write Start (`03 00 00 00`)
4. Begin Monitor polling + subscribe to Reps notifications

### 6.3 Soft Stop (Just Lift / Fault Clear)

**Evidence**: `vitruvian-ble-protocol.md:283-292`, `KableBleRepository.kt:1607-1609`

1. Write StopPacket (`50 00`)
2. Continue polling (for Just Lift auto-restart)

### 6.4 Hard Stop (End Session)

**Evidence**: `vitruvian-ble-protocol.md:289`, `KableBleRepository.kt:1586-1588`

1. Write Reset (`0A 00 00 00`)
2. Stop polling jobs

### 6.5 Heartbeat (Connection Keep-alive)

**Evidence**: `KableBleRepository.kt:117-125`, `vitruvian-ble-protocol.md:109-115`

Every 2000ms:
1. Try read() on Monitor characteristic (with 1500ms timeout)
2. If fails, write no-op `00 00 00 00` to TX characteristic

---

## 7. Known Variants / Legacy Formats

### 7.1 Packet Size Conflicts

**Evidence**: `vitruvian-ble-protocol.md:265-267`, `BleConstants.kt:54`

| Frame | Kotlin Uses | Some Docs Say |
|-------|-------------|---------------|
| Program Params | **96 bytes** | 97 bytes |
| Echo Control | **32 bytes** | 29 bytes |

**Resolution**: Kotlin implementation uses 96/32 respectively. Both work.

### 7.2 Legacy Reps Format

**Evidence**: `reps.go:47-52`, `KableBleRepository.kt:2527-2546`

Some firmware returns 6-byte reps instead of 24-byte. Parser must handle both.

### 7.3 Monitor Position Units

**Evidence**: `vitruvian-ble-protocol.md:151`

Go spike observes position as centimeters (`pos_raw/10`); Kotlin labels as mm. Treat as "position units" until validated per firmware.

---

## 8. Known-but-unused UUIDs/Commands

| Item | UUID/Opcode | Status | Evidence |
|------|-------------|--------|----------|
| NUS RX (standard) | `6e400003-...` | Not present on Vitruvian | `KableBleRepository.kt:68-70` |
| Cable Left | `bc4344e9-...` | Defined, not read in core path | `BleConstants.kt:17` |
| Cable Right | `92ef83d6-...` | Defined, not read in core path | `BleConstants.kt:18` |
| WiFi State | `a7d06ce0-...` | Defined, not used | `BleConstants.kt:23` |
| Unknown Auth | `36e6c2ee-...` | "Purpose unclear" per code | `BleConstants.kt:31-32` |
| GATT Service | `00001801-...` | Referenced but not used | `BleConstants.kt:10` |

---

## 9. Unknowns & Questions

| Question | Evidence | Notes |
|----------|----------|-------|
| Monitor bytes 6-7, 12-13 purpose | `vitruvian-ble-protocol.md:138-142` | Labeled "unknown/unused" - likely velocity_raw |
| Unknown Auth characteristic purpose | `BleConstants.kt:31-32` | "Purpose unclear but may be needed" |
| Position units (mm vs cm) | `vitruvian-ble-protocol.md:151` | Empirical conflict between Go/Kotlin |
| Exact meaning of status bits 7-14 | - | Only bits 0-6 and 15 documented |
| repsRomTotal interpretation | `vitruvian-ble-protocol.md:192-193` | "varies by firmware" |
| Init Preset coefficient meanings | `BlePacketFactory.kt:48-59` | Magic bytes, purpose undocumented |

---

## 10. EXCLUDED (App Logic)

These are **not part of the Device Protocol API**. They are app-level orchestration:

| Topic | Location | Description |
|-------|----------|-------------|
| Handle detection state machine | `KableBleRepository.kt:219-221,2013-2028` | Just Lift grab/release UX |
| Auto-start/auto-stop timers | `KableBleRepository.kt:96-102,1321-1338` | Thresholds for handle UX |
| Rep counting UX | `domain/usecase/RepCounterFromMachine.kt` | App-level rep tracking |
| Session/routine flow | `presentation/viewmodel/MainViewModel.kt` | Workout session orchestration |
| Reconnection policy | `KableBleRepository.kt:131,2241-2245` | Auto-reconnect logic |
| WaitingForRest timeout | `KableBleRepository.kt:136-138,321-323` | iOS autostart fix |
| Dynamic baseline (overhead pulley) | `KableBleRepository.kt:141-145,327-329` | Issue #176 UX compensation |

---

## Coverage Checklist

### TX Writes Mapped

| Write | Mapped To | Section |
|-------|-----------|---------|
| `p.write(txCharacteristic, ...)` at line 962 | Heartbeat no-op (`0x00`) | 3.1 |
| `sendWorkoutCommand()` at line 1456 | Color scheme (`0x11`) | 3.8 |
| `sendWorkoutCommand()` at line 1491 | Generic command dispatch | - |
| `sendWorkoutCommand()` at line 1546 | Init sequence (`0x01`) | 3.11 |
| `sendWorkoutCommand()` at line 1567 | Legacy start (`0x02`) | 3.12 |
| `sendWorkoutCommand()` at line 1588 | Reset command (`0x0A`) | 3.4 |
| `sendWorkoutCommand()` at line 1609 | StopPacket (`0x50`) | 3.3 |
| `sendWorkoutCommand()` at line 2660,2689 | Disco mode colors (`0x11`) | 3.8 |

**Total TX paths**: 8 - All mapped to documented commands.

### RX/Notify Mapped

| Characteristic | Parser | Call Site |
|----------------|--------|-----------|
| Monitor | `parseMonitorData()` | `KableBleRepository.kt:1373` |
| Reps | `parseRepsCharacteristicData()` | `KableBleRepository.kt:1010` |
| Diagnostic | `parseDiagnosticData()` | `KableBleRepository.kt:1141` |
| Heuristic | `parseHeuristicData()` | `KableBleRepository.kt:1189` |
| Version | (observed but not parsed in detail) | `KableBleRepository.kt:77` |
| Mode | (observed but not parsed in detail) | `KableBleRepository.kt:78` |

**Total RX paths**: 6 - All primary telemetry mapped.

### Unmapped Items

| Item | Status |
|------|--------|
| Cable Left/Right characteristics | Known-but-unused (Section 8) |
| WiFi State characteristic | Known-but-unused (Section 8) |
| Unknown Auth characteristic | Purpose UNKNOWN (Section 8) |
| Update State / BLE Update Request | Reserved for OTA (not documented) |

**All TX opcodes are now documented**: `0x00`, `0x01`, `0x02`, `0x03`, `0x04`, `0x05`, `0x0A`, `0x11`, `0x4E`, `0x4F`, `0x50`

---

*End of Vitruvian Device Protocol API Specification*
