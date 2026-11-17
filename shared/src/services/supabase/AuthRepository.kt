package services.supabase

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus

object AuthRepository {
    val supabaseClient by lazy {
        require(SupabaseConfig.PROJECT_URL.isNotBlank() && SupabaseConfig.PUBLISHABLE_KEY.isNotBlank()) {
            "Supabase is not configured. Set the project URL and publishable key in SupabaseConfig.kt."
        }

        createSupabaseClient(
            supabaseUrl = SupabaseConfig.PROJECT_URL,
            supabaseKey = SupabaseConfig.PUBLISHABLE_KEY,
        ) {
            install(Auth) {
                host = SupabaseConfig.OAUTH_HOST
                scheme = SupabaseConfig.OAUTH_SCHEME
                flowType = FlowType.PKCE
            }
        }
    }

    val sessionStatus
        get() = supabaseClient.auth.sessionStatus

    suspend fun hasActiveSession(): Boolean =
        supabaseClient.auth.currentSessionOrNull() != null

    suspend fun signIn(email: String, password: String) {
        supabaseClient.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String): Boolean {
        supabaseClient.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        return hasActiveSession()
    }

    suspend fun signInWithGoogle() {
        supabaseClient.auth.signInWith(Google)
    }

    suspend fun signOut() {
        supabaseClient.auth.signOut()
    }
}
