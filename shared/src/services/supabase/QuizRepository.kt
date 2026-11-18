package services.supabase

import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal data class QuizQuestionDraft(
    val prompt: String?,
    val imageUrl: String?,
    val options: List<QuizOptionDraft>,
    val correctOption: Int,
)

internal data class QuizOptionDraft(
    val text: String?,
    val imageUrl: String?,
)

internal data class QuizDraftSaveResult(
    val quizId: String,
    val inserted: Int,
    val skipped: Int,
)

internal object QuizRepository {
    private const val SAVE_DRAFT_FUNCTION = "save-quiz-draft"
    private const val MAX_TITLE_LENGTH = 120
    private const val MAX_DESCRIPTION_LENGTH = 1000
    private const val MAX_QUESTIONS_PER_REQUEST = 100
    private const val MAX_PROMPT_LENGTH = 2000
    private const val MAX_OPTION_LENGTH = 500
    private const val MAX_URL_LENGTH = 2048
    private val ALLOWED_DIFFICULTIES = setOf("easy", "medium", "hard")

    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun saveDraft(
        title: String,
        description: String?,
        difficulty: String,
        questions: List<QuizQuestionDraft>,
    ): QuizDraftSaveResult {
        val normalizedTitle = title.trim()
        require(normalizedTitle.length in 1..MAX_TITLE_LENGTH) {
            "Quiz title must contain 1 to $MAX_TITLE_LENGTH characters."
        }

        val normalizedDescription = description?.trim()?.takeIf { it.isNotEmpty() }
        require(normalizedDescription == null || normalizedDescription.length <= MAX_DESCRIPTION_LENGTH) {
            "Quiz description must not exceed $MAX_DESCRIPTION_LENGTH characters."
        }

        val normalizedDifficulty = difficulty.trim().lowercase()
        require(normalizedDifficulty in ALLOWED_DIFFICULTIES) {
            "Invalid quiz difficulty."
        }

        require(questions.size in 1..MAX_QUESTIONS_PER_REQUEST) {
            "Between 1 and $MAX_QUESTIONS_PER_REQUEST questions are required."
        }

        val request = buildJsonObject {
            put("title", normalizedTitle)
            putNullable("description", normalizedDescription)
            put("difficulty", normalizedDifficulty)
            putJsonArray("questions") {
                questions.forEachIndexed { questionIndex, question ->
                    val prompt = question.prompt?.trim()?.takeIf { it.isNotEmpty() }
                    val imageUrl = question.imageUrl?.trim()?.takeIf { it.isNotEmpty() }

                    require(prompt != null || imageUrl != null) {
                        "Question ${questionIndex + 1} needs text or an HTTPS image URL."
                    }
                    require(prompt == null || prompt.length <= MAX_PROMPT_LENGTH) {
                        "Question ${questionIndex + 1} text must not exceed $MAX_PROMPT_LENGTH characters."
                    }
                    require(imageUrl == null || isValidHttpsUrl(imageUrl)) {
                        "Question ${questionIndex + 1} image must be a valid HTTPS URL."
                    }
                    require(question.options.size == 4) {
                        "Question ${questionIndex + 1} must have exactly four answer options."
                    }
                    require(question.correctOption in 0..3) {
                        "Question ${questionIndex + 1} needs a correct option from 0 to 3."
                    }

                    add(
                        buildJsonObject {
                            putNullable("prompt", prompt)
                            putNullable("image_url", imageUrl)
                            put("correct_option", question.correctOption)
                            putJsonArray("options") {
                                question.options.forEachIndexed { optionIndex, option ->
                                    val text = option.text?.trim()?.takeIf { it.isNotEmpty() }
                                    val optionImageUrl = option.imageUrl?.trim()?.takeIf { it.isNotEmpty() }

                                    require(text != null || optionImageUrl != null) {
                                        "Question ${questionIndex + 1}, option ${optionIndex + 1} needs text or an HTTPS image URL."
                                    }
                                    require(text == null || text.length <= MAX_OPTION_LENGTH) {
                                        "Question ${questionIndex + 1}, option ${optionIndex + 1} text must not exceed $MAX_OPTION_LENGTH characters."
                                    }
                                    require(optionImageUrl == null || isValidHttpsUrl(optionImageUrl)) {
                                        "Question ${questionIndex + 1}, option ${optionIndex + 1} image must be a valid HTTPS URL."
                                    }

                                    add(
                                        buildJsonObject {
                                            putNullable("text", text)
                                            putNullable("image_url", optionImageUrl)
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }

        val response = try {
            SupabaseService.functions.invoke(
                function = SAVE_DRAFT_FUNCTION,
                body = request,
                headers = Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                },
            )
        } catch (e: RestException) {
            val serverMessage = extractErrorMessage(e.error)
                ?: "Unable to save quiz draft right now."
            throw IllegalStateException(serverMessage, e)
        }

        val rawResponse = response.bodyAsText()
        val resultObject = json.parseToJsonElement(rawResponse).jsonObject
        return QuizDraftSaveResult(
            quizId = resultObject["quiz_id"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalStateException("Unexpected response from quiz saver."),
            inserted = resultObject["inserted"]?.jsonPrimitive?.intOrNull ?: 0,
            skipped = resultObject["skipped"]?.jsonPrimitive?.intOrNull ?: 0,
        )
    }

    private fun isValidHttpsUrl(url: String): Boolean =
        url.length in 9..MAX_URL_LENGTH &&
                url.startsWith("https://", ignoreCase = true) &&
                url.none { it.isWhitespace() }

    private fun extractErrorMessage(rawError: String): String? =
        runCatching {
            json.parseToJsonElement(rawError)
                .jsonObject["error"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()
}

private fun JsonObjectBuilder.putNullable(
    key: String,
    value: String?,
) {
    if (value == null) {
        put(key, JsonNull)
    } else {
        put(key, value)
    }
}