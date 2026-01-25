package protocol

import (
	"github.com/go-ble/ble"
)

var (
	ServiceUART = ble.MustParse("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
	CharTXWrite = ble.MustParse("6e400002-b5a3-f393-e0a9-e50e24dcca9e") // Write With Response

	CharMonitorRead = ble.MustParse("90e991a6-c548-44ed-969b-eb541014eae3") // read()
	CharRepsNotify  = ble.MustParse("8308f2a6-0875-4a94-a86f-5c5c5e1b068a") // notify()
)
