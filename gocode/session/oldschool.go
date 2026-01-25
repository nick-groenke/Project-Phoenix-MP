package session

import (
	"math"
	"time"

	"github.com/nick-groenke/Project-Phoenix-MP/gocode/telemetry"
)

type State int

const (
	Idle State = iota
	Ready
	Calibrating
	Active
	Ending
)

type Config struct {
	HoldSteady time.Duration
	HoldBottom time.Duration

	PickupPosCm     float32
	SteadyVelCmPerS float32
	BelowBottomCm   float32
	EndingGrace      time.Duration

	CalibrationRepsTarget uint16
}

func DefaultConfig() Config {
	return Config{
		HoldSteady: 3 * time.Second,
		HoldBottom: 2 * time.Second,

		PickupPosCm:     8.0,
		SteadyVelCmPerS: 10.0,
		BelowBottomCm:   5.0,
		EndingGrace:     150 * time.Millisecond,

		CalibrationRepsTarget: 3,
	}
}

// OldSchoolMachine is a hands-free session state machine driven by telemetry.
// It never emits motor-start commands; orchestration is handled elsewhere.
type OldSchoolMachine struct {
	cfg Config

	state State
	armed bool

	lastAt   time.Time
	lastMon  telemetry.MonitorSample
	hasLast  bool

	steadySince *time.Time
	bottomSince *time.Time
	endingSince *time.Time

		calibrationBottomCm float32
		hasCalibBottom      bool

	lastWarmupCount uint16
	calibrationDone bool
}

func NewOldSchool(cfg Config) *OldSchoolMachine {
	if cfg.HoldSteady == 0 {
		cfg.HoldSteady = DefaultConfig().HoldSteady
	}
	if cfg.HoldBottom == 0 {
		cfg.HoldBottom = DefaultConfig().HoldBottom
	}
	if cfg.PickupPosCm == 0 {
		cfg.PickupPosCm = DefaultConfig().PickupPosCm
	}
	if cfg.SteadyVelCmPerS == 0 {
		cfg.SteadyVelCmPerS = DefaultConfig().SteadyVelCmPerS
	}
	if cfg.CalibrationRepsTarget == 0 {
		cfg.CalibrationRepsTarget = DefaultConfig().CalibrationRepsTarget
	}
	return &OldSchoolMachine{
		cfg:   cfg,
		state: Idle,
	}
}

func (m *OldSchoolMachine) State() State { return m.state }

func (m *OldSchoolMachine) Arm() {
	m.armed = true
	if m.state == Idle {
		m.transition(Ready)
	}
}

func (m *OldSchoolMachine) Disarm() {
	m.resetToIdle()
}

func (m *OldSchoolMachine) Update(at time.Time, mon telemetry.MonitorSample) {
	if !m.armed {
		m.trackLast(at, mon)
		return
	}

	switch m.state {
	case Ready:
		m.updateReady(at, mon)
	case Calibrating:
		m.updateCalibrating(at, mon)
	case Active:
		m.updateActive(at, mon)
	case Ending:
		if m.endingSince != nil && at.Sub(*m.endingSince) >= m.cfg.EndingGrace {
			m.resetToIdle()
		}
	}

	m.trackLast(at, mon)
}

func (m *OldSchoolMachine) OnReps(at time.Time, reps telemetry.RepsEvent) {
	if !m.armed {
		return
	}
	if m.state != Calibrating {
		return
	}
	if reps.Format != "official24" {
		return
	}

	if reps.RangeBottom != 0 {
		m.calibrationBottomCm = reps.RangeBottom
		m.hasCalibBottom = true
	}

	if reps.RepsRomCount < m.lastWarmupCount {
		m.lastWarmupCount = reps.RepsRomCount
		return
	}

	m.lastWarmupCount = reps.RepsRomCount
	if reps.RepsRomCount >= m.cfg.CalibrationRepsTarget {
		m.calibrationDone = true
		m.transition(Active)
	}

	_ = at
}

func (m *OldSchoolMachine) updateReady(at time.Time, mon telemetry.MonitorSample) {
	if !m.isPickedUp(mon) {
		m.steadySince = nil
		return
	}

	va, vb, ok := m.velocityCmPerS(at, mon)
	if !ok {
		return
	}

	steady := (mon.PosAcm > m.cfg.PickupPosCm && math.Abs(float64(va)) <= float64(m.cfg.SteadyVelCmPerS)) ||
		(mon.PosBcm > m.cfg.PickupPosCm && math.Abs(float64(vb)) <= float64(m.cfg.SteadyVelCmPerS))

	if !steady {
		m.steadySince = nil
		return
	}

	if m.steadySince == nil {
		t := at
		m.steadySince = &t
		return
	}

		if at.Sub(*m.steadySince) >= m.cfg.HoldSteady {
			m.lastWarmupCount = 0
			m.calibrationDone = false
			m.hasCalibBottom = false
			m.calibrationBottomCm = 0
			m.transition(Calibrating)
		}
}

func (m *OldSchoolMachine) updateCalibrating(at time.Time, mon telemetry.MonitorSample) {
	// Track a conservative bottom estimate until we receive RangeBottom from reps notify.
	bottom := min32(mon.PosAcm, mon.PosBcm)
	if !m.hasCalibBottom || bottom < m.calibrationBottomCm {
		m.calibrationBottomCm = bottom
		m.hasCalibBottom = true
	}

	_ = at
}

func (m *OldSchoolMachine) updateActive(at time.Time, mon telemetry.MonitorSample) {
	if !m.hasCalibBottom {
		return
	}

	target := m.calibrationBottomCm - m.cfg.BelowBottomCm
	below := mon.PosAcm <= target || mon.PosBcm <= target
	if !below {
		m.bottomSince = nil
		return
	}

	if m.bottomSince == nil {
		t := at
		m.bottomSince = &t
		return
	}

	if at.Sub(*m.bottomSince) >= m.cfg.HoldBottom {
		t := at
		m.endingSince = &t
		m.transition(Ending)
	}
}

func (m *OldSchoolMachine) resetToIdle() {
	m.armed = false
	m.transition(Idle)

	m.steadySince = nil
	m.bottomSince = nil
	m.endingSince = nil
	m.hasLast = false
	m.hasCalibBottom = false
	m.calibrationBottomCm = 0
	m.lastWarmupCount = 0
	m.calibrationDone = false
}

func (m *OldSchoolMachine) transition(to State) {
	m.state = to
	if to == Idle {
		m.armed = false
	}
}

func (m *OldSchoolMachine) trackLast(at time.Time, mon telemetry.MonitorSample) {
	m.lastAt = at
	m.lastMon = mon
	m.hasLast = true
}

func (m *OldSchoolMachine) isPickedUp(mon telemetry.MonitorSample) bool {
	// One-cable semantics: either cable exceeding pickup position counts.
	return mon.PosAcm > m.cfg.PickupPosCm || mon.PosBcm > m.cfg.PickupPosCm
}

func (m *OldSchoolMachine) velocityCmPerS(at time.Time, mon telemetry.MonitorSample) (va, vb float32, ok bool) {
	if !m.hasLast {
		return 0, 0, false
	}
	dt := at.Sub(m.lastAt).Seconds()
	if dt <= 0 {
		return 0, 0, false
	}
	return float32(float64(mon.PosAcm-m.lastMon.PosAcm) / dt),
		float32(float64(mon.PosBcm-m.lastMon.PosBcm) / dt),
		true
}

func min32(a, b float32) float32 {
	if a < b {
		return a
	}
	return b
}
