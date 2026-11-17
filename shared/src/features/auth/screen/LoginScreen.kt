package features.auth.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.aurafeed.quiz.resources.Res
import io.aurafeed.quiz.resources.app_logo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import features.auth.component.AuthComponent
import kotlin.coroutines.cancellation.CancellationException

internal val AuthFieldShape = RoundedCornerShape(16.dp)
internal val AuthButtonShape = RoundedCornerShape(28.dp)

private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

internal fun validateEmail(value: String): String? {
    val normalized = value.trim()
    return when {
        normalized.isEmpty() -> "Email is required"
        !EMAIL_REGEX.matches(normalized) -> "Enter a valid email"
        else -> null
    }
}

private class FeedbackVisuals(
    override val message: String,
    val isError: Boolean,
) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration =
        if (isError) SnackbarDuration.Long else SnackbarDuration.Short
}

internal fun CoroutineScope.showFeedback(
    host: SnackbarHostState,
    message: String,
    isError: Boolean = false,
) {
    launch {
        host.currentSnackbarData?.dismiss()
        host.showSnackbar(FeedbackVisuals(message, isError))
    }
}

internal fun Exception.toAuthDisplayMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: "Unable to complete the request. Please try again."

@Composable
private fun AuthSnackbarHost(state: SnackbarHostState) {
    val c = MaterialTheme.colorScheme
    SnackbarHost(hostState = state) { data ->
        val isError = (data.visuals as? FeedbackVisuals)?.isError == true
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(12.dp),
            containerColor = if (isError) c.error else c.inverseSurface,
            contentColor = if (isError) c.onError else c.inverseOnSurface,
        )
    }
}

@Composable
internal fun AuthScaffold(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onNavigate: (() -> Unit)? = null,
    navigationIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    navigationDescription: String = "Back",
    navigationEnabled: Boolean = true,
    maxContentWidth: Dp = 440.dp,
    centerVertically: Boolean = true,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = modifier,
        containerColor = c.background,
        snackbarHost = { AuthSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            val viewportHeight = maxHeight
            val compactHeight = viewportHeight < 500.dp
            val horizontalPadding = if (maxWidth < 360.dp) 16.dp else 24.dp
            val topPadding = if (compactHeight) 56.dp else 72.dp

            LaunchedEffect(scrollState.isScrollInProgress) {
                if (scrollState.isScrollInProgress) focusManager.clearFocus()
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = maxContentWidth)
                        .fillMaxWidth()
                        .heightIn(min = viewportHeight)
                        .padding(
                            start = horizontalPadding,
                            end = horizontalPadding,
                            top = topPadding,
                            bottom = 72.dp,
                        ),
                    verticalArrangement = if (centerVertically) Arrangement.Center else Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = content,
                )
            }

            if (onNavigate != null) {
                IconButton(
                    onClick = onNavigate,
                    enabled = navigationEnabled,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, top = 8.dp),
                ) {
                    Icon(
                        imageVector = navigationIcon,
                        contentDescription = navigationDescription,
                        tint = c.onBackground,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AuthIllustration(modifier: Modifier = Modifier, height: Dp = 180.dp) {
    Image(
        painter = painterResource(Res.drawable.app_logo),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    )
    Spacer(Modifier.height(24.dp))
}

@Composable
internal fun AuthHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    val t = MaterialTheme.typography
    Column(modifier = modifier) {
        Text(
            text = title,
            style = t.headlineMedium.copy(fontWeight = FontWeight.W800),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = t.bodyMedium.copy(color = c.onSurfaceVariant),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
internal fun authFieldColors() = MaterialTheme.colorScheme.let { c ->
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = c.surface,
        unfocusedContainerColor = c.surface,
        disabledContainerColor = c.surface,
        errorContainerColor = c.surface,
        focusedBorderColor = c.primary,
        unfocusedBorderColor = c.outlineVariant,
        disabledBorderColor = c.outlineVariant.copy(alpha = 0.5f),
        errorBorderColor = c.error,
        focusedLeadingIconColor = c.primary,
        unfocusedLeadingIconColor = c.onSurfaceVariant,
        focusedTrailingIconColor = c.primary,
        unfocusedTrailingIconColor = c.onSurfaceVariant,
        focusedPlaceholderColor = c.onSurfaceVariant,
        unfocusedPlaceholderColor = c.onSurfaceVariant,
        cursorColor = c.primary,
    )
}

@Composable
internal fun AuthPrimaryButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = AuthButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = c.primary,
            contentColor = c.onPrimary,
            disabledContainerColor = c.primary.copy(alpha = 0.45f),
            disabledContentColor = c.onPrimary.copy(alpha = 0.7f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = text
                if (loading) stateDescription = "Loading"
            },
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = c.onPrimary,
            )
        } else {
            Text(text = text, fontSize = 17.sp, fontWeight = FontWeight.W800)
        }
    }
}

@Composable
internal fun PasswordVisibilityToggle(obscured: Boolean, onToggle: () -> Unit) {
    IconButton(
        onClick = onToggle,
        modifier = Modifier.semantics {
            contentDescription = if (obscured) "Show password" else "Hide password"
        },
    ) {
        EyeIcon(open = !obscured)
    }
}

