# Change: Add Go (macOS) “Just Lift” TUI + BLE spike

## Why
We need a macOS terminal tool that can safely connect to a Vitruvian device over BLE, stream telemetry, and replicate the “Just Lift” hands-free workflow to keep the hardware usable offline.

## What Changes
- Add a Go-based macOS BLE client (CLI first) to prove end-to-end scan/connect/read/notify/write.
- Add a Go “Just Lift” controller (state machine) that can run hands-free after the user arms a session.
- Add a terminal UI to configure per-cable weight + observe live telemetry and session state.
- Add always-available safety actions (soft stop + reset/hard stop).

## Impact
- Affected specs: `go-just-lift-tui` (new)
- Affected code: `gocode/` (new Go module + TUI), BLE protocol reference `gocode/vitruvian-ble-protocol.md`, frame builders derived from `shared/src/commonMain/kotlin/com/devil/phoenixproject/util/BlePacketFactory.kt`
- Risk: Safety-critical motor control; this change requires conservative defaults and explicit arming before any motor start.

