-- Authenticated RPCs for creating matches and joining Custom/Challenge rooms.
-- Run after match_schema.sql. These SECURITY DEFINER functions derive the
-- player ID from auth.uid(); clients cannot choose another user's ID.
-- Custom rooms remain waiting; add a separate start_custom_match RPC later.

begin;

create or replace function public.create_match(
    p_quiz_id uuid,
    p_mode text,
    p_question_count integer,
    p_max_players smallint default null
)
returns table (match_id uuid, room_code text)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
    v_user_status text;
    v_is_banned boolean;
    v_quiz_time_limit integer;
    v_available_questions integer;
    v_max_players smallint;
    v_status text;
    v_started_at timestamptz;
    v_ends_at timestamptz;
    v_room_code text;
    v_match_id uuid;
begin
    if v_user_id is null then
        raise exception 'Authentication required' using errcode = '28000';
    end if;
    if p_quiz_id is null or p_question_count is null or p_question_count < 1 then
        raise exception 'A quiz and positive question count are required';
    end if;
    if p_mode is null or p_mode not in ('single', 'custom', 'challenge') then
        raise exception 'Invalid match mode';
    end if;

    select up.status, up.is_banned into v_user_status, v_is_banned
    from public.user_profiles up where up.id = v_user_id;
    if not found or v_is_banned or v_user_status <> 'active' then
        raise exception 'Account is not permitted to play';
    end if;

    select q.time_limit_seconds into v_quiz_time_limit
    from public.quizzes q
    where q.id = p_quiz_id and q.status = 'published';
    if not found then
        raise exception 'Published quiz not found';
    end if;

    select count(*)::integer into v_available_questions
    from public.questions qs where qs.quiz_id = p_quiz_id;
    if p_question_count > v_available_questions then
        raise exception 'Requested question count exceeds available questions';
    end if;

    if p_mode = 'single' then
        if p_max_players is not null and p_max_players <> 1 then
            raise exception 'Single Play supports exactly one player';
        end if;
        v_max_players := 1;
        v_status := 'active';
        v_started_at := now();
        v_room_code := null;
    elsif p_mode = 'challenge' then
        if p_max_players is not null and p_max_players <> 2 then
            raise exception 'Challenge supports exactly two players';
        end if;
        v_max_players := 2;
        v_status := 'waiting';
        v_started_at := null;
        v_room_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 12));
    else
        if p_max_players is null or p_max_players < 2 or p_max_players > 100 then
            raise exception 'Custom room player limit must be between 2 and 100';
        end if;
        v_max_players := p_max_players;
        v_status := 'waiting';
        v_started_at := null;
        v_room_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 12));
    end if;

    if v_started_at is not null and v_quiz_time_limit is not null then
        v_ends_at := v_started_at + make_interval(secs => v_quiz_time_limit);
    else
        v_ends_at := null;
    end if;

    insert into public.matches (
        quiz_id, mode, status, host_user_id, room_code, max_players,
        question_count, started_at, ends_at
    ) values (
        p_quiz_id, p_mode, v_status, v_user_id, v_room_code, v_max_players,
        p_question_count, v_started_at, v_ends_at
    ) returning id into v_match_id;

    insert into public.match_players (match_id, user_id, seat, player_status)
    values (v_match_id, v_user_id, 1, case when v_status = 'active' then 'ready' else 'joined' end);

    -- Choose a random question subset and persist its order for this match.
    insert into public.match_questions (match_id, quiz_id, question_id, position)
    select v_match_id, p_quiz_id, chosen.id,
           (row_number() over (order by random()) - 1)::integer
    from (
        select qs.id
        from public.questions qs
        where qs.quiz_id = p_quiz_id
        order by random()
        limit p_question_count
    ) as chosen;

    return query select v_match_id, v_room_code;
end;
$$;

create or replace function public.join_match(p_room_code text)
returns table (match_id uuid, mode text, status text, player_count integer)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
    v_user_status text;
    v_is_banned boolean;
    v_match public.matches%rowtype;
    v_player_count integer;
    v_seat smallint;
    v_time_limit integer;
