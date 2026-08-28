package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"sort"
	"strings"
	"syscall"
	"time"

	"github.com/go-ble/ble"
	"github.com/go-ble/ble/examples/lib/dev"

	"github.com/nick-groenke/Project-Phoenix-MP/gocode/internal/serial"
	"github.com/nick-groenke/Project-Phoenix-MP/gocode/internal/vitruvian"
	"github.com/nick-groenke/Project-Phoenix-MP/gocode/justlift"
	"github.com/nick-groenke/Project-Phoenix-MP/gocode/protocol"
	"github.com/nick-groenke/Project-Phoenix-MP/gocode/session"
	"github.com/nick-groenke/Project-Phoenix-MP/gocode/telemetry"
)

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, "error:", err)
		os.Exit(1)
	}
}

func run(args []string) error {
	if len(args) == 0 {
		usage()
		return errors.New("missing command")
	}

	dev, err := dev.NewDevice("default")
	if err != nil {
		return fmt.Errorf("init BLE device: %w", err)
	}
	ble.SetDefaultDevice(dev)

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	switch args[0] {
	case "scan":
		return cmdScan(ctx, args[1:])
	case "connect":
		return cmdConnect(ctx, args[1:])
	case "just-lift":
		return cmdJustLift(ctx, args[1:])
	default:
		usage()
		return fmt.Errorf("unknown command: %s", args[0])
	}
}

func usage() {
	fmt.Fprint(os.Stderr, `vitruvian-spike

Usage:
  vitruvian-spike scan [flags]
  vitruvian-spike connect [flags]
  vitruvian-spike just-lift [flags]

Commands:
  scan     Scan and list candidate devices
  connect  Connect and verify characteristics + stream telemetry
  just-lift  Run headless “Just Lift” orchestrator (no TUI)
`)
}

func cmdScan(ctx context.Context, args []string) error {
	fs := flag.NewFlagSet("scan", flag.ContinueOnError)
	fs.SetOutput(os.Stderr)
	duration := fs.Duration("duration", 6*time.Second, "scan duration")
	allowAll := fs.Bool("all", false, "list all advertisements (not just candidate names)")
	verbose := fs.Bool("verbose", false, "print every advertisement (default prints once per device + summary)")
	if err := fs.Parse(args); err != nil {
		return err
	}

	ctx, cancel := context.WithTimeout(ctx, *duration)
	defer cancel()

	fmt.Fprintln(os.Stdout, "Scanning… (Ctrl+C to stop)")
	seen := map[string]vitruvian.SeenDevice{}
	err := ble.Scan(ctx, false, func(a ble.Advertisement) {
		name := strings.TrimSpace(a.LocalName())
		if !*allowAll && !vitruvian.IsCandidateName(name) {
			return
		}
		key := a.Addr().String()
		if _, ok := seen[key]; !ok && !*verbose {
			vitruvian.PrintAdvertisement(os.Stdout, a)
		}
		seen[key] = vitruvian.SeenDevice{
			Addr: key,
			Name: name,
			RSSI: a.RSSI(),
		}
		if *verbose {
			vitruvian.PrintAdvertisement(os.Stdout, a)
		}
	}, nil)
	if errors.Is(err, context.DeadlineExceeded) || errors.Is(err, context.Canceled) {
		err = nil
	}
	if err != nil {
		return err
	}

	if len(seen) == 0 {
		fmt.Fprintln(os.Stdout, "No matching devices found.")
		return nil
	}

	devices := make([]vitruvian.SeenDevice, 0, len(seen))
	for _, d := range seen {
		devices = append(devices, d)
	}
	sort.Slice(devices, func(i, j int) bool { return devices[i].RSSI > devices[j].RSSI })

	fmt.Fprintln(os.Stdout, "\nFound devices:")
	for _, d := range devices {
		fmt.Fprintf(os.Stdout, "%s  rssi=%4d  name=%q\n", d.Addr, d.RSSI, d.Name)
	}
	fmt.Fprintln(os.Stdout, "\nTip: connect with:")
	fmt.Fprintln(os.Stdout, "  vitruvian-spike connect --addr <addr> --monitor 30s")
	return nil
}

