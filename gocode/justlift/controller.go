package justlift

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/nick-groenke/Project-Phoenix-MP/gocode/internal/vitruvian"
	"github.com/nick-groenke/Project-Phoenix-MP/gocode/protocol"
	"github.com/nick-groenke/Project-Phoenix-MP/gocode/session"
	"github.com/nick-groenke/Project-Phoenix-MP/gocode/telemetry"
)

type Writer interface {
	WriteWithResponse(ctx context.Context, frame []byte) error
}

type ControllerConfig struct {
	Session          session.Config
	WeightPerCableLb int
	StartDelay       time.Duration

	ResetOnFatal bool
}

type Controller struct {
	cfg ControllerConfig
	tx  Writer

	m *session.OldSchoolMachine

	armed          bool
	startAttempted bool

	lastState session.State
	emit      func(Event)
}

func NewController(cfg ControllerConfig, tx Writer, emit func(Event)) (*Controller, error) {
	if tx == nil {
		return nil, errors.New("nil tx writer")
	}
	if emit == nil {
		emit = func(Event) {}
	}
	if err := ValidateWeightLb(cfg.WeightPerCableLb); err != nil {
		return nil, err
	}

	m := session.NewOldSchool(cfg.Session)
	return &Controller{
		cfg:       cfg,
		tx:        tx,
		m:         m,
		lastState: m.State(),
		emit:      emit,
	}, nil
}

func (c *Controller) State() session.State { return c.m.State() }

func (c *Controller) Armed() bool { return c.armed }

func (c *Controller) Arm(at time.Time) {
	if c.armed {
		return
	}
	c.armed = true
	c.emit(Event{At: at, Type: EventArmed})
	c.m.Arm()
	c.processTransitions(context.Background(), at)
}

func (c *Controller) Disarm(ctx context.Context, at time.Time, reason string) error {
	if ctx == nil {
		return errors.New("nil context")
	}
	softErr := c.softStop(ctx, at, reason)
	c.armed = false
	c.startAttempted = false
	c.m.Disarm()
	c.emit(Event{At: at, Type: EventDisarmed, Detail: reason})
	c.processTransitions(ctx, at)
	return softErr
}

func (c *Controller) SoftStop(ctx context.Context, at time.Time, reason string) error {
	return c.Disarm(ctx, at, reason)
}

func (c *Controller) Reset(ctx context.Context, at time.Time, reason string) error {
	if ctx == nil {
		return errors.New("nil context")
	}
	err := c.tx.WriteWithResponse(ctx, protocol.CmdReset())
	c.emit(Event{At: at, Type: EventReset, Detail: reason, Err: err})
	c.armed = false
	c.startAttempted = false
	c.m.Disarm()
	c.emit(Event{At: at, Type: EventDisarmed, Detail: "reset"})
	c.processTransitions(ctx, at)
	return err
}

func (c *Controller) OnMonitor(ctx context.Context, at time.Time, mon telemetry.MonitorSample) error {
	if ctx == nil {
		return errors.New("nil context")
	}
	c.emit(Event{At: at, Type: EventMonitorSample, HasMonitor: true, Monitor: mon})
	c.m.Update(at, mon)
	return c.processTransitions(ctx, at)
}

func (c *Controller) OnReps(ctx context.Context, at time.Time, reps telemetry.RepsEvent) error {
	if ctx == nil {
		return errors.New("nil context")
	}
	c.emit(Event{At: at, Type: EventRepsEvent, HasReps: true, Reps: reps})
	c.m.OnReps(at, reps)
	return c.processTransitions(ctx, at)
}

func (c *Controller) processTransitions(ctx context.Context, at time.Time) error {
	for {
		cur := c.m.State()
		if cur == c.lastState {
			return nil
		}

		from := c.lastState
		to := cur
		c.lastState = to
		c.emit(Event{At: at, Type: EventStateTransition, From: from, To: to})

		if !c.armed {
			continue
		}

		if to == session.Calibrating && !c.startAttempted {
			if err := c.startSet(ctx, at); err != nil {
				c.emit(Event{At: at, Type: EventError, Detail: "start set", Err: err})
				if c.cfg.ResetOnFatal {
					_ = c.Reset(ctx, at, "fatal: start failed")
				} else {
					_ = c.SoftStop(ctx, at, "start failed")
				}
				return err
			}
			continue
		}

		if to == session.Ending {
			_ = c.SoftStop(ctx, at, "ending")
			continue
		}
	}
}

func (c *Controller) startSet(ctx context.Context, at time.Time) error {
	c.startAttempted = true

	kg := float32(float64(c.cfg.WeightPerCableLb) * 0.45359237)
	frame, err := protocol.BuildProgramParams(protocol.ProgramParams{
		Mode:             protocol.ProgramOldSchool,
		WeightPerCableKg: kg,
		IsJustLift:       true,
		IsAMRAP:          true,
	})
	if err != nil {
		return fmt.Errorf("build program params: %w", err)
	}
	if err := c.tx.WriteWithResponse(ctx, frame); err != nil {
		return fmt.Errorf("write program params: %w", err)
	}
	c.emit(Event{At: at, Type: EventSentProgramParams})

	if err := sleepContext(ctx, c.cfg.StartDelay); err != nil {
		return err
	}

	err = c.tx.WriteWithResponse(ctx, protocol.CmdStart())
	if err == nil {
		c.emit(Event{At: at, Type: EventSentStart})
		return nil
	}
	if errors.Is(err, vitruvian.ErrSuppressedMotorStart) {
		c.emit(Event{At: at, Type: EventStartSuppressed})
		return nil
	}
	return fmt.Errorf("write start: %w", err)
}

func (c *Controller) softStop(ctx context.Context, at time.Time, reason string) error {
	err := c.tx.WriteWithResponse(ctx, protocol.CmdSoftStop())
	c.emit(Event{At: at, Type: EventSoftStop, Detail: reason, Err: err})
	return err
}

func sleepContext(ctx context.Context, d time.Duration) error {
	if d <= 0 {
		return nil
	}
	t := time.NewTimer(d)
	defer t.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-t.C:
		return nil
	}
}
