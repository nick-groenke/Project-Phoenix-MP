package telemetry

import (
	"encoding/binary"
	"errors"
	"math"
)

var ErrRepsInvalidLength = errors.New("reps payload invalid length")

type RepsEvent struct {
	Format string // "official24" or "legacy6"

	UpCounter   uint32
	DownCounter uint32

	RangeTop    float32
	RangeBottom float32

	RepsRomCount  uint16
	RepsRomTotal  uint16
	RepsSetCount  uint16
	RepsSetTotal  uint16

	LegacyTopCounter      uint16
	LegacyCompleteCounter uint16
}

func ParseReps(b []byte) (RepsEvent, error) {
	switch len(b) {
	case 24:
		up := binary.LittleEndian.Uint32(b[0:4])
		down := binary.LittleEndian.Uint32(b[4:8])
		top := math.Float32frombits(binary.LittleEndian.Uint32(b[8:12]))
		bottom := math.Float32frombits(binary.LittleEndian.Uint32(b[12:16]))
		return RepsEvent{
			Format:       "official24",
			UpCounter:    up,
			DownCounter:  down,
			RangeTop:     top,
			RangeBottom:  bottom,
			RepsRomCount: binary.LittleEndian.Uint16(b[16:18]),
			RepsRomTotal: binary.LittleEndian.Uint16(b[18:20]),
			RepsSetCount: binary.LittleEndian.Uint16(b[20:22]),
			RepsSetTotal: binary.LittleEndian.Uint16(b[22:24]),
		}, nil
	case 6:
		return RepsEvent{
			Format:              "legacy6",
			LegacyTopCounter:    binary.LittleEndian.Uint16(b[0:2]),
			LegacyCompleteCounter: binary.LittleEndian.Uint16(b[4:6]),
		}, nil
	default:
		return RepsEvent{}, ErrRepsInvalidLength
	}
}

