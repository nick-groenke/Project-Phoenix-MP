# Phase 1: Foundation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Set up subscription infrastructure with RevenueCat + Supabase, enabling the "Go Pro" flow where users can subscribe and unlock premium features.

**Architecture:** RevenueCat SDK handles payments, Supabase handles auth/user data, SubscriptionManager gates features in-app.

**Tech Stack:** RevenueCat Kotlin SDK, Supabase Kotlin Client, Ktor (existing), Koin (existing), SQLDelight (existing).

---

## Prerequisites (Manual Setup - Not Code)

Before starting implementation, complete these external setup steps:

### Supabase Project Setup
1. Create project at https://supabase.com/dashboard
2. Note: Project URL and anon key (for app config)
3. Enable Auth providers:
   - Email/Password (Settings > Auth > Providers)
   - Google OAuth (requires Google Cloud Console setup)
   - Apple OAuth (requires Apple Developer setup)
4. Create database tables (run in SQL Editor):

```sql
-- User subscription tracking (synced from RevenueCat)
CREATE TABLE user_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    revenuecat_customer_id TEXT,
    subscription_status TEXT DEFAULT 'free', -- 'free', 'active', 'expired', 'grace_period'
    product_id TEXT,
    expires_at TIMESTAMPTZ,
    last_verified_at TIMESTAMPTZ DEFAULT now(),
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(user_id)
);

-- Row Level Security
ALTER TABLE user_subscriptions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can read own subscription"
    ON user_subscriptions FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can update own subscription"
    ON user_subscriptions FOR UPDATE
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own subscription"
    ON user_subscriptions FOR INSERT
    WITH CHECK (auth.uid() = user_id);
```

### RevenueCat Project Setup
1. Create project at https://app.revenuecat.com
2. Add apps: Android (Play Store) + iOS (App Store)
3. Create Entitlement: `pro_access`
4. Create Products in App Store Connect / Play Console
5. Link products to `pro_access` entitlement
6. Note: API keys for Android and iOS

### App Store / Play Store Products
1. **App Store Connect**: Create subscription product (e.g., `phoenix_pro_monthly`)
2. **Play Console**: Create subscription product with same ID
3. Configure pricing, trial periods, etc.

---

## Task 1: Add Dependencies to Version Catalog

**Files:**
- Modify: `gradle/libs.versions.toml`

**Step 1: Add Supabase and RevenueCat versions**

Add to `[versions]` section:
```toml
supabase = "3.1.4"
revenuecat = "2.2.15+17.25.0"
```

**Step 2: Add library declarations**

Add to `[libraries]` section:
```toml
# Supabase
supabase-bom = { module = "io.github.jan-tennert.supabase:bom", version.ref = "supabase" }
supabase-auth = { module = "io.github.jan-tennert.supabase:auth-kt" }
supabase-postgrest = { module = "io.github.jan-tennert.supabase:postgrest-kt" }
supabase-realtime = { module = "io.github.jan-tennert.supabase:realtime-kt" }

# RevenueCat (KMP)
revenuecat-purchases-core = { module = "com.revenuecat.purchases:purchases-kmp-core", version.ref = "revenuecat" }
```

**Step 3: Sync Gradle**

Run: `./gradlew --refresh-dependencies`

**Step 4: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: add Supabase and RevenueCat dependencies to version catalog"
```

---

## Task 2: Add Dependencies to Shared Module

**Files:**
- Modify: `shared/build.gradle.kts`

**Step 1: Add Supabase BOM and modules to commonMain**

Find the `commonMain` dependencies block and add:

```kotlin
val commonMain by getting {
    dependencies {
        // ... existing dependencies ...

        // Supabase
        implementation(project.dependencies.platform(libs.supabase.bom))
        implementation(libs.supabase.auth)
        implementation(libs.supabase.postgrest)

        // RevenueCat
        implementation(libs.revenuecat.purchases.core)
    }
}
```

**Step 2: Sync and verify build**

Run: `./gradlew :shared:compileKotlinIosArm64 :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add shared/build.gradle.kts
git commit -m "build: add Supabase and RevenueCat to shared module"
```

---

## Task 3: Create Configuration Constants

**Files:**
- Create: `shared/src/commonMain/kotlin/com/devil/phoenixproject/config/AppConfig.kt`

**Step 1: Create config file**

```kotlin
package com.devil.phoenixproject.config

object AppConfig {
    // Supabase
    const val SUPABASE_URL = "https://ilzlswmatadlnsuxatcv.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imlsemxzd21hdGFkbG5zdXhhdGN2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjY4ODA5MzUsImV4cCI6MjA4MjQ1NjkzNX0._fNm07SvkCsMId2oBrg-Rf_5HCypTwLkjWu0T_5QizA"