func cmdConnect(ctx context.Context, args []string) error {
	fs := flag.NewFlagSet("connect", flag.ContinueOnError)
	fs.SetOutput(os.Stderr)
	addr := fs.String("addr", "", "device address to connect to (as printed by scan)")
	nameSubstr := fs.String("name-substr", "", "connect to the first device whose name contains this substring")
	connectTimeout := fs.Duration("connect-timeout", 12*time.Second, "timeout for the connect scan+connect phase")
	dialTimeout := fs.Duration("dial-timeout", 12*time.Second, "timeout for the dial/connect operation once a device is chosen")
	monitorFor := fs.Duration("monitor", 20*time.Second, "how long to stream monitor reads for")
	readTimeout := fs.Duration("read-timeout", 1500*time.Millisecond, "monitor read timeout")
	printInterval := fs.Duration("print-interval", 250*time.Millisecond, "minimum time between printed samples (does not affect read rate)")
	format := fs.String("format", "decode", "output format for monitor samples: decode|raw|both")
	maxReadFailures := fs.Int("max-read-failures", 3, "disconnect after this many consecutive monitor read failures")
	softStopNow := fs.Bool("soft-stop-now", false, "send soft stop (0x50 0x00) immediately after connect")
	resetNow := fs.Bool("reset-now", false, "send reset/hard stop (0x0A 00 00 00) immediately after connect")
	dryRun := fs.Bool("dry-run", true, "enable dry-run guard (suppresses motor-start writes)")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if *addr == "" && *nameSubstr == "" {
		return errors.New("provide --addr or --name-substr")
	}

	filter := func(a ble.Advertisement) bool {
		name := strings.TrimSpace(a.LocalName())
		if *addr != "" && a.Addr().String() == *addr {
			return true
		}
		if *nameSubstr != "" && strings.Contains(name, *nameSubstr) {
			return true
		}
		return false
	}

	fmt.Fprintf(os.Stdout, "Connecting… (addr=%q name-substr=%q connect-timeout=%s dial-timeout=%s)\n", *addr, *nameSubstr, connectTimeout.String(), dialTimeout.String())

	cln, chosen, err := connectAndDial(ctx, *connectTimeout, *dialTimeout, *addr, *nameSubstr, filter)
	if err != nil {
		return err
	}
	defer cln.CancelConnection()
	fmt.Fprintf(os.Stdout, "Connected to %s\n", chosen)

	exec := serial.New()
	defer exec.Close()

	p, err := cln.DiscoverProfile(true)
	if err != nil {
		return fmt.Errorf("discover profile: %w", err)
	}

	tx, monitor, reps := protocol.FindRequiredCharacteristics(p)
	if tx == nil || monitor == nil || reps == nil {
		return fmt.Errorf("missing required characteristics: tx=%v monitor=%v reps=%v", tx != nil, monitor != nil, reps != nil)
	}

	fmt.Fprintln(os.Stdout, "Verified characteristics:")
	fmt.Fprintf(os.Stdout, "  TX write:    %s\n", tx.UUID.String())
	fmt.Fprintf(os.Stdout, "  Monitor read:%s\n", monitor.UUID.String())
	fmt.Fprintf(os.Stdout, "  Reps notify: %s\n", reps.UUID.String())

	txw := vitruvian.TXWriter{
		Client: cln,
		TX:     tx,
		Exec:   exec,
		DryRun: *dryRun,
	}

	if err := exec.Do(ctx, func(ctx context.Context) error {
		return cln.Subscribe(reps, false, func(b []byte) {
			ev, err := telemetry.ParseReps(b)
			if err != nil {
				fmt.Fprintf(os.Stdout, "reps notify (unparsed): %s\n", vitruvian.Hex(b))
				return
			}
			printReps(os.Stdout, ev)
		})
	}); err != nil {
		return fmt.Errorf("subscribe reps notify: %w", err)
	}

	if *softStopNow {
		if err := txw.WriteWithResponse(ctx, protocol.CmdSoftStop()); err != nil {
			return fmt.Errorf("soft stop write: %w", err)
		}
	}
	if *resetNow {
		if err := txw.WriteWithResponse(ctx, protocol.CmdReset()); err != nil {
			return fmt.Errorf("reset write: %w", err)
		}
	}

	monitorCtx, cancel := context.WithTimeout(ctx, *monitorFor)
	defer cancel()

	var lastPrint time.Time
	var lastAt time.Time
	var lastMon telemetry.MonitorSample
	hasLast := false
	failures := 0
	for {
		if err := monitorCtx.Err(); err != nil {
			return nil
		}

		readCtx, readCancel := context.WithTimeout(monitorCtx, *readTimeout)
		buf, err := execRead(readCtx, exec, cln, monitor)
		readCancel()

		if err != nil {
			failures++
			fmt.Fprintf(os.Stderr, "monitor read error (%d/%d): %v\n", failures, *maxReadFailures, err)
			if failures >= *maxReadFailures {
				fmt.Fprintln(os.Stderr, "too many monitor read failures; disconnecting")
				return nil
			}
			continue
		}
		failures = 0

		now := time.Now()
		if lastPrint.IsZero() || now.Sub(lastPrint) >= *printInterval {
			s, err := telemetry.ParseMonitor(buf)
			if err != nil {
				fmt.Fprintf(os.Stdout, "monitor (unparsed): %s\n", vitruvian.Hex(buf))
			} else {
				va, vb := float32(0), float32(0)
				if hasLast {
					dt := now.Sub(lastAt).Seconds()
					if dt > 0 {
						va = float32(float64(s.PosAcm-lastMon.PosAcm) / dt)
						vb = float32(float64(s.PosBcm-lastMon.PosBcm) / dt)
					}
				}
				printMonitor(os.Stdout, *format, buf, s, va, vb)
				lastAt = now
				lastMon = s
				hasLast = true
			}
			lastPrint = now
		}
	}
}

