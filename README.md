# Supabase Auth setup

The shared app uses Supabase email/password Auth. Before running it, set the
project URL and its publishable (or legacy anon) key in
`shared/src/services/supabase/SupabaseConfig.kt`. Do not use a `service_role`
key in the client or commit project credentials.

Enable the Email provider in Supabase Auth. Email confirmation follows the
project's **Confirm email** setting; when enabled, users must confirm their
address before signing in. The existing `supabase/migrations/user_profile.sql`
migration creates a `user_profiles` row for each new Auth user.

Google sign-in is available from both login and registration. Enable the
Google provider and configure its OAuth credentials in Supabase Auth; keep the
provider secret in Supabase rather than the app. Add
`quizbangla://login-callback` to the project's allowed redirect URLs. This
callback scheme is registered by the Android and iOS app targets.

The shared module supports Android, iOS, desktop JVM, and WebAssembly. The
Supabase Kotlin Auth SDK manages the auth session on each platform.