    // RevenueCat - These are set per-platform
    object RevenueCat {
        const val ENTITLEMENT_PRO = "pro_access"
    }
}

// Platform-specific RevenueCat API keys
expect object PlatformConfig {
    val revenueCatApiKey: String
}
```

**Step 2: Create Android platform config**

Create: `shared/src/androidMain/kotlin/com/devil/phoenixproject/config/PlatformConfig.android.kt`

```kotlin
package com.devil.phoenixproject.config

actual object PlatformConfig {
    // Test/sandbox key - replace with production key before release
    actual val revenueCatApiKey: String = "test_cBqVmeMuksjKrXmwfPVIXtlubeh"
}
```

**Step 3: Create iOS platform config**

Create: `shared/src/iosMain/kotlin/com/devil/phoenixproject/config/PlatformConfig.ios.kt`

```kotlin
package com.devil.phoenixproject.config

actual object PlatformConfig {
    // Test/sandbox key - replace with production key before release
    // Note: iOS may need a separate key from RevenueCat dashboard
    actual val revenueCatApiKey: String = "test_cBqVmeMuksjKrXmwfPVIXtlubeh"
}
```

**Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/devil/phoenixproject/config/
git add shared/src/androidMain/kotlin/com/devil/phoenixproject/config/
git add shared/src/iosMain/kotlin/com/devil/phoenixproject/config/
git commit -m "feat: add app configuration for Supabase and RevenueCat"
```

---

## Task 4: Create Supabase Client Singleton

**Files:**
- Create: `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/remote/SupabaseClient.kt`

**Step 1: Create the Supabase client**

```kotlin
package com.devil.phoenixproject.data.remote

import com.devil.phoenixproject.config.AppConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest

object SupabaseClientProvider {

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = AppConfig.SUPABASE_URL,
            supabaseKey = AppConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
        }
    }

    val auth: Auth
        get() = client.auth

    val postgrest: Postgrest
        get() = client.postgrest
}
```

**Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/devil/phoenixproject/data/remote/SupabaseClient.kt
git commit -m "feat: add Supabase client singleton"
```

---

## Task 5: Create Auth Repository Interface

**Files:**
- Create: `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/AuthRepository.kt`

**Step 1: Define the auth repository interface**

```kotlin
package com.devil.phoenixproject.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class AuthUser(
    val id: String,
    val email: String?,
    val displayName: String?
)

