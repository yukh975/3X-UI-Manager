package net.yukh.xui.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import net.yukh.xui.BuildConfig
import net.yukh.xui.i18n.tr

/**
 * App info, its own history, and the author's other projects — split out of
 * Settings so neither screen is a grab bag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onClose: () -> Unit, onCheckUpdates: () -> Unit = {}) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showChangelog by remember { mutableStateOf(false) }
    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(tr("About")) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Close"))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("3X-UI Manager", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${tr("Version")}: $appVersion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("© 2026 Yuriy Khachaturian (yukh.net)", style = MaterialTheme.typography.bodyMedium)
                    // The GitLab (standard) build self-updates; the F-Droid build can't
                    // (F-Droid owns updates), so it shows where updates come from instead.
                    if (BuildConfig.IN_APP_UPDATER) {
                        OutlinedButton(onClick = onCheckUpdates, modifier = Modifier.fillMaxWidth()) {
                            Text(tr("Check for updates"))
                        }
                    } else {
                        Text(
                            tr("Installed from F-Droid — updates come through the F-Droid catalog."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { showChangelog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Changelog"))
                    }
                    OutlinedButton(
                        onClick = { uriHandler.openUri("https://github.com/yukh975/3X-UI-Manager") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  " + tr("Project on GitHub"))
                    }
                }
            }

            Text(tr("Our projects"), style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ProjectRow(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        title = tr("3x-ui manual"),
                        subtitle = tr("Documentation for the panel this app manages"),
                        onClick = { uriHandler.openUri("https://github.com/yukh975/3X-UI-Manual") },
                    )
                    HorizontalDivider()
                    ProjectRow(
                        icon = Icons.Outlined.CurrencyExchange,
                        title = tr("Currency Converter"),
                        subtitle = tr("Exchange rates for Android, on F-Droid"),
                        onClick = { uriHandler.openUri("https://f-droid.org/packages/net.yukh.currency") },
                    )
                    HorizontalDivider()
                    ProjectRow(
                        icon = Icons.Outlined.Dns,
                        title = "netadm.pro",
                        subtitle = tr("Tools for network and system administrators"),
                        onClick = { uriHandler.openUri("https://netadm.pro") },
                    )
                }
            }
        }
    }

    if (showChangelog) {
        BackHandler(onBack = { showChangelog = false })
        ChangelogScreen(onClose = { showChangelog = false })
    }
}

@Composable
private fun ProjectRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
