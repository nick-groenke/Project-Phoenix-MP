package protocol

import (
	"encoding/binary"
	"errors"
	"math"
)

type ProgramMode uint8

const (
	ProgramOldSchool ProgramMode = iota
	ProgramPump
	ProgramTUT
	ProgramTUTBeast
	ProgramEccentricOnly
)

type ProgramParams struct {
	Mode ProgramMode

	WeightPerCableKg         float32
	ProgressionRegressionKg  float32
	Reps                     uint8
	WarmupReps               uint8
	IsJustLift               bool
	IsAMRAP                  bool
}

func BuildProgramParams(p ProgramParams) ([]byte, error) {
	frame := make([]byte, 96)

	// Header (u32 LE) with opcode at [0].
	frame[0] = 0x04
	frame[1] = 0x00
	frame[2] = 0x00
	frame[3] = 0x00

	// Reps field at offset 0x04.
	if p.IsJustLift || p.IsAMRAP {
		frame[0x04] = 0xFF
	} else {
		sum := int(p.Reps) + int(p.WarmupReps)
		if sum > 0xFF {
			return nil, errors.New("reps + warmupReps overflows byte")
		}
		frame[0x04] = uint8(sum)
	}

	frame[0x05] = 0x03
	frame[0x06] = 0x03
	frame[0x07] = 0x00

	putFloat32LE(frame, 0x08, 5.0)
	putFloat32LE(frame, 0x0c, 5.0)
	putFloat32LE(frame, 0x1c, 5.0)

	// Fixed thresholds/limits from the working Kotlin implementation.
	putUint16LE(frame, 0x14, 0x00FA)
	putUint16LE(frame, 0x16, 0x00FA)
	putUint16LE(frame, 0x18, 0x00C8)
	putUint16LE(frame, 0x1a, 0x001E)

	putUint16LE(frame, 0x24, 0x00FA)
	putUint16LE(frame, 0x26, 0x00FA)
	putUint16LE(frame, 0x28, 0x00C8)
	putUint16LE(frame, 0x2a, 0x001E)

	putUint16LE(frame, 0x2c, 0x00FA)
	putUint16LE(frame, 0x2e, 0x0050)

	profileMode := p.Mode
	if p.IsJustLift {
		profileMode = ProgramOldSchool
	}
	profile := modeProfile(profileMode)
	copy(frame[0x30:0x50], profile[:])

	adjustedPerCable := p.WeightPerCableKg
	if p.ProgressionRegressionKg != 0 {
		adjustedPerCable -= p.ProgressionRegressionKg
	}

	totalWeightKg := adjustedPerCable
	effectiveKg := adjustedPerCable + 10.0

	putFloat32LE(frame, 0x54, float64(effectiveKg))
	putFloat32LE(frame, 0x58, float64(totalWeightKg))
	putFloat32LE(frame, 0x5c, float64(p.ProgressionRegressionKg))

	return frame, nil
}

func modeProfile(mode ProgramMode) [32]byte {
	var b [32]byte

	switch mode {
	case ProgramOldSchool:
		putInt16LE(b[:], 0x00, 0)
		putInt16LE(b[:], 0x02, 20)
		putFloat32LE(b[:], 0x04, 3.0)
		putInt16LE(b[:], 0x08, 75)
		putInt16LE(b[:], 0x0a, 600)
		putFloat32LE(b[:], 0x0c, 50.0)
		putInt16LE(b[:], 0x10, -1300)
		putInt16LE(b[:], 0x12, -1200)
		putFloat32LE(b[:], 0x14, 100.0)
		putInt16LE(b[:], 0x18, -260)
		putInt16LE(b[:], 0x1a, -110)
		putFloat32LE(b[:], 0x1c, 0.0)
	case ProgramPump:
		putInt16LE(b[:], 0x00, 50)
		putInt16LE(b[:], 0x02, 450)
		putFloat32LE(b[:], 0x04, 10.0)
		putInt16LE(b[:], 0x08, 500)
		putInt16LE(b[:], 0x0a, 600)
		putFloat32LE(b[:], 0x0c, 50.0)
		putInt16LE(b[:], 0x10, -700)
		putInt16LE(b[:], 0x12, -550)
		putFloat32LE(b[:], 0x14, 1.0)
		putInt16LE(b[:], 0x18, -100)
		putInt16LE(b[:], 0x1a, -50)
		putFloat32LE(b[:], 0x1c, 1.0)
	case ProgramTUT:
		putInt16LE(b[:], 0x00, 250)
		putInt16LE(b[:], 0x02, 350)
		putFloat32LE(b[:], 0x04, 7.0)
		putInt16LE(b[:], 0x08, 450)
		putInt16LE(b[:], 0x0a, 600)
		putFloat32LE(b[:], 0x0c, 50.0)
		putInt16LE(b[:], 0x10, -900)
		putInt16LE(b[:], 0x12, -700)
		putFloat32LE(b[:], 0x14, 70.0)
		putInt16LE(b[:], 0x18, -100)
		putInt16LE(b[:], 0x1a, -50)
		putFloat32LE(b[:], 0x1c, 14.0)
	case ProgramTUTBeast:
		putInt16LE(b[:], 0x00, 150)
		putInt16LE(b[:], 0x02, 250)
		putFloat32LE(b[:], 0x04, 7.0)
		putInt16LE(b[:], 0x08, 350)
		putInt16LE(b[:], 0x0a, 450)
		putFloat32LE(b[:], 0x0c, 50.0)
		putInt16LE(b[:], 0x10, -900)
		putInt16LE(b[:], 0x12, -700)
		putFloat32LE(b[:], 0x14, 70.0)
		putInt16LE(b[:], 0x18, -100)
		putInt16LE(b[:], 0x1a, -50)
		putFloat32LE(b[:], 0x1c, 28.0)
	case ProgramEccentricOnly:
		putInt16LE(b[:], 0x00, 50)
		putInt16LE(b[:], 0x02, 550)
		putFloat32LE(b[:], 0x04, 50.0)
		putInt16LE(b[:], 0x08, 650)
		putInt16LE(b[:], 0x0a, 750)
		putFloat32LE(b[:], 0x0c, 10.0)
		putInt16LE(b[:], 0x10, -900)
		putInt16LE(b[:], 0x12, -700)
		putFloat32LE(b[:], 0x14, 70.0)
		putInt16LE(b[:], 0x18, -100)
		putInt16LE(b[:], 0x1a, -50)
		putFloat32LE(b[:], 0x1c, 20.0)
	default:
		// Fallback to OldSchool.
		return modeProfile(ProgramOldSchool)
	}

	return b
}

func putUint16LE(dst []byte, off int, v uint16) {
	binary.LittleEndian.PutUint16(dst[off:off+2], v)
}

func putInt16LE(dst []byte, off int, v int16) {
	binary.LittleEndian.PutUint16(dst[off:off+2], uint16(v))
}

func putFloat32LE(dst []byte, off int, v float64) {
	binary.LittleEndian.PutUint32(dst[off:off+4], math.Float32bits(float32(v)))
}

