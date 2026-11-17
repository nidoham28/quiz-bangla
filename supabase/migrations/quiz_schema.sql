-- Quiz content schema for the online quiz game.
-- Run after user_profile.sql. Admin app must use trusted server-side
-- Functions for writes; never ship the Supabase service_role key in an app.
-- Correct answers are stored separately and denied to anon/authenticated roles.

begin;

-- Admin authorization records. Provision these rows only from a trusted
-- server/admin workflow; do not let users assign themselves as admins.
create table if not exists public.admin_roles (
    user_id uuid primary key references auth.users (id) on delete cascade,
    role text not null default 'quiz_admin' check (role in ('quiz_admin', 'super_admin')),
    created_at timestamptz not null default now()
);
alter table public.admin_roles enable row level security;
revoke all on public.admin_roles from public, anon, authenticated;
grant select on public.admin_roles to service_role;

create table if not exists public.categories (
    id uuid primary key default gen_random_uuid(),
    name text not null check (char_length(trim(name)) between 1 and 60),
    slug text not null unique check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    description text,
    is_active boolean not null default true,
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.quizzes (
    id uuid primary key default gen_random_uuid(),
    title text not null check (char_length(trim(title)) between 1 and 120),
    description text check (description is null or char_length(description) <= 1000),
    category_id uuid references public.categories(id) on delete restrict,
    difficulty text not null default 'medium'
        check (difficulty in ('easy', 'medium', 'hard')),
    language text not null default 'eng' check (language = 'eng'),
    cover_image text,
    status text not null default 'draft'
        check (status in ('draft', 'published', 'archived')),
    time_limit_seconds integer
        check (time_limit_seconds is null or time_limit_seconds between 5 and 7200),
    created_by uuid references auth.users(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    published_at timestamptz
);

create table if not exists public.questions (
    id uuid primary key default gen_random_uuid(),
    quiz_id uuid not null references public.quizzes(id) on delete cascade,
    position integer not null check (position >= 0),
    prompt text,
    image_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (quiz_id, position),
    unique (quiz_id, id)
);

-- A question may contain text, an image, or both.
alter table public.questions
    alter column prompt drop not null,
    add column if not exists image_url text;
alter table public.questions
    drop constraint if exists questions_prompt_check,
    drop constraint if exists questions_prompt_or_image_required,
    drop constraint if exists questions_prompt_length;
alter table public.questions
    add constraint questions_prompt_length check (
        prompt is null or char_length(trim(prompt)) between 1 and 2000
    ),
    add constraint questions_prompt_or_image_required check (
        nullif(trim(prompt), '') is not null or nullif(trim(image_url), '') is not null
    );

create table if not exists public.question_options (
    id uuid primary key default gen_random_uuid(),
    question_id uuid not null references public.questions(id) on delete cascade,
    position integer not null check (position >= 0),
    option_text text,
    image_url text,
    created_at timestamptz not null default now(),
    unique (question_id, position),
    unique (question_id, id)
);

-- An answer option may contain text, an image, or both.
alter table public.question_options
    alter column option_text drop not null,
    add column if not exists image_url text;
alter table public.question_options
    drop constraint if exists question_options_option_text_check,
    drop constraint if exists question_options_option_text_or_image_required,
    drop constraint if exists question_options_option_text_length;
alter table public.question_options
    add constraint question_options_option_text_length check (
        option_text is null or char_length(trim(option_text)) between 1 and 500
    ),
    add constraint question_options_option_text_or_image_required check (
        nullif(trim(option_text), '') is not null or nullif(trim(image_url), '') is not null
    );

-- Correct answers are not returned to player clients. Trusted server-side
-- answer-validation logic reads this table with the service_role.
create table if not exists public.question_answer_keys (
    question_id uuid primary key references public.questions(id) on delete cascade,
    correct_option_id uuid not null,
    created_at timestamptz not null default now(),
    constraint question_answer_keys_option_belongs_to_question
        foreign key (question_id, correct_option_id)
        references public.question_options (question_id, id)
        on delete cascade
);

-- Only the trusted backend can read/write the answer key or administer content.
alter table public.question_answer_keys enable row level security;
revoke all on public.question_answer_keys from public, anon, authenticated;
grant all on public.question_answer_keys to service_role;

-- Quiz/category content is readable by players only when published/active.
alter table public.categories enable row level security;
alter table public.quizzes enable row level security;
alter table public.questions enable row level security;
alter table public.question_options enable row level security;

revoke all on public.categories, public.quizzes, public.questions, public.question_options
    from public, anon, authenticated;
grant select on public.categories, public.quizzes, public.questions, public.question_options
    to anon, authenticated;
grant all on public.categories, public.quizzes, public.questions, public.question_options
    to service_role;

-- Categories: show active categories to visitors. The quiz list itself only
-- includes published quizzes.
drop policy if exists "Anyone can read active categories" on public.categories;
create policy "Anyone can read active categories"
    on public.categories for select to anon, authenticated
    using (is_active = true);

drop policy if exists "Anyone can read published quizzes" on public.quizzes;
create policy "Anyone can read published quizzes"
    on public.quizzes for select to anon, authenticated
    using (status = 'published');

drop policy if exists "Anyone can read questions from published quizzes" on public.questions;
create policy "Anyone can read questions from published quizzes"
    on public.questions for select to anon, authenticated
    using (
        exists (
            select 1 from public.quizzes q
            where q.id = questions.quiz_id and q.status = 'published'
        )
    );

drop policy if exists "Anyone can read options from published quizzes" on public.question_options;
create policy "Anyone can read options from published quizzes"
    on public.question_options for select to anon, authenticated
    using (
        exists (
            select 1
            from public.questions qs
            join public.quizzes q on q.id = qs.quiz_id
            where qs.id = question_options.question_id
              and q.status = 'published'
        )
    );

-- Validate four options and one answer key per question before publishing.
-- Invoke only from a trusted Edge Function after it verifies admin_roles.
create or replace function public.publish_quiz(p_quiz_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_question_count integer;
begin
    if p_quiz_id is null then
        raise exception 'quiz_id is required';
    end if;

    if not exists (select 1 from public.quizzes q where q.id = p_quiz_id) then
        raise exception 'Quiz not found';
    end if;

    select count(*)::integer into v_question_count
    from public.questions qs where qs.quiz_id = p_quiz_id;

    if v_question_count < 1 then
        raise exception 'A quiz must contain at least one question';
    end if;

    if exists (
        select 1
        from public.questions qs
        left join lateral (
            select count(*)::integer as option_count
            from public.question_options qo
            where qo.question_id = qs.id
        ) opts on true
        left join public.question_answer_keys ak on ak.question_id = qs.id
        where qs.quiz_id = p_quiz_id
          and (opts.option_count <> 4 or ak.question_id is null)
    ) then
        raise exception 'Every question must have exactly four options and one correct answer key';
    end if;

    update public.quizzes
    set status = 'published',
        published_at = coalesce(published_at, now()),
        updated_at = now()
    where id = p_quiz_id;
end;
$$;
revoke all on function public.publish_quiz(uuid) from public, anon, authenticated;
grant execute on function public.publish_quiz(uuid) to service_role;

-- No INSERT/UPDATE/DELETE policies or grants are given to app users.
-- Admin app writes must go through an Edge Function that validates the caller
-- against admin_roles, then uses a server-side service_role client.

create index if not exists quizzes_published_category_idx
    on public.quizzes (category_id, created_at desc)
    where status = 'published';
create index if not exists questions_quiz_position_idx
    on public.questions (quiz_id, position);
create index if not exists question_options_question_position_idx
    on public.question_options (question_id, position);
create index if not exists admin_roles_role_idx
    on public.admin_roles (role);

comment on table public.admin_roles is 'Admin authorization list. Manage only through a trusted server/admin workflow.';
comment on table public.question_answer_keys is 'Private answer key. Never expose to player clients.';
comment on table public.question_options is 'Public answer choices; this table does not contain correctness information.';
comment on column public.quizzes.status is 'draft, published, or archived. Players can read published quizzes only.';
comment on column public.quizzes.time_limit_seconds is 'Optional quiz time limit; authoritative timing must be enforced server-side.';

commit;
