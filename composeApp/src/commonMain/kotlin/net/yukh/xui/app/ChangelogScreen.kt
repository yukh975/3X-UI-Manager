package net.yukh.xui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * The app's release history, read from the changelog compiled into the binary.
 * Opened from About — the update prompt only ever shows the one version you are
 * moving to, and there was no way to look back at what changed before that.
 */
@Composable
fun ChangelogScreen(onClose: () -> Unit) {
    val lang = LocalAppLanguage.current
    val releases = remember(lang) { Changelog.load(lang) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) { Text(tr("Back")) }
                Text(tr("Changelog"), style = MaterialTheme.typography.titleMedium)
                Box(Modifier.padding(end = 8.dp))
            }
            if (releases.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        tr("No changelog available."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(releases, key = { it.version }) { release ->
                        ReleaseCard(release, initiallyExpanded = release == releases.first())
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: ChangelogRelease, initiallyExpanded: Boolean) {
    var expanded by remember(release.version) { mutableStateOf(initiallyExpanded) }
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
                Text(
                    if (expanded) "▴" else "▾",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
