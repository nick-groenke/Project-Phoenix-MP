package protocol

import (
	"encoding/binary"
	"math"
	"testing"
)

func TestBuildProgramParams_JustLift(t *testing.T) {
	frame, err := BuildProgramParams(ProgramParams{
		Mode:            ProgramOldSchool,
		WeightPerCableKg: 20.5,
		WarmupReps:       3,
		Reps:             8,
		IsJustLift:       true,
	})
	if err != nil {
		t.Fatalf("BuildProgramParams: %v", err)
	}
	if got := len(frame); got != 96 {
		t.Fatalf("frame len = %d, want 96", got)
	}
	if frame[0] != 0x04 {
		t.Fatalf("opcode = 0x%02x, want 0x04", frame[0])
	}
	if frame[0x04] != 0xFF {
		t.Fatalf("reps byte = 0x%02x, want 0xFF", frame[0x04])
	}

	effective := float32(20.5 + 10.0)
	total := float32(20.5)
	gotEffective := math.Float32frombits(binary.LittleEndian.Uint32(frame[0x54 : 0x54+4]))
	gotTotal := math.Float32frombits(binary.LittleEndian.Uint32(frame[0x58 : 0x58+4]))
	if gotEffective != effective {
		t.Fatalf("effectiveKg = %v, want %v", gotEffective, effective)
	}
	if gotTotal != total {
		t.Fatalf("totalWeightKg = %v, want %v", gotTotal, total)
	}
}

func TestBuildProgramParams_NonAMRAP_RepsSum(t *testing.T) {
	frame, err := BuildProgramParams(ProgramParams{
		Mode:      ProgramOldSchool,
		WarmupReps: 3,
		Reps:       8,
	})
	if err != nil {
		t.Fatalf("BuildProgramParams: %v", err)
	}
	if frame[0x04] != 11 {
		t.Fatalf("reps byte = %d, want 11", frame[0x04])
	}
}

func TestCmdStart_IsMotorStartFrame(t *testing.T) {
	if !IsMotorStartFrame(CmdStart()) {
		t.Fatalf("CmdStart should be detected as motor-start")
	}
	if IsMotorStartFrame(CmdSoftStop()) {
		t.Fatalf("CmdSoftStop should not be detected as motor-start")
	}
}

