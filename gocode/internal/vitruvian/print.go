package vitruvian

import (
	"fmt"
	"io"
	"strings"

	"github.com/go-ble/ble"
)

func PrintAdvertisement(w io.Writer, a ble.Advertisement) {
	name := strings.TrimSpace(a.LocalName())
	fmt.Fprintf(w, "%s  rssi=%4d  name=%q\n", a.Addr(), a.RSSI(), name)
}

func Hex(b []byte) string {
	const hexdigits = "0123456789abcdef"
	if len(b) == 0 {
		return ""
	}
	out := make([]byte, len(b)*2)
	for i, v := range b {
		out[i*2] = hexdigits[v>>4]
		out[i*2+1] = hexdigits[v&0x0f]
	}
	return string(out)
}
