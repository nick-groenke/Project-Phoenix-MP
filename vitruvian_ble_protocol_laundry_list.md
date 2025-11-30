# Vitruvian Trainer BLE/GATT Protocol - Comprehensive Laundry List

## Overview

This document catalogs all BLE/GATT protocol signals the VitruvianProjectPhoenix app sends to and receives from Vitruvian Trainer machines (Euclid VIT-200 and Trainer+ VIT-300).

**BLE Stack**: Nordic BLE Library  
**Primary Service**: Nordic UART Service (NUS)  
**Device Name Pattern**: `Vee_*`

---

## 1. BLE Service & Characteristic UUIDs

### Nordic UART Service (NUS)
| Component | UUID | Description |
|-----------|------|-------------|
| NUS Service | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` | Main communication service |
| NUS RX Characteristic | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` | App → Machine (Write) |
| NUS TX Characteristic | `6e400003-b5a3-f393-e0a9-e50e24dcca9e` | Machine → App (Notify) |

### Monitor Characteristics (Likely Custom Service)
| Characteristic | Purpose |
|---------------|---------|
| Monitor Characteristic | Real-time workout metrics (load, position, velocity, power) |

---

## 2. Functional Flow 1: Device Discovery & Connection

### 2.1 Scanning Phase
| Direction | Signal/Action | Description |
|-----------|---------------|-------------|
| App → BLE Stack | Start BLE Scan | Scan for devices advertising NUS service |
| BLE Stack → App | Discover Device | Device name starts with "Vee_" (e.g., "Vee_12345") |
| App → BLE Stack | Stop Scan | Once target device found |

### 2.2 Connection Establishment
| Step | Direction | Signal/Action | Description |
|------|-----------|---------------|-------------|
| 1 | App → Machine | Connect Request | Initiate GATT connection |
| 2 | Machine → App | Connection Response | Connection established |
| 3 | App → Machine | MTU Request | Request larger MTU (typically 512 bytes) |
| 4 | Machine → App | MTU Response | Confirmed MTU size |
| 5 | App → Machine | Service Discovery | Discover all GATT services |
| 6 | Machine → App | Service List | Returns available services |
| 7 | App → Machine | Characteristic Discovery | Discover characteristics for each service |

### 2.3 Notification Setup (24 Operations)
| Step | Direction | Signal | Description |
|------|-----------|--------|-------------|
| 1-24 | App → Machine | Enable Notifications | Enable CCCD for each characteristic |
| - | App → Machine | Enable NUS TX Notifications | Subscribe to machine responses |
| - | App → Machine | Enable Monitor Notifications | Subscribe to real-time metrics |
| - | App → Machine | Enable Rep Counter Notifications | Subscribe to rep events |

**Critical Note**: App tracks completion of all 25 async BLE operations (1 MTU + 24 notifications) before setting `ConnectionStatus.Ready`

### 2.4 Hardware Detection
| Direction | Signal | Description |
|-----------|--------|-------------|
| App reads | Device Name | Pattern matching for hardware model |
| - | `Vee_*` | Standard Trainer |
| - | VIT-200 | Euclid (V-Form Trainer) - Max 200kg |
| - | VIT-300 | Trainer+ - Max 220kg |

---

## 3. Functional Flow 2: Initiating a Workout

### 3.1 Pre-Workout Configuration

#### Set Workout Mode Command
| Direction | Characteristic | Command | Parameters |
|-----------|---------------|---------|------------|
| App → Machine | NUS RX | SET_MODE | Workout mode byte |

**Workout Mode Values**:
| Mode | Value | Description |
|------|-------|-------------|
| Old School | TBD | Constant resistance both phases |
| Pump | TBD | Increased speed = increased resistance |
| TUT (Time Under Tension) | TBD | Extended time under load |
| TUT Beast | TBD | Enhanced TUT mode |
| Eccentric-Only | TBD | Resistance only on lowering phase |
| Echo | TBD | Adaptive real-time weight adjustment |

