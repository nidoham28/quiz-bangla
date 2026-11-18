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

## Saving quiz drafts

The Add Questions screen sends drafts to the `save-quiz-draft` Edge Function.
Apply `supabase/migrations/quiz_schema.sql` first, then apply
`supabase/migrations/save_quiz_draft.sql` in the Supabase SQL Editor. Deploy the
function from a Supabase CLI project linked to the same Supabase project:

```text
supabase functions deploy save-quiz-draft
```

The function validates the caller's Supabase session and allows writes only
when the authenticated email is `nidohamondol@gmail.com`. It reuses a draft
quiz with the same title (ignoring case and surrounding whitespace) and skips
duplicate question text/image content within that quiz. New quizzes remain
drafts.

The Edge Function requires the standard `SUPABASE_URL`, `SUPABASE_ANON_KEY`,
and `SUPABASE_SERVICE_ROLE_KEY` runtime secrets. Keep the service-role key
server-side; never add it to the app or commit it to this repository.
