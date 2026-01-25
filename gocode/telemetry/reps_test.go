package telemetry

import (
	"encoding/binary"
	"math"
	"testing"
)

func TestParseReps_Official24(t *testing.T) {
	b := make([]byte, 24)
	binary.LittleEndian.PutUint32(b[0:4], 2)
	binary.LittleEndian.PutUint32(b[4:8], 1)
	binary.LittleEndian.PutUint32(b[8:12], math.Float32bits(100.0))
	binary.LittleEndian.PutUint32(b[12:16], math.Float32bits(10.0))
	binary.LittleEndian.PutUint16(b[16:18], 3)
	binary.LittleEndian.PutUint16(b[18:20], 5)
	binary.LittleEndian.PutUint16(b[20:22], 7)
	binary.LittleEndian.PutUint16(b[22:24], 11)

	e, err := ParseReps(b)
	if err != nil {
		t.Fatalf("ParseReps: %v", err)
	}
	if e.Format != "official24" || e.UpCounter != 2 || e.DownCounter != 1 {
		t.Fatalf("counters=%+v", e)
	}
	if e.RangeTop != 100.0 || e.RangeBottom != 10.0 {
		t.Fatalf("range=%+v", e)
	}
	if e.RepsRomCount != 3 || e.RepsSetCount != 7 {
		t.Fatalf("counts=%+v", e)
	}
}

