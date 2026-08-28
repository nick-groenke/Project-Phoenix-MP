package justlift

import (
	"context"
	"errors"
	"fmt"
	"sync"
	"time"

	"github.com/go-ble/ble"

	"github.com/nick-groenke/Project-Phoenix-MP/gocode/internal/serial"
	"github.com/nick-groenke/Project-Phoenix-MP/gocode/internal/vitruvian"
	"github.com/nick-groenke/Project-Phoenix-MP/gocode/telemetry"
)

var (
	ErrTelemetryStall = errors.New("telemetry stalled")
	ErrDisconnected   = errors.New("BLE disconnected")
)

type Config struct {
	StartArmed bool

	Session ControllerConfig

	MonitorReadTimeout time.Duration
	MonitorInterval    time.Duration
	MaxReadFailures    int

	EventsBuffer int
}

type BLEDeps struct {
	Client  ble.Client
	TX      *ble.Characteristic
	Monitor *ble.Characteristic
	Reps    *ble.Characteristic
	Exec    *serial.Executor

	DryRun bool
}

type Orchestrator struct {
	cfg Config
	d   BLEDeps

	txw vitruvian.TXWriter
	c   *Controller

	events chan Event

	control chan controlReq
}

type controlKind int

const (
	ctrlArm controlKind = iota
	ctrlDisarm
	ctrlSoftStop
	ctrlReset
)

type controlReq struct {
	kind   controlKind
	reason string
	resp   chan error
}

func New(cfg Config, d BLEDeps) (*Orchestrator, error) {
	if d.Client == nil {
		return nil, errors.New("nil BLE client")
	}
	if d.TX == nil || d.Monitor == nil || d.Reps == nil {
		return nil, fmt.Errorf("missing required characteristic(s): tx=%v monitor=%v reps=%v", d.TX != nil, d.Monitor != nil, d.Reps != nil)
	}
	if d.Exec == nil {
		return nil, errors.New("nil executor")
	}

	if cfg.MonitorReadTimeout == 0 {
		cfg.MonitorReadTimeout = 1500 * time.Millisecond
	}
	if cfg.MonitorInterval == 0 {
		cfg.MonitorInterval = 40 * time.Millisecond
	}
	if cfg.MaxReadFailures == 0 {
		cfg.MaxReadFailures = 3
	}
	if cfg.EventsBuffer <= 0 {
		cfg.EventsBuffer = 256
	}
	if cfg.Session.StartDelay == 0 {
		cfg.Session.StartDelay = 50 * time.Millisecond
	}

	events := make(chan Event, cfg.EventsBuffer)
	emit := func(ev Event) {
		select {
		case events <- ev:
		default:
		}
	}

	txw := vitruvian.TXWriter{
		Client: d.Client,
		TX:     d.TX,
		Exec:   d.Exec,
		DryRun: d.DryRun,
	}

	c, err := NewController(cfg.Session, txw, emit)
	if err != nil {
		return nil, err
	}

	return &Orchestrator{
		cfg:     cfg,
		d:       d,
		txw:     txw,
		c:       c,
		events:  events,
		control: make(chan controlReq, 8),
	}, nil
}

func (o *Orchestrator) Events() <-chan Event { return o.events }

func (o *Orchestrator) Arm(ctx context.Context, reason string) error {
	return o.sendControl(ctx, ctrlArm, reason)
}

func (o *Orchestrator) Disarm(ctx context.Context, reason string) error {
	return o.sendControl(ctx, ctrlDisarm, reason)
}

func (o *Orchestrator) SoftStop(ctx context.Context, reason string) error {
	return o.sendControl(ctx, ctrlSoftStop, reason)
}

func (o *Orchestrator) Reset(ctx context.Context, reason string) error {
	return o.sendControl(ctx, ctrlReset, reason)
}

func (o *Orchestrator) sendControl(ctx context.Context, kind controlKind, reason string) error {
	if ctx == nil {
		return errors.New("nil context")
	}
	r := controlReq{
		kind:   kind,
		reason: reason,
		resp:   make(chan error, 1),
	}
	select {
	case <-ctx.Done():
		return ctx.Err()
	case o.control <- r:
	}
	select {
	case <-ctx.Done():
		return ctx.Err()
	case err := <-r.resp:
		return err
	}
}

