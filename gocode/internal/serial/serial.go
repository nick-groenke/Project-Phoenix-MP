package serial

import (
	"context"
	"errors"
)

type req struct {
	ctx context.Context
	fn  func(context.Context) error
	res chan error
}

// Executor runs operations sequentially (single-flight) to avoid concurrent GATT ops.
type Executor struct {
	ch chan req
}

func New() *Executor {
	e := &Executor{
		ch: make(chan req),
	}
	go e.loop()
	return e
}

func (e *Executor) Close() {
	close(e.ch)
}

func (e *Executor) Do(ctx context.Context, fn func(context.Context) error) error {
	if ctx == nil {
		return errors.New("nil context")
	}
	r := req{
		ctx: ctx,
		fn:  fn,
		res: make(chan error, 1),
	}
	select {
	case <-ctx.Done():
		return ctx.Err()
	case e.ch <- r:
	}
	select {
	case <-ctx.Done():
		return ctx.Err()
	case err := <-r.res:
		return err
	}
}

func (e *Executor) loop() {
	for r := range e.ch {
		if r.ctx.Err() != nil {
			r.res <- r.ctx.Err()
			continue
		}
		r.res <- r.fn(r.ctx)
	}
}