sealed class AuthState {
    data object Loading : AuthState()
    data object NotAuthenticated : AuthState()
    data class Authenticated(val user: AuthUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

interface AuthRepository {
    val authState: StateFlow<AuthState>
    val currentUser: AuthUser?

    suspend fun signUpWithEmail(email: String, password: String): Result<AuthUser>
    suspend fun signInWithEmail(email: String, password: String): Result<AuthUser>
    suspend fun signInWithGoogle(): Result<AuthUser>
    suspend fun signInWithApple(): Result<AuthUser>
    suspend fun signOut(): Result<Unit>
    suspend fun refreshSession(): Result<Unit>
}
```

**Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/AuthRepository.kt
git commit -m "feat: add AuthRepository interface"
```

---

## Task 6: Implement Supabase Auth Repository

**Files:**
- Create: `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/SupabaseAuthRepository.kt`

**Step 1: Implement the repository**

```kotlin
package com.devil.phoenixproject.data.repository

import com.devil.phoenixproject.data.remote.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SupabaseAuthRepository : AuthRepository {

    private val auth = SupabaseClientProvider.auth
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUser: AuthUser?
        get() = auth.currentUserOrNull()?.let { user ->
            AuthUser(
                id = user.id,
                email = user.email,
                displayName = user.userMetadata?.get("display_name")?.toString()
            )
        }

    init {
        scope.launch {
            auth.sessionStatus.collect { status ->
                _authState.value = when (status) {
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user
                        AuthState.Authenticated(
                            AuthUser(
                                id = user?.id ?: "",
                                email = user?.email,
                                displayName = user?.userMetadata?.get("display_name")?.toString()
                            )
                        )
                    }
                    is SessionStatus.NotAuthenticated -> AuthState.NotAuthenticated
                    is SessionStatus.Initializing -> AuthState.Loading
                    is SessionStatus.RefreshFailure -> AuthState.Error("Session refresh failed")
                }
            }
        }
    }

    override suspend fun signUpWithEmail(email: String, password: String): Result<AuthUser> {
        return try {
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            currentUser?.let { Result.success(it) }
                ?: Result.failure(Exception("Sign up succeeded but user is null"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> {
        return try {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            currentUser?.let { Result.success(it) }
                ?: Result.failure(Exception("Sign in succeeded but user is null"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogle(): Result<AuthUser> {
        // Platform-specific implementation needed
        return Result.failure(NotImplementedError("Google sign-in requires platform implementation"))
    }

    override suspend fun signInWithApple(): Result<AuthUser> {
        // Platform-specific implementation needed
        return Result.failure(NotImplementedError("Apple sign-in requires platform implementation"))
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshSession(): Result<Unit> {
        return try {
            auth.refreshCurrentSession()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

**Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/SupabaseAuthRepository.kt
git commit -m "feat: implement SupabaseAuthRepository"
```

---

## Task 7: Extend UserProfile Database Schema

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/devil/phoenixproject/database/VitruvianDatabase.sq`
- Create: `shared/src/commonMain/sqldelight/com/devil/phoenixproject/database/4.sqm`

**Step 1: Create migration file for new columns**

Create: `shared/src/commonMain/sqldelight/com/devil/phoenixproject/database/4.sqm`

```sql
-- Add subscription-related columns to UserProfile
ALTER TABLE UserProfile ADD COLUMN supabase_user_id TEXT;
ALTER TABLE UserProfile ADD COLUMN subscription_status TEXT DEFAULT 'free';
ALTER TABLE UserProfile ADD COLUMN subscription_expires_at INTEGER;
ALTER TABLE UserProfile ADD COLUMN last_auth_at INTEGER;
```

**Step 2: Add new queries to VitruvianDatabase.sq**

Find the UserProfile section and add these queries:

```sql
-- Subscription queries
linkProfileToSupabase:
UPDATE UserProfile
SET supabase_user_id = ?, last_auth_at = ?
WHERE id = ?;

updateSubscriptionStatus:
UPDATE UserProfile
SET subscription_status = ?, subscription_expires_at = ?
WHERE id = ?;

getProfileBySupabaseId:
SELECT * FROM UserProfile WHERE supabase_user_id = ?;

getActiveProfileSubscriptionStatus:
SELECT subscription_status FROM UserProfile WHERE isActive = 1;
```

**Step 3: Increment DEV_SCHEMA_VERSION**

In `shared/src/androidMain/kotlin/com/devil/phoenixproject/database/DriverFactory.android.kt`, increment:

```kotlin
private const val DEV_SCHEMA_VERSION = 5  // Was 4
```

**Step 4: Verify build**

Run: `./gradlew :shared:generateCommonMainVitruvianDatabaseInterface`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add shared/src/commonMain/sqldelight/
git add shared/src/androidMain/kotlin/com/devil/phoenixproject/database/DriverFactory.android.kt
git commit -m "feat: add subscription columns to UserProfile schema"
```

---

## Task 8: Update UserProfile Domain Model

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/devil/phoenixproject/domain/model/UserProfile.kt` (or wherever UserProfile is defined)

**Step 1: Find and update UserProfile data class**

Add new fields to the UserProfile data class:

```kotlin
data class UserProfile(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val createdAt: Long,
    val isActive: Boolean,
    // New subscription fields
    val supabaseUserId: String? = null,
    val subscriptionStatus: SubscriptionStatus = SubscriptionStatus.FREE,
    val subscriptionExpiresAt: Long? = null,
    val lastAuthAt: Long? = null
)

enum class SubscriptionStatus {
    FREE,
    ACTIVE,
    EXPIRED,
    GRACE_PERIOD;

    companion object {
        fun fromString(value: String?): SubscriptionStatus {
            return when (value?.lowercase()) {
                "active" -> ACTIVE
                "expired" -> EXPIRED
                "grace_period" -> GRACE_PERIOD
                else -> FREE
            }
        }
    }

    fun toDbString(): String = name.lowercase()
}
```

**Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/devil/phoenixproject/domain/model/
git commit -m "feat: add subscription fields to UserProfile model"
```

---

## Task 9: Update UserProfile Repository

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/UserProfileRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/SqlDelightUserProfileRepository.kt`

**Step 1: Add new methods to interface**

```kotlin
interface UserProfileRepository {
    // ... existing methods ...

    suspend fun linkToSupabase(profileId: String, supabaseUserId: String)
    suspend fun updateSubscriptionStatus(profileId: String, status: SubscriptionStatus, expiresAt: Long?)
    suspend fun getProfileBySupabaseId(supabaseUserId: String): UserProfile?
    fun getActiveProfileSubscriptionStatus(): Flow<SubscriptionStatus>
}
```

**Step 2: Implement in SqlDelightUserProfileRepository**

```kotlin
override suspend fun linkToSupabase(profileId: String, supabaseUserId: String) {
    queries.linkProfileToSupabase(
        supabase_user_id = supabaseUserId,
        last_auth_at = Clock.System.now().toEpochMilliseconds(),
        id = profileId
    )
}

override suspend fun updateSubscriptionStatus(profileId: String, status: SubscriptionStatus, expiresAt: Long?) {
    queries.updateSubscriptionStatus(
        subscription_status = status.toDbString(),
        subscription_expires_at = expiresAt,
        id = profileId
    )
}

override suspend fun getProfileBySupabaseId(supabaseUserId: String): UserProfile? {
    return queries.getProfileBySupabaseId(supabaseUserId)
        .executeAsOneOrNull()
        ?.toUserProfile()
}

override fun getActiveProfileSubscriptionStatus(): Flow<SubscriptionStatus> {
    return queries.getActiveProfileSubscriptionStatus()
        .asFlow()
        .mapToOneOrNull(Dispatchers.IO)
        .map { SubscriptionStatus.fromString(it) }
}
```

**Step 3: Update mapper function to include new fields**

Ensure the `toUserProfile()` mapper includes the new columns.

**Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/UserProfileRepository.kt
git add shared/src/commonMain/kotlin/com/devil/phoenixproject/data/repository/SqlDelightUserProfileRepository.kt
git commit -m "feat: add subscription methods to UserProfileRepository"
```

---

## Task 10: Create SubscriptionManager

**Files:**
- Create: `shared/src/commonMain/kotlin/com/devil/phoenixproject/domain/subscription/SubscriptionManager.kt`

**Step 1: Create the SubscriptionManager**

```kotlin
package com.devil.phoenixproject.domain.subscription

import com.devil.phoenixproject.config.AppConfig
import com.devil.phoenixproject.data.repository.UserProfileRepository
import com.devil.phoenixproject.domain.model.SubscriptionStatus
import com.devil.phoenixproject.domain.model.UserProfile
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.models.CustomerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SubscriptionManager(
    private val userProfileRepository: UserProfileRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _customerInfo = MutableStateFlow<CustomerInfo?>(null)
    val customerInfo: StateFlow<CustomerInfo?> = _customerInfo.asStateFlow()

    private val _isProSubscriber = MutableStateFlow(false)
    val hasProAccess: StateFlow<Boolean> = _isProSubscriber.asStateFlow()

    // Feature access flows - all tied to Pro for now
    val canUseAIRoutines: StateFlow<Boolean> = hasProAccess
    val canAccessCommunityLibrary: StateFlow<Boolean> = hasProAccess
    val canSyncToCloud: StateFlow<Boolean> = hasProAccess
    val canUseHealthIntegrations: StateFlow<Boolean> = hasProAccess

    init {
        // Listen to RevenueCat customer info updates
        scope.launch {
            try {
                Purchases.sharedInstance.customerInfoFlow.collect { info ->
                    _customerInfo.value = info
                    updateProStatus(info)
                }
            } catch (e: Exception) {
                // RevenueCat not initialized yet
            }
        }

        // Also watch local profile subscription status as backup
        scope.launch {
            userProfileRepository.getActiveProfileSubscriptionStatus().collect { status ->
                if (_customerInfo.value == null) {
                    // Use local status if RevenueCat hasn't loaded yet
                    _isProSubscriber.value = status == SubscriptionStatus.ACTIVE
                }
            }
        }
    }

    private fun updateProStatus(customerInfo: CustomerInfo) {
        val hasEntitlement = customerInfo.entitlements
            .active
            .containsKey(AppConfig.RevenueCat.ENTITLEMENT_PRO)
        _isProSubscriber.value = hasEntitlement

        // Sync to local database
        scope.launch {
            userProfileRepository.activeProfile.value?.let { profile ->
                val status = if (hasEntitlement) SubscriptionStatus.ACTIVE else SubscriptionStatus.FREE
                val expiresAt = customerInfo.entitlements
                    .active[AppConfig.RevenueCat.ENTITLEMENT_PRO]
                    ?.expirationDate
                    ?.time
                userProfileRepository.updateSubscriptionStatus(profile.id, status, expiresAt)
            }
        }
    }

    suspend fun refreshCustomerInfo(): Result<CustomerInfo> {
        return try {
            val info = Purchases.sharedInstance.awaitCustomerInfo()
            _customerInfo.value = info
            updateProStatus(info)
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restorePurchases(): Result<CustomerInfo> {
        return try {
            val info = Purchases.sharedInstance.awaitRestore()
            _customerInfo.value = info
            updateProStatus(info)
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun loginToRevenueCat(userId: String) {
        scope.launch {
            try {
                Purchases.sharedInstance.awaitLogIn(userId)
                refreshCustomerInfo()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun logoutFromRevenueCat() {
        scope.launch {
            try {
                Purchases.sharedInstance.awaitLogOut()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
```

**Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/devil/phoenixproject/domain/subscription/
git commit -m "feat: add SubscriptionManager for premium feature gating"
```

---

## Task 11: Create RevenueCat Initializer (Platform-Specific)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/devil/phoenixproject/domain/subscription/RevenueCatInitializer.kt`
- Create: `shared/src/androidMain/kotlin/com/devil/phoenixproject/domain/subscription/RevenueCatInitializer.android.kt`
- Create: `shared/src/iosMain/kotlin/com/devil/phoenixproject/domain/subscription/RevenueCatInitializer.ios.kt`

**Step 1: Create expect declaration**

`shared/src/commonMain/kotlin/com/devil/phoenixproject/domain/subscription/RevenueCatInitializer.kt`:

```kotlin
package com.devil.phoenixproject.domain.subscription

expect object RevenueCatInitializer {
    fun initialize()
}
```

**Step 2: Create Android implementation**

`shared/src/androidMain/kotlin/com/devil/phoenixproject/domain/subscription/RevenueCatInitializer.android.kt`:

```kotlin
package com.devil.phoenixproject.domain.subscription

import android.app.Application
import com.devil.phoenixproject.config.PlatformConfig
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration

actual object RevenueCatInitializer {
    private var application: Application? = null

    fun setApplication(app: Application) {
        application = app
    }

    actual fun initialize() {
        val app = application ?: throw IllegalStateException("Application not set. Call setApplication() first.")

        Purchases.configure(
            PurchasesConfiguration(
                apiKey = PlatformConfig.revenueCatApiKey
            )
        )
    }
}
```

**Step 3: Create iOS implementation**

`shared/src/iosMain/kotlin/com/devil/phoenixproject/domain/subscription/RevenueCatInitializer.ios.kt`:

```kotlin
package com.devil.phoenixproject.domain.subscription

import com.devil.phoenixproject.config.PlatformConfig
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration

actual object RevenueCatInitializer {
    actual fun initialize() {
        Purchases.configure(
            PurchasesConfiguration(
                apiKey = PlatformConfig.revenueCatApiKey
            )
        )
    }
}
```

**Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/devil/phoenixproject/domain/subscription/RevenueCatInitializer.kt
git add shared/src/androidMain/kotlin/com/devil/phoenixproject/domain/subscription/
git add shared/src/iosMain/kotlin/com/devil/phoenixproject/domain/subscription/
git commit -m "feat: add platform-specific RevenueCat initialization"
```

---

## Task 12: Register New Services in Koin

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/devil/phoenixproject/di/AppModule.kt`

**Step 1: Add new singletons to commonModule**

```kotlin
val commonModule = module {
    // ... existing registrations ...

    // Auth
    single<AuthRepository> { SupabaseAuthRepository() }

    // Subscription
    single { SubscriptionManager(get()) }
}
```

**Step 2: Add required imports**

```kotlin
import com.devil.phoenixproject.data.repository.AuthRepository
import com.devil.phoenixproject.data.repository.SupabaseAuthRepository
import com.devil.phoenixproject.domain.subscription.SubscriptionManager
```

**Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/devil/phoenixproject/di/AppModule.kt
git commit -m "feat: register AuthRepository and SubscriptionManager in Koin"
```

---

## Task 13: Initialize RevenueCat in Android App

**Files:**
- Modify: `androidApp/src/main/java/com/devil/phoenixproject/PhoenixApplication.kt` (or MainActivity)

**Step 1: Initialize RevenueCat on app start**

In your Application class or early in MainActivity:

```kotlin
import com.devil.phoenixproject.domain.subscription.RevenueCatInitializer

class PhoenixApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Set application context for RevenueCat
        RevenueCatInitializer.setApplication(this)
        RevenueCatInitializer.initialize()

        // ... existing initialization ...
    }
}
```

**Step 2: Commit**

```bash
git add androidApp/src/main/java/com/devil/phoenixproject/
git commit -m "feat: initialize RevenueCat in Android app"
```

---

## Task 14: Initialize RevenueCat in iOS App

**Files:**
- Modify: `iosApp/VitruvianPhoenix/VitruvianPhoenixApp.swift`

**Step 1: Add RevenueCat initialization**

```swift
import shared

