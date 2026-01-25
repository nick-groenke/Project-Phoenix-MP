package vitruvian

import "strings"

func IsCandidateName(name string) bool {
	name = strings.TrimSpace(name)
	return strings.HasPrefix(name, "Vee_") ||
		strings.HasPrefix(name, "VIT") ||
		strings.HasPrefix(name, "Vitruvian")
}

