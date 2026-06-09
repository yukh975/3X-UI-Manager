package net.yukh.xui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.yukh.xui.shared.api.PanelApi
import net.yukh.xui.shared.dto.Client
import net.yukh.xui.shared.dto.InboundSlim
import net.yukh.xui.shared.dto.Node
import net.yukh.xui.shared.dto.ServerStatus

/**
 * Root of the shared iOS/Android Compose Multiplatform app. Connect → tabbed
 * dashboard (Dashboard / Inbounds / Clients / Nodes), all in commonMain driving
 * the shared Ktor PanelApi. The connection is persisted (SessionStore) and
 * auto-restored on launch. No DI framework yet; the Android app keeps its own
 * richer Hilt-based UI.
 */
@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var baseUrl by remember { mutableStateOf("") }
            var token by remember { mutableStateOf("") }
            var connected by remember { mutableStateOf(false) }
            var busy by remember { mutableStateOf(false) }
            var refreshing by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            var tab by remember { mutableStateOf(0) }
            var lang by remember { mutableStateOf(LANG_EN) }
            var status by remember { mutableStateOf<ServerStatus?>(null) }
            var inbounds by remember { mutableStateOf<List<InboundSlim>>(emptyList()) }
            var clients by remember { mutableStateOf<List<Client>>(emptyList()) }
            var nodes by remember { mutableStateOf<List<Node>>(emptyList()) }
            var onlines by remember { mutableStateOf<List<String>>(emptyList()) }
            var api by remember { mutableStateOf<PanelApi?>(null) }
            val store = remember { SessionStore() }
            val scope = rememberCoroutineScope()

            // Validate URL+token by fetching status; on success keep the client,
            // mark connected and persist the session. Returns success.
            suspend fun connect(url: String, tok: String): Boolean {
                busy = true
                error = null
                val a = PanelApi(url.trim(), tok.trim())
                val ok = try {
                    val resp = a.serverStatus()
                    if (resp.success) {
                        api = a
                        status = resp.obj
                        connected = true
                        true
                    } else {
                        error = resp.msg.ifBlank { "Login failed — check URL / token" }
                        a.close()
                        false
                    }
                } catch (e: Throwable) {
                    error = e.message ?: "Network error"
                    a.close()
                    false
                }
                busy = false
                if (ok) store.save(url.trim(), tok.trim())
                return ok
            }

            suspend fun refreshAll() {
                val a = api ?: return
                try {
                    a.serverStatus().let { if (it.success) status = it.obj }
                    a.inbounds().let { if (it.success) inbounds = it.obj ?: emptyList() }
                    a.clients().let { if (it.success) clients = it.obj ?: emptyList() }
                    a.nodes().let { if (it.success) nodes = it.obj ?: emptyList() }
                    a.onlines().let { if (it.success) onlines = it.obj ?: emptyList() }
                    error = null
                } catch (e: Throwable) {
                    error = e.message ?: "Network error"
                }
            }

            // Auto-restore a saved session + language on first launch.
            LaunchedEffect(Unit) {
                lang = store.loadLang() ?: LANG_EN
                if (!connected) {
                    store.load()?.let { saved ->
                        baseUrl = saved.baseUrl
                        token = saved.token
                        connect(saved.baseUrl, saved.token)
                    }
                }
            }

            val doDisconnect: () -> Unit = {
                api?.close()
                api = null
                status = null
                inbounds = emptyList(); clients = emptyList(); nodes = emptyList(); onlines = emptyList()
                tab = 0
                connected = false
                store.clear()
            }

            CompositionLocalProvider(LocalAppLanguage provides lang) {
                if (!connected) {
                    ConnectScreen(
                        baseUrl = baseUrl,
                        token = token,
                        busy = busy,
                        error = error,
                        onBaseUrl = { baseUrl = it; error = null },
                        onToken = { token = it; error = null },
                        onConnect = { scope.launch { connect(baseUrl, token) } },
                    )
                } else {
                    val tabs = listOf("Dashboard", "Inbounds", "Clients", "Nodes", "More")
                    val icons = listOf("📊", "🔌", "👥", "🌐", "⚙️")
                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                tabs.forEachIndexed { i, label ->
                                    NavigationBarItem(
                                        selected = tab == i,
                                        onClick = { tab = i },
                                        icon = { Text(icons[i]) },
                                        label = { Text(tr(label), maxLines = 1, softWrap = false) },
                                    )
                                }
                            }
                        },
                    ) { inner ->
                        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
                            when (tab) {
                                0 -> DashboardScreen(
                                    host = baseUrl,
                                    status = status,
                                    onlineEmails = onlines,
                                    refreshing = refreshing,
                                    error = error,
                                    onRefresh = { scope.launch { refreshing = true; refreshAll(); refreshing = false } },
                                    onDisconnect = doDisconnect,
                                )
                                1 -> InboundsListScreen(inbounds)
                                2 -> ClientsListScreen(clients)
                                3 -> NodesListScreen(nodes)
                                else -> MoreScreen(
                                    host = baseUrl,
                                    lang = lang,
                                    onLang = { lang = it; store.saveLang(it) },
                                    onDisconnect = doDisconnect,
                                )
                            }
                        }
                    }
                    LaunchedEffect(connected) {
                        if (connected) refreshAll()
                        while (connected) {
                            delay(5_000)
                            refreshAll()
                        }
                    }
                }
            }
        }
    }
}
