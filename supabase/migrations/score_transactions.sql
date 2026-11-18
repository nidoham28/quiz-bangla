-- Server-authoritative, idempotent score calculations.
-- Run after user_profile.sql in Supabase SQL Editor / migration workflow.
-- These SECURITY DEFINER functions must only be called by a trusted backend
-- (for example a Supabase Edge Function using the service_role key).
-- Never put the service_role key in the client app.

begin;

create table if not exists public.score_transactions (
                                                         id bigint generated always as identity primary key,
                                                         user_id uuid not null references public.user_profiles (id) on delete cascade,
    score_type text not null check (score_type in ('gold', 'diamond')),
    amount bigint not null,
    reason text not null check (reason in (
                                'single_correct',
                                'single_wrong',
                                'challenge_win',
                                'challenge_loss',
                                'challenge_draw'
                                          )),
    reference_id uuid not null,
    created_at timestamptz not null default now(),
    -- One award/debit per user, score type and answer/match ID.
    constraint score_transactions_once_per_reference unique (user_id, score_type, reference_id),
    constraint score_transactions_type_reason_match check (
    (score_type = 'gold' and reason in ('single_correct', 'single_wrong'))
    or (score_type = 'diamond' and reason in ('challenge_win', 'challenge_loss', 'challenge_draw'))
    )
    );

-- Ensure consistency constraint exists cleanly if table already existed.
alter table public.score_transactions
drop constraint if exists score_transactions_type_reason_match;
alter table public.score_transactions
    add constraint score_transactions_type_reason_match check (
        (score_type = 'gold' and reason in ('single_correct', 'single_wrong'))
            or (score_type = 'diamond' and reason in ('challenge_win', 'challenge_loss', 'challenge_draw'))
        );

comment on table public.score_transactions is
    'Append-only audit log for Gold and Diamond changes. Only trusted server-side calculation functions may write.';

alter table public.score_transactions enable row level security;
revoke all on table public.score_transactions from public, anon, authenticated;
grant select on table public.score_transactions to service_role;

create index if not exists score_transactions_reference_idx
    on public.score_transactions (reference_id);
create index if not exists score_transactions_user_created_idx
    on public.score_transactions (user_id, created_at desc);

-- Gold: Single Play correct +2; wrong -1, floored at zero.
-- p_is_correct must be decided by trusted server-side answer validation,
-- not by the client. p_answer_id must uniquely identify this answer attempt.
create or replace function public.apply_single_play_gold(
    p_user_id uuid,
    p_answer_id uuid,
    p_is_correct boolean
)
returns table (applied boolean, new_gold_score bigint, applied_delta bigint)
language plpgsql
security definer
set search_path = ''
as $$
declare
v_balance bigint;
    v_delta bigint;
begin
    if p_user_id is null or p_answer_id is null or p_is_correct is null then
        raise exception 'user_id, answer_id and correctness are required';
end if;

select up.gold_score into v_balance
from public.user_profiles as up
where up.id = p_user_id
    for update;

if not found then
        raise exception 'Profile not found';
end if;

    if exists (
        select 1 from public.score_transactions as st
        where st.user_id = p_user_id
          and st.score_type = 'gold'
          and st.reference_id = p_answer_id
    ) then
        return query select false, v_balance, 0::bigint;
return;
end if;

    if p_is_correct then
        v_delta := 2;
else
        v_delta := -least(1::bigint, v_balance);
end if;

update public.user_profiles as up
set gold_score = v_balance + v_delta
where up.id = p_user_id
    returning up.gold_score into v_balance;

insert into public.score_transactions (user_id, score_type, amount, reason, reference_id)
values (
           p_user_id,
           'gold',
           v_delta,
           case when p_is_correct then 'single_correct' else 'single_wrong' end,
           p_answer_id
       );

return query select true, v_balance, v_delta;
end;
$$;

