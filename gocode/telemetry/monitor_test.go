package telemetry

import (
	"encoding/binary"
	"testing"
)

func TestParseMonitor(t *testing.T) {
	b := make([]byte, 18)

	// ticks = 0x00020001
	binary.LittleEndian.PutUint16(b[0:2], 0x0001)
	binary.LittleEndian.PutUint16(b[2:4], 0x0002)

	// posA_raw = 90 => 9.0cm
	binary.LittleEndian.PutUint16(b[4:6], uint16(int16(90)))
	// loadA_raw = 1234 => 12.34kg
	binary.LittleEndian.PutUint16(b[8:10], 1234)

	// posB_raw = -50 => -5.0cm
	var posB int16 = -50
	binary.LittleEndian.PutUint16(b[10:12], uint16(posB))
	// loadB_raw = 10 => 0.10kg
	binary.LittleEndian.PutUint16(b[14:16], 10)

	binary.LittleEndian.PutUint16(b[16:18], 0xBEEF)

	s, err := ParseMonitor(b)
	if err != nil {
		t.Fatalf("ParseMonitor: %v", err)
	}
	if s.Ticks != 0x00020001 {
		t.Fatalf("Ticks=%08x", s.Ticks)
	}
	if s.PosAcm != 9.0 {
		t.Fatalf("PosAcm=%v", s.PosAcm)
	}
	if diff := s.LoadAkg - 12.34; diff < -0.001 || diff > 0.001 {
		t.Fatalf("LoadAkg=%v", s.LoadAkg)
	}
	if s.PosBcm != -5.0 {
		t.Fatalf("PosBcm=%v", s.PosBcm)
	}
	if diff := s.LoadBkg - 0.10; diff < -0.001 || diff > 0.001 {
		t.Fatalf("LoadBkg=%v", s.LoadBkg)
	}
	if !s.HasStatus || s.Status != 0xBEEF {
		t.Fatalf("status=%v has=%v", s.Status, s.HasStatus)
	}
}
