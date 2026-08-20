package net.yukh.xui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.launch
import net.yukh.xui.shared.api.PanelApi
import net.yukh.xui.shared.dto.ApiToken

/**
 * Panel administration over the token-accessible setting API: change the admin
 * credentials, manage API tokens, and restart the panel. Self-contained — it
 * drives [api] directly. Destructive actions confirm first.
 */
@Composable
fun PanelAdminScreen(api: PanelApi, lang: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var tokens by remember { mutableStateOf<List<ApiToken>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    var oldU by remember { mutableStateOf("") }
    var oldP by remember { mutableStateOf("") }
    var newU by remember { mutableStateOf("") }
    var newP by remember { mutableStateOf("") }

    var newToken by remember { mutableStateOf<ApiToken?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var showRestart by remember { mutableStateOf(false) }
    var showCreds by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ApiToken?>(null) }

    var subAnnounce by remember { mutableStateOf("") }
    var smtpFrom by remember { mutableStateOf("") }
    var smtpFromName by remember { mutableStateOf("") }
    var outboundDownThreshold by remember { mutableStateOf("3") }
    var subLoaded by remember { mutableStateOf(false) }
    // The whole settings object, kept verbatim so a save only changes the
    // fields this screen edits.
    var rawSettings by remember { mutableStateOf<JsonObject?>(null) }

    suspend fun reload() {
        loading = true
        runCatching { api.listApiTokens() }
            .onSuccess { if (it.success) tokens = it.obj ?: emptyList() }
        loading = false
    }
    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(Unit) {
        runCatching { api.getRawSettings() }.getOrNull()?.let { all ->
            rawSettings = all
            subAnnounce = all.str("subAnnounce")
            smtpFrom = all.str("smtpFrom")
            smtpFromName = all.str("smtpFromName")
            outboundDownThreshold = (all.int("outboundDownThreshold") ?: 3).toString()
        }
        subLoaded = true
    }

    fun saveSettings(patch: Map<String, JsonElement>, ok: String, fail: String) {
        val all = rawSettings ?: return
        busy = true
        scope.launch {
            val r = runCatching { api.updateSettings(all, patch) }.getOrNull()
            if (r?.success == true) {
                rawSettings = JsonObject(all.toMutableMap().apply { putAll(patch) })
                message = tr(lang, ok)
            } else {
                message = tr(lang, fail)
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text(tr("Back")) }
            Text(tr("Panel admin"), style = MaterialTheme.typography.titleMedium)
            Box(Modifier.padding(end = 8.dp))
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }

            // ---- Admin account ----
            Text(tr("Admin account"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Change the panel login. Enter the current credentials to confirm."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PField(oldU, { oldU = it }, tr("Current username"))
                    PField(oldP, { oldP = it }, tr("Current password"), password = true)
                    PField(newU, { newU = it }, tr("New username"))
                    PField(newP, { newP = it }, tr("New password"), password = true)
                    Button(
                        onClick = { showCreds = true },
                        enabled = !busy && oldU.isNotBlank() && oldP.isNotBlank() && newU.isNotBlank() && newP.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(tr("Change credentials")) }
                }
            }

            // ---- API tokens ----
            Text(tr("API tokens"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    when {
                        loading -> Text(tr("Loading…"), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        tokens.isEmpty() -> Text(tr("No API tokens yet."), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> tokens.forEach { t ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(t.name.ifBlank { "#${t.id}" }, Modifier.weight(1f))
                                Switch(checked = t.enabled, onCheckedChange = { on ->
                                    scope.launch { runCatching { api.setApiTokenEnabled(t.id, on) }; reload() }
                                })
                                TextButton(onClick = { deleteTarget = t }) {
                                    Text(tr("Delete"), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { showCreate = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Create token"))
            }

            // ---- Subscription ----
            Text(tr("Subscription"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Announcement shown on the subscription page. HTML is allowed; leave blank to hide it."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = subAnnounce,
                        onValueChange = { subAnnounce = it },
                        label = { Text(tr("Announcement")) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            saveSettings(
                                mapOf("subAnnounce" to JsonPrimitive(subAnnounce)),
                                "Announcement saved",
                                "Couldn't save announcement",
                            )
                        },
                        enabled = !busy && subLoaded,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(tr("Save announcement")) }
                }
            }

            // ---- Email (panel v3.6.0) ----
            Text(tr("Email (SMTP)"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Address shown in the From header of panel emails. Leave empty to use the SMTP username."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = smtpFrom,
                        onValueChange = { smtpFrom = it },
                        label = { Text(tr("From address")) },
                        placeholder = { Text("panel@example.com") },
                        singleLine = true,
                        enabled = subLoaded && !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = smtpFromName,
                        onValueChange = { smtpFromName = it },
                        label = { Text(tr("Sender name")) },
                        singleLine = true,
                        enabled = subLoaded && !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            saveSettings(
                                mapOf(
                                    "smtpFrom" to JsonPrimitive(smtpFrom.trim()),
                                    "smtpFromName" to JsonPrimitive(smtpFromName.trim()),
                                ),
                                "Email settings saved",
                                "Couldn't save settings",
                            )
                        },
                        enabled = !busy && subLoaded,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(tr("Save email settings")) }
                }
            }

            // ---- Notifications (panel v3.6.0) ----
            Text(tr("Notifications"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Consecutive failed probes before an \"outbound down\" alert fires. 1 = alert on the first failure."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = outboundDownThreshold,
                        onValueChange = { outboundDownThreshold = it.filter(Char::isDigit).take(3) },
                        label = { Text(tr("Outbound-down threshold")) },
                        singleLine = true,
                        enabled = subLoaded && !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            val threshold = (outboundDownThreshold.toIntOrNull() ?: 3).coerceIn(1, 100)
                            outboundDownThreshold = threshold.toString()
                            saveSettings(
                                mapOf("outboundDownThreshold" to JsonPrimitive(threshold)),
                                "Notification settings saved",
                                "Couldn't save settings",
                            )
                        },
                        enabled = !busy && subLoaded,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(tr("Save notification settings")) }
                }
            }

            // ---- Panel ----
            Text(tr("Panel"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            OutlinedButton(onClick = { showRestart = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Restart panel"))
            }
        }
    }

    if (showCreds) {
        ConfirmDialog(
            title = tr("Change credentials?"),
            body = tr("The panel login changes immediately. Your API token keeps working."),
            confirm = tr("Change"),
            onConfirm = {
                showCreds = false
                busy = true
                scope.launch {
                    val r = runCatching { api.updateUser(oldU.trim(), oldP, newU.trim(), newP) }.getOrNull()
                    message = if (r?.success == true) tr(lang, "Credentials updated") else tr(lang, "Couldn't change credentials")
                    busy = false
                }
            },
            onDismiss = { showCreds = false },
        )
    }

    if (showCreate) {
        TokenNameDialog(
            onConfirm = { name ->
                showCreate = false
                busy = true
                scope.launch {
                    val r = runCatching { api.createApiToken(name.trim()) }.getOrNull()
                    if (r?.success == true) newToken = r.obj else message = tr(lang, "Couldn't create token")
                    busy = false
                    reload()
                }
            },
            onDismiss = { showCreate = false },
        )
    }

    newToken?.let { tok ->
        AlertDialog(
            onDismissRequest = { newToken = null },
            title = { Text(tr("Token created")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Copy it now — it's shown only once."), style = MaterialTheme.typography.bodyMedium)
                    Card { Text(tok.token, Modifier.fillMaxWidth().padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(tok.token)); newToken = null }) { Text(tr("Copy & close")) } },
            dismissButton = { TextButton(onClick = { newToken = null }) { Text(tr("Close")) } },
        )
    }

    deleteTarget?.let { t ->
        ConfirmDialog(
            title = tr("Delete token?"),
            body = tr("Apps using this token will stop working. This can't be undone."),
            confirm = tr("Delete"),
            onConfirm = {
                deleteTarget = null
                scope.launch { runCatching { api.deleteApiToken(t.id) }; reload() }
            },
            onDismiss = { deleteTarget = null },
        )
    }

    if (showRestart) {
        ConfirmDialog(
            title = tr("Restart panel?"),
            body = tr("The panel restarts and the app reconnects in a few seconds.") + "\n\n" +
                tr("If the app reaches the panel through it, the connection will drop — reconnect manually."),
            confirm = tr("Restart"),
            onConfirm = {
                showRestart = false
                busy = true
                scope.launch {
                    val r = runCatching { api.restartPanel() }.getOrNull()
                    message = if (r?.success == true) tr(lang, "Panel is restarting…") else tr(lang, "Couldn't restart the panel")
                    busy = false
                }
            },
            onDismiss = { showRestart = false },
        )
    }
}

@Composable
private fun PField(value: String, onChange: (String) -> Unit, label: String, password: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TokenNameDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Create token")) },
        text = { OutlinedTextField(name, { name = it }, label = { Text(tr("Token name")) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(tr("Create")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

private fun JsonObject.str(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.int(key: String): Int? =
    this[key]?.jsonPrimitive?.intOrNull