-- Challenge result codes:
--   player_one_win: player one +5, player two -2 (not below zero)
--   player_two_win: player two +5, player one -2 (not below zero)
--   draw: both players +3
-- The backend must validate the match and result before calling this function.
create or replace function public.settle_challenge_diamond(
    p_match_id uuid,
    p_player_one_id uuid,
    p_player_two_id uuid,
    p_result text
)
returns table (user_id uuid, applied boolean, new_diamond_score bigint, applied_delta bigint)
language plpgsql
security definer
set search_path = ''
as $$
declare
v_one_balance bigint;
    v_two_balance bigint;
    v_one_delta bigint;
    v_two_delta bigint;
    v_existing_count integer;
begin
    if p_match_id is null
       or p_player_one_id is null
       or p_player_two_id is null
       or p_player_one_id = p_player_two_id
       or p_result is null
       or p_result not in ('player_one_win', 'player_two_win', 'draw') then
        raise exception 'Invalid match settlement input';
end if;

    -- Lock both profiles in stable ID order to reduce deadlock risk.
    perform up.id
    from public.user_profiles as up
    where up.id in (p_player_one_id, p_player_two_id)
    order by up.id
    for update;

select up.diamond_score into v_one_balance
from public.user_profiles as up where up.id = p_player_one_id;
select up.diamond_score into v_two_balance
from public.user_profiles as up where up.id = p_player_two_id;

if v_one_balance is null or v_two_balance is null then
        raise exception 'One or both player profiles were not found';
end if;

select count(*)::integer into v_existing_count
from public.score_transactions as st
where st.score_type = 'diamond'
  and st.reference_id = p_match_id
  and st.user_id in (p_player_one_id, p_player_two_id);

if v_existing_count > 0 then
        if v_existing_count <> 2 then
            raise exception 'Partial challenge settlement detected; manual review required';
end if;
return query
select p_player_one_id, false, v_one_balance, 0::bigint
union all
select p_player_two_id, false, v_two_balance, 0::bigint;
return;
end if;

    if p_result = 'draw' then
        v_one_delta := 3;
        v_two_delta := 3;
    elsif p_result = 'player_one_win' then
        v_one_delta := 5;
        v_two_delta := -least(2::bigint, v_two_balance);
else
        v_one_delta := -least(2::bigint, v_one_balance);
        v_two_delta := 5;
end if;

update public.user_profiles as up
set diamond_score = case
                        when up.id = p_player_one_id then v_one_balance + v_one_delta
                        when up.id = p_player_two_id then v_two_balance + v_two_delta
    end
where up.id in (p_player_one_id, p_player_two_id);

insert into public.score_transactions (user_id, score_type, amount, reason, reference_id)
values
    (p_player_one_id, 'diamond', v_one_delta,
     case p_result
         when 'draw' then 'challenge_draw'
         when 'player_one_win' then 'challenge_win'
         else 'challenge_loss'
         end,
     p_match_id),
    (p_player_two_id, 'diamond', v_two_delta,
     case p_result
         when 'draw' then 'challenge_draw'
         when 'player_two_win' then 'challenge_win'
         else 'challenge_loss'
         end,
     p_match_id);

select up.diamond_score into v_one_balance
from public.user_profiles as up where up.id = p_player_one_id;
select up.diamond_score into v_two_balance
from public.user_profiles as up where up.id = p_player_two_id;

return query
select p_player_one_id, true, v_one_balance, v_one_delta
union all
select p_player_two_id, true, v_two_balance, v_two_delta;
end;
$$;

-- Do not allow direct table writes or function calls from app-user roles.
revoke all on function public.apply_single_play_gold(uuid, uuid, boolean) from public, anon, authenticated;
revoke all on function public.settle_challenge_diamond(uuid, uuid, uuid, text) from public, anon, authenticated;
grant execute on function public.apply_single_play_gold(uuid, uuid, boolean) to service_role;
grant execute on function public.settle_challenge_diamond(uuid, uuid, uuid, text) to service_role;

commit;