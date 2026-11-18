package features.questions.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val QUIZ_TITLE_LIMIT = 120
private const val QUIZ_DESCRIPTION_LIMIT = 1000
private const val QUESTION_PROMPT_LIMIT = 2000
private const val OPTION_TEXT_LIMIT = 500
private const val QUESTION_TEXT = "Text"
private const val QUESTION_IMAGE = "Image"
private val OPTION_LABELS = listOf("A", "B", "C", "D")
private val DIFFICULTIES = listOf("Easy", "Medium", "Hard")
private val FORMATS = listOf(QUESTION_TEXT, QUESTION_IMAGE)
private val WEB_URL_PATTERN = Regex("^https://[^\\s]+$", RegexOption.IGNORE_CASE)

private data class DraftOption(
    val text: String?,
    val imageUrl: String?,
)

private data class DraftQuestion(
    val prompt: String?,
    val imageUrl: String?,
    val options: List<DraftOption>,
    val correctOption: Int,
)

private fun isHttpsUrl(value: String) = WEB_URL_PATTERN.matches(value.trim())

/** Shows a character counter only when the user is close to the limit. */
private fun counterText(length: Int, limit: Int): String? =
    if (length >= limit * 0.8) "$length/$limit" else null

/** Returns a supporting-text slot, or null so no empty space is reserved. */
private fun supporting(text: String?): (@Composable () -> Unit)? {
    if (text == null) return null
    val slot: @Composable () -> Unit = { Text(text) }
    return slot
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddQuestionsScreen(onBack: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val questions = remember { mutableStateListOf<DraftQuestion>() }

    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var difficulty by rememberSaveable { mutableStateOf("Medium") }
    var questionFormat by rememberSaveable { mutableStateOf(QUESTION_TEXT) }
    var answerFormat by rememberSaveable { mutableStateOf(QUESTION_TEXT) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var questionImageUrl by rememberSaveable { mutableStateOf("") }
    var textOptions by rememberSaveable { mutableStateOf(List(4) { "" }) }
    var imageOptions by rememberSaveable { mutableStateOf(List(4) { "" }) }
    var correctOption by rememberSaveable { mutableStateOf(0) }
    var showNotice by rememberSaveable { mutableStateOf(true) }
    var attemptedAdd by remember { mutableStateOf(false) }
    var attemptedSave by remember { mutableStateOf(false) }

    val isTextQuestion = questionFormat == QUESTION_TEXT
    val isTextAnswer = answerFormat == QUESTION_TEXT
    val currentOptions = if (isTextAnswer) textOptions else imageOptions

    fun optionIsValid(option: String) =
        if (isTextAnswer) option.isNotBlank() else isHttpsUrl(option)

    val questionIsValid = if (isTextQuestion) prompt.isNotBlank() else isHttpsUrl(questionImageUrl)

    val titleError =
        if ((attemptedAdd || attemptedSave) && title.isBlank()) "Enter a quiz title" else null
    val promptError = when {
        !attemptedAdd || questionIsValid -> null
        isTextQuestion -> "Enter the question"
        else -> "Enter an HTTPS image URL"
    }
    val optionErrors = currentOptions.map { option ->
        when {
            !attemptedAdd || optionIsValid(option) -> null
            isTextAnswer -> "Enter an answer"
            else -> "Enter an HTTPS image URL"
        }
    }

    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    fun addQuestion() {
        attemptedAdd = true
        if (title.isBlank() || !questionIsValid || !currentOptions.all(::optionIsValid)) return

        questions += DraftQuestion(
            prompt = prompt.trim().takeIf { isTextQuestion },
            imageUrl = questionImageUrl.trim().takeIf { !isTextQuestion },
            options = currentOptions.map { option ->
                DraftOption(
                    text = option.trim().takeIf { isTextAnswer },
                    imageUrl = option.trim().takeIf { !isTextAnswer },
                )
            },
            correctOption = correctOption,
        )
        prompt = ""
        questionImageUrl = ""
        textOptions = List(4) { "" }
        imageOptions = List(4) { "" }
        correctOption = 0
        attemptedAdd = false
        showMessage("Question ${questions.size} added")
    }

    fun removeQuestion(index: Int) {
        val removed = questions.removeAt(index)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "Question removed",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                questions.add(index.coerceAtMost(questions.size), removed)
            }
        }
    }

    fun saveDraft() {
        attemptedSave = true
        when {
            title.isBlank() -> showMessage("Enter a quiz title first")
            questions.isEmpty() -> showMessage("Add at least one question")
            else -> showMessage("Can't save yet. A trusted Supabase admin service isn't set up.")
        }
    }

    fun updateOption(index: Int, value: String) {
        if (isTextAnswer) {
            if (value.length <= OPTION_TEXT_LIMIT) {
                textOptions = textOptions.toMutableList().also { it[index] = value }
            }
        } else {
            imageOptions = imageOptions.toMutableList().also { it[index] = value }
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "New quiz",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = when (questions.size) {
                                0 -> "Draft"
                                1 -> "Draft, 1 question"
                                else -> "Draft, ${questions.size} questions"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { saveDraft() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text("Save draft")
                    }
                    Button(
                        onClick = { addQuestion() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Add question")
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AnimatedVisibility(visible = showNotice) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                start = 16.dp,
                                top = 4.dp,
                                end = 4.dp,
                                bottom = 4.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(
                                text = "Drafts stay on this screen only. Saving needs a trusted Supabase admin service, which isn't set up yet.",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            IconButton(onClick = { showNotice = false }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss notice",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }

                SectionCard(title = "Quiz details") {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { if (it.length <= QUIZ_TITLE_LIMIT) title = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        label = { Text("Quiz title") },
                        placeholder = { Text("e.g. Bangladesh General Knowledge") },
                        singleLine = true,
                        isError = titleError != null,
                        supportingText = supporting(
                            titleError ?: counterText(title.length, QUIZ_TITLE_LIMIT),
                        ),
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = {
                            if (it.length <= QUIZ_DESCRIPTION_LIMIT) description = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        label = { Text("Description (optional)") },
                        minLines = 2,
                        maxLines = 4,
                        supportingText = supporting(
                            counterText(description.length, QUIZ_DESCRIPTION_LIMIT),
                        ),
                    )
                    SegmentedChoice(
                        label = "Difficulty",
                        options = DIFFICULTIES,
                        selected = difficulty,
                        onSelected = { difficulty = it },
                    )
                }

                SectionCard(
                    title = "Question ${questions.size + 1}",
                    subtitle = "Tap a letter to mark the correct answer",
                ) {
                    SegmentedChoice(
                        label = "Question format",
                        options = FORMATS,
                        selected = questionFormat,
                        onSelected = { questionFormat = it },
                    )

                    if (isTextQuestion) {
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { if (it.length <= QUESTION_PROMPT_LIMIT) prompt = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            label = { Text("Question") },
                            placeholder = { Text("Write your question") },
                            minLines = 3,
                            maxLines = 6,
                            isError = promptError != null,
                            supportingText = supporting(
                                promptError ?: counterText(prompt.length, QUESTION_PROMPT_LIMIT),
                            ),
                        )
                    } else {
                        OutlinedTextField(
                            value = questionImageUrl,
                            onValueChange = { questionImageUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            label = { Text("Question image URL") },
                            placeholder = { Text("https://example.com/question.jpg") },
                            singleLine = true,
                            isError = promptError != null,
                            supportingText = supporting(promptError ?: "Use a public HTTPS image URL"),
                        )
                    }

                    SegmentedChoice(
                        label = "Answer format",
                        options = FORMATS,
                        selected = answerFormat,
                        onSelected = { answerFormat = it },
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        currentOptions.forEachIndexed { index, option ->
                            AnswerOption(
                                label = OPTION_LABELS[index],
                                value = option,
                                isImage = !isTextAnswer,
                                isCorrect = correctOption == index,
                                error = optionErrors[index],
                                onSelect = { correctOption = index },
                                onValueChange = { updateOption(index, it) },
                            )
                        }
                    }
                }

                if (questions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Added questions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        questions.forEachIndexed { index, question ->
                            DraftQuestionItem(
                                number = index + 1,
                                question = question,
                                onRemove = { removeQuestion(index) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun SegmentedChoice(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = selected == option,
                    onClick = { onSelected(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(option)
                }
            }
        }
    }
}

@Composable
private fun AnswerOption(
    label: String,
    value: String,
    isImage: Boolean,
    isCorrect: Boolean,
    error: String?,
    onSelect: () -> Unit,
    onValueChange: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (isCorrect) colors.primaryContainer.copy(alpha = 0.45f) else colors.surface,
        label = "optionContainer",
    )
    val border by animateColorAsState(
        targetValue = if (isCorrect) colors.primary else colors.outlineVariant,
        label = "optionBorder",
    )
    val badge by animateColorAsState(
        targetValue = if (isCorrect) colors.primary else colors.surfaceVariant,
        label = "optionBadge",
    )
    val badgeContent by animateColorAsState(
        targetValue = if (isCorrect) colors.onPrimary else colors.onSurfaceVariant,
        label = "optionBadgeContent",
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = container,
        border = BorderStroke(if (isCorrect) 1.5.dp else 1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(badge)
                    .selectable(selected = isCorrect, role = Role.RadioButton, onClick = onSelect),
                contentAlignment = Alignment.Center,
            ) {
                if (isCorrect) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Option $label is the correct answer",
                        modifier = Modifier.size(20.dp),
                        tint = badgeContent,
                    )
                } else {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeContent,
                    )
                }
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                label = { Text(if (isImage) "Option $label image URL" else "Option $label") },
                placeholder = if (isImage) {
                    { Text("https://example.com/answer.jpg") }
                } else {
                    null
                },
                singleLine = true,
                isError = error != null,
                supportingText = supporting(
                    error ?: if (isImage) null else counterText(value.length, OPTION_TEXT_LIMIT),
                ),
            )
        }
    }
}

@Composable
private fun DraftQuestionItem(
    number: Int,
    question: DraftQuestion,
    onRemove: () -> Unit,
) {
    val answersAreImages = question.options.firstOrNull()?.imageUrl != null

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$number",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = question.prompt ?: "Image question",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Correct: ${OPTION_LABELS[question.correctOption]}" +
                            if (answersAreImages) " (image answers)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Remove question $number",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}