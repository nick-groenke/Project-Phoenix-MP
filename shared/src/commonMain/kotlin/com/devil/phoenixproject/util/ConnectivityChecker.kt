package com.devil.phoenixproject.util

/**
 * Platform-specific connectivity checker.
 * Used by SyncTriggerManager to skip sync attempts when offline.
 */
expect class ConnectivityChecker {
    /**
     * Returns true if the device appears to be online.
     * This is a best-effort check - sync may still fail due to network issues.
     */
    fun isOnline(): Boolean
}
