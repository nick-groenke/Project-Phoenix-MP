# BLE Pre-Connection Cleanup Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add explicit cleanup of existing BLE connections before creating new ones, matching parent repo behavior to prevent "dangling GATT connections" on Android 16/Pixel 7.

**Architecture:** Extract cleanup logic from `disconnect()` into a reusable `cleanupExistingConnection()` method, then call it at the start of `connect()`. This ensures idempotent connection handling where calling `connect()` multiple times is safe.

**Tech Stack:** Kotlin, Kable BLE library, Coroutines

---

## Background

**Parent repo behavior** (`BleRepositoryImpl.connectToDevice`):
```kotlin
// Critical cleanup before new connection
bleManager?.stopPolling()
cleanup()
close()  // Prevents "dangling GATT connections" on Android 16/Pixel 7
// Then create new VitruvianBleManager
```

**Current Phoenix behavior** (`KableBleRepository.connect`):
```kotlin
stopScanning()
peripheral = Peripheral(advertisement)  // No cleanup!
```

**Risk:** If `connect()` is called while an existing peripheral exists:
- Old GATT connection may not release properly
- Connection failures on Android 16/Pixel 7
- Resource leaks and unpredictable retry behavior

---

## Task 1: Add cleanupExistingConnection() Method

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/KableBleRepository.kt:1321-1340`

**Step 1: Write the cleanup extraction method**

Add new private method after `stopPolling()` (around line 1580):

```kotlin
/**
 * Clean up any existing connection before creating a new one.
 * Matches parent repo behavior to prevent "dangling GATT connections"
 * which cause issues on Android 16/Pixel 7.
 *
 * This is idempotent - safe to call even if no connection exists.
 */
private suspend fun cleanupExistingConnection() {
    val existingPeripheral = peripheral ?: return

    log.d { "Cleaning up existing connection before new connection attempt" }
    logRepo.info(
        LogEventType.DISCONNECT,
        "Cleaning up existing connection (pre-connect)",
        connectedDeviceName,
        connectedDeviceAddress
    )

    // Cancel all polling jobs (matches disconnect() behavior)
    heartbeatJob?.cancel()
    heartbeatJob = null
    monitorPollingJob?.cancel()
    monitorPollingJob = null
    diagnosticPollingJob?.cancel()
    diagnosticPollingJob = null

    // Disconnect and release the peripheral
    try {
        existingPeripheral.disconnect()
    } catch (e: Exception) {
        log.w { "Cleanup disconnect error (non-fatal): ${e.message}" }
    }

    peripheral = null
    // Note: Don't update _connectionState here - we're about to connect
    // and the Connecting state will be set by the caller
}
```

**Step 2: Verify the code compiles**

Run: `./gradlew :shared:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/KableBleRepository.kt
git commit -m "refactor(ble): extract cleanupExistingConnection() from disconnect logic

