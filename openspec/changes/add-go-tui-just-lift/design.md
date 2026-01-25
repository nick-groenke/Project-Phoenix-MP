## Context
This change adds a Go-based terminal tool under `gocode/` to control a Vitruvian device over BLE on macOS. It is safety-critical: any inadvertent motor start or delayed stop could cause injury.

## Goals / Non-Goals
- Goals:
  - Prove macOS BLE feasibility in Go (scan/connect/read/notify/write).
  - Implement “Just Lift” Old School hands-free workflow after explicit user arming.
  - Provide always-available stop/reset actions and conservative fail-safe behaviors.
- Non-Goals:
  - Full parity with every official app mode beyond “Just Lift / Old School”.
  - Reverse-engineering every unknown BLE command up-front (spike + iterative capture is expected).

## Decisions
- Explicit arming gate:
  - Motor-start (`0x03`) MUST be blocked until the user explicitly arms a session.
  - Once armed, the session should be hands-free (no keypress required after handle pickup).
- Concurrency model:
  - Enforce strict single-flight GATT operations (no concurrent reads/writes/notify configuration).
- Development safety:
  - Provide a dry-run mode that never emits motor-start writes, even if the state machine would start.

## Proposed Defaults (pending confirmation)
- Start trigger: “hold steady-ish” for `n = 3s` after pickup.
- End trigger: hold at bottom for `j = 2s`.
- One-cable semantics: either cable meeting a trigger condition counts (pickup/steady/bottom).
- Weight configuration (per-cable):
  - Step size: `1 lb`
  - Minimum: `10 lb`
  - Maximum: `100 lb`
- Calibration weight: use device minimum (user-estimated ~8 lb per cable; to be verified on-device).
- Stop policy (confirmed):
  - `S` performs Soft Stop (send `0x50 0x00`) and is always available.
  - `R` performs Reset/Hard Stop (send `0x0A 00 00 00`) and is always available.
  - On disconnect or telemetry stall while armed: automatically send Soft Stop, then disarm.
- BLE health monitoring (proposed; tune during spike):
  - Monitor telemetry is considered “stalled” after `3` consecutive read timeouts.
  - On stall: disconnect cleanly and return to safe non-armed state (after auto soft stop if armed).

## Dry-Run Mode (confirmed)
- Purpose: development/testing mode that lets the full hands-free workflow run without ever starting motors.
- Behavior:
  - Motor-start (`0x03`) writes are always suppressed.
  - The app may still send non-start writes (e.g., program params `0x04`) so the control path can be exercised.
  - `S` (Soft Stop) and `R` (Reset/Hard Stop) are always allowed and are not suppressed.
  - The UI MUST clearly indicate “DRY RUN” and MUST surface when a motor-start was suppressed.

## Risks / Trade-offs
- BLE library stability on macOS:
  - Mitigation: feasibility spike first; choose the simplest library that supports CoreBluetooth reliably.
- Unknown calibration semantics:
  - Mitigation: treat calibration as a state machine + telemetry problem first; validate/extend once official traffic is observed.

## Migration Plan
No migration; this is an additive tool in `gocode/`.

## Open Questions
- What exact BLE frames/timing the official app uses for “Old School” calibration and “lightest weight”.
 
## Resolved
- Confirmed: “no keypress after pickup” still allows an explicit “arm” step before pickup.
