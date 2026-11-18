package services.supabase

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
// import io.github.jan.supabase.postgrest.Postgrest
// import io.github.jan.supabase.postgrest.postgrest
// import io.github.jan.supabase.storage.Storage
// import io.github.jan.supabase.storage.storage
// import io.github.jan.supabase.realtime.Realtime
// import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.SupabaseClient as SupabaseClientType

object SupabaseService {

    val instance: SupabaseClientType by lazy {
        require(SupabaseConfig.PROJECT_URL.isNotBlank() && SupabaseConfig.PUBLISHABLE_KEY.isNotBlank()) {
            "Supabase is not configured. Set the project URL and publishable key in SupabaseConfig.kt."
        }

        createSupabaseClient(
            supabaseUrl = SupabaseConfig.PROJECT_URL,
            supabaseKey = SupabaseConfig.PUBLISHABLE_KEY,
        ) {
            // Add or remove plugins here only.
            install(Auth) {
                host = SupabaseConfig.OAUTH_HOST
                scheme = SupabaseConfig.OAUTH_SCHEME
                flowType = FlowType.PKCE
            }

            // install(Postgrest)
            // install(Storage)
            // install(Realtime)
            install(Functions)
        }
    }

    // Shortcuts so other classes don't touch `instance` directly.
    val auth get() = instance.auth
    // val postgrest get() = instance.postgrest
    // val storage get() = instance.storage
    // val realtime get() = instance.realtime
    val functions get() = instance.functions
}