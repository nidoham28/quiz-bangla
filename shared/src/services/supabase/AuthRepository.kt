package services.supabase

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

internal object AuthRepository {
    private val client by lazy {
        require(SupabaseConfig.PROJECT_URL.isNotBlank() && SupabaseConfig.PUBLISHABLE_KEY.isNotBlank()) {
            "Supabase is not configured. Set the project URL and publishable key in SupabaseConfig.kt."
        }

        createSupabaseClient(
            supabaseUrl = SupabaseConfig.PROJECT_URL,
            supabaseKey = SupabaseConfig.PUBLISHABLE_KEY,
        ) {
            install(Auth)
        }
    }

    suspend fun hasActiveSession(): Boolean =
        client.auth.currentSessionOrNull() != null

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String): Boolean {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        return hasActiveSession()
    }

    suspend fun signOut() {
        client.auth.signOut()
    }
}
