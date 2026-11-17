-- Match/session schema for Single Play, Custom rooms and live 1v1 Challenges.
-- Run after user_profile.sql and quiz_schema.sql.
-- Match creation, joining, answer submission and finalization must use trusted
-- server-side Functions. Do not grant app clients direct write access.

begin;

create table if not exists public.matches (
    id uuid primary key default gen_random_uuid(),
    quiz_id uuid not null references public.quizzes(id) on delete restrict,
    mode text not null check (mode in ('single', 'custom', 'challenge')),
    status text not null default 'waiting'
        check (status in ('waiting', 'active', 'completed', 'cancelled')),
    host_user_id uuid not null references public.user_profiles(id) on delete restrict,
    room_code text unique,
    max_players smallint not null default 1 check (max_players between 1 and 100),
    question_count integer not null check (question_count > 0),
    current_question_position integer not null default 0 check (current_question_position >= 0),
    winner_id uuid references public.user_profiles(id) on delete set null,
    result text check (result is null or result in ('player_one_win', 'player_two_win', 'draw', 'host_win_by_forfeit', 'guest_win_by_forfeit')),
    end_reason text check (end_reason is null or end_reason in ('normal', 'timeout', 'forfeit', 'server_cancel')),
    created_at timestamptz not null default now(),
    started_at timestamptz,
    ends_at timestamptz,
    completed_at timestamptz,
    score_settled_at timestamptz,
    unique (id, quiz_id),
    constraint matches_mode_player_count check (
        (mode = 'single' and max_players = 1)
        or (mode = 'challenge' and max_players = 2)
        or (mode = 'custom' and max_players between 2 and 100)
    ),
    constraint matches_settled_only_when_completed check (
        score_settled_at is null or status = 'completed'
    )
);

create table if not exists public.match_players (
    match_id uuid not null references public.matches(id) on delete cascade,
    user_id uuid not null references public.user_profiles(id) on delete restrict,
    seat smallint not null check (seat > 0),
    player_status text not null default 'joined'
        check (player_status in ('joined', 'ready', 'disconnected', 'forfeited', 'completed')),
    joined_at timestamptz not null default now(),
    last_seen_at timestamptz,
    primary key (match_id, user_id),
    unique (match_id, seat)
);

-- Snapshot the question set/order for a match so later quiz edits do not
-- change a match already in progress.
create table if not exists public.match_questions (
    match_id uuid not null,
    quiz_id uuid not null,
    question_id uuid not null,
    position integer not null check (position >= 0),
    primary key (match_id, question_id),
    unique (match_id, position),
    constraint match_questions_match_quiz_fk
        foreign key (match_id, quiz_id)
        references public.matches (id, quiz_id) on delete cascade,
    constraint match_questions_question_quiz_fk
        foreign key (quiz_id, question_id)
        references public.questions (quiz_id, id) on delete restrict
);

create table if not exists public.answers (
    id uuid primary key default gen_random_uuid(),
    match_id uuid not null,
    user_id uuid not null,
    question_id uuid not null,
    selected_option_id uuid not null,
    is_correct boolean not null,
    response_time_ms integer check (response_time_ms is null or response_time_ms >= 0),
    submitted_at timestamptz not null default now(),
    constraint answers_one_per_player_question unique (match_id, user_id, question_id),
    constraint answers_match_question_fk
        foreign key (match_id, question_id)
        references public.match_questions (match_id, question_id) on delete cascade,
    constraint answers_match_player_fk
        foreign key (match_id, user_id)
        references public.match_players (match_id, user_id) on delete cascade,
    constraint answers_option_for_question_fk
        foreign key (question_id, selected_option_id)
        references public.question_options (question_id, id) on delete restrict
);

-- Realtime/client users may read only data for matches they joined.
-- Membership check is security-definer to avoid recursive RLS on match_players.
create or replace function public.is_match_member(p_match_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.match_players mp
        where mp.match_id = p_match_id
          and mp.user_id = (select auth.uid())
    );
$$;
revoke all on function public.is_match_member(uuid) from public, anon;
grant execute on function public.is_match_member(uuid) to authenticated;

alter table public.matches enable row level security;
alter table public.match_players enable row level security;
alter table public.match_questions enable row level security;
alter table public.answers enable row level security;

revoke all on public.matches, public.match_players, public.match_questions, public.answers
    from public, anon, authenticated;
grant select on public.matches, public.match_players, public.match_questions, public.answers
    to authenticated;
grant all on public.matches, public.match_players, public.match_questions, public.answers
    to service_role;

-- No anon access to private match data.
drop policy if exists "Members can read their matches" on public.matches;
create policy "Members can read their matches"
    on public.matches for select to authenticated
    using (public.is_match_member(id));

drop policy if exists "Members can read players in their matches" on public.match_players;
create policy "Members can read players in their matches"
    on public.match_players for select to authenticated
    using (public.is_match_member(match_id));

drop policy if exists "Members can read questions in their matches" on public.match_questions;
create policy "Members can read questions in their matches"
    on public.match_questions for select to authenticated
    using (public.is_match_member(match_id));

drop policy if exists "Members can read answers in their matches" on public.answers;
drop policy if exists "Players can read only their own answers" on public.answers;
create policy "Players can read only their own answers"
    on public.answers for select to authenticated
    using (user_id = (select auth.uid()) and public.is_match_member(match_id));

-- There are deliberately no client INSERT/UPDATE/DELETE policies. Trusted
-- Functions must validate membership, mode, match state, deadlines, answer
-- correctness, player count, result and one-time score settlement.

create index if not exists matches_status_created_idx
    on public.matches (status, created_at desc);
create index if not exists matches_quiz_status_idx
    on public.matches (quiz_id, status);
create index if not exists match_players_user_joined_idx
    on public.match_players (user_id, joined_at desc);
create index if not exists match_questions_order_idx
    on public.match_questions (match_id, position);
create index if not exists answers_user_match_idx
    on public.answers (user_id, match_id, submitted_at desc);

comment on column public.matches.mode is 'single, custom, or challenge.';
comment on column public.matches.room_code is 'Optional invite code for Custom/Challenge rooms; generate securely and expire it server-side.';
comment on column public.matches.question_count is 'Question count fixed when the server creates the match.';
comment on column public.matches.score_settled_at is 'Set by trusted server logic after one-time score_transactions settlement.';
comment on table public.match_questions is 'Immutable-in-practice snapshot of questions and order selected for a match.';
comment on column public.answers.is_correct is 'Server-validated correctness; never trust a client-supplied value.';

commit;