func cmdJustLift(ctx context.Context, args []string) error {
	fs := flag.NewFlagSet("just-lift", flag.ContinueOnError)
	fs.SetOutput(os.Stderr)
	addr := fs.String("addr", "", "device address to connect to (as printed by scan)")
	nameSubstr := fs.String("name-substr", "", "connect to the first device whose name contains this substring")
	connectTimeout := fs.Duration("connect-timeout", 12*time.Second, "timeout for the connect scan+connect phase")
	dialTimeout := fs.Duration("dial-timeout", 12*time.Second, "timeout for the dial/connect operation once a device is chosen")
	weightLb := fs.Int("weight-lb", 10, "per-cable weight in lb (10-100 step 1)")
	arm := fs.Bool("arm", false, "arm the session (enables auto start/stop behavior)")
	dryRun := fs.Bool("dry-run", true, "enable dry-run guard (suppresses motor-start writes)")
	n := fs.Duration("n", 3*time.Second, "hold steady duration before calibration (start trigger)")
	j := fs.Duration("j", 2*time.Second, "hold bottom duration to end set")
	printTelemetry := fs.Bool("print-telemetry", false, "print parsed monitor/reps events (very chatty)")
	if err := fs.Parse(args); err != nil {
		return err
	}
	if *addr == "" && *nameSubstr == "" {
		return errors.New("provide --addr or --name-substr")
	}
	if err := justlift.ValidateWeightLb(*weightLb); err != nil {
		return err
	}

	filter := func(a ble.Advertisement) bool {
		name := strings.TrimSpace(a.LocalName())
		if *addr != "" && a.Addr().String() == *addr {
			return true
		}
		if *nameSubstr != "" && strings.Contains(name, *nameSubstr) {
			return true
		}
		return false
	}

	fmt.Fprintf(os.Stdout, "Connecting… (addr=%q name-substr=%q)\n", *addr, *nameSubstr)
	cln, chosen, err := connectAndDial(ctx, *connectTimeout, *dialTimeout, *addr, *nameSubstr, filter)
	if err != nil {
		return err
	}
	defer cln.CancelConnection()
	fmt.Fprintf(os.Stdout, "Connected to %s\n", chosen)

	exec := serial.New()
	defer exec.Close()

	p, err := cln.DiscoverProfile(true)
	if err != nil {
		return fmt.Errorf("discover profile: %w", err)
	}

	tx, monitor, reps := protocol.FindRequiredCharacteristics(p)
	if tx == nil || monitor == nil || reps == nil {
		return fmt.Errorf("missing required characteristics: tx=%v monitor=%v reps=%v", tx != nil, monitor != nil, reps != nil)
	}

	sessCfg := session.DefaultConfig()
	sessCfg.HoldSteady = *n
	sessCfg.HoldBottom = *j

	o, err := justlift.New(
		justlift.Config{
			StartArmed: *arm,
			Session: justlift.ControllerConfig{
				Session:          sessCfg,
				WeightPerCableLb: *weightLb,
			},
		},
		justlift.BLEDeps{
			Client:  cln,
			TX:      tx,
			Monitor: monitor,
			Reps:    reps,
			Exec:    exec,
			DryRun:  *dryRun,
		},
	)
	if err != nil {
		return err
	}

	fmt.Fprintf(os.Stdout, "Just Lift running (armed=%v dry-run=%v weight=%dlb/cable n=%s j=%s). Ctrl+C to stop.\n", *arm, *dryRun, *weightLb, n.String(), j.String())

	donePrinting := make(chan struct{})
	go func() {
		defer close(donePrinting)
		for ev := range o.Events() {
			if !*printTelemetry && (ev.Type == justlift.EventMonitorSample || ev.Type == justlift.EventRepsEvent) {
				continue
			}
			fmt.Fprintf(os.Stdout, "%s  %s\n", ev.At.Format("15:04:05.000"), ev.String())
		}
	}()

	err = o.Run(ctx)
	<-donePrinting
	return err
}