#### Set Weight/Load Command
| Direction | Characteristic | Command | Data Format |
|-----------|---------------|---------|-------------|
| App → Machine | NUS RX | SET_LOAD | u16 little-endian (kg × 10) |

Example: 44.1 kg = `0x1B9` = bytes `0xB9 0x01`

#### Set Rep Target Command
| Direction | Characteristic | Command | Parameters |
|-----------|---------------|---------|------------|
| App → Machine | NUS RX | SET_REPS | warmup_reps (u8), target_reps (u8) |

### 3.2 Echo Mode Frame Construction
For Echo/Adaptive mode, a special configuration frame is built:

```
Frame Structure (Example):
━━━━━━━━━━ ECHO FRAME CONSTRUCTION ━━━━━━━━━━
Input Parameters:
  level: Harder (levelValue=1)
  eccentricPct: 150%
  warmupReps: 3
  targetReps: 10
  isJustLift: false

Echo Parameters (calculated):
  eccentricPct: 150%
  concentricPct: 50%
  gain: 1.25
  cap: 40.0

Frame bytes:
  0x08-0x09 (eccentric u16): 0x96 0x00 = 150
```

| Byte Offset | Field | Type | Description |
|-------------|-------|------|-------------|
| 0x00-0x01 | Mode | u16 | Echo mode identifier |
| 0x02-0x03 | Concentric % | u16 | Concentric load percentage |
| 0x04-0x05 | Level | u16 | Difficulty level (0=Hard, 1=Harder, 2=Hardest, 3=Epic) |
| 0x06-0x07 | Gain | f16/u16 | Adaptive gain factor |
| 0x08-0x09 | Eccentric % | u16 | Eccentric load percentage (e.g., 150 = 150%) |
| 0x0A-0x0B | Cap | u16 | Maximum weight cap |
| 0x0C | Warmup Reps | u8 | Number of warmup reps |
| 0x0D | Target Reps | u8 | Number of working reps |

### 3.3 Start Workout Command
| Direction | Characteristic | Command | Description |
|-----------|---------------|---------|-------------|
| App → Machine | NUS RX | START_WORKOUT | Initiates workout with configured parameters |

**Pre-conditions**:
- All 25 BLE operations must be complete
- NUS RX characteristic must be available
- Connection status must be `Ready`

### 3.4 LED Configuration (Optional)
| Direction | Characteristic | Command | Parameters |
|-----------|---------------|---------|------------|
| App → Machine | NUS RX | SET_LED_COLOR | RGB values |

---

## 4. Functional Flow 3: Active Workout Monitoring

### 4.1 Real-Time Metric Polling

The app uses **suspend-based sequential reads** (matching official app architecture):

```kotlin
// Correct approach (matches official app):
while (isActive) {
    val success = readMonitorCharacteristicSuspend() // WAITS for callback
    // Next read only after this one completes
}
// → Natural rate limiting → no queue flooding → stable connection
```

**DO NOT USE** fire-and-forget approach (causes Android 16 disconnects):
```kotlin
// BROKEN approach:
while (isActive) {
    readCharacteristic(char).enqueue() // Fire and forget!
    delay(100) // Next read regardless of completion
}
// → Floods BLE queue → supervision timeout → disconnect
```

### 4.2 Monitor Characteristic Data
| Direction | Characteristic | Data | Description |
|-----------|---------------|------|-------------|
| Machine → App | Monitor | Metrics Packet | ~10Hz polling rate |

**Metrics Packet Structure**:
| Byte Offset | Field | Type | Unit | Description |
|-------------|-------|------|------|-------------|
| 0x00-0x01 | Load Right | u16 | kg × 10 | Right cable load |
| 0x02-0x03 | Load Left | u16 | kg × 10 | Left cable load |
| 0x04-0x05 | Position | u16 | mm | Cable extension position |
| 0x06-0x07 | Velocity | i16 | mm/s | Movement velocity (signed) |
| 0x08-0x09 | Power | u16 | watts | Instantaneous power |
| 0x0A | Status Flags | u8 | bitfield | Machine status bits |