@main
struct VitruvianPhoenixApp: App {
    init() {
        KoinKt.doInitKoin()
        KoinKt.runMigrations()
        RevenueCatInitializer.shared.initialize()  // Add this
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

**Step 2: Commit**

```bash
git add iosApp/VitruvianPhoenix/VitruvianPhoenixApp.swift
git commit -m "feat: initialize RevenueCat in iOS app"
```

---

## Task 15: Create PremiumFeatureGate Composable

**Files:**
- Create: `shared/src/commonMain/kotlin/com/devil/phoenixproject/ui/components/PremiumFeatureGate.kt`

**Step 1: Create the gating composable**

```kotlin
package com.devil.phoenixproject.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devil.phoenixproject.domain.subscription.SubscriptionManager

@Composable
fun PremiumFeatureGate(
    subscriptionManager: SubscriptionManager,
    featureName: String,
    featureDescription: String = "Unlock with Phoenix Pro",
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val hasAccess by subscriptionManager.hasProAccess.collectAsState()

    if (hasAccess) {
        content()
    } else {
        LockedFeatureOverlay(
            featureName = featureName,
            featureDescription = featureDescription,
            onUpgradeClick = onUpgradeClick,
            modifier = modifier
        )
    }
}

@Composable
fun LockedFeatureOverlay(
    featureName: String,
    featureDescription: String,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
            .clickable(onClick = onUpgradeClick)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = featureName,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Text(
                text = featureDescription,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onUpgradeClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Upgrade to Pro")
            }
        }
    }
}

@Composable
fun PremiumBadge(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = "PRO",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
```

**Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/devil/phoenixproject/ui/components/PremiumFeatureGate.kt
git commit -m "feat: add PremiumFeatureGate composable for feature gating"
```

---

## Task 16: Create Auth Screen UI

**Files:**
- Create: `shared/src/commonMain/kotlin/com/devil/phoenixproject/ui/screens/auth/AuthScreen.kt`

**Step 1: Create the authentication screen**

```kotlin
package com.devil.phoenixproject.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.devil.phoenixproject.data.repository.AuthRepository
import com.devil.phoenixproject.data.repository.AuthState
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    onAuthSuccess: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val authState by authRepository.authState.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            onAuthSuccess()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (isSignUp) "Create Account" else "Sign In",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Sign in to unlock Phoenix Pro features",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password"
                    )
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (isSignUp) ImeAction.Next else ImeAction.Done
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (isSignUp) {
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(
            onClick = {
                errorMessage = null
                if (isSignUp && password != confirmPassword) {
                    errorMessage = "Passwords do not match"
                    return@Button
                }

                isLoading = true
                scope.launch {
                    val result = if (isSignUp) {
                        authRepository.signUpWithEmail(email, password)
                    } else {
                        authRepository.signInWithEmail(email, password)
                    }

                    isLoading = false
                    result.onFailure { e ->
                        errorMessage = e.message ?: "Authentication failed"
                    }
                }
            },
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(if (isSignUp) "Create Account" else "Sign In")
            }
        }

