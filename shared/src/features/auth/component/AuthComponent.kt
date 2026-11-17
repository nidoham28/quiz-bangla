package features.auth.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import features.auth.login.screen.AuthFieldShape
import features.auth.login.screen.AuthFooter
import features.auth.login.screen.AuthHeader
import features.auth.login.screen.AuthIllustration
import features.auth.login.screen.AuthPrimaryButton
import features.auth.login.screen.AuthScaffold
import features.auth.login.screen.PasswordVisibilityToggle
import features.auth.login.screen.authFieldColors

internal class AuthComponent {
    @Composable
    fun Screen(
        snackbarHostState: SnackbarHostState,
        title: String,
        subtitle: String,
        onNavigate: (() -> Unit)? = null,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        AuthScaffold(
            snackbarHostState = snackbarHostState,
            onNavigate = onNavigate,
        ) {
            AuthIllustration()
            AuthHeader(title = title, subtitle = subtitle)
            content()
        }
    }

    @Composable
    fun EmailField(
        value: String,
        onValueChange: (String) -> Unit,
        error: String?,
        modifier: Modifier = Modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            isError = error != null,
            modifier = modifier.fillMaxWidth(),
            shape = AuthFieldShape,
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            supportingText = error?.let { { Text(it) } },
            colors = authFieldColors(),
        )
    }

    @Composable
    fun PasswordField(
        value: String,
        onValueChange: (String) -> Unit,
        label: String,
        visible: Boolean,
        onToggleVisibility: () -> Unit,
        modifier: Modifier = Modifier,
        error: String? = null,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            isError = error != null,
            modifier = modifier.fillMaxWidth(),
            shape = AuthFieldShape,
            label = { Text(label) },
            singleLine = true,
            visualTransformation = if (visible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = error?.let { { Text(it) } },
            trailingIcon = {
                PasswordVisibilityToggle(
                    obscured = !visible,
                    onToggle = onToggleVisibility,
                )
            },
            colors = authFieldColors(),
        )
    }

    @Composable
    fun PrimaryButton(
        text: String,
        loading: Boolean,
        onClick: () -> Unit,
    ) {
        AuthPrimaryButton(text = text, loading = loading, onClick = onClick)
    }

    @Composable
    fun Footer(
        prompt: String,
        action: String,
        onClick: () -> Unit,
    ) {
        AuthFooter(
            prompt = prompt,
            action = action,
            onClick = onClick,
            enabled = true,
        )
    }
}
