package services.supabase

import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object AuthRepository {

    val sessionStatus
        get() = SupabaseService.auth.sessionStatus

    suspend fun hasActiveSession(timeout: Duration = 10.seconds): Boolean {
        withTimeoutOrNull(timeout) {
            SupabaseService.auth.awaitInitialization()
        }
        return hasSessionNow()
    }

    private fun hasSessionNow(): Boolean =
        SupabaseService.auth.currentSessionOrNull() != null

    suspend fun signIn(email: String, password: String) {
        SupabaseService.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String): Boolean {
        SupabaseService.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        return hasSessionNow()
    }

    suspend fun signInWithGoogle() {
        SupabaseService.auth.signInWith(Google)
    }

    suspend fun signOut() {
        SupabaseService.auth.signOut()
    }
}