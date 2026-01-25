# Go TUI + macOS BLE “Just Lift” — first steps plan

Primary reference: `docs/vitruvian-ble-protocol.md`

## User requirements (captured)

- Primary mode: **Old School**
- Weight configuration: **per-cable kg**
- Rep target: **AMRAP / infinite until user stops**
- UX constraint: **once handles are picked up, no keypresses should be required**
- Baseline UX to emulate (official app):
  1. Enter “Just Lift”, choose Old School, set weight
  2. Lift handles and hold steady for `n` seconds → calibration flow begins
  3. Perform **3 calibration reps** at no/lightest weight
  4. Configured weight engages → user lifts until done
  5. Hold **below calibrated bottom** for `j` seconds → set ends, weight turns off
  6. User can drop/lower handles to let cables retract
- Safety: immediate stop must always be available (soft stop + hard stop/reset)

## Goal

Build a terminal UI in `gocode/` that can connect to a Vitruvian device on macOS, stream telemetry, and replicate the “Just Lift” behaviors (auto-start/stop via handle detection + safe stop/reset commands).

## 0) Spec / scope gate

- Create an OpenSpec change (e.g., `add-go-tui-just-lift`) to lock down:
  - Safety requirements (always-available stop, explicit motor start, conservative defaults)
  - Connection robustness (timeouts, serial GATT ops, failure handling)
  - Exact “Just Lift” semantics to match (or intentionally differ from) the official app:
    - Old School hands-free calibration + AMRAP until “hold at bottom to stop”
    - Clarify whether “no keypress after pickup” still allows an explicit “session enable” before pickup

## 1) macOS feasibility spike (CLI, no TUI)

Deliverable: a tiny Go CLI that proves macOS BLE works end-to-end.

- Scan and list candidate devices by advertised name:
  - `Vee_*`, `VIT*`, `Vitruvian*` (from `docs/vitruvian-ble-protocol.md`)
- Connect to a selected device and verify:
  - TX write characteristic: `6e400002-b5a3-f393-e0a9-e50e24dcca9e` (**Write With Response**)
  - Monitor read characteristic: `90e991a6-c548-44ed-969b-eb541014eae3` (poll via `read()`)
  - Reps notifications: `8308f2a6-0875-4a94-a86f-5c5c5e1b068a` (subscribe to `notify()`)
- Implement strict single-flight GATT operations (no concurrent read/write/notify config).
- Add timeouts (start with ~1500ms for Monitor reads) and a clean disconnect on repeated failures.

## 2) Protocol constants + frame builders (Go)

Deliverable: Go package(s) that centralize UUIDs and command building.

- Port constants & frames from:
  - `docs/vitruvian-ble-protocol.md`
  - `shared/src/commonMain/kotlin/com/devil/phoenixproject/util/BlePacketFactory.kt`
- Minimum commands needed for “Just Lift” parity:
  - Start motors: `0x03 0x00 0x00 0x00`
  - Soft stop: `0x50 0x00`
  - Reset/hard stop: `0x0A 0x00 0x00 0x00`
  - Program params frame (`0x04`, 96 bytes), with reps byte set to `0xFF` for Just Lift / AMRAP

## 3) Telemetry parsing + handle-state machine (no UI)

Deliverable: a headless loop that emits events you can unit test.

- Monitor parse (little-endian) from `docs/vitruvian-ble-protocol.md`:
  - posA/posB (mm), loadA/loadB (kg), optional status word
- Compute velocity and implement a hands-free “Old School” session state machine:
  - `Idle (safe/read-only)` → `Ready` → `Calibrating` → `Active` → `Ending` → `Idle`
  - Detect “hold steady” for `n` seconds to enter `Calibrating` (configurable; pick conservative default).
  - Run **3 calibration reps** at “no/lightest weight” (TBD how to represent/command this on-device).
  - After calibration, engage configured per-cable weight and transition to `Active`.
  - Detect “hold below calibrated bottom” for `j` seconds to end the set and disengage weight.
  - Always keep an immediate “stop” action available regardless of state.
- Unknowns to resolve early (likely by observing the official app’s BLE traffic):
  - How the device/app triggers calibration vs regular Old School start (commands/frames/timing).
  - What “lightest weight” means in BLE params (0kg? minimum non-zero?).
  - Reasonable defaults for `n` and `j`, and how to infer “steady” + “below bottom” from Monitor samples.

## 4) Wire “Just Lift” control flow (still no TUI)

Deliverable: a control orchestrator that matches the intended UX.

- On “start set”:
  - Write `0x04` program params (Just Lift: reps `0xFF`)
  - Delay ~50ms
  - Write `0x03` start
- On “stop”:
  - Always allow immediate soft stop (`0x50 0x00`)
  - Optionally use reset (`0x0A ...`) when ending the session

## 5) Build the TUI

Deliverable: a usable terminal app with clear safety affordances.

- Device list → connect/disconnect
- Live telemetry panes (pos/load/vel, reps if available)
- Session state display (idle/ready/calibrating/active/ending) + key safety/status indicators
- Keybinds:
  - `S`: Soft stop (`0x50 0x00`)
  - `R`: Reset (`0x0A 00 00 00`)
  - `Enter`: Enable/disable “hands-free session” (so no keypress is needed after pickup)
  - Optional: `W`: Edit per-cable weight, `N/J`: adjust `n`/`j` (if we expose tuning in-TUI)

## 6) Test/validation approach

- Unit test the monitor parsing and handle-state machine with recorded byte payloads.
- Add a “dry run” mode that never sends motor-start (`0x03`) to reduce risk during development.
