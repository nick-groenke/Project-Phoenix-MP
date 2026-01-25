package vitruvian

import (
	"context"
	"errors"
	"fmt"

	"github.com/go-ble/ble"

	"github.com/nick-groenke/Project-Phoenix-MP/gocode/internal/serial"
	"github.com/nick-groenke/Project-Phoenix-MP/gocode/protocol"
)

var ErrSuppressedMotorStart = errors.New("dry-run: suppressed motor start")

type TXWriter struct {
	Client ble.Client
	TX     *ble.Characteristic
	Exec   *serial.Executor

	DryRun bool
}

func (w TXWriter) WriteWithResponse(ctx context.Context, frame []byte) error {
	if w.TX == nil {
		return errors.New("nil TX characteristic")
	}
	if w.Client == nil {
		return errors.New("nil BLE client")
	}
	if w.Exec == nil {
		return errors.New("nil executor")
	}

	if w.DryRun && protocol.IsMotorStartFrame(frame) {
		return ErrSuppressedMotorStart
	}

	return w.Exec.Do(ctx, func(ctx context.Context) error {
		if err := w.Client.WriteCharacteristic(w.TX, frame, true); err != nil {
			return fmt.Errorf("write TX: %w", err)
		}
		return nil
	})
}

