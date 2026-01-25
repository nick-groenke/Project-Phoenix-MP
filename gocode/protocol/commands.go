package protocol

func CmdStart() []byte { return []byte{0x03, 0x00, 0x00, 0x00} }

func CmdSoftStop() []byte { return []byte{0x50, 0x00} }

func CmdReset() []byte { return []byte{0x0A, 0x00, 0x00, 0x00} }

func IsMotorStartFrame(frame []byte) bool {
	return len(frame) > 0 && frame[0] == 0x03
}

