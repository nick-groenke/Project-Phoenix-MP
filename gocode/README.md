# gocode

Standalone Go prototype for macOS BLE + terminal UX experiments.

## vitruvian-spike (CLI)

Feasibility spike that:
- Scans for candidate Vitruvian devices (`Vee_*`, `VIT*`, `Vitruvian*`)
- Connects and verifies required characteristics (TX write, Monitor read, Reps notify)
- Polls Monitor via sequential `read()` with timeouts
- Subscribes to reps notifications (if available)

### Usage

```bash
cd gocode
go run ./cmd/vitruvian-spike scan --duration 6s
go run ./cmd/vitruvian-spike connect --name-substr Vee_ --monitor 30s
# or (recommended) connect by the exact addr from scan:
go run ./cmd/vitruvian-spike connect --addr <addr> --monitor 30s
# show raw monitor payloads too:
go run ./cmd/vitruvian-spike connect --addr <addr> --format both
```

Safety:
- This spike does not send motor-start (`0x03`) commands.
- Use `--soft-stop-now` or `--reset-now` only if you understand their effects.
