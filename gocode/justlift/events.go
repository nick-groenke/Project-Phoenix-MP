package justlift

import (
	"fmt"
	"time"

	"github.com/nick-groenke/Project-Phoenix-MP/gocode/session"
	"github.com/nick-groenke/Project-Phoenix-MP/gocode/telemetry"
)

type EventType string

const (
	EventArmed             EventType = "armed"
	EventDisarmed          EventType = "disarmed"
	EventStateTransition   EventType = "state_transition"
	EventMonitorSample     EventType = "monitor_sample"
	EventRepsEvent         EventType = "reps_event"
	EventSentProgramParams EventType = "sent_program_params"
	EventSentStart         EventType = "sent_start"
	EventStartSuppressed   EventType = "start_suppressed"
	EventSoftStop          EventType = "soft_stop"
	EventReset             EventType = "reset"
	EventTelemetryStalled  EventType = "telemetry_stalled"
	EventDisconnected      EventType = "disconnected"
	EventError             EventType = "error"
)

type Event struct {
	At   time.Time
	Type EventType

	From session.State
	To   session.State

	HasMonitor bool
	Monitor    telemetry.MonitorSample

	HasReps bool
	Reps    telemetry.RepsEvent

	Detail string
	Err    error
}

func (e Event) String() string {
	if e.Type == EventStateTransition {
		return fmt.Sprintf("%s: %s -> %s", e.Type, stateName(e.From), stateName(e.To))
	}
	if e.Detail != "" {
		if e.Err != nil {
			return fmt.Sprintf("%s: %s (%v)", e.Type, e.Detail, e.Err)
		}
		return fmt.Sprintf("%s: %s", e.Type, e.Detail)
	}
	if e.Err != nil {
		return fmt.Sprintf("%s: %v", e.Type, e.Err)
	}
	return string(e.Type)
}

func stateName(s session.State) string {
	switch s {
	case session.Idle:
		return "Idle"
	case session.Ready:
		return "Ready"
	case session.Calibrating:
		return "Calibrating"
	case session.Active:
		return "Active"
	case session.Ending:
		return "Ending"
	default:
		return fmt.Sprintf("State(%d)", int(s))
	}
}