begin
    if v_user_id is null then
        raise exception 'Authentication required' using errcode = '28000';
    end if;
    if p_room_code is null or char_length(trim(p_room_code)) <> 12 then
        raise exception 'Invalid room code';
    end if;

    select up.status, up.is_banned into v_user_status, v_is_banned
    from public.user_profiles up where up.id = v_user_id;
    if not found or v_is_banned or v_user_status <> 'active' then
        raise exception 'Account is not permitted to play';
    end if;

    -- Serialize joins on the room row so its capacity cannot be exceeded.
    select m.* into v_match
    from public.matches m
    where m.room_code = upper(trim(p_room_code))
      and m.status = 'waiting'
      and m.mode in ('custom', 'challenge')
    for update;
    if not found then
        raise exception 'Room not found or no longer accepting players';
    end if;

    if exists (
        select 1 from public.match_players mp
        where mp.match_id = v_match.id and mp.user_id = v_user_id
    ) then
        raise exception 'Already joined this room';
    end if;

    select count(*)::integer into v_player_count
    from public.match_players mp where mp.match_id = v_match.id;
    if v_player_count >= v_match.max_players then
        raise exception 'Room is full';
    end if;

    select gs::smallint into v_seat
    from generate_series(1, v_match.max_players) as gs
    where not exists (
        select 1 from public.match_players mp
        where mp.match_id = v_match.id and mp.seat = gs
    )
    order by gs
    limit 1;

    insert into public.match_players (match_id, user_id, seat, player_status)
    values (v_match.id, v_user_id, v_seat, 'joined');

    v_player_count := v_player_count + 1;

    -- A 1v1 Challenge starts automatically once its second player joins.
    if v_match.mode = 'challenge' and v_player_count = 2 then
        select q.time_limit_seconds into v_time_limit
        from public.quizzes q where q.id = v_match.quiz_id;

        update public.matches m
        set status = 'active',
            started_at = now(),
            ends_at = case
                when v_time_limit is null then null
                else now() + make_interval(secs => v_time_limit)
            end
        where m.id = v_match.id;

        update public.match_players mp
        set player_status = 'ready'
        where mp.match_id = v_match.id;

        v_match.status := 'active';
    end if;

    return query select v_match.id, v_match.mode, v_match.status, v_player_count;
end;
$$;

