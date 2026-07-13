package net.yukh.xui.ui.screen.paneladmin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.yukh.xui.data.api.dto.ApiToken
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.components.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelAdminScreen(onClose: () -> Unit, vm: PanelAdminViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.loadTokens(); vm.loadSubscription() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.dismissMessage() } }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); vm.dismissError() } }

    var showRestart by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ApiToken?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(tr("Panel admin")) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Close")) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) { Snackbar { Text(it.visuals.message) } } },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Admin account ----
            SectionTitle(tr("Admin account"))
            CredentialsCard(busy = state.busy, onChange = vm::changeCredentials)

            // ---- API tokens ----
            SectionTitle(tr("API tokens"))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    when {
                        state.tokensLoading -> Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        }
                        state.tokens.isEmpty() -> Text(
                            tr("No API tokens yet."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                        else -> state.tokens.forEachIndexed { i, t ->
                            if (i > 0) HorizontalDivider()
                            TokenRow(
                                token = t,
                                onToggle = { vm.setTokenEnabled(t.id, it) },
                                onDelete = { deleteTarget = t },
                            )
                        }
                    }
                }
            }
            OutlinedButton(onClick = { showCreate = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Create token"))
            }

            // ---- Panel ----
            // ---- Subscription ----
            SectionTitle(tr("Subscription"))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Announcement shown as a banner on the subscription info page. Leave empty for none."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = state.subAnnounce,
                        onValueChange = vm::setSubAnnounce,
                        label = { Text(tr("Announcement")) },
                        enabled = state.subLoaded && !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = vm::saveSubAnnounce, enabled = state.subLoaded && !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Save"))
                    }
                }
            }

            // ---- Panel ----
            SectionTitle(tr("Panel"))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Restart the panel service. The app's connection drops for a few seconds while it comes back."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { showRestart = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Restart panel"))
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateTokenDialog(
            onConfirm = { name -> showCreate = false; vm.createToken(name) },
            onDismiss = { showCreate = false },
        )
    }

    state.newToken?.let { tok -> NewTokenDialog(tok, onDismiss = vm::dismissNewToken) }

    deleteTarget?.let { t ->
        ConfirmDialog(
            title = tr("Delete token?"),
            body = tr("Apps using") + " \"${t.name}\" " + tr("will stop working. This can't be undone."),
            confirmLabel = tr("Delete"),
            onConfirm = { deleteTarget = null; vm.deleteToken(t.id) },
            onDismiss = { deleteTarget = null },
        )
    }

    if (showRestart) {
        ConfirmDialog(
            title = tr("Restart panel?"),
            body = tr("The panel service restarts and the app reconnects in a few seconds.") + "\n\n" +
                tr("If the app reaches the panel through it, the connection will drop — reconnect manually."),
            confirmLabel = tr("Restart"),
            onConfirm = { showRestart = false; vm.restartPanel() },
            onDismiss = { showRestart = false },
        )
    }
}

@Composable
private fun CredentialsCard(busy: Boolean, onChange: (String, String, String, String) -> Unit) {
    var oldU by remember { mutableStateOf("") }
    var oldP by remember { mutableStateOf("") }
    var newU by remember { mutableStateOf("") }
    var newP by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val valid = oldU.isNotBlank() && oldP.isNotBlank() && newU.isNotBlank() && newP.isNotBlank()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Change the panel login username and password. Enter the current ones to confirm."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Field(oldU, { oldU = it }, tr("Current username"))
            PasswordField(oldP, { oldP = it }, tr("Current password"))
            Field(newU, { newU = it }, tr("New username"))
            PasswordField(newP, { newP = it }, tr("New password"))
            Button(onClick = { confirm = true }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(tr("Change credentials"))
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = tr("Change credentials?"),
            body = tr("The panel login changes immediately. Your API token keeps working, but other sessions are signed out."),
            confirmLabel = tr("Change"),
            onConfirm = { confirm = false; onChange(oldU, oldP, newU, newP) },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun TokenRow(token: ApiToken, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(token.name.ifBlank { "#${token.id}" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = token.enabled, onCheckedChange = onToggle)
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = tr("Delete"), tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CreateTokenDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Create token")) },
        text = { Field(name, { name = it }, tr("Token name")) },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(tr("Create")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

@Composable
private fun NewTokenDialog(token: ApiToken, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Token created")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Copy it now — it's shown only once and can't be retrieved later."),
                    style = MaterialTheme.typography.bodyMedium)
                Card {
                    Text(
                        token.token,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(token.token)); onDismiss() }) { Text(tr("Copy & close")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Close")) } },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
