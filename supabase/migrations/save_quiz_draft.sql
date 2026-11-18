-- Save draft quizzes atomically. Only the trusted Edge Function's service-role
-- client may execute this function.
begin;

create index if not exists quizzes_draft_title_idx
    on public.quizzes (lower(trim(title)), created_at desc)
    where status = 'draft';

create or replace function public.save_quiz_draft(
    p_title text,
    p_description text,
    p_difficulty text,
    p_created_by uuid,
    p_questions jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
v_quiz_id uuid;
    v_existed boolean := false;
    v_question jsonb;
    v_option jsonb;
    v_prompt text;
    v_image_url text;
    v_option_text text;
    v_option_image_url text;
    v_question_id uuid;
    v_option_id uuid;
    v_option_ids uuid[];
    v_correct_position integer;
    v_question_position integer;
    v_inserted_count integer := 0;
    v_skipped_count integer := 0;
    v_index integer;
begin
    if p_title is null or char_length(trim(p_title)) not between 1 and 120 then
        raise exception 'Quiz title must contain 1 to 120 characters'
            using errcode = '22023';
end if;
    if p_description is not null and char_length(trim(p_description)) > 1000 then
        raise exception 'Quiz description must not exceed 1000 characters'
            using errcode = '22023';
end if;
    if p_difficulty is null or p_difficulty not in ('easy', 'medium', 'hard') then
        raise exception 'Invalid quiz difficulty'
            using errcode = '22023';
end if;
    if p_created_by is null then
        raise exception 'Authenticated user is required'
            using errcode = '22023';
end if;
    if p_questions is null or jsonb_typeof(p_questions) is distinct from 'array' then
        raise exception 'Questions must be an array'
            using errcode = '22023';
end if;
    if jsonb_array_length(p_questions) not between 1 and 100 then
        raise exception 'Between 1 and 100 questions are required'
            using errcode = '22023';
end if;

    -- Serialize saves for a title so concurrent requests reuse one draft quiz.
    perform pg_advisory_xact_lock(
        hashtextextended('save_quiz_draft:' || lower(trim(p_title)), 0)
    );

select q.id
into v_quiz_id
from public.quizzes q
where lower(trim(q.title)) = lower(trim(p_title))
  and q.status = 'draft'
order by q.created_at desc
    limit 1
    for update;

if v_quiz_id is null then
        insert into public.quizzes (title, description, difficulty, language, status, created_by)
        values (trim(p_title), nullif(trim(p_description), ''), p_difficulty, 'eng', 'draft', p_created_by)
        returning id into v_quiz_id;
else
        v_existed := true;
end if;

select coalesce(max(q.position) + 1, 0)
into v_question_position
from public.questions q
where q.quiz_id = v_quiz_id;

for v_question in select value from jsonb_array_elements(p_questions)
                                        loop
    if jsonb_typeof(v_question) is distinct from 'object' then
                      raise exception 'Each question must be an object'
                      using errcode = '22023';
end if;

        v_prompt := nullif(trim(v_question ->> 'prompt'), '');
        v_image_url := nullif(trim(v_question ->> 'image_url'), '');
        if (v_prompt is null and v_image_url is null)
            or (v_prompt is not null and char_length(v_prompt) > 2000) then
            raise exception 'Question must contain text or an image and stay within the text limit'
                using errcode = '22023';
end if;
        if jsonb_typeof(v_question -> 'options') is distinct from 'array' then
            raise exception 'Each question options value must be an array'
                using errcode = '22023';
end if;
        if jsonb_array_length(v_question -> 'options') <> 4 then
            raise exception 'Each question must have exactly four options'
                using errcode = '22023';
end if;
        if coalesce(v_question ->> 'correct_option', '') !~ '\A[0-3]\z' then
            raise exception 'Each question must specify a correct option from 0 to 3'
                using errcode = '22023';
end if;
        v_correct_position := (v_question ->> 'correct_option')::integer;

        -- Compare prompt case-insensitively, but keep image_url case-sensitive.
        if exists (
            select 1
            from public.questions q
            where q.quiz_id = v_quiz_id
              and lower(trim(coalesce(q.prompt, ''))) = lower(coalesce(v_prompt, ''))
              and trim(coalesce(q.image_url, '')) = coalesce(v_image_url, '')
        ) then
            v_skipped_count := v_skipped_count + 1;
continue;
end if;

insert into public.questions (quiz_id, position, prompt, image_url)
values (v_quiz_id, v_question_position, v_prompt, v_image_url)
    returning id into v_question_id;

v_option_ids := array[]::uuid[];
for v_index in 0..3
        loop
            v_option := v_question -> 'options' -> v_index;
            if jsonb_typeof(v_option) is distinct from 'object' then
                raise exception 'Each answer option must be an object'
                    using errcode = '22023';
end if;

            v_option_text := nullif(trim(v_option ->> 'text'), '');
            v_option_image_url := nullif(trim(v_option ->> 'image_url'), '');
            if (v_option_text is null and v_option_image_url is null)
                or (v_option_text is not null and char_length(v_option_text) > 500) then
                raise exception 'Each answer option must contain text or an image and stay within the text limit'
                    using errcode = '22023';
end if;

insert into public.question_options (question_id, position, option_text, image_url)
values (v_question_id, v_index, v_option_text, v_option_image_url)
    returning id into v_option_id;
v_option_ids := array_append(v_option_ids, v_option_id);
end loop;

insert into public.question_answer_keys (question_id, correct_option_id)
values (v_question_id, v_option_ids[v_correct_position + 1]);

v_inserted_count := v_inserted_count + 1;
        v_question_position := v_question_position + 1;
end loop;

    if v_existed and v_inserted_count > 0 then
update public.quizzes
set updated_at = pg_catalog.now()
where id = v_quiz_id;
end if;

return jsonb_build_object(
        'quiz_id', v_quiz_id,
        'inserted', v_inserted_count,
        'skipped', v_skipped_count
       );
end;
$$;

revoke all on function public.save_quiz_draft(text, text, text, uuid, jsonb)
    from public, anon, authenticated;
grant execute on function public.save_quiz_draft(text, text, text, uuid, jsonb)
    to service_role;

commit;