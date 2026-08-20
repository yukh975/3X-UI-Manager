package net.yukh.xui.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp

/**
 * App info, its own history, and the author's other projects — split out of
 * Settings so neither screen is a grab bag.
 */
@Composable
fun AboutScreen(host: String, onCheckUpdates: () -> Unit, onClose: () -> Unit) {
    var showChangelog by remember { mutableStateOf(false) }

    if (showChangelog) {
        ChangelogScreen(onClose = { showChangelog = false })
        return
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) { Text(tr("Back")) }
                Text(tr("About"), style = MaterialTheme.typography.titleMedium)
                Box(Modifier.padding(end = 8.dp))
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("3X-UI Manager", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${tr("Version")}: ${appVersionName()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (host.isNotBlank()) {
                            Text(
                                "${tr("Panel URL:")} $host",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val linkColor = MaterialTheme.colorScheme.primary
                        Text(
                            buildAnnotatedString {
                                append("© 2026 Yuriy Khachaturian (")
                                withLink(
                                    LinkAnnotation.Url(
                                        "https://yukh.net",
                                        TextLinkStyles(SpanStyle(color = linkColor)),
                                    ),
                                ) { append("yukh.net") }
                                append(")")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = onCheckUpdates, modifier = Modifier.fillMaxWidth()) {
                            Text(tr("Check for updates"))
                        }
                        OutlinedButton(onClick = { showChangelog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(tr("Changelog"))
                        }
                        OutlinedButton(
                            onClick = { platformOpenUrl("https://github.com/yukh975/3X-UI-Manager") },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(tr("Project on GitHub"))
                        }
                    }
                }

                Text(tr("Our projects"), style = MaterialTheme.typography.titleMedium)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ProjectRow(
                            icon = "📖",
                            title = tr("3x-ui manual"),
                            subtitle = tr("Documentation for the panel this app manages"),
                            url = "https://github.com/yukh975/3X-UI-Manual",
                        )
                        HorizontalDivider()
                        ProjectRow(
                            icon = "💱",
                            title = tr("Currency Converter"),
                            subtitle = tr("Exchange rates for Android, on F-Droid"),
                            url = "https://f-droid.org/packages/net.yukh.currency",
                        )
                        HorizontalDivider()
                        ProjectRow(
                            icon = "🖧",
                            title = "netadm.pro",
                            subtitle = tr("Tools for network and system administrators"),
                            url = "https://netadm.pro",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(icon: String, title: String, subtitle: String, url: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { platformOpenUrl(url) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(icon, style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("↗", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
