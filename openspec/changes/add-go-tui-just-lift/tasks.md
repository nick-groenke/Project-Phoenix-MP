## 1. Proposal / Safety Gate
- [x] 1.1 Confirm “no keypress after pickup” semantics allow explicit pre-arm before pickup
- [x] 1.2 Define conservative defaults (`n=3s`, `j=2s`, “one cable counts”, weight `10–100 lb` step `1 lb`) and failure behaviors (stop policy: auto soft stop on disconnect/stall)
- [x] 1.3 Define dry-run mode behavior (block `0x03` motor start; allow non-start writes; always allow stop/reset)

## 2. macOS BLE Feasibility Spike (CLI, no TUI)
- [x] 2.1 Choose Go BLE library with reliable CoreBluetooth support on macOS (selected: `github.com/go-ble/ble`)
- [x] 2.2 Scan and list candidate devices (`Vee_*`, `VIT*`, `Vitruvian*`)
- [x] 2.3 Connect and verify GATT UUIDs (TX write, Monitor read, Reps notify)
- [x] 2.4 Enforce strict single-flight GATT operations + timeouts + clean disconnect

## 3. Protocol Building Blocks (Go)
- [x] 3.1 Centralize UUIDs/constants in a Go package
- [x] 3.2 Implement frame builders for `0x03` start, `0x50` soft stop, `0x0A` reset, `0x04` program params
- [x] 3.3 Add a “dry run” transport that blocks motor-start writes

## 4. Telemetry Parsing + State Machine (Headless)
- [x] 4.1 Parse monitor frames (pos/load/optional status) from `gocode/vitruvian-ble-protocol.md`
- [x] 4.2 Compute velocity and rep detection primitives (testable)
- [x] 4.3 Implement Old School hands-free state machine (idle/ready/calibrating/active/ending)

## 5. Just Lift Orchestrator (Still No TUI)
- [ ] 5.1 Implement “start set” sequence (program params → short delay → start), guarded by explicit arming
- [ ] 5.2 Implement “stop set” (soft stop always; reset on fatal / explicit request)
- [ ] 5.3 Add logging + structured events for later TUI integration

## 6. Terminal UI
- [ ] 6.1 Device list + connect/disconnect UX
- [ ] 6.2 Live telemetry panes + session state display
- [ ] 6.3 Keybinds: `S` soft stop, `R` reset, `Enter` arm/disarm, optional tuning controls

## 7. Validation
- [x] 7.1 Unit tests for telemetry parsing + state machine using recorded payloads
- [ ] 7.2 “Dry run” manual test checklist (no `0x03` writes)