func execRead(ctx context.Context, exec *serial.Executor, cln ble.Client, c *ble.Characteristic) ([]byte, error) {
	var out []byte
	err := exec.Do(ctx, func(ctx context.Context) error {
		b, err := cln.ReadCharacteristic(c)
		if err != nil {
			return err
		}
		out = append([]byte(nil), b...)
		return nil
	})
	return out, err
}

var errNoMatchingAdvertisement = errors.New("no matching advertisement found")

func connectAndDial(
	ctx context.Context,
	connectTimeout time.Duration,
	dialTimeout time.Duration,
	addr string,
	nameSubstr string,
	filter ble.AdvFilter,
) (ble.Client, string, error) {
	if addr != "" {
		fmt.Fprintf(os.Stdout, "Dialing by addr: %s\n", addr)
		dialCtx, cancel := context.WithTimeout(ctx, dialTimeout)
		defer cancel()
		cln, err := ble.Dial(dialCtx, ble.NewAddr(addr))
		if err == nil {
			return cln, addr, nil
		}
		fmt.Fprintf(os.Stderr, "dial by addr failed: %v\n", err)
		fmt.Fprintln(os.Stderr, "Falling back to scan+dial…")
	}

	scanCtx, cancel := context.WithTimeout(ctx, connectTimeout)
	defer cancel()

	adv, err := scanForFirstMatch(scanCtx, filter)
	if err != nil {
		if errors.Is(err, errNoMatchingAdvertisement) && nameSubstr != "" {
			return nil, "", fmt.Errorf("no advertisements matched --name-substr=%q within %s", nameSubstr, connectTimeout.String())
		}
		if errors.Is(err, errNoMatchingAdvertisement) && addr != "" {
			return nil, "", fmt.Errorf("no advertisements matched --addr=%q within %s", addr, connectTimeout.String())
		}
		return nil, "", err
	}

	chosen := adv.Addr().String()
	fmt.Fprintf(os.Stdout, "Dialing discovered device: %s name=%q\n", chosen, strings.TrimSpace(adv.LocalName()))
	dialCtx, cancelDial := context.WithTimeout(ctx, dialTimeout)
	defer cancelDial()
	cln, err := ble.Dial(dialCtx, adv.Addr())
	if err != nil {
		return nil, "", fmt.Errorf("dial: %w", err)
	}
	return cln, chosen, nil
}