-- Host-only start for Custom rooms. A Custom match never changes any score.
create or replace function public.start_custom_match(p_match_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
    v_match public.matches%rowtype;
    v_player_count integer;
    v_time_limit integer;
begin
    if v_user_id is null then
        raise exception 'Authentication required' using errcode = '28000';
    end if;

    select m.* into v_match
    from public.matches m
    where m.id = p_match_id
    for update;
    if not found then
        raise exception 'Match not found';
    end if;
    if v_match.mode <> 'custom' or v_match.status <> 'waiting' then
        raise exception 'Only a waiting Custom room can be started';
    end if;
    if v_match.host_user_id <> v_user_id then
        raise exception 'Only the room host can start this match';
    end if;

    select count(*)::integer into v_player_count
    from public.match_players mp where mp.match_id = p_match_id;
    if v_player_count < 2 then
        raise exception 'At least two players are required';
    end if;

    select q.time_limit_seconds into v_time_limit
    from public.quizzes q where q.id = v_match.quiz_id;

    update public.matches m
    set status = 'active',
        started_at = now(),
        ends_at = case
            when v_time_limit is null then null
            else now() + make_interval(secs => v_time_limit)
        end
    where m.id = p_match_id;

    update public.match_players mp
    set player_status = 'ready'
    where mp.match_id = p_match_id;
end;
$$;

-- Submit and validate one answer for the currently active question.
-- The answer key is read only inside this trusted function. Single Play awards
-- Gold through the idempotent score transaction function; other modes do not.
create or replace function public.submit_answer(
    p_match_id uuid,
    p_question_id uuid,
    p_selected_option_id uuid,
    p_response_time_ms integer default null
)
returns table (
    answer_id uuid,
    is_correct boolean,
    match_status text,
    current_question_position integer,
    gold_score bigint
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := (select auth.uid());
    v_match public.matches%rowtype;
    v_current_question_id uuid;
    v_correct_option_id uuid;
    v_position integer;
    v_answer_id uuid;
    v_is_correct boolean;
    v_next_position integer;
    v_player_count integer;
    v_answered_count integer;
    v_gold_score bigint;
begin
    if v_user_id is null then
        raise exception 'Authentication required' using errcode = '28000';
    end if;
    if p_match_id is null or p_question_id is null or p_selected_option_id is null then
        raise exception 'Match, question and selected option are required';
    end if;
    if p_response_time_ms is not null and p_response_time_ms < 0 then
        raise exception 'Response time cannot be negative';
    end if;

    select m.* into v_match
    from public.matches m where m.id = p_match_id
    for update;
    if not found or v_match.status <> 'active' then
        raise exception 'Match is not active';
    end if;
    if v_match.ends_at is not null and now() > v_match.ends_at then
        raise exception 'Match deadline has passed';
    end if;
    if not exists (
        select 1 from public.match_players mp
        where mp.match_id = p_match_id
          and mp.user_id = v_user_id
          and mp.player_status in ('joined', 'ready', 'disconnected')
    ) then
        raise exception 'You are not an active player in this match';
    end if;

    select mq.question_id, mq.position into v_current_question_id, v_position
    from public.match_questions mq
    where mq.match_id = p_match_id
      and mq.position = v_match.current_question_position;
    if not found or v_current_question_id <> p_question_id then
        raise exception 'This is not the current question';
    end if;

    if not exists (
        select 1 from public.question_options qo
        where qo.id = p_selected_option_id and qo.question_id = p_question_id
    ) then
        raise exception 'Selected option does not belong to this question';
    end if;
    if exists (
        select 1 from public.answers a
        where a.match_id = p_match_id
          and a.user_id = v_user_id
          and a.question_id = p_question_id
    ) then
        raise exception 'An answer has already been submitted for this question';
    end if;

    select ak.correct_option_id into v_correct_option_id
    from public.question_answer_keys ak
    where ak.question_id = p_question_id;
    if not found then
        raise exception 'Question answer key is missing';
    end if;
    v_is_correct := (p_selected_option_id = v_correct_option_id);

    insert into public.answers (
        match_id, user_id, question_id, selected_option_id,
        is_correct, response_time_ms
    ) values (
        p_match_id, v_user_id, p_question_id, p_selected_option_id,
        v_is_correct, p_response_time_ms
    ) returning id into v_answer_id;

    if v_match.mode = 'single' then
        select result.new_gold_score into v_gold_score
        from public.apply_single_play_gold(v_user_id, v_answer_id, v_is_correct) as result;
    end if;

    v_next_position := v_position + 1;
    if v_match.mode = 'single' then
        if v_next_position >= v_match.question_count then
            update public.matches m
            set current_question_position = v_match.question_count,
                status = 'completed',
                completed_at = now()
            where m.id = p_match_id;
        else
            update public.matches m
            set current_question_position = v_next_position
            where m.id = p_match_id;
        end if;
    else
        select count(*)::integer into v_player_count
        from public.match_players mp
        where mp.match_id = p_match_id
          and mp.player_status <> 'forfeited';

        select count(*)::integer into v_answered_count
        from public.answers a
        where a.match_id = p_match_id and a.question_id = p_question_id;

        if v_answered_count >= v_player_count then
            if v_next_position >= v_match.question_count then
                if v_match.mode = 'custom' then
                    update public.matches m
                    set current_question_position = v_match.question_count,
                        status = 'completed',
                        completed_at = now()
                    where m.id = p_match_id;
                else
                    -- Challenge remains active at the end position until the
                    -- trusted finish/settlement function records its result.
                    update public.matches m
                    set current_question_position = v_match.question_count
                    where m.id = p_match_id;
                end if;
            else
                update public.matches m
                set current_question_position = v_next_position
                where m.id = p_match_id;
            end if;
        end if;
    end if;

    return query
    select v_answer_id,
           v_is_correct,
           m.status,
           m.current_question_position,
           coalesce(v_gold_score, up.gold_score)
    from public.matches m
    join public.user_profiles up on up.id = v_user_id
    where m.id = p_match_id;
end;
$$;

revoke all on function public.create_match(uuid, text, integer, smallint) from public, anon;
revoke all on function public.join_match(text) from public, anon;
revoke all on function public.start_custom_match(uuid) from public, anon;
revoke all on function public.submit_answer(uuid, uuid, uuid, integer) from public, anon;
grant execute on function public.create_match(uuid, text, integer, smallint) to authenticated;
grant execute on function public.join_match(text) to authenticated;
grant execute on function public.start_custom_match(uuid) to authenticated;
grant execute on function public.submit_answer(uuid, uuid, uuid, integer) to authenticated;

commit;
