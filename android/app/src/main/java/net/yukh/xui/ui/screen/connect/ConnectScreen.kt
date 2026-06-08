package net.yukh.xui.ui.screen.connect

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    onConnected: () -> Unit,
    vm: ConnectViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Connect to panel") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.url,
                onValueChange = vm::setUrl,
                label = { Text("Panel URL") },
                placeholder = { Text("https://panel.example.com:2053/") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                supportingText = { Text("Include the webBasePath if your admin set one") },
            )

            AuthMethodPicker(
                selected = state.method,
                onSelect = vm::setMethod,
            )

            when (state.method) {
                AuthMethod.Token -> TokenSection(state = state, vm = vm)
                AuthMethod.Credentials -> CredentialsSection(state = state, vm = vm)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Allow self-signed TLS", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Disable certificate verification — only enable for your own panel.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.allowInsecureTls,
                    onCheckedChange = vm::setAllowInsecureTls,
                )
            }

            OutlinedTextField(
                value = state.subBaseUrl,
                onValueChange = vm::setSubBaseUrl,
                label = { Text("Subscription base URL (optional)") },
                placeholder = { Text("https://panel.example.com:2096/sub/") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                supportingText = {
                    Text(
                        "Needed for subscription links/QR when using an API token " +
                            "(the token can't read panel sub settings). Leave empty " +
                            "with login/password.",
                    )
                },
            )

            HorizontalDivider()

            Button(
                onClick = { vm.submit(onConnected) },
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text("  Signing in…")
                } else {
                    Text(if (state.method == AuthMethod.Token) "Connect" else "Sign in")
                }
            }

            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthMethodPicker(
    selected: AuthMethod,
    onSelect: (AuthMethod) -> Unit,
) {
    val items = listOf(
        AuthMethod.Token to "API token",
        AuthMethod.Credentials to "Login & password",
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, (method, label) ->
            SegmentedButton(
                selected = selected == method,
                onClick = { onSelect(method) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = items.size),
            ) { Text(label) }
        }
    }
}

@Composable
private fun TokenSection(state: ConnectUiState, vm: ConnectViewModel) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = state.token,
        onValueChange = vm::setToken,
        label = { Text("API token") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        supportingText = {
            Text("Create one in Settings → Security → API Token on the panel.")
        },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Hide token" else "Show token",
                )
            }
        },
    )
}

@Composable
private fun CredentialsSection(state: ConnectUiState, vm: ConnectViewModel) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = state.username,
        onValueChange = vm::setUsername,
        label = { Text("Username") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = state.password,
        onValueChange = vm::setPassword,
        label = { Text("Password") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                )
            }
        },
    )

    OutlinedTextField(
        value = state.twoFactorCode,
        onValueChange = vm::setTwoFactorCode,
        label = { Text("2FA code") },
        placeholder = { Text("Leave empty if 2FA is disabled") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        supportingText = {
            Text("Only required if 2FA is enabled on your account.")
        },
    )

    // Hidden by default — the field above already communicates this — but kept
    // around in case we want to add a probe later that flips this on.
    AnimatedVisibility(visible = false) { Text("") }
}
