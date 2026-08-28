package justlift

import (
	"context"
	"sync"
	"testing"
	"time"

	"github.com/nick-groenke/Project-Phoenix-MP/gocode/internal/vitruvian"
	"github.com/nick-groenke/Project-Phoenix-MP/gocode/protocol"
	"github.com/nick-groenke/Project-Phoenix-MP/gocode/session"
	"github.com/nick-groenke/Project-Phoenix-MP/gocode/telemetry"
)

type fakeWriter struct {
	mu     sync.Mutex
	writes [][]byte

	errOnStart error
}

func (w *fakeWriter) WriteWithResponse(ctx context.Context, frame []byte) error {
	w.mu.Lock()
	w.writes = append(w.writes, append([]byte(nil), frame...))
	w.mu.Unlock()

	if len(frame) > 0 && frame[0] == 0x03 && w.errOnStart != nil {
		return w.errOnStart
	}
	return nil
}

func (w *fakeWriter) Frames() [][]byte {
	w.mu.Lock()
	defer w.mu.Unlock()
	out := make([][]byte, 0, len(w.writes))
	for _, f := range w.writes {
		out = append(out, append([]byte(nil), f...))
	}
	return out
}

func TestController_StartSequence_ProgramParamsThenStart(t *testing.T) {
	tx := &fakeWriter{}
	var eventsMu sync.Mutex
	var events []Event
	emit := func(ev Event) {
		eventsMu.Lock()
		events = append(events, ev)
		eventsMu.Unlock()
	}

	sess := session.DefaultConfig()
	sess.HoldSteady = 2 * time.Millisecond
	sess.PickupPosCm = 1.0
	sess.SteadyVelCmPerS = 1000.0

	c, err := NewController(ControllerConfig{
		Session:          sess,
		WeightPerCableLb: 10,
		StartDelay:       0,
	}, tx, emit)
	if err != nil {
		t.Fatalf("NewController: %v", err)
	}

	t0 := time.Unix(0, 0)
	c.Arm(t0)

	mon := telemetry.MonitorSample{PosAcm: 2.0, PosBcm: 0.0}
	if err := c.OnMonitor(context.Background(), t0.Add(0*time.Millisecond), mon); err != nil {
		t.Fatalf("OnMonitor#1: %v", err)
	}
	if err := c.OnMonitor(context.Background(), t0.Add(1*time.Millisecond), mon); err != nil {
		t.Fatalf("OnMonitor#2: %v", err)
	}
	if err := c.OnMonitor(context.Background(), t0.Add(3*time.Millisecond), mon); err != nil {
		t.Fatalf("OnMonitor#3: %v", err)
	}

	frames := tx.Frames()
	if len(frames) != 2 {
		t.Fatalf("writes = %d, want 2", len(frames))
	}
	if frames[0][0] != 0x04 {
		t.Fatalf("first write opcode = 0x%02x, want 0x04 (program params)", frames[0][0])
	}
	if frames[1][0] != 0x03 {
		t.Fatalf("second write opcode = 0x%02x, want 0x03 (start)", frames[1][0])
	}

	_ = events
}

