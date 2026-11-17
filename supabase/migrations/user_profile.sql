-- Supabase user profiles and game-account fields.
-- Run in the Supabase SQL Editor or through the project's migration workflow.
-- New Auth users start with 1000 Currency, Gold and Diamond.
-- Client users cannot change balances, likes, experience, badge, ban/premium state,
-- activity preferences, or IDs. Use trusted server-side logic for those fields.

begin;

-- Preserve data from the previous version of this schema when renaming fields.
do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'user_profiles' and column_name = 'display_name'
    ) and not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'user_profiles' and column_name = 'name'
    ) then
        alter table public.user_profiles rename column display_name to name;
    end if;

    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'user_profiles' and column_name = 'avatar_url'
    ) and not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'user_profiles' and column_name = 'avatar'
    ) then
        alter table public.user_profiles rename column avatar_url to avatar;
    end if;

    -- The previous version used these as numeric activity counters. Preserve
    -- those values under explicit counter names; the requested fields below
    -- are now activity preferences (categories).
    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'user_profiles' and column_name = 'active_days'
          and data_type <> 'text'
    ) and not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'user_profiles' and column_name = 'active_days_count'
    ) then
        alter table public.user_profiles rename column active_days to active_days_count;
    end if;

    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'user_profiles' and column_name = 'active_time_seconds'
    ) and not exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'user_profiles' and column_name = 'active_time_seconds_total'
    ) then
        alter table public.user_profiles rename column active_time_seconds to active_time_seconds_total;
    end if;
end;
$$;

create table if not exists public.user_profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    name text,
    description text,
    avatar text,
    gender text not null default 'unspecified',
    date_of_birth date,
    language text not null default 'eng',
    is_verified boolean not null default false,
    is_private boolean not null default false,
    is_banned boolean not null default false,
    is_premium boolean not null default false,
    status text not null default 'active',
    active_days text not null default 'flexible',
    active_time text not null default 'flexible',
    mode text not null default 'no_prefs',
    profile_likes_count bigint not null default 0,
    experience_score bigint not null default 0,
    currency_balance bigint not null default 1000,
    gold_score bigint not null default 1000,
    diamond_score bigint not null default 1000,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint user_profiles_id_unique unique (id),
    constraint user_profiles_name_length check (name is null or char_length(name) <= 25),
    constraint user_profiles_description_length check (description is null or char_length(description) <= 150),
    constraint user_profiles_gender_allowed check (gender in ('male', 'female', 'unspecified')),
    constraint user_profiles_language_allowed check (language = 'eng'),
    constraint user_profiles_active_days_allowed check (active_days in ('flexible', 'weekdays', 'weekend_only')),
    constraint user_profiles_active_time_allowed check (active_time in ('morning', 'afternoon', 'night', 'flexible')),
    constraint user_profiles_mode_allowed check (mode in ('no_prefs', 'classic', 'practice', 'challenge')),
    constraint user_profiles_status_allowed check (status in ('active', 'inactive', 'deactive')),
    constraint user_profiles_likes_nonnegative check (profile_likes_count >= 0),
    constraint user_profiles_experience_nonnegative check (experience_score >= 0),
    constraint user_profiles_currency_nonnegative check (currency_balance >= 0),
    constraint user_profiles_gold_nonnegative check (gold_score >= 0),
    constraint user_profiles_diamond_nonnegative check (diamond_score >= 0)
);

-- Add requested columns to a previously-created table, preserving existing rows.
alter table public.user_profiles
    add column if not exists name text,
    add column if not exists description text,
    add column if not exists avatar text,
    add column if not exists gender text not null default 'unspecified',
    add column if not exists date_of_birth date,
    add column if not exists language text not null default 'eng',
    add column if not exists is_verified boolean not null default false,
    add column if not exists is_private boolean not null default false,
    add column if not exists is_banned boolean not null default false,
    add column if not exists is_premium boolean not null default false,
    add column if not exists status text not null default 'active',
    add column if not exists active_days text not null default 'flexible',
    add column if not exists active_time text not null default 'flexible',
    add column if not exists mode text not null default 'no_prefs',
    add column if not exists profile_likes_count bigint not null default 0,
    add column if not exists experience_score bigint not null default 0,
    add column if not exists currency_balance bigint not null default 1000,
    add column if not exists gold_score bigint not null default 1000,
    add column if not exists diamond_score bigint not null default 1000,
    add column if not exists created_at timestamptz not null default now(),
    add column if not exists updated_at timestamptz not null default now();