func (o *Orchestrator) Run(ctx context.Context) (runErr error) {
	if ctx == nil {
		return errors.New("nil context")
	}

	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	if o.cfg.StartArmed {
		o.c.Arm(time.Now())
	}

	monCh := make(chan telemetry.MonitorSample, 16)
	repsCh := make(chan telemetry.RepsEvent, 16)
	errCh := make(chan error, 1)

	var wg sync.WaitGroup
	wg.Add(1)
	go func() {
		defer wg.Done()
		o.monitorLoop(ctx, monCh, errCh)
	}()

	if err := o.subscribeReps(ctx, repsCh); err != nil {
		cancel()
		wg.Wait()
		close(o.events)
		return err
	}
	defer o.unsubscribeReps()

	defer func() {
		cancel()
		wg.Wait()
		o.bestEffortSoftStop()
		close(o.events)
	}()

	for {
		select {
		case <-ctx.Done():
			if errors.Is(ctx.Err(), context.Canceled) {
				return nil
			}
			return ctx.Err()

		case <-o.d.Client.Disconnected():
			o.emit(Event{At: time.Now(), Type: EventDisconnected})
			o.bestEffortSoftStop()
			return ErrDisconnected

		case err := <-errCh:
			if err != nil {
				o.emit(Event{At: time.Now(), Type: EventTelemetryStalled, Err: err})
				o.bestEffortSoftStop()
				return ErrTelemetryStall
			}

		case mon := <-monCh:
			if err := o.c.OnMonitor(ctx, time.Now(), mon); err != nil {
				return err
			}

		case reps := <-repsCh:
			if err := o.c.OnReps(ctx, time.Now(), reps); err != nil {
				return err
			}

		case req := <-o.control:
			at := time.Now()
			var err error
			switch req.kind {
			case ctrlArm:
				o.c.Arm(at)
			case ctrlDisarm:
				err = o.c.Disarm(ctx, at, req.reason)
			case ctrlSoftStop:
				err = o.c.SoftStop(ctx, at, req.reason)
			case ctrlReset:
				err = o.c.Reset(ctx, at, req.reason)
			default:
				err = errors.New("unknown control request")
			}
			req.resp <- err
		}
	}
}

func (o *Orchestrator) emit(ev Event) {
	select {
	case o.events <- ev:
	default:
	}
}

func (o *Orchestrator) monitorLoop(ctx context.Context, out chan<- telemetry.MonitorSample, errCh chan<- error) {
	ticker := time.NewTicker(o.cfg.MonitorInterval)
	defer ticker.Stop()

	failures := 0
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}

		readCtx, cancel := context.WithTimeout(ctx, o.cfg.MonitorReadTimeout)
		buf, err := o.execRead(readCtx, o.d.Monitor)
		cancel()
		if err != nil {
			failures++
			if failures >= o.cfg.MaxReadFailures {
				select {
				case errCh <- err:
				default:
				}
				return
			}
			continue
		}
		failures = 0

		s, err := telemetry.ParseMonitor(buf)
		if err != nil {
			continue
		}
		select {
		case <-ctx.Done():
			return
		case out <- s:
		}
	}
}

func (o *Orchestrator) execRead(ctx context.Context, c *ble.Characteristic) ([]byte, error) {
	var out []byte
	err := o.d.Exec.Do(ctx, func(ctx context.Context) error {
		b, err := o.d.Client.ReadCharacteristic(c)
		if err != nil {
			return err
		}
		out = append([]byte(nil), b...)
		return nil
	})
	return out, err
}

func (o *Orchestrator) subscribeReps(ctx context.Context, out chan<- telemetry.RepsEvent) error {
	return o.d.Exec.Do(ctx, func(ctx context.Context) error {
		return o.d.Client.Subscribe(o.d.Reps, false, func(b []byte) {
			ev, err := telemetry.ParseReps(b)
			if err != nil {
				return
			}
			select {
			case out <- ev:
			default:
			}
		})
	})
}

func (o *Orchestrator) unsubscribeReps() {
	ctx, cancel := context.WithTimeout(context.Background(), 750*time.Millisecond)
	defer cancel()
	_ = o.d.Exec.Do(ctx, func(ctx context.Context) error {
		return o.d.Client.Unsubscribe(o.d.Reps, false)
	})
}

func (o *Orchestrator) bestEffortSoftStop() {
	if !o.c.Armed() {
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 750*time.Millisecond)
	defer cancel()
	_ = o.c.SoftStop(ctx, time.Now(), "shutdown")
}
