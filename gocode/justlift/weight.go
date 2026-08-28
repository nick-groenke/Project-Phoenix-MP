package justlift

import "fmt"

const (
	MinWeightLb  = 10
	MaxWeightLb  = 100
	StepWeightLb = 1
)

func ValidateWeightLb(lb int) error {
	if lb < MinWeightLb || lb > MaxWeightLb {
		return fmt.Errorf("weight-lb=%d out of range (%d-%d)", lb, MinWeightLb, MaxWeightLb)
	}
	if (lb-MinWeightLb)%StepWeightLb != 0 {
		return fmt.Errorf("weight-lb=%d not on %d lb steps", lb, StepWeightLb)
	}
	return nil
}