@Composable
internal fun AuthLink(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    val t = MaterialTheme.typography
    val isClickable = onClick != null && enabled

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                enabled = isClickable,
                onClickLabel = text,
                role = Role.Button,
            ) { onClick?.invoke() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = (if (small) t.bodySmall else t.bodyMedium).copy(
                color = if (isClickable) c.primary else c.onSurfaceVariant.copy(alpha = 0.5f),
                fontWeight = if (small) FontWeight.W700 else FontWeight.W800,
            ),
        )
    }
}

@Composable
internal fun AuthFooter(
    prompt: String,
    action: String,
    onClick: (() -> Unit)?,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = prompt, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(6.dp))
        AuthLink(text = action, onClick = onClick, enabled = enabled)
    }
}

@Composable
internal fun SocialDivider() {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f), color = c.outlineVariant)
        Text(
            text = "or continue with",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = c.outlineVariant)
    }
}

@Composable
internal fun GoogleButton(onClick: () -> Unit, enabled: Boolean) {
    val c = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = AuthButtonShape,
        border = BorderStroke(1.dp, c.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoogleLogo(Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Continue with Google",
                fontSize = 16.sp,
                fontWeight = FontWeight.W700,
                color = c.onSurface,
            )
        }
    }
}

@Composable
fun GoogleLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sw = size.width * 0.20f
        val inset = sw / 2f
        val arcSize = Size(size.width - sw, size.height - sw)
        val topLeft = Offset(inset, inset)
        val stroke = Stroke(width = sw)

        val red = Color(0xFFEA4335)
        val yellow = Color(0xFFFBBC05)
        val green = Color(0xFF34A853)
        val blue = Color(0xFF4285F4)

        drawArc(red, 200f, 115f, false, topLeft, arcSize, style = stroke)
        drawArc(yellow, 125f, 75f, false, topLeft, arcSize, style = stroke)
        drawArc(green, 45f, 80f, false, topLeft, arcSize, style = stroke)
        drawArc(blue, 0f, 45f, false, topLeft, arcSize, style = stroke)
        drawLine(
            color = blue,
            start = Offset(size.width / 2f, size.height / 2f),
            end = Offset(size.width - inset * 0.2f, size.height / 2f),
            strokeWidth = sw,
        )
    }
}

@Composable
fun EyeIcon(open: Boolean, modifier: Modifier = Modifier) {
    val tint = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)

        val almond = Path().apply {
            moveTo(w * 0.10f, h * 0.50f)
            cubicTo(w * 0.32f, h * 0.04f, w * 0.68f, h * 0.04f, w * 0.90f, h * 0.50f)
            cubicTo(w * 0.68f, h * 0.96f, w * 0.32f, h * 0.96f, w * 0.10f, h * 0.50f)
            close()
        }
        drawPath(almond, color = tint, style = stroke)

        if (open) {
            drawCircle(color = tint, radius = h * 0.15f)
        } else {
            drawLine(
                color = tint,
                start = Offset(w * 0.12f, h * 0.88f),
                end = Offset(w * 0.88f, h * 0.12f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun LoginScreen(
    appName: String = "Quiz Bangla",
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onLogin: suspend (email: String, password: String) -> Unit = { _, _ -> },
    onForgotPassword: () -> Unit = {},
    onCreateAccount: () -> Unit = {},
) {
    val authComponent = remember { AuthComponent() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            scope.showFeedback(snackbarHostState, errorMessage, isError = true)
        }
    }

    authComponent.Screen(
        snackbarHostState = snackbarHostState,
        title = "Welcome back",
        subtitle = "Sign in to continue your next challenge.",
    ) {
        authComponent.EmailField(
            value = email,
            onValueChange = {
                email = it
                emailError = validateEmail(it)
            },
            error = emailError,
        )

        Spacer(Modifier.height(12.dp))

        authComponent.PasswordField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            visible = passwordVisible,
            onToggleVisibility = { passwordVisible = !passwordVisible },
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            AuthLink(
                text = "Forgot password?",
                onClick = {
                    onForgotPassword()
                    scope.showFeedback(snackbarHostState, "Password recovery is coming soon.")
                },
                small = true,
            )
        }

        Spacer(Modifier.height(20.dp))

        authComponent.PrimaryButton(
            text = "Log In",
            loading = submitting || isLoading,
            onClick = {
                val emailValidation = validateEmail(email)
                emailError = emailValidation
                if (emailValidation != null) {
                    scope.showFeedback(snackbarHostState, emailValidation, isError = true)
                    return@PrimaryButton
                }

                if (password.isBlank()) {
                    scope.showFeedback(snackbarHostState, "Password is required", isError = true)
                    return@PrimaryButton
                }

                submitting = true
                scope.launch {
                    try {
                        onLogin(email.trim(), password)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (exception: Exception) {
                        scope.showFeedback(
                            snackbarHostState,
                            exception.toAuthDisplayMessage(),
                            isError = true,
                        )
                    } finally {
                        submitting = false
                    }
                }
            },
        )

        Spacer(Modifier.height(18.dp))

        SocialDivider()

        Spacer(Modifier.height(18.dp))

        GoogleButton(
            onClick = {
                scope.showFeedback(snackbarHostState, "Google sign in is not connected yet.")
            },
            enabled = true,
        )

        Spacer(Modifier.height(24.dp))

        authComponent.Footer(
            prompt = "Need an account?",
            action = "Create one",
            onClick = {
                onCreateAccount()
            },
        )

        Text(
            text = "${appName}'s challenge portal",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}
