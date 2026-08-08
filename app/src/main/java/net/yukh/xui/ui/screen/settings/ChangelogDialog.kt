package net.yukh.xui.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.yukh.xui.i18n.LocalAppLanguage
import net.yukh.xui.i18n.tr

/**
 * The app's release history, read from the changelog bundled in the APK. Opened
 * from About — the update prompt only ever shows the one version you're moving
 * to, and there was no way to look back at what changed before that.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val releases = remember(lang) { Changelog.load(context, lang) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(tr("Changelog")) },
                        navigationIcon = {
                            IconButton(onClick = onClose) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Close"))
                            }
                        },
                    )
                },
            ) { padding ->
                if (releases.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                        Text(
                            tr("No changelog available."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(releases, key = { it.version }) { release ->
                            // The newest version is the one people open this for.
                            ReleaseCard(release, initiallyExpanded = release == releases.first())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: ChangelogRelease, initiallyExpanded: Boolean) {
    var expanded by rememberSaveable(release.version) { mutableStateOf(initiallyExpanded) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(release.version, style = MaterialTheme.typography.titleMedium)
                    if (release.date.isNotBlank()) {
                        Text(
                            release.date,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) tr("Collapse") else tr("Expand"),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    release.groups.forEach { group ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                group.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            group.items.forEach { item ->
                                Row {
                                    Text("•  ", style = MaterialTheme.typography.bodyMedium)
                                    Text(withBold(item), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Render the changelog's `**bold**` spans; everything else is plain text. */
private fun withBold(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (true) {
        val open = text.indexOf("**", i)
        if (open < 0) { append(text.substring(i)); break }
        val close = text.indexOf("**", open + 2)
        if (close < 0) { append(text.substring(i)); break }
        append(text.substring(i, open))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(open + 2, close)) }
        i = close + 2
    }
}
