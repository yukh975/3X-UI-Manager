package net.yukh.xui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.yukh.xui.shared.dto.isValidJson

/**
 * Xray config editor — the full config JSON (outbounds, routing, dns) plus the
 * outbound test URL, mirroring the Android XrayConfigScreen. On panel v3.3.0 the
 * /panel/api/xray endpoints accept a Bearer token, so this works in token mode.
 */
@Composable
fun XrayConfigScreen(
    configJson: String,
    testUrl: String,
    loading: Boolean,
    saving: Boolean,
    error: String?,
    onConfigChange: (String) -> Unit,
    onTestUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val valid = configJson.isNotBlank() && isValidJson(configJson)
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, enabled = !saving) { Text(tr("Cancel")) }
            Text(tr("Xray config"), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onSave, enabled = !saving && !loading && valid) {
                Text(if (saving) "…" else tr("Save"))
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            if (loading) {
                Text(tr("Loading…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                OutlinedTextField(
                    value = testUrl,
                    onValueChange = onTestUrlChange,
                    label = { Text(tr("Outbound test URL")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(tr("xray config (JSON)"), style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = configJson,
                    onValueChange = onConfigChange,
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 340.dp),
                )
                if (!valid) {
                    Text(tr("Invalid JSON."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    tr("Save, then restart Xray to apply. A broken config can take Xray down."),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
