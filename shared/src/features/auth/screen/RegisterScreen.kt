package features.auth.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import features.auth.component.AuthComponent
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@Composable
internal fun RegisterScreen(
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRegister: suspend (email: String, password: String) -> Boolean = { _, _ -> false },
    onGoogleSignIn: suspend () -> Unit = {},
    onAuthenticated: () -> Unit = {},
    onLogin: () -> Unit = {},
) {
    val authComponent = remember { AuthComponent() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            scope.showFeedback(snackbarHostState, errorMessage, isError = true)
        }
    }

    val emailError = if (attempted) validateEmail(email) else null
    val passwordError = if (attempted && password.isBlank()) "Password is required" else null
    val confirmPasswordError = when {
        !attempted -> null
        confirmPassword.isBlank() -> "Confirm your password"
        confirmPassword != password -> "Passwords do not match"
        else -> null
    }

    authComponent.Screen(
        snackbarHostState = snackbarHostState,
        title = "Create your account",
        subtitle = "Join Quiz Bangla and start your next challenge.",
        onNavigate = onLogin,
    ) {
        authComponent.EmailField(
            value = email,
            onValueChange = { email = it },
            error = emailError,
        )

        Spacer(Modifier.height(12.dp))

        authComponent.PasswordField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            visible = passwordVisible,
            onToggleVisibility = { passwordVisible = !passwordVisible },
            error = passwordError,
        )

        Spacer(Modifier.height(12.dp))

        authComponent.PasswordField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = "Confirm password",
            visible = confirmPasswordVisible,
            onToggleVisibility = { confirmPasswordVisible = !confirmPasswordVisible },
            error = confirmPasswordError,
        )

        Spacer(Modifier.height(24.dp))

        authComponent.PrimaryButton(
            text = "Create account",
            loading = submitting || isLoading,
            onClick = {
                attempted = true
                val emailValidation = validateEmail(email)
                val validationError = when {
                    emailValidation != null -> emailValidation
                    password.isBlank() -> "Password is required"
                    confirmPassword.isBlank() -> "Confirm your password"
                    confirmPassword != password -> "Passwords do not match"
                    else -> null
                }

                if (validationError != null) {
                    scope.showFeedback(snackbarHostState, validationError, isError = true)
                } else {
                    submitting = true
                    scope.launch {
                        try {
                            val signedIn = onRegister(email.trim(), password)
                            if (signedIn) {
                                onAuthenticated()
                            } else {
                                scope.showFeedback(
                                    snackbarHostState,
                                    "Account created. Check your email to confirm your account before logging in.",
                                )
                            }
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
                }
            },
        )

        Spacer(Modifier.height(18.dp))

        SocialDivider()

        Spacer(Modifier.height(18.dp))

        GoogleButton(
            onClick = {
                submitting = true
                scope.launch {
                    try {
                        onGoogleSignIn()
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
            enabled = !submitting && !isLoading,
        )

        Spacer(Modifier.height(24.dp))

        authComponent.Footer(
            prompt = "Already have an account?",
            action = "Log in",
            onClick = onLogin,
        )
    }
}
