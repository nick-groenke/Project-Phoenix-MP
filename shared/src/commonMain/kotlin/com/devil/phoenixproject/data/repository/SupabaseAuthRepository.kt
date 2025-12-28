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
