package session

import (
	"testing"
	"time"

	"github.com/nick-groenke/Project-Phoenix-MP/gocode/telemetry"
)

func TestOldSchoolMachine_HoldSteady_ToCalibration_ToActive_ToEnding(t *testing.T) {
	cfg := DefaultConfig()
	cfg.SteadyVelCmPerS = 1000 // make steady detection easy for this test
	cfg.BelowBottomCm = 0
	cfg.EndingGrace = 0

	m := NewOldSchool(cfg)
	m.Arm()
	if m.State() != Ready {
		t.Fatalf("state=%v want Ready", m.State())
	}

	start := time.Unix(0, 0)
	mon := telemetry.MonitorSample{PosAcm: 9.0, PosBcm: 0}

	// Feed steady samples for > 3s.
	at := start
	for i := 0; i < 40; i++ {
		m.Update(at, mon)
		at = at.Add(100 * time.Millisecond)
	}

	if m.State() != Calibrating {
		t.Fatalf("state=%v want Calibrating", m.State())
	}

	// Provide reps notifications reaching 3 warmup reps.
	m.OnReps(at, telemetry.RepsEvent{Format: "official24", RepsRomCount: 1, RangeBottom: 5.0})
	m.OnReps(at, telemetry.RepsEvent{Format: "official24", RepsRomCount: 2, RangeBottom: 5.0})
	m.OnReps(at, telemetry.RepsEvent{Format: "official24", RepsRomCount: 3, RangeBottom: 5.0})

	if m.State() != Active {
		t.Fatalf("state=%v want Active", m.State())
	}

	// Hold at/below bottom (<= 5.0) for >= 2s.
	mon.PosAcm = 5.0
	for i := 0; i < 25; i++ {
		m.Update(at, mon)
		at = at.Add(100 * time.Millisecond)
	}

	if m.State() != Idle {
		t.Fatalf("state=%v want Idle", m.State())
	}
}