-- Replace old/looser checks with the requested allowed values and limits.
alter table public.user_profiles
    drop constraint if exists user_profiles_id_unique,
    drop constraint if exists user_profiles_name_length,
    drop constraint if exists user_profiles_display_name_length,
    drop constraint if exists user_profiles_description_length,
    drop constraint if exists user_profiles_gender_allowed,
    drop constraint if exists user_profiles_language_allowed,
    drop constraint if exists user_profiles_language_code_length,
    drop constraint if exists user_profiles_active_days_allowed,
    drop constraint if exists user_profiles_active_time_allowed,
    drop constraint if exists user_profiles_mode_allowed,
    drop constraint if exists user_profiles_status_allowed,
    drop constraint if exists user_profiles_likes_nonnegative,
    drop constraint if exists user_profiles_experience_nonnegative,
    drop constraint if exists user_profiles_active_days_nonnegative,
    drop constraint if exists user_profiles_active_time_nonnegative,
    drop constraint if exists user_profiles_currency_nonnegative,
    drop constraint if exists user_profiles_gold_nonnegative,
    drop constraint if exists user_profiles_diamond_nonnegative;

alter table public.user_profiles
    add constraint user_profiles_id_unique unique (id),
    add constraint user_profiles_name_length check (name is null or char_length(name) <= 25),
    add constraint user_profiles_description_length check (description is null or char_length(description) <= 150),
    add constraint user_profiles_gender_allowed check (gender in ('male', 'female', 'unspecified')),
    add constraint user_profiles_language_allowed check (language = 'eng'),
    add constraint user_profiles_active_days_allowed check (active_days in ('flexible', 'weekdays', 'weekend_only')),
    add constraint user_profiles_active_time_allowed check (active_time in ('morning', 'afternoon', 'night', 'flexible')),
    add constraint user_profiles_mode_allowed check (mode in ('no_prefs', 'classic', 'practice', 'challenge')),
    add constraint user_profiles_status_allowed check (status in ('active', 'inactive', 'deactive')),
    add constraint user_profiles_likes_nonnegative check (profile_likes_count >= 0),
    add constraint user_profiles_experience_nonnegative check (experience_score >= 0),
    add constraint user_profiles_currency_nonnegative check (currency_balance >= 0),
    add constraint user_profiles_gold_nonnegative check (gold_score >= 0),
    add constraint user_profiles_diamond_nonnegative check (diamond_score >= 0);

comment on column public.user_profiles.id is 'Unique profile identifier and Supabase Auth user ID.';
comment on column public.user_profiles.name is 'Optional display name, maximum 25 characters.';
comment on column public.user_profiles.description is 'Optional profile description, maximum 150 characters.';
comment on column public.user_profiles.avatar is 'Optional avatar value/URL. NULL and an empty string are both allowed.';
comment on column public.user_profiles.gender is 'Allowed values: male, female, unspecified.';
comment on column public.user_profiles.date_of_birth is 'Optional date of birth; NULL is allowed. Treat as private personal information.';
comment on column public.user_profiles.language is 'Currently only eng is supported.';
comment on column public.user_profiles.is_verified is 'Verified badge state; trusted staff/server-side logic only.';
comment on column public.user_profiles.is_private is 'Profile privacy setting; enforce visibility in read policies and leaderboard queries.';
comment on column public.user_profiles.is_banned is 'Ban state; trusted staff/server-side logic only. Banned profiles are excluded from the public profile view.';
comment on column public.user_profiles.status is 'Server-only account status: active, inactive, or deactive. Not selectable or writable by client roles; only active, non-banned, non-private profiles appear publicly.';
comment on column public.user_profiles.is_premium is 'Premium entitlement; trusted billing/server-side logic only.';
comment on column public.user_profiles.active_days is 'Preference: flexible, weekdays, or weekend_only.';
comment on column public.user_profiles.active_time is 'Preference: morning, afternoon, night, or flexible.';
comment on column public.user_profiles.mode is 'Preference: no_prefs, classic, practice, or challenge.';
comment on column public.user_profiles.profile_likes_count is 'Likes received; update only through trusted server-side logic.';
comment on column public.user_profiles.experience_score is 'Experience points for level calculation; update only through trusted server-side logic.';

