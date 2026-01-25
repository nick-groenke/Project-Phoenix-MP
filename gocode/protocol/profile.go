package protocol

import "github.com/go-ble/ble"

func FindRequiredCharacteristics(p *ble.Profile) (tx, monitor, reps *ble.Characteristic) {
	if p == nil {
		return nil, nil, nil
	}
	for _, s := range p.Services {
		for _, c := range s.Characteristics {
			switch {
			case c.UUID.Equal(CharTXWrite):
				tx = c
			case c.UUID.Equal(CharMonitorRead):
				monitor = c
			case c.UUID.Equal(CharRepsNotify):
				reps = c
			}
		}
	}
	return tx, monitor, reps
}