func TestController_StartSuppressed_IsNotFatal(t *testing.T) {
	tx := &fakeWriter{errOnStart: vitruvian.ErrSuppressedMotorStart}
	var gotSuppressed bool
	emit := func(ev Event) {
		if ev.Type == EventStartSuppressed {
			gotSuppressed = true
		}
	}

	sess := session.DefaultConfig()
	sess.HoldSteady = 1 * time.Millisecond
	sess.PickupPosCm = 1.0
	sess.SteadyVelCmPerS = 1000.0

	c, err := NewController(ControllerConfig{
		Session:          sess,
		WeightPerCableLb: 10,
		StartDelay:       0,
	}, tx, emit)
	if err != nil {
		t.Fatalf("NewController: %v", err)
	}

	t0 := time.Unix(0, 0)
	c.Arm(t0)

	mon := telemetry.MonitorSample{PosAcm: 2.0, PosBcm: 0.0}
	if err := c.OnMonitor(context.Background(), t0, mon); err != nil {
		t.Fatalf("OnMonitor#1: %v", err)
	}
	if err := c.OnMonitor(context.Background(), t0.Add(2*time.Millisecond), mon); err != nil {
		t.Fatalf("OnMonitor#2: %v", err)
	}
	if err := c.OnMonitor(context.Background(), t0.Add(4*time.Millisecond), mon); err != nil {
		t.Fatalf("OnMonitor#3: %v", err)
	}

	frames := tx.Frames()
	if len(frames) != 2 {
		t.Fatalf("writes = %d, want 2", len(frames))
	}
	if frames[0][0] != 0x04 || frames[1][0] != 0x03 {
		t.Fatalf("write opcodes = [0x%02x 0x%02x], want [0x04 0x03]", frames[0][0], frames[1][0])
	}
	if !gotSuppressed {
		t.Fatalf("expected EventStartSuppressed")
	}
}

func TestController_Ending_SoftStopThenDisarm(t *testing.T) {
	tx := &fakeWriter{}
	emit := func(Event) {}

	sess := session.DefaultConfig()
	sess.HoldSteady = 1 * time.Millisecond
	sess.HoldBottom = 2 * time.Millisecond
	sess.PickupPosCm = 1.0
	sess.SteadyVelCmPerS = 1000.0
	sess.CalibrationRepsTarget = 1
	sess.BelowBottomCm = 0

	c, err := NewController(ControllerConfig{
		Session:          sess,
		WeightPerCableLb: 10,
		StartDelay:       0,
	}, tx, emit)
	if err != nil {
		t.Fatalf("NewController: %v", err)
	}

	t0 := time.Unix(0, 0)
	c.Arm(t0)

	monUp := telemetry.MonitorSample{PosAcm: 2.0, PosBcm: 0.0}
	if err := c.OnMonitor(context.Background(), t0, monUp); err != nil {
		t.Fatalf("OnMonitor#1: %v", err)
	}
	if err := c.OnMonitor(context.Background(), t0.Add(2*time.Millisecond), monUp); err != nil {
		t.Fatalf("OnMonitor#2: %v", err)
	}
	if err := c.OnMonitor(context.Background(), t0.Add(4*time.Millisecond), monUp); err != nil {
		t.Fatalf("OnMonitor#3: %v", err)
	}

	reps := telemetry.RepsEvent{Format: "official24", RepsRomCount: 1, RangeBottom: 2.0}
	if err := c.OnReps(context.Background(), t0.Add(5*time.Millisecond), reps); err != nil {
		t.Fatalf("OnReps: %v", err)
	}

	monBottom := telemetry.MonitorSample{PosAcm: 2.0, PosBcm: 2.0}
	if err := c.OnMonitor(context.Background(), t0.Add(6*time.Millisecond), monBottom); err != nil {
		t.Fatalf("OnMonitor#4: %v", err)
	}
	if err := c.OnMonitor(context.Background(), t0.Add(9*time.Millisecond), monBottom); err != nil {
		t.Fatalf("OnMonitor#5: %v", err)
	}

	frames := tx.Frames()
	if len(frames) < 1 {
		t.Fatalf("no writes captured")
	}
	last := frames[len(frames)-1]
	if len(last) != len(protocol.CmdSoftStop()) || last[0] != protocol.CmdSoftStop()[0] || last[1] != protocol.CmdSoftStop()[1] {
		t.Fatalf("last write = %v, want soft stop %v", last, protocol.CmdSoftStop())
	}
	if c.Armed() {
		t.Fatalf("controller should be disarmed")
	}
	if c.State() != session.Idle {
		t.Fatalf("state = %v, want Idle", c.State())
	}
}