        TextButton(
            onClick = { isSignUp = !isSignUp }
        ) {
            Text(
                if (isSignUp) "Already have an account? Sign in"
                else "Don't have an account? Sign up"
            )
        }

        TextButton(onClick = onDismiss) {
            Text("Cancel")
        }
    }
}
```

**Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/devil/phoenixproject/ui/screens/auth/
git commit -m "feat: add AuthScreen for email authentication"
```

---

## Task 17: Create Paywall Screen

**Files:**
- Create: `shared/src/commonMain/kotlin/com/devil/phoenixproject/ui/screens/subscription/PaywallScreen.kt`

**Step 1: Create the paywall screen**

```kotlin
package com.devil.phoenixproject.ui.screens.subscription

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devil.phoenixproject.domain.subscription.SubscriptionManager
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.models.Offerings
import kotlinx.coroutines.launch

@Composable
fun PaywallScreen(
    subscriptionManager: SubscriptionManager,
    onPurchaseSuccess: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offerings by remember { mutableStateOf<Offerings?>(null) }
    var selectedPackage by remember { mutableStateOf<Package?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isPurchasing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            offerings = Purchases.sharedInstance.awaitOfferings()
            selectedPackage = offerings?.current?.availablePackages?.firstOrNull()
            isLoading = false
        } catch (e: Exception) {
            errorMessage = "Failed to load subscription options"
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "Phoenix Pro",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Unlock the full potential of your training",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Features list
        ProFeatureItem(
            icon = Icons.Default.Cloud,
            title = "Cloud Sync",
            description = "Sync your workouts across all devices"
        )

        ProFeatureItem(
            icon = Icons.Default.AutoAwesome,
            title = "AI Routines",
            description = "Generate personalized workout plans"
        )

        ProFeatureItem(
            icon = Icons.Default.PhotoCamera,
            title = "Import Programs",
            description = "Import workouts from screenshots or text"
        )

        ProFeatureItem(
            icon = Icons.Default.People,
            title = "Community Library",
            description = "Browse and share routines with others"
        )

        ProFeatureItem(
            icon = Icons.Default.FitnessCenter,
            title = "Health Integrations",
            description = "Sync with Garmin, Apple Health, and more"
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Package selection
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            offerings?.current?.availablePackages?.forEach { pkg ->
                PackageOption(
                    package_ = pkg,
                    isSelected = selectedPackage == pkg,
                    onClick = { selectedPackage = pkg }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Purchase button
        Button(
            onClick = {
                selectedPackage?.let { pkg ->
                    isPurchasing = true
                    scope.launch {
                        try {
                            Purchases.sharedInstance.awaitPurchase(pkg)
                            subscriptionManager.refreshCustomerInfo()
                            onPurchaseSuccess()
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Purchase failed"
                        }
                        isPurchasing = false
                    }
                }
            },
            enabled = selectedPackage != null && !isPurchasing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isPurchasing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Subscribe Now")
            }
        }

        // Restore purchases
        TextButton(
            onClick = {
                scope.launch {
                    try {
                        subscriptionManager.restorePurchases()
                    } catch (e: Exception) {
                        errorMessage = "Failed to restore purchases"
                    }
                }
            }
        ) {
            Text("Restore Purchases")
        }

        TextButton(onClick = onDismiss) {
            Text("Maybe Later")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Legal text
        Text(
            text = "Subscription automatically renews unless canceled at least 24 hours before the end of the current period.",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ProFeatureItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackageOption(
    package_: Package,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) null else CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = package_.product.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = package_.product.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = package_.product.priceString,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
```

**Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/devil/phoenixproject/ui/screens/subscription/
git commit -m "feat: add PaywallScreen for subscription purchase"
```

---

## Task 18: Create Go Pro Flow Coordinator

**Files:**
- Create: `shared/src/commonMain/kotlin/com/devil/phoenixproject/ui/screens/subscription/GoProFlow.kt`

**Step 1: Create the flow coordinator**

```kotlin
package com.devil.phoenixproject.ui.screens.subscription

import androidx.compose.runtime.*
import com.devil.phoenixproject.data.repository.AuthRepository
import com.devil.phoenixproject.data.repository.AuthState
import com.devil.phoenixproject.data.repository.UserProfileRepository
import com.devil.phoenixproject.domain.subscription.SubscriptionManager
import com.devil.phoenixproject.ui.screens.auth.AuthScreen

enum class GoProStep {
    AUTH,
    PAYWALL,
    SUCCESS
}

@Composable
fun GoProFlow(
    authRepository: AuthRepository,
    subscriptionManager: SubscriptionManager,
    userProfileRepository: UserProfileRepository,
    onComplete: () -> Unit,
    onDismiss: () -> Unit
) {
    val authState by authRepository.authState.collectAsState()
    val hasProAccess by subscriptionManager.hasProAccess.collectAsState()

    // Determine starting step based on current state
    var currentStep by remember {
        mutableStateOf(
            when {
                hasProAccess -> GoProStep.SUCCESS
                authState is AuthState.Authenticated -> GoProStep.PAYWALL
                else -> GoProStep.AUTH
            }
        )
    }

    // Watch for state changes
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated && currentStep == GoProStep.AUTH) {
            // Link profile to Supabase account
            val user = (authState as AuthState.Authenticated).user
            userProfileRepository.activeProfile.value?.let { profile ->
                userProfileRepository.linkToSupabase(profile.id, user.id)
            }
            // Login to RevenueCat with Supabase user ID
            subscriptionManager.loginToRevenueCat(user.id)
            currentStep = GoProStep.PAYWALL
        }
    }

    LaunchedEffect(hasProAccess) {
        if (hasProAccess) {
            currentStep = GoProStep.SUCCESS
        }
    }

    when (currentStep) {
        GoProStep.AUTH -> {
            AuthScreen(
                authRepository = authRepository,
                onAuthSuccess = {
                    // Will be handled by LaunchedEffect above
                },
                onDismiss = onDismiss
            )
        }

        GoProStep.PAYWALL -> {
            PaywallScreen(
                subscriptionManager = subscriptionManager,
                onPurchaseSuccess = {
                    currentStep = GoProStep.SUCCESS
                },
                onDismiss = onDismiss
            )
        }

        GoProStep.SUCCESS -> {
            // Show success briefly then complete
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(1500)
                onComplete()
            }

            SubscriptionSuccessScreen()
        }
    }
}

