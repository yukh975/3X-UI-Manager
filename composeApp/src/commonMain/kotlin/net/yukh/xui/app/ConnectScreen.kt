package net.yukh.xui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** First screen: panel base URL + API token. Mirrors the Android Connect screen
 *  (token auth path), shared via Compose Multiplatform. */
@Composable
fun ConnectScreen(
    baseUrl: String,
    token: String,
    allowInsecure: Boolean,
    busy: Boolean,
    error: String?,
    onBaseUrl: (String) -> Unit,
    onToken: (String) -> Unit,
    onAllowInsecure: (Boolean) -> Unit,
    onConnect: () -> Unit,
    addMode: Boolean = false,
    onClose: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
) {
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (addMode) tr("Add panel") else "3X-UI Manager",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (addMode) tr("Connect to another panel") else tr("Connect to your panel"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrl,
            label = { Text(tr("Panel URL")) },
            placeholder = { Text("https://host:port/path") },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = token,
            onValueChange = onToken,
            label = { Text(tr("API token")) },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
        )

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                tr("Allow self-signed TLS"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = allowInsecure, onCheckedChange = onAllowInsecure, enabled = !busy)
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onConnect,
            enabled = !busy && baseUrl.isNotBlank() && token.isNotBlank(),
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
            } else {
                Text(if (addMode) tr("Add") else tr("Connect"))
            }
        }
        if (addMode && onClose != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClose, enabled = !busy) { Text(tr("Cancel")) }
        }
    }
        // Settings entry (language / version / update check) for the first launch,
        // before any panel is signed in and the More tab exists.
        if (onSettings != null) {
            TextButton(
                onClick = onSettings,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) { Text("⚙", style = MaterialTheme.typography.headlineSmall) }
        }
    }
}
