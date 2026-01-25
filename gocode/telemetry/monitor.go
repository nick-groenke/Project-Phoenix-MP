package telemetry

import (
	"encoding/binary"
	"errors"
)

var ErrMonitorTooShort = errors.New("monitor payload too short")

type MonitorSample struct {
	Ticks uint32

	// PosAcm is cable position in centimeters, derived from posA_raw / 10.0.
	PosAcm  float32
	LoadAkg float32
	// PosBcm is cable position in centimeters, derived from posB_raw / 10.0.
	PosBcm  float32
	LoadBkg float32

	HasStatus bool
	Status    uint16
}

const (
	StatusRepTopReady    uint16 = 0x0001
	StatusRepBottomReady uint16 = 0x0002
	StatusRomOutsideHigh uint16 = 0x0004
	StatusRomOutsideLow  uint16 = 0x0008
	StatusRomUnload      uint16 = 0x0010
	StatusSpotterActive  uint16 = 0x0020
	StatusDeloadWarn     uint16 = 0x0040
	StatusDeloadOccurred uint16 = 0x8000
)

func DecodeStatus(status uint16) []string {
	var out []string
	if status&StatusRepTopReady != 0 {
		out = append(out, "REP_TOP_READY")
	}
	if status&StatusRepBottomReady != 0 {
		out = append(out, "REP_BOTTOM_READY")
	}
	if status&StatusRomOutsideHigh != 0 {
		out = append(out, "ROM_OUTSIDE_HIGH")
	}
	if status&StatusRomOutsideLow != 0 {
		out = append(out, "ROM_OUTSIDE_LOW")
	}
	if status&StatusRomUnload != 0 {
		out = append(out, "ROM_UNLOAD_ACTIVE")
	}
	if status&StatusSpotterActive != 0 {
		out = append(out, "SPOTTER_ACTIVE")
	}
	if status&StatusDeloadWarn != 0 {
		out = append(out, "DELOAD_WARN")
	}
	if status&StatusDeloadOccurred != 0 {
		out = append(out, "DELOAD_OCCURRED")
	}
	return out
}

func ParseMonitor(b []byte) (MonitorSample, error) {
	if len(b) < 16 {
		return MonitorSample{}, ErrMonitorTooShort
	}

	ticksLo := binary.LittleEndian.Uint16(b[0:2])
	ticksHi := binary.LittleEndian.Uint16(b[2:4])
	ticks := uint32(ticksLo) + (uint32(ticksHi) << 16)

	posARaw := int16(binary.LittleEndian.Uint16(b[4:6]))
	loadARaw := binary.LittleEndian.Uint16(b[8:10])
	posBRaw := int16(binary.LittleEndian.Uint16(b[10:12]))
	loadBRaw := binary.LittleEndian.Uint16(b[14:16])

	s := MonitorSample{
		Ticks:   ticks,
		PosAcm:  float32(posARaw) / 10.0,
		PosBcm:  float32(posBRaw) / 10.0,
		LoadAkg: float32(loadARaw) / 100.0,
		LoadBkg: float32(loadBRaw) / 100.0,
	}

	if len(b) >= 18 {
		s.HasStatus = true
		s.Status = binary.LittleEndian.Uint16(b[16:18])
	}

	return s, nil
}
