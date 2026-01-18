# Mobile-Portal Sync Design

**Date:** 2026-01-04
**Status:** Approved
**Branch:** premium_features

## Overview

Cloud sync between the Phoenix mobile app and the web portal, enabling multi-device support as a premium feature.

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Scope | Full feature parity | Premium users expect complete data access |
| Direction | Two-way sync | Multi-device support requires bidirectional |
| Conflict Resolution | Append-only with soft deletes | Safest for workout data, allows recovery |
| Timing | Auto after workout + periodic | Immediate capture + background catch-up |
| MetricSamples | Lazy load on demand | Reduces sync payload, data available when needed |

## Architecture

### Sync Model: Append-Only with Soft Deletes

Every syncable entity includes:
- `client_id` - UUID generated on mobile (stable identifier)
- `server_id` - UUID assigned by portal on first upload
- `created_at` - Record creation timestamp
- `updated_at` - Last modification timestamp
- `deleted_at` - Soft delete timestamp (null = active)
- `device_id` - Originating device identifier

### Sync Flow

```
Mobile completes workout
        │
        ▼
Immediate push to portal ──► Portal assigns server_id
        │                           │
        ▼                           ▼
Mobile stores server_id ◄── Response with ID mappings
        │
        ▼
Periodic sync (every 15 min)
        │
        ├──► Push: records where updated_at > last_sync
        │
        └──► Pull: records modified by other devices
```

### Conflict Handling

Since append-only, true conflicts are rare:
- Same `client_id` from multiple devices → keep all versions, show most recent
- Deletions are flags, not actual deletes
- Data can always be recovered

## Entities to Sync

### Tier 1 - Core (immediate after workout)
- `WorkoutSession` - Full 40+ field schema
- `PersonalRecord` - PRs with weight/volume/1RM
- `PhaseStatistics` - Concentric/eccentric aggregates

### Tier 2 - Supporting (periodic background)
- `Routine` - Custom workout routines
- `RoutineExercise` - Exercises within routines
- `Superset` - Superset groupings
- `Exercise` (custom only) - User-created exercises
- `GamificationStats` - Badges, streaks, totals
- `EarnedBadge` - Individual achievements

### Tier 3 - On-Demand (lazy load)
- `MetricSample` - Raw sensor data, fetched per-session

### Not Synced (device-local)
- `Exercise` (built-in) - Bundled library
- `ExerciseVideo` - Bundled content
- `ConnectionLog` - Debug logs
- `DiagnosticsHistory` - Machine diagnostics

## API Endpoints

### Authentication
All endpoints require `Authorization: Bearer <jwt>` header.

### Core Sync

```
POST /api/sync/push
```
Upload changed records since last sync.

Request:
```json
{
  "device_id": "uuid",
  "last_sync": 1704000000000,
  "sessions": [...],
  "records": [...],
  "phase_stats": [...],
  "routines": [...],
  "routine_exercises": [...],
  "supersets": [...],
  "exercises": [...],
  "badges": [...],
  "gamification_stats": {...}
}
```

Response:
```json
{
  "sync_time": 1704001000000,
  "id_mappings": {
    "sessions": {"client_id": "server_id", ...},
    "records": {...},
    ...
  }
}
```

```
POST /api/sync/pull
```
Request records modified since timestamp.

Request:
```json
{
  "device_id": "uuid",
  "last_sync": 1704000000000
}
```

Response:
```json
{
  "sync_time": 1704001000000,
  "sessions": [...],
  "records": [...],
  ...
}
```

```
GET /api/sync/status
```
Check sync state and subscription.

Response:
```json
{
  "last_sync": 1704000000000,
  "pending_changes": 5,
  "subscription_status": "premium",
  "subscription_expires_at": "2026-12-31T23:59:59Z"
}
```

### On-Demand (MetricSamples)

```
GET /api/sessions/{id}/metrics
```
Fetch raw sensor data for a session.

```
POST /api/sessions/{id}/metrics
```
Upload MetricSamples (separate from main sync due to size).

## Backend Schema Changes

### Users Table Additions
```kotlin
val lastSyncAt = timestamp("last_sync_at").nullable()
val subscriptionStatus = varchar("subscription_status", 50).default("free")
val subscriptionExpiresAt = timestamp("subscription_expires_at").nullable()
```

### WorkoutSessions Table
Expand to full mobile schema (40+ fields) plus sync fields.

### New Tables Required
- `PhaseStatistics` - concentric/eccentric data
- `MetricSamples` - raw sensor data
- `Routines` - custom routines
- `RoutineExercises` - exercises in routines
- `Supersets` - superset groupings
- `CustomExercises` - user-created exercises
- `EarnedBadges` - gamification
- `GamificationStats` - aggregated stats
- `SyncDevices` - track user's devices

### Sync Metadata (all syncable tables)
```kotlin
val clientId = uuid("client_id").uniqueIndex()
val deviceId = uuid("device_id")
val createdAt = timestamp("created_at")
val updatedAt = timestamp("updated_at")
val deletedAt = timestamp("deleted_at").nullable()
```

## Mobile App Integration

### New Components

```kotlin
// shared/commonMain
class SyncManager(
    private val api: PortalApi,
    private val database: VitruvianDatabase,
    private val preferences: SyncPreferences
) {
    suspend fun pushChanges()
    suspend fun pullChanges()
    suspend fun syncAll()
    fun getStatus(): Flow<SyncStatus>
}

class PortalApi(
    private val httpClient: HttpClient,
    private val tokenProvider: TokenProvider
) {
    suspend fun push(request: SyncPushRequest): SyncPushResponse
    suspend fun pull(request: SyncPullRequest): SyncPullResponse
    suspend fun getStatus(): SyncStatusResponse
    suspend fun uploadMetrics(sessionId: String, metrics: List<MetricSample>)
    suspend fun downloadMetrics(sessionId: String): List<MetricSample>
}
```

### User Flow
1. User creates account on portal (web)
2. Mobile app → Settings → "Link Account"
3. Enter portal credentials
4. On success, store JWT and begin sync
5. Automatic sync after workouts + periodic background

### Offline Handling
- Workouts save locally first (existing behavior)
- Failed uploads queue for retry
- Retry on network availability
- UI shows "pending sync" indicator

### Premium Gating
Check `subscription_status = 'premium'` before sync operations.
Free users see upgrade prompt.

## Implementation Phases

### Phase 1: Backend Schema & API
- Expand Exposed ORM tables
- Implement push/pull/status endpoints
- Add MetricSample endpoints
- Test with curl/Postman

### Phase 2: Mobile Sync Foundation
- Create PortalApi client (Ktor)
- Create SyncManager with queue
- Add "Link Account" UI
- Secure credential storage

### Phase 3: Auto-Sync Integration
- Hook workout completion → push
- Periodic background sync
- Sync status indicator
- Offline queue handling

### Phase 4: Two-Way Sync
- Pull on app launch
- Merge remote → local
- Device ID tracking
- Multi-device testing

### Phase 5: Polish
- Sync progress UI
- Error/retry handling
- "Last synced" display
- Premium gating integration

## Testing Strategy

- Unit tests for SyncManager logic
- Integration tests for API endpoints
- Multi-device simulation tests
- Offline/online transition tests
- Large dataset performance tests (MetricSamples)

## Open Questions

- Sync frequency when app is backgrounded (Android WorkManager vs iOS Background Tasks)
- Maximum MetricSample batch size for upload
- Data retention policy for deleted records
