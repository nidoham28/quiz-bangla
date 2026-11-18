import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const ADMIN_EMAIL = "nidohamondol@gmail.com";
const MAX_REQUEST_BYTES = 1_000_000;
const MAX_URL_LENGTH = 2048;

function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function isHttpsUrl(value) {
  if (typeof value !== "string" || value.length > MAX_URL_LENGTH) {
    return false;
  }
  try {
    return new URL(value).protocol === "https:";
  } catch {
    return false;
  }
}

async function readBodyWithLimit(request, maxBytes) {
  if (!request.body) {
    return "";
  }
  const reader = request.body.getReader();
  const chunks = [];
  let totalBytes = 0;

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    totalBytes += value.byteLength;
    if (totalBytes > maxBytes) {
      try {
        await reader.cancel();
      } catch {
        // Ignore stream cancel errors.
      }
      throw new Error("PAYLOAD_TOO_LARGE");
    }
    chunks.push(value);
  }

  const merged = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    merged.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return new TextDecoder().decode(merged);
}

function validateQuestion(question) {
  if (!question || typeof question !== "object" || Array.isArray(question)) {
    return "Each question must be an object.";
  }
  if (
      question.prompt !== undefined &&
      question.prompt !== null &&
      typeof question.prompt !== "string"
  ) {
    return "Question text must be a string.";
  }
  if (
      question.image_url !== undefined &&
      question.image_url !== null &&
      (typeof question.image_url !== "string" ||
          (question.image_url.trim() && !isHttpsUrl(question.image_url.trim())))
  ) {
    return "Question images must use valid HTTPS URLs (max 2048 chars).";
  }

  const hasPrompt = typeof question.prompt === "string" && question.prompt.trim().length > 0;
  const hasQuestionImage =
      typeof question.image_url === "string" && isHttpsUrl(question.image_url.trim());
  if (!hasPrompt && !hasQuestionImage) {
    return "Each question needs text or an HTTPS image URL.";
  }
  if (typeof question.prompt === "string" && question.prompt.trim().length > 2000) {
    return "Question text must not exceed 2000 characters.";
  }
  if (!Array.isArray(question.options) || question.options.length !== 4) {
    return "Each question must have exactly four answer options.";
  }
  if (
      !Number.isInteger(question.correct_option) ||
      question.correct_option < 0 ||
      question.correct_option > 3
  ) {
    return "Each question needs a correct option from 0 to 3.";
  }

  for (const option of question.options) {
    if (!option || typeof option !== "object" || Array.isArray(option)) {
      return "Each answer option must be an object.";
    }
    if (
        option.text !== undefined &&
        option.text !== null &&
        typeof option.text !== "string"
    ) {
      return "Answer text must be a string.";
    }
    if (
        option.image_url !== undefined &&
        option.image_url !== null &&
        (typeof option.image_url !== "string" ||
            (option.image_url.trim() && !isHttpsUrl(option.image_url.trim())))
    ) {
      return "Answer images must use valid HTTPS URLs (max 2048 chars).";
    }
    const hasText = typeof option.text === "string" && option.text.trim().length > 0;
    const hasImage = typeof option.image_url === "string" && isHttpsUrl(option.image_url.trim());
    if (!hasText && !hasImage) {
      return "Each answer option needs text or an HTTPS image URL.";
    }
    if (typeof option.text === "string" && option.text.trim().length > 500) {
      return "Answer text must not exceed 500 characters.";
    }
  }
  return null;
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  const requestId = crypto.randomUUID();
  if (request.method !== "POST") {
    console.warn(JSON.stringify({
      event: "save_quiz_draft.rejected",
      request_id: requestId,
      reason: "method_not_allowed",
      method: request.method,
    }));
    return jsonResponse(405, { error: "Method not allowed." });
  }
  console.info(JSON.stringify({
    event: "save_quiz_draft.request_received",
    request_id: requestId,
  }));

  const authorization = request.headers.get("Authorization");
  const bearerMatch = authorization?.trim().match(/^Bearer\s+(\S+)$/i);
  if (!bearerMatch) {
    return jsonResponse(401, { error: "Sign in before saving a quiz." });
  }

  const projectUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!projectUrl || !anonKey || !serviceRoleKey) {
    console.error("Supabase URL, anon key, or service-role key is not configured.");
    return jsonResponse(500, { error: "Quiz saving is not configured on the server." });
  }

  const authClient = createClient(projectUrl, anonKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: userData, error: authError } = await authClient.auth.getUser(bearerMatch[1]);
  if (authError || !userData?.user) {
    return jsonResponse(401, { error: "Your session is invalid or expired. Sign in again." });
  }

  const adminClient = createClient(projectUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  // Authorize via public.admin_roles first, with verified ADMIN_EMAIL as fallback.
  const { data: adminRole, error: roleError } = await adminClient
      .from("admin_roles")
      .select("role")
      .eq("user_id", userData.user.id)
      .in("role", ["quiz_admin", "super_admin"])
      .maybeSingle();

  if (roleError) {
    console.error("Failed to verify admin_roles:", roleError);
    return jsonResponse(500, { error: "Unable to verify editor permissions right now." });
  }

  const isVerifiedAdminEmail =
      Boolean(userData.user.email_confirmed_at) &&
      (userData.user.email ?? "").trim().toLowerCase() === ADMIN_EMAIL;

  if (!adminRole && !isVerifiedAdminEmail) {
    return jsonResponse(403, { error: "Only an authorized quiz editor can save questions." });
  }

  const contentLengthHeader = request.headers.get("Content-Length");
  if (contentLengthHeader !== null) {
    const contentLength = Number(contentLengthHeader);
    if (Number.isFinite(contentLength) && contentLength > MAX_REQUEST_BYTES) {
      return jsonResponse(413, { error: "The quiz submission is too large." });
    }
  }

  let payload;
  try {
    const rawBody = await readBodyWithLimit(request, MAX_REQUEST_BYTES);
    payload = JSON.parse(rawBody);
  } catch (err) {
    if (err instanceof Error && err.message === "PAYLOAD_TOO_LARGE") {
      return jsonResponse(413, { error: "The quiz submission is too large." });
    }
    return jsonResponse(400, { error: "Request body must be valid JSON." });
  }

  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    return jsonResponse(400, { error: "Request body must be a JSON object." });
  }
  if (
      typeof payload.title !== "string" ||
      payload.title.trim().length < 1 ||
      payload.title.trim().length > 120
  ) {
    return jsonResponse(400, { error: "Quiz title must contain 1 to 120 characters." });
  }
  if (
      payload.description !== null &&
      payload.description !== undefined &&
      (typeof payload.description !== "string" || payload.description.trim().length > 1000)
  ) {
    return jsonResponse(400, { error: "Quiz description must not exceed 1000 characters." });
  }

  const cleanDescription =
      typeof payload.description === "string" && payload.description.trim().length > 0
          ? payload.description.trim()
          : null;

  const difficulty =
      typeof payload.difficulty === "string" ? payload.difficulty.trim().toLowerCase() : "";
  if (!["easy", "medium", "hard"].includes(difficulty)) {
    return jsonResponse(400, { error: "Invalid quiz difficulty." });
  }
  if (!Array.isArray(payload.questions) || payload.questions.length < 1 || payload.questions.length > 100) {
    return jsonResponse(400, { error: "Between 1 and 100 questions are required." });
  }
  for (const question of payload.questions) {
    const validationError = validateQuestion(question);
    if (validationError) {
      return jsonResponse(400, { error: validationError });
    }
  }

  const { data, error } = await adminClient.rpc("save_quiz_draft", {
    p_title: payload.title.trim(),
    p_description: cleanDescription,
    p_difficulty: difficulty,
    p_created_by: userData.user.id,
    p_questions: payload.questions,
  });

  if (error) {
    if (error.code === "22023") {
      return jsonResponse(400, { error: error.message });
    }
    console.error(JSON.stringify({
      event: "save_quiz_draft.rpc_failed",
      request_id: requestId,
      code: error.code,
      message: error.message,
    }));
    return jsonResponse(500, { error: "Unable to save this quiz right now." });
  }

  if (!data || typeof data !== "object") {
    console.error(JSON.stringify({
      event: "save_quiz_draft.unexpected_response",
      request_id: requestId,
      response_type: typeof data,
    }));
    return jsonResponse(500, { error: "Unexpected response from quiz saver." });
  }

  console.info(JSON.stringify({
    event: "save_quiz_draft.completed",
    request_id: requestId,
    question_count: payload.questions.length,
    inserted: data.inserted,
    skipped: data.skipped,
  }));
  return jsonResponse(200, {
    quiz_id: data.quiz_id,
    inserted: data.inserted,
    skipped: data.skipped,
  });
});