Prepares for pre-connection cleanup to match parent repo behavior.
Addresses Android 16/Pixel 7 dangling GATT connection issues.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 2: Call Cleanup in connect()

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/KableBleRepository.kt:547-582`

**Step 1: Add cleanup call at start of connect()**

Modify `connect()` to call cleanup before creating new peripheral:

```kotlin
override suspend fun connect(device: ScannedDevice): Result<Unit> {
    log.i { "Connecting to device: ${device.name}" }
    logRepo.info(
        LogEventType.CONNECT_START,
        "Connecting to device",
        device.name,
        device.address
    )

    // ADDED: Clean up any existing connection first (matches parent repo)
    // Prevents "dangling GATT connections" on Android 16/Pixel 7
    cleanupExistingConnection()

    _connectionState.value = ConnectionState.Connecting

    val advertisement = discoveredAdvertisements[device.address]
    // ... rest of existing code unchanged
```

**Step 2: Verify the code compiles**

Run: `./gradlew :shared:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/KableBleRepository.kt
git commit -m "fix(ble): add pre-connection cleanup to prevent dangling GATT

Calls cleanupExistingConnection() before creating new peripheral.
Matches parent repo behavior for Android 16/Pixel 7 compatibility.

Fixes: Potential connection failures on reconnect scenarios

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 3: Call Cleanup in scanAndConnect()

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/KableBleRepository.kt:499-545`

**Step 1: Add cleanup call in scanAndConnect()**

Since `scanAndConnect()` calls `connect()` internally, we should also add cleanup at the entry point for safety:

```kotlin
override suspend fun scanAndConnect(timeoutMs: Long): Result<Unit> {
    log.i { "scanAndConnect: Starting scan and auto-connect (timeout: ${timeoutMs}ms)" }
    logRepo.info(LogEventType.SCAN_START, "Scan and connect started")

    // ADDED: Ensure clean state before scan+connect cycle
    cleanupExistingConnection()

    _connectionState.value = ConnectionState.Scanning
    _scannedDevices.value = emptyList()
    discoveredAdvertisements.clear()
    // ... rest of existing code unchanged
```

**Step 2: Verify the code compiles**

Run: `./gradlew :shared:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL

**Step 3: Run full build to ensure no regressions**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/KableBleRepository.kt
git commit -m "fix(ble): add cleanup to scanAndConnect for consistent behavior

Ensures clean state at start of scan+connect cycle.
Defensive measure for reconnection edge cases.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 4: Update Documentation

**Files:**
- Modify: `ProjectPhoenixFlows.md`

**Step 1: Update Flow 1 documentation**

Add cleanup step to "Device Connection and Initialization Flow":

```markdown
### 1. Device Connection and Initialization Flow

**Description:** The sequence executed when `ensureConnection` is called to establish a link, negotiate protocol parameters, and set up the initial monitoring state.

* **Step 1: Pre-Connection Cleanup (NEW - Parity with parent)**
* `KableBleRepository` calls `cleanupExistingConnection()` to release any dangling GATT connections.
* **Code:** Cancels polling jobs, disconnects existing peripheral, releases resources.
* **Purpose:** Prevents Android 16/Pixel 7 connection issues from stale GATT state.

* **Step 2: Scan and Connect**
* `MainViewModel` calls `bleRepository.scanAndConnect()`.
* `KableBleRepository` filters advertisements for names starting with "Vee_" or "VIT" or service UUID `0000fef3`.
* Upon finding a device, `peripheral.connect()` is called with a retry mechanism (3 attempts).

// ... rest unchanged
```

**Step 2: Commit**

```bash
git add ProjectPhoenixFlows.md
git commit -m "docs: document pre-connection cleanup in BLE flow

Updates Flow 1 to include new cleanup step matching parent repo.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
```

---

## Task 5: Record Decision in Daem0nMCP

**Step 1: Remember the decision**

```
mcp__daem0nmcp__remember(
    category="decision",
    content="Added pre-connection GATT cleanup in KableBleRepository.connect() and scanAndConnect() to match parent repo behavior. Prevents dangling GATT connections on Android 16/Pixel 7.",
    rationale="Parent repo explicitly cleans up existing BleManager before creating new connections. Phoenix was missing this, risking connection failures on specific Android versions.",
    tags=["ble", "connection", "android", "parity"],
    file_path="shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/KableBleRepository.kt"
)
```

---

## Verification Checklist

- [ ] `cleanupExistingConnection()` method added
- [ ] `connect()` calls cleanup before creating peripheral
- [ ] `scanAndConnect()` calls cleanup at start
- [ ] `./gradlew build` passes
- [ ] Documentation updated
- [ ] Decision recorded in Daem0nMCP

---

## Risk Assessment

**Low Risk:** This change is additive and defensive:
- Cleanup is idempotent (safe to call when no connection exists)
- Matches proven parent repo pattern
- No changes to existing behavior paths when no prior connection exists
- Improves reliability for reconnection scenarios

**Testing Notes:** Manual testing recommended on:
1. Fresh connection (no prior connection)
2. Reconnection after explicit disconnect
3. Reconnection after unexpected disconnect
4. Multiple rapid connect() calls
