package net.yukh.xui.ui.screen.xrayedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.components.Field
import net.yukh.xui.ui.components.LabeledDropdown
import net.yukh.xui.ui.components.PanelFeatureUnsupported
import net.yukh.xui.ui.components.SectionTitle

/**
 * Xray's scheduled geo-database refresh (panel 3.7.0). Xray downloads each
 * listed file on the schedule and hot-reloads it without a restart — the
 * unattended counterpart of the dashboard's manual geo update.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeodataScreen(onClose: () -> Unit, vm: GeodataViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.savedMessage) { state.savedMessage?.let { snackbar.showSnackbar(it); vm.dismissMessage() } }
    LaunchedEffect(state.error) { if (state.available) state.error?.let { snackbar.showSnackbar(it); vm.dismissMessage() } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(tr("Geodata auto-update")) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Close")) }
                },
                actions = {
                    if (state.available) TextButton(onClick = vm::save, enabled = state.canSave) {
                        if (state.saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text(tr("Save"))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) { Snackbar { Text(it.visuals.message) } } },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.unsupported -> PanelFeatureUnsupported(tr("Geodata auto-update"))
                !state.available -> SessionGate()
                else -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        tr("Xray downloads these files on schedule and reloads them without a restart. URLs must be HTTPS, and each file has to exist in the panel's bin folder once before Xray can update it."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    SectionTitle(tr("Schedule"))
                    Field(tr("Cron (5 fields)"), state.cron) { vm.setCron(it) }
                    if (state.assets.isNotEmpty() && state.invalidCron) {
                        Text(
                            tr("Cron must have 5 fields, e.g. 0 4 * * *"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    LabeledDropdown(
                        tr("Download through outbound (optional)"),
                        state.outbound,
                        listOf("") + state.outboundTags,
                    ) { vm.setOutbound(it) }

                    SectionTitle(tr("Files"))
                    if (state.assets.isEmpty()) {
                        Text(
                            tr("No files configured. Reference them in routing rules as ext:geosite_custom.dat:category."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.assets.forEachIndexed { i, asset ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Field(tr("File name"), asset.file) { vm.updateAsset(i, asset.copy(file = it)) }
                                Field(tr("URL (https)"), asset.url) { vm.updateAsset(i, asset.copy(url = it)) }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    IconButton(onClick = { vm.removeAsset(i) }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = tr("Delete"), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                    if (state.invalidUrl) {
                        Text(
                            tr("Each file needs an HTTPS URL."),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (state.invalidFile) {
                        Text(
                            tr("File names must be plain names like geosite_custom.dat (no paths)."),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    OutlinedButton(onClick = vm::addAsset, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Add file"))
                    }
                }
            }
        }
    }
}
