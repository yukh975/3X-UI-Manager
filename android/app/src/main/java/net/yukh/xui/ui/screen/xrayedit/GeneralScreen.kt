package net.yukh.xui.ui.screen.xrayedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import net.yukh.xui.data.json.bool
import net.yukh.xui.data.json.child
import net.yukh.xui.data.json.put
import net.yukh.xui.data.json.putBool
import net.yukh.xui.data.json.putString
import net.yukh.xui.data.json.string
import net.yukh.xui.i18n.tr
import net.yukh.xui.ui.components.Field
import net.yukh.xui.ui.components.LabeledDropdown
import net.yukh.xui.ui.components.SectionTitle
import net.yukh.xui.ui.components.SwitchRow

private val ROUTING_STRATEGY = listOf("AsIs", "IPIfNonMatch", "IPOnDemand")
private val LOG_LEVELS = listOf("none", "debug", "info", "warning", "error")
private val MASK_ADDRESS = listOf("", "quarter", "half", "full")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralScreen(onClose: () -> Unit, vm: GeneralViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.savedMessage) { state.savedMessage?.let { snackbar.showSnackbar(it); vm.dismissSavedMessage() } }
    LaunchedEffect(state.error) { if (state.available) state.error?.let { snackbar.showSnackbar(it); vm.dismissError() } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(tr("General / Logs")) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Close")) }
                },
                actions = {
                    if (state.available) TextButton(onClick = vm::save, enabled = state.dirty && !state.saving) {
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
                !state.available -> SessionGate()
                else -> {
                    val cfg = state.config
                    val routing = cfg.child("routing")
                    val log = cfg.child("log")
                    val policy = cfg.child("policy")
                    val system = policy.child("system")
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SectionTitle(tr("General"))
                        LabeledDropdown(tr("Routing strategy"), routing.string("domainStrategy").ifBlank { "AsIs" }, ROUTING_STRATEGY) {
                            vm.update(cfg.put("routing", routing.putString("domainStrategy", it)))
                        }
                        Field(tr("Outbound test URL"), state.testUrl) { vm.setTestUrl(it) }

                        SectionTitle(tr("Logs"))
                        LabeledDropdown(tr("Log level"), log.string("loglevel").ifBlank { "warning" }, LOG_LEVELS) {
                            vm.update(cfg.put("log", log.putString("loglevel", it)))
                        }
                        Field(tr("Access log (path, empty = off)"), log.string("access")) {
                            vm.update(cfg.put("log", log.putString("access", it)))
                        }
                        Field(tr("Error log (path, empty = off)"), log.string("error")) {
                            vm.update(cfg.put("log", log.putString("error", it)))
                        }
                        LabeledDropdown(tr("Mask address"), log.string("maskAddress"), MASK_ADDRESS) {
                            vm.update(cfg.put("log", log.putString("maskAddress", it)))
                        }
                        SwitchRow(tr("DNS log"), log.bool("dnsLog")) {
                            vm.update(cfg.put("log", log.putBool("dnsLog", it)))
                        }

                        SectionTitle(tr("Statistics"))
                        for ((key, label) in STATS_FIELDS) {
                            SwitchRow(tr(label), system.bool(key)) {
                                vm.update(cfg.put("policy", policy.put("system", system.putBool(key, it))))
                            }
                        }
                    }
                }
            }
        }
    }
}

private val STATS_FIELDS = listOf(
    "statsInboundUplink" to "Inbound uplink stats",
    "statsInboundDownlink" to "Inbound downlink stats",
    "statsOutboundUplink" to "Outbound uplink stats",
    "statsOutboundDownlink" to "Outbound downlink stats",
)

@Composable
internal fun SessionGate() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tr("Couldn't load the Xray config"), style = MaterialTheme.typography.titleMedium)
        Text(
            tr("On panel v3.3.0+ the Xray config works with an API token. On older panels it's only exposed to a login session — reconnect with login/password."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