@Composable
private fun SubscriptionSuccessScreen() {
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = androidx.compose.ui.Modifier.size(64.dp),
                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
            )

            androidx.compose.material3.Text(
                text = "Welcome to Phoenix Pro!",
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
            )

            androidx.compose.material3.Text(
                text = "All premium features are now unlocked",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

**Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/devil/phoenixproject/ui/screens/subscription/GoProFlow.kt
git commit -m "feat: add GoProFlow coordinator for auth + purchase flow"
```

---

## Task 19: Add Subscription Settings Section

**Files:**
- Create: `shared/src/commonMain/kotlin/com/devil/phoenixproject/ui/screens/settings/SubscriptionSettingsSection.kt`

**Step 1: Create subscription settings UI**

```kotlin
package com.devil.phoenixproject.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devil.phoenixproject.data.repository.AuthRepository
import com.devil.phoenixproject.data.repository.AuthState
import com.devil.phoenixproject.domain.subscription.SubscriptionManager
import kotlinx.coroutines.launch

@Composable
fun SubscriptionSettingsSection(
    subscriptionManager: SubscriptionManager,
    authRepository: AuthRepository,
    onGoProClick: () -> Unit,
    onManageSubscriptionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasProAccess by subscriptionManager.hasProAccess.collectAsState()
    val authState by authRepository.authState.collectAsState()
    val customerInfo by subscriptionManager.customerInfo.collectAsState()
    val scope = rememberCoroutineScope()

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (hasProAccess) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = if (hasProAccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (hasProAccess) "Phoenix Pro" else "Free Plan",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (hasProAccess) {
                        customerInfo?.entitlements?.active?.get("pro_access")?.let { entitlement ->
                            entitlement.expirationDate?.let { date ->
                                Text(
                                    text = "Renews ${formatDate(date.time)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Divider()

            if (hasProAccess) {
                // Show account info
                (authState as? AuthState.Authenticated)?.user?.let { user ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = user.email ?: "No email",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                TextButton(
                    onClick = onManageSubscriptionClick
                ) {
                    Text("Manage Subscription")
                }

                TextButton(
                    onClick = {
                        scope.launch {
                            authRepository.signOut()
                            subscriptionManager.logoutFromRevenueCat()
                        }
                    }
                ) {
                    Text("Sign Out")
                }
            } else {
                Text(
                    text = "Upgrade to Pro for cloud sync, AI routines, and more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onGoProClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Star, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upgrade to Pro")
                }

                TextButton(
                    onClick = {
                        scope.launch {
                            subscriptionManager.restorePurchases()
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Restore Purchases")
                }
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String {
    // Simple date formatting - in production use kotlinx-datetime
    val date = java.util.Date(epochMillis)
    val format = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
    return format.format(date)
}
```

**Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/devil/phoenixproject/ui/screens/settings/
git commit -m "feat: add SubscriptionSettingsSection for settings screen"
```

---

## Task 20: Verify Build and Create Final Commit

**Step 1: Verify Android build**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: Verify iOS framework build**

Run: `./gradlew :shared:compileKotlinIosArm64`
Expected: BUILD SUCCESSFUL

**Step 3: Run tests**

Run: `./gradlew :shared:allTests`
Expected: All tests pass

**Step 4: Create summary commit**

```bash
git add .
git commit -m "feat: complete Phase 1 subscription foundation

- Added Supabase client for auth and data
- Added RevenueCat SDK for payments
- Created SubscriptionManager for feature gating
- Extended UserProfile with subscription fields
- Created AuthScreen for email authentication
- Created PaywallScreen for subscription purchase
- Created GoProFlow coordinator
- Added PremiumFeatureGate composable
- Added subscription section to settings

Ready for Phase 2 (Cloud Sync) implementation."
```

---

## Integration Checklist

After completing all tasks, verify:

- [ ] Supabase project created with auth providers enabled
- [ ] RevenueCat project created with products and entitlements
- [ ] App builds successfully on Android
- [ ] App builds successfully on iOS
- [ ] RevenueCat initializes without errors
- [ ] Auth flow works (sign up, sign in, sign out)
- [ ] Paywall shows subscription options
- [ ] Purchase flow completes (use sandbox/test accounts)
- [ ] Subscription status persists across app restarts
- [ ] PremiumFeatureGate correctly gates content
- [ ] Settings shows correct subscription state

---

## Next Steps

After Phase 1 is complete:

1. **Phase 2: Cloud Sync** - Sync tables, CloudSyncManager, conflict resolution
2. **Phase 3: AI Features** - Edge functions, routine generation, program import
3. **Phase 4: Community Library** - Shared routines, browse/search, ratings
4. **Phase 5: Health Integrations** - Health Connect, HealthKit, third-party APIs
