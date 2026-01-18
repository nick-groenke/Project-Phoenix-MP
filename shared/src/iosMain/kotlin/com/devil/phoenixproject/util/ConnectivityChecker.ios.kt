package com.devil.phoenixproject.util

/**
 * iOS connectivity checker.
 * Returns true optimistically - we let the HTTP request timeout handle true offline.
 * This avoids complex NWPathMonitor setup while still being correct behavior.
 */
actual class ConnectivityChecker {
    actual fun isOnline(): Boolean {
        // Optimistic: attempt sync, let HTTP timeout handle offline
        // Full iOS implementation would use NWPathMonitor
        return true
    }
}