-- Keep updated_at current for permitted profile edits.
create or replace function public.set_user_profiles_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

drop trigger if exists set_user_profiles_updated_at on public.user_profiles;
create trigger set_user_profiles_updated_at
before update on public.user_profiles
for each row execute function public.set_user_profiles_updated_at();

-- Create a profile once when an Auth user is created.
create or replace function public.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.user_profiles (
        id,
        name,
        description,
        avatar,
        gender,
        date_of_birth,
        language,
        is_verified,
        is_private,
        is_banned,
        is_premium,
        active_days,
        active_time,
        mode,
        profile_likes_count,
        experience_score,
        currency_balance,
        gold_score,
        diamond_score
    )
    values (
        new.id,
        nullif(trim(new.raw_user_meta_data ->> 'name'), ''),
        null,
        null,
        'unspecified',
        null,
        'eng',
        false,
        false,
        false,
        false,
        'flexible',
        'flexible',
        'no_prefs',
        0,
        0,
        1000,
        1000,
        1000
    )
    on conflict (id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created_profile on auth.users;
create trigger on_auth_user_created_profile
    after insert on auth.users
    for each row execute function public.handle_new_auth_user();

-- Backfill existing Auth users without resetting any existing profile/balance.
insert into public.user_profiles (id, name, currency_balance, gold_score, diamond_score)
select u.id, nullif(trim(u.raw_user_meta_data ->> 'name'), ''), 1000, 1000, 1000
from auth.users as u
on conflict (id) do nothing;

alter table public.user_profiles enable row level security;
revoke all on table public.user_profiles from anon, authenticated;

-- Owners can read all columns only on their own row (RLS below).
grant select (
    id, name, description, avatar, gender, date_of_birth, language,
    is_verified, is_private, is_banned, is_premium,
    active_days, active_time, mode, profile_likes_count, experience_score,
    currency_balance, gold_score, diamond_score, created_at, updated_at
) on public.user_profiles to authenticated;

-- Public-safe projection. This view intentionally omits DOB, balances, ban/
-- premium flags, activity details and other private/internal fields.
-- Private profiles are absent from this view.
create or replace view public.public_profiles
with (security_barrier = true)
as
select id, name, avatar, description, is_verified, profile_likes_count
from public.user_profiles
where is_private = false
  and is_banned = false
  and status = 'active';

grant select on public.public_profiles to anon, authenticated;

drop policy if exists "Users can read their own profile" on public.user_profiles;
create policy "Users can read their own profile"
    on public.user_profiles for select to authenticated
    using ((select auth.uid()) = id);

-- Users can edit these fields only. Balances, likes, experience, badge,
-- ban/premium/account status, activity preferences, mode, ID and timestamps are server-controlled.
-- The account status column is also not granted for client-side reads.
grant update (name, description, avatar, gender, date_of_birth, language, is_private)
    on table public.user_profiles to authenticated;
drop policy if exists "Users can update their own profile details" on public.user_profiles;
create policy "Users can update their own profile details"
    on public.user_profiles for update to authenticated
    using ((select auth.uid()) = id)
    with check ((select auth.uid()) = id);
revoke insert, delete on table public.user_profiles from anon, authenticated;

revoke all on function public.handle_new_auth_user() from public, anon, authenticated;
revoke all on function public.set_user_profiles_updated_at() from public, anon, authenticated;

commit;
