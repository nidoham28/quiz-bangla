# Supabase Auth setup

The shared app uses Supabase email/password Auth. Before running it, set the
project URL and its publishable (or legacy anon) key in
`shared/src/services/supabase/SupabaseConfig.kt`. Do not use a `service_role`
key in the client or commit project credentials.

Enable the Email provider in Supabase Auth. Email confirmation follows the
project's **Confirm email** setting; when enabled, users must confirm their
address before signing in. The existing `supabase/migrations/user_profile.sql`
migration creates a `user_profiles` row for each new Auth user.

The shared module supports Android, iOS, desktop JVM, and WebAssembly. The
Supabase Kotlin Auth SDK manages the auth session on each platform.