**Status Flags** (stored in database v24):
| Bit | Flag | Description |
|-----|------|-------------|
| 0 | Active | Machine actively providing resistance |
| 1 | At Top | Cable at top position (concentric complete) |
| 2 | At Bottom | Cable at bottom position (eccentric complete) |
| 3-7 | Reserved | Future use |

### 4.3 Rep Counter Notifications
| Direction | Characteristic | Event | Description |
|-----------|---------------|-------|-------------|
| Machine → App | Rep Counter | REP_COMPLETE | Rep finished (eccentric phase complete) |
| Machine → App | Rep Counter | REP_PENDING | Rep in progress (concentric phase complete) |

**Rep Detection States**:
- **Pending Rep**: Reached top of rep (concentric complete) - displayed grayed out
- **Completed Rep**: Reached bottom of rep (eccentric complete) - displayed colored

### 4.4 Warmup Phase
| Direction | Signal | Description |
|-----------|--------|-------------|
| Machine → App | Rep notifications | Process during Countdown state |
| App internal | Set Active state | Before BLE commands to machine |

**Fix Applied**: Warmup reps now register correctly by processing rep notifications during Countdown state.

---

## 5. Functional Flow 4: Workout Control Commands

### 5.1 Pause/Resume
| Direction | Characteristic | Command | Description |
|-----------|---------------|---------|-------------|
| App → Machine | NUS RX | PAUSE_WORKOUT | Pause active workout |
| App → Machine | NUS RX | RESUME_WORKOUT | Resume paused workout |

### 5.2 Stop Workout
| Direction | Characteristic | Command | Description |
|-----------|---------------|---------|-------------|
| App → Machine | NUS RX | STOP_WORKOUT | End workout, release tension |

**Behavior**: Machine releases cable tension at bottom of final rep.

### 5.3 Skip/Adjust (During Workout)
| Direction | Characteristic | Command | Description |
|-----------|---------------|---------|-------------|
| App → Machine | NUS RX | ADJUST_LOAD | Change weight mid-workout |
| App → Machine | NUS RX | SKIP_SET | Skip to next set |

---

## 6. Functional Flow 5: Disconnection & Cleanup

### 6.1 Graceful Disconnect
| Step | Direction | Action | Description |
|------|-----------|--------|-------------|
| 1 | App | Stop Polling | Cancel metric read coroutine |
| 2 | App | Cleanup | Clear cached characteristics |
| 3 | App → Machine | Close GATT | Close BLE connection |
| 4 | App | Release Manager | Close BleManager instance |

**Critical Fix (v0.6.1-beta)**: Old BleManager must be fully closed before creating new one to prevent dangling GATT connections (~12 second disconnect loops).

### 6.2 Reconnection Sequence
| Step | Action | Description |
|------|--------|-------------|
| 1 | stopPolling() | Stop any active polling |
| 2 | cleanup() | Clear characteristic references |
| 3 | close() | Close existing BleManager |
| 4 | Create new BleManager | Fresh instance |
| 5 | Connect | Standard connection flow |

---

## 7. Error Handling & Edge Cases

### 7.1 Common Errors

| Error | Cause | Solution |
|-------|-------|----------|
| "NUS RX characteristic not available" | Commands sent before BLE init complete | Wait for all 25 operations to complete |
| Supervision Timeout | BLE queue flooding (Android 16) | Use suspend-based sequential reads |
| ~12 second disconnect loops | Dangling GATT connection on reconnect | Close existing BleManager before creating new |

### 7.2 Android 16 / Pixel 7 Specific Issues
- **Root Cause**: Android 16 enforces stricter BLE supervision timeout
- **Symptom**: Disconnects after workout initiation
- **Fix**: Convert polling from timer-based to suspend-based sequential reads

---

## 8. Hardware-Specific Protocol Variations

### 8.1 Euclid (VIT-200) vs Trainer+ (VIT-300)

| Feature | Euclid (VIT-200) | Trainer+ (VIT-300) |
|---------|------------------|-------------------|
| Max Resistance | 200 kg (440 lbs) | 220 kg (485 lbs) |
| Eccentric Mode | Known issues | Fully supported |
| Device Name | Vee_* | Vee_* |

### 8.2 Eccentric Mode Limitations
- Euclid may have protocol differences for eccentric-only mode
- Enhanced logging added to diagnose eccentric load transmission

---

## 9. Protocol Constants Summary

### 9.1 BLE Configuration
```kotlin
object BleConstants {
    const val NUS_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    const val NUS_RX_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
    const val NUS_TX_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
    
    const val DEVICE_NAME_PREFIX = "Vee_"
    const val MTU_SIZE = 512
    const val POLLING_INTERVAL_MS = 100
    const val TOTAL_BLE_OPERATIONS = 25  // 1 MTU + 24 notifications
}
```

### 9.2 Workout Mode Enumeration
```kotlin
enum class WorkoutMode(val value: Int) {
    OLD_SCHOOL(0),      // Constant resistance both phases
    PUMP(1),            // Speed-reactive resistance
    TUT(2),             // Time Under Tension
    TUT_BEAST(3),       // Enhanced TUT
    ECCENTRIC_ONLY(4),  // Eccentric-only loading
    ECHO(5)             // Adaptive real-time adjustment
}
```

### 9.3 Echo Mode Difficulty Levels
```kotlin
enum class EchoLevel(val levelValue: Int) {
    HARD(0),
    HARDER(1),      // Default
    HARDEST(2),
    EPIC(3)
}
```

---

## 10. Data Flow Diagram

```
┌─────────────┐                    ┌─────────────────┐
│  Android    │                    │   Vitruvian     │
│    App      │                    │    Machine      │
└──────┬──────┘                    └────────┬────────┘
       │                                    │
       │  1. Scan for "Vee_*" devices       │
       │─────────────────────────────────────>
       │                                    │
       │  2. Connect (GATT)                 │
       │─────────────────────────────────────>
       │                                    │
       │  3. Request MTU (512)              │
       │─────────────────────────────────────>
       │                                    │
       │  4. Discover Services              │
       │─────────────────────────────────────>
       │                                    │
       │  5. Enable 24 Notifications        │
       │─────────────────────────────────────>
       │                                    │
       │  [ConnectionStatus.Ready]          │
       │                                    │
       │  6. SET_MODE (workout type)        │
       │─────────────────────────────────────>
       │  (NUS RX)                          │
       │                                    │
       │  7. SET_LOAD (weight)              │
       │─────────────────────────────────────>
       │                                    │
       │  8. SET_REPS (warmup + target)     │
       │─────────────────────────────────────>
       │                                    │
       │  9. START_WORKOUT                  │
       │─────────────────────────────────────>
       │                                    │
       │  10. Monitor Data (polling)        │
       │<─────────────────────────────────────
       │  (load, position, velocity, power) │
       │                                    │
       │  11. Rep Notifications             │
       │<─────────────────────────────────────
       │  (REP_PENDING, REP_COMPLETE)       │
       │                                    │
       │  12. STOP_WORKOUT                  │
       │─────────────────────────────────────>
       │                                    │
       │  13. Disconnect                    │
       │─────────────────────────────────────>
       │                                    │
```

---

## 11. Notes & Caveats

1. **Exact byte values for commands** (START_WORKOUT, SET_MODE, etc.) are implemented in `ProtocolBuilder.kt` - this document describes the logical protocol; exact opcodes require source code review.

2. **Monitor characteristic UUID** and additional custom service UUIDs may exist beyond NUS - the machine appears to use custom Vitruvian-specific services for metrics.

3. **Database v24** introduced storage of machine status flags per metric sample for enhanced diagnostics.

4. **Audio interruption fix** (v0.6.2-beta): SoundPool uses `USAGE_ASSISTANCE_SONIFICATION` to avoid stealing audio focus from background apps.

---

*Document generated from VitruvianProjectPhoenix release notes, issues, and community documentation.*  
*Last updated: November 2025*