func scanForFirstMatch(ctx context.Context, filter ble.AdvFilter) (ble.Advertisement, error) {
	ctx2, cancel := context.WithCancel(ctx)
	defer cancel()

	found := make(chan ble.Advertisement, 1)
	handler := func(a ble.Advertisement) {
		select {
		case found <- a:
			cancel()
		default:
		}
	}

	err := ble.Scan(ctx2, false, handler, filter)
	select {
	case a := <-found:
		return a, nil
	default:
	}

	if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
		return nil, errNoMatchingAdvertisement
	}
	if err != nil {
		return nil, err
	}
	return nil, errNoMatchingAdvertisement
}

func printMonitor(w *os.File, format string, raw []byte, s telemetry.MonitorSample, velA, velB float32) {
	switch format {
	case "raw":
		fmt.Fprintf(w, "monitor: %s\n", vitruvian.Hex(raw))
	case "both":
		fmt.Fprintf(w, "monitor: %s\n", vitruvian.Hex(raw))
		fallthrough
	case "decode":
		if s.HasStatus {
			flags := telemetry.DecodeStatus(s.Status)
			if len(flags) == 0 {
				fmt.Fprintf(w, "ticks=%d posA=%.1fcm loadA=%.2fkg velA=%.0fcm/s | posB=%.1fcm loadB=%.2fkg velB=%.0fcm/s status=0x%04x\n",
					s.Ticks, s.PosAcm, s.LoadAkg, velA, s.PosBcm, s.LoadBkg, velB, s.Status)
				return
			}
			fmt.Fprintf(w, "ticks=%d posA=%.1fcm loadA=%.2fkg velA=%.0fcm/s | posB=%.1fcm loadB=%.2fkg velB=%.0fcm/s status=0x%04x %v\n",
				s.Ticks, s.PosAcm, s.LoadAkg, velA, s.PosBcm, s.LoadBkg, velB, s.Status, flags)
			return
		}
		fmt.Fprintf(w, "ticks=%d posA=%.1fcm loadA=%.2fkg velA=%.0fcm/s | posB=%.1fcm loadB=%.2fkg velB=%.0fcm/s\n",
			s.Ticks, s.PosAcm, s.LoadAkg, velA, s.PosBcm, s.LoadBkg, velB)
	default:
		fmt.Fprintf(w, "monitor: %s\n", vitruvian.Hex(raw))
	}
}

func printReps(w *os.File, e telemetry.RepsEvent) {
	switch e.Format {
	case "official24":
		fmt.Fprintf(w, "reps: up=%d down=%d warmup=%d/%d set=%d/%d rangeTop=%.1f rangeBottom=%.1f\n",
			e.UpCounter, e.DownCounter, e.RepsRomCount, e.RepsRomTotal, e.RepsSetCount, e.RepsSetTotal, e.RangeTop, e.RangeBottom)
	case "legacy6":
		fmt.Fprintf(w, "reps: legacy top=%d complete=%d\n", e.LegacyTopCounter, e.LegacyCompleteCounter)
	default:
		fmt.Fprintf(w, "reps notify (unparsed): %+v\n", e)
	}
}
