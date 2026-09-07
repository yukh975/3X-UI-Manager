package net.yukh.xui.app

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.yukh.xui.shared.json.jsonGetObjectList
import net.yukh.xui.shared.json.jsonGetString
import net.yukh.xui.shared.json.jsonPutString
import net.yukh.xui.shared.json.jsonRemove
import net.yukh.xui.shared.json.jsonSetObjectList

/**
 * Xray's scheduled geo-database refresh (panel 3.7.0): Xray downloads each
 * listed file on the schedule and hot-reloads it without a restart — the
 * unattended counterpart of the dashboard's manual geo update. The settings
 * live in the Xray config template under `geodata`, so the whole config is
 * round-tripped and only that key is touched.
 */
@Composable
fun GeodataXrayScreen(
    configJson: String,
    loading: Boolean,
    saving: Boolean,
    error: String?,
    onConfigChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    fun assets() = jsonGetObjectList(configJson, listOf("geodata", "assets"))
    fun setAssets(items: List<String>) {
        // No files means no schedule to keep — drop the block entirely, the way
        // the panel does, instead of leaving an empty one behind.
        onConfigChange(
            if (items.isEmpty()) jsonRemove(configJson, listOf("geodata"))
            else jsonSetObjectList(configJson, listOf("geodata", "assets"), items),
        )
    }

    val cron = jsonGetString(configJson, listOf("geodata", "cron")).ifBlank { DEFAULT_GEO_CRON }
    val outbound = jsonGetString(configJson, listOf("geodata", "outbound"))
    val outboundTags = jsonGetObjectList(configJson, listOf("outbounds"))
        .map { jsonGetString(it, listOf("tag")) }
        .filter { it.isNotBlank() }

    val items = assets()
    val invalidUrl = items.any { !jsonGetString(it, listOf("url")).trim().startsWith("https://") }
    val invalidFile = items.any {
        val f = jsonGetString(it, listOf("file")).trim()
        f.isEmpty() || f.contains('/') || f.contains('\\') || f.contains("..")
    }
    val invalidCron = cron.trim().split(Regex("\\s+")).size != 5
    val canSave = !saving && (items.isEmpty() || (!invalidUrl && !invalidFile && !invalidCron))

    Column(Modifier.fillMaxSize()) {
        XrayEditHeader(tr("Geodata auto-update"), saving, onCancel, onSave, canSave = canSave && !loading)
        if (error != null) {
            Text(error, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                tr("Xray downloads these files on schedule and reloads them without a restart. URLs must be HTTPS, and each file has to exist in the panel's bin folder once before Xray can update it."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            XraySection(tr("Schedule"))
            XrayField(cron, { onConfigChange(jsonPutString(configJson, listOf("geodata", "cron"), it)) }, tr("Cron (5 fields)"))
            if (items.isNotEmpty() && invalidCron) {
                Text(tr("Cron must have 5 fields, e.g. 0 4 * * *"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
            if (outboundTags.isNotEmpty()) {
                XrayLabel(tr("Download through outbound (optional)"))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
                    (listOf("") + outboundTags).forEach { tag ->
                        FilterChip(
                            selected = outbound == tag,
                            onClick = { onConfigChange(jsonPutString(configJson, listOf("geodata", "outbound"), tag)) },
                            label = { Text(tag.ifBlank { tr("Default route") }) },
                        )
                    }
                }
            }

            XraySection(tr("Files"))
            if (items.isEmpty()) {
                Text(
                    tr("No files configured. Reference them in routing rules as ext:geosite_custom.dat:category."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items.forEachIndexed { i, asset ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        XrayField(jsonGetString(asset, listOf("file")), { v ->
                            setAssets(items.toMutableList().also { it[i] = jsonPutString(asset, listOf("file"), v) })
                        }, tr("File name"))
                        XrayField(jsonGetString(asset, listOf("url")), { v ->
                            setAssets(items.toMutableList().also { it[i] = jsonPutString(asset, listOf("url"), v) })
                        }, tr("URL (https)"))
                        Row(Modifier.fillMaxWidth(), Arrangement.End) {
                            TextButton(onClick = { setAssets(items.filterIndexed { j, _ -> j != i }) }) {
                                Text(tr("Delete"), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            if (invalidUrl) {
                Text(tr("Each file needs an HTTPS URL."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
            if (invalidFile) {
                Text(tr("File names must be plain names like geosite_custom.dat (no paths)."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { setAssets(items + """{"url":"","file":""}""") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("Add file")) }
        }
    }
}

private const val DEFAULT_GEO_CRON = "0 4 * * *"
