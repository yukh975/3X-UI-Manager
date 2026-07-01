package net.yukh.xui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.yukh.xui.shared.api.AppUpdate

/** Update-check UI state. iOS can't install an unsigned .ipa itself, so an
 *  available update opens the release page rather than installing in-app. */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val update: AppUpdate) : UpdateUiState
    data object UpToDate : UpdateUiState
    data object Error : UpdateUiState
}

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onOpenPage: (AppUpdate) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(tr("Update available")) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${tr("Version")} ${state.update.version}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val notes = state.update.notes.trim()
                    if (notes.isNotBlank()) {
                        Text(notes, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        tr("Open the release page to download and reinstall the app."),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onOpenPage(state.update) }) { Text(tr("Open release page")) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Later")) } },
        )

        UpdateUiState.Checking -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(tr("Checking for updates…")) },
            text = { CircularProgressIndicator() },
            confirmButton = { },
        )

        UpdateUiState.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(tr("You're up to date")) },
            text = { Text(tr("You already have the latest version.")) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(tr("OK")) } },
        )

        UpdateUiState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(tr("Update check failed")) },
            text = { Text(tr("Couldn't reach the update server. Try again later.")) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(tr("OK")) } },
        )

        UpdateUiState.Idle -> Unit
    }
}
