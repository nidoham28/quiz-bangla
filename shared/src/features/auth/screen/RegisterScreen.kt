package features.auth.register.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
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
import features.auth.login.screen.showFeedback
import features.auth.login.screen.validateEmail

@Composable
internal fun RegisterScreen(
    onRegister: (email: String, password: String) -> Unit = { _, _ -> },
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
            loading = false,
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
                    onRegister(email.trim(), password)
                }
            },
        )

        Spacer(Modifier.height(24.dp))

        authComponent.Footer(
            prompt = "Already have an account?",
            action = "Log in",
            onClick = onLogin,
        )
    }
}
