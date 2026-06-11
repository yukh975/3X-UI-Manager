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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.yukh.xui.shared.api.AuthExpiredException
import net.yukh.xui.shared.api.PanelApi
import net.yukh.xui.shared.dto.Client
import net.yukh.xui.shared.dto.ClientCreatePayload
import net.yukh.xui.shared.dto.ClientModel
import net.yukh.xui.shared.dto.InboundModel
import net.yukh.xui.shared.dto.InboundSlim
import net.yukh.xui.shared.dto.Node
import net.yukh.xui.shared.dto.NodeModel
import net.yukh.xui.shared.dto.ServerStatus
import net.yukh.xui.shared.dto.TrafficSummary
import net.yukh.xui.shared.dto.parseXrayObj
import net.yukh.xui.shared.dto.trafficByNode

/** One server's currently-online clients, for the grouped online view. */
data class OnlineGroup(val server: String, val isMain: Boolean, val emails: List<String>)

/** Panel geo-database allowlist (see ServerService.UpdateGeofile). */
private val GEO_FILES = listOf(
    "geoip.dat", "geosite.dat", "geoip_RU.dat", "geosite_RU.dat", "geoip_IR.dat", "geosite_IR.dat",
)

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
            var allowInsecure by remember { mutableStateOf(false) }
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
            var onlineGroups by remember { mutableStateOf<List<OnlineGroup>>(emptyList()) }
            var onlineLoading by remember { mutableStateOf(false) }
            var editingNode by remember { mutableStateOf<NodeModel?>(null) }
            var editingNodeNew by remember { mutableStateOf(false) }
            var editingInbound by remember { mutableStateOf<InboundModel?>(null) }
            var editingInboundNew by remember { mutableStateOf(false) }
            var showXray by remember { mutableStateOf(false) }
            var showGeneralX by remember { mutableStateOf(false) }
            var showDnsX by remember { mutableStateOf(false) }
            var showRoutingX by remember { mutableStateOf(false) }
            var showPanelAdmin by remember { mutableStateOf(false) }
            var xrayConfigJson by remember { mutableStateOf("") }
            var xrayTestUrl by remember { mutableStateOf("") }
            var xrayLoading by remember { mutableStateOf(false) }
            var editingClient by remember { mutableStateOf<Client?>(null) }
            var editingClientNew by remember { mutableStateOf(false) }
            var clientLinks by remember { mutableStateOf<List<String>>(emptyList()) }
            var clientLinksLoading by remember { mutableStateOf(false) }
            var geoUpdating by remember { mutableStateOf<Set<String>>(emptySet()) }
            var editorSaving by remember { mutableStateOf(false) }
            var editorError by remember { mutableStateOf<String?>(null) }
            var api by remember { mutableStateOf<PanelApi?>(null) }
            val store = remember { SessionStore() }
            val lock = remember { AppLock() }
            var locked by remember { mutableStateOf(lock.hasPasscode()) }
            val scope = rememberCoroutineScope()

            // Re-lock whenever the app leaves the foreground, so returning to it
            // requires the passcode / biometrics again (not just at cold launch).
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP && lock.hasPasscode()) locked = true
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // Validate URL+token by fetching status; on success keep the client,
            // mark connected and persist the session. Returns success.
            suspend fun connect(url: String, tok: String, insecure: Boolean): Boolean {
                busy = true
                error = null
                val a = PanelApi(url.trim(), tok.trim(), insecure)
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
                if (ok) store.save(url.trim(), tok.trim(), insecure)
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
                } catch (e: AuthExpiredException) {
                    // Token revoked/disabled in the panel — drop to Connect with a
                    // clear message (fields stay pre-filled for a quick fix).
                    api?.close(); api = null
                    connected = false
                    error = tr(lang, "Your API token is no longer valid. Reconnect with a working one.")
                } catch (e: Throwable) {
                    error = e.message ?: "Network error"
                }
            }

            // Online clients grouped BY SERVER (main panel + each node), mirroring
            // the Android "Online by server" dialog. The central /clients/onlines
            // can't say which inbound a client is on, so: main = online ∩ clients
            // of main-panel inbounds (nodeId 0); each node is queried directly for
            // its own онлайн, falling back to inbound-membership if unreachable.
            suspend fun loadOnlineGroups() {
                val a = api ?: return
                onlineLoading = true
                val onlineSet = onlines.toSet()
                fun membersOf(nid: Int): Set<String> =
                    inbounds.filter { (it.nodeId ?: 0) == nid }
                        .flatMap { ib -> ib.clientStats.map { it.email } }.toSet()
                val main = OnlineGroup("", isMain = true, onlineSet.filter { it in membersOf(0) }.sorted())
                val nodeGroups = coroutineScope {
                    nodes.filter { it.enable }.map { node ->
                        async {
                            val direct = try { a.nodeOnlines(node) } catch (e: Throwable) { null }
                            val emails = if (direct?.success == true) direct.obj ?: emptyList()
                                else onlineSet.filter { it in membersOf(node.id) }
                            OnlineGroup(node.remark.ifBlank { node.name }, isMain = false, emails.sorted())
                        }
                    }.awaitAll()
                }
                onlineGroups = listOf(main) + nodeGroups
                onlineLoading = false
            }

            suspend fun loadXray() {
                val a = api ?: return
                xrayLoading = true; editorError = null
                val r = try { a.getXraySetting() } catch (e: Throwable) { editorError = e.message ?: "Network error"; null }
                if (r?.success == true && r.obj != null) {
                    val parsed = parseXrayObj(r.obj!!)
                    if (parsed != null) { xrayConfigJson = parsed.configJson; xrayTestUrl = parsed.testUrl }
                    else editorError = "Couldn't parse Xray config"
                } else if (r != null) {
                    editorError = r.msg.ifBlank { "Xray config unavailable (needs panel v3.3.0+)" }
                }
                xrayLoading = false
            }

            // Save the (possibly structurally-edited) Xray config, then run [close]
            // on success. Shared by the raw editor + the structured sections.
            fun saveXrayThen(close: () -> Unit) {
                scope.launch {
                    editorSaving = true; editorError = null
                    val r = try { api?.updateXraySetting(xrayConfigJson, xrayTestUrl) }
                        catch (e: Throwable) { editorError = e.message ?: "Network error"; null }
                    editorSaving = false
                    if (r?.success == true) close()
                    else if (r != null) editorError = r.msg.ifBlank { "Save failed" }
                }
            }

            // Auto-restore a saved session + language on first launch.
            LaunchedEffect(Unit) {
                lang = store.loadLang() ?: LANG_EN
                if (!connected) {
                    store.load()?.let { saved ->
                        baseUrl = saved.baseUrl
                        token = saved.token
                        allowInsecure = saved.allowInsecure
                        connect(saved.baseUrl, saved.token, saved.allowInsecure)
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
                if (locked) {
                    LockScreen(
                        biometryEnabled = lock.biometryEnabled(),
                        onUnlock = { code -> if (lock.check(code)) { locked = false; true } else false },
                        onBiometric = { lock.authenticate("Unlock 3X-UI Manager") { ok -> if (ok) locked = false } },
                    )
                } else if (!connected) {
                    ConnectScreen(
                        baseUrl = baseUrl,
                        token = token,
                        allowInsecure = allowInsecure,
                        busy = busy,
                        error = error,
                        onBaseUrl = { baseUrl = it; error = null },
                        onToken = { token = it; error = null },
                        onAllowInsecure = { allowInsecure = it },
                        onConnect = { scope.launch { connect(baseUrl, token, allowInsecure) } },
                    )
                } else if (editingClient != null) {
                    ClientEditorScreen(
                        source = editingClient!!,
                        isNew = editingClientNew,
                        availableInbounds = inbounds,
                        saving = editorSaving,
                        error = editorError,
                        links = clientLinks,
                        linksLoading = clientLinksLoading,
                        onShowLinks = {
                            scope.launch {
                                clientLinksLoading = true
                                val r = try { api?.clientLinks(editingClient!!.email) } catch (e: Throwable) { null }
                                clientLinks = r?.obj ?: emptyList()
                                clientLinksLoading = false
                            }
                        },
                        onSave = { model, inboundIds ->
                            scope.launch {
                                editorSaving = true; editorError = null
                                val r = try {
                                    if (editingClientNew) api?.addClient(ClientCreatePayload(model, inboundIds))
                                    else api?.updateClient(editingClient!!.email, model)
                                } catch (e: Throwable) { editorError = e.message ?: "Network error"; null }
                                editorSaving = false
                                if (r?.success == true) { editingClient = null; clientLinks = emptyList(); refreshAll() }
                                else if (r != null) editorError = r.msg.ifBlank { "Save failed" }
                            }
                        },
                        onDelete = {
                            scope.launch {
                                editorSaving = true; editorError = null
                                val r = try { api?.deleteClient(editingClient!!.email) }
                                    catch (e: Throwable) { editorError = e.message ?: "Network error"; null }
                                editorSaving = false
                                if (r?.success == true) { editingClient = null; clientLinks = emptyList(); refreshAll() }
                                else if (r != null) editorError = r.msg.ifBlank { "Delete failed" }
                            }
                        },
                        onCancel = { editingClient = null; clientLinks = emptyList(); editorError = null },
                    )
                } else if (editingInbound != null) {
                    InboundEditorScreen(
                        initial = editingInbound!!,
                        isNew = editingInboundNew,
                        saving = editorSaving,
                        error = editorError,
                        onSave = { model ->
                            scope.launch {
                                editorSaving = true; editorError = null
                                val r = try {
                                    if (editingInboundNew) api?.addInbound(model) else api?.updateInbound(model.id, model)
                                } catch (e: Throwable) { editorError = e.message ?: "Network error"; null }
                                editorSaving = false
                                if (r?.success == true) { editingInbound = null; refreshAll() }
                                else if (r != null) editorError = r.msg.ifBlank { "Save failed" }
                            }
                        },
                        onDelete = {
                            scope.launch {
                                editorSaving = true; editorError = null
                                val r = try { api?.deleteInbound(editingInbound!!.id) }
                                    catch (e: Throwable) { editorError = e.message ?: "Network error"; null }
                                editorSaving = false
                                if (r?.success == true) { editingInbound = null; refreshAll() }
                                else if (r != null) editorError = r.msg.ifBlank { "Delete failed" }
                            }
                        },
                        onCancel = { editingInbound = null; editorError = null },
                    )
                } else if (editingNode != null) {
                    NodeEditorScreen(
                        initial = editingNode!!,
                        isNew = editingNodeNew,
                        saving = editorSaving,
                        error = editorError,
                        onSave = { model ->
                            scope.launch {
                                editorSaving = true; editorError = null
                                val r = try {
                                    if (editingNodeNew) api?.addNode(model) else api?.updateNode(model.id, model)
                                } catch (e: Throwable) { editorError = e.message ?: "Network error"; null }
                                editorSaving = false
                                if (r?.success == true) { editingNode = null; refreshAll() }
                                else if (r != null) editorError = r.msg.ifBlank { "Save failed" }
                            }
                        },
                        onDelete = {
                            scope.launch {
                                editorSaving = true; editorError = null
                                val r = try { api?.deleteNode(editingNode!!.id) }
                                    catch (e: Throwable) { editorError = e.message ?: "Network error"; null }
                                editorSaving = false
                                if (r?.success == true) { editingNode = null; refreshAll() }
                                else if (r != null) editorError = r.msg.ifBlank { "Delete failed" }
                            }
                        },
                        onCancel = { editingNode = null; editorError = null },
                    )
                } else if (showXray) {
                    XrayConfigScreen(
                        configJson = xrayConfigJson,
                        testUrl = xrayTestUrl,
                        loading = xrayLoading,
                        saving = editorSaving,
                        error = editorError,
                        onConfigChange = { xrayConfigJson = it },
                        onTestUrlChange = { xrayTestUrl = it },
                        onSave = {
                            scope.launch {
                                editorSaving = true; editorError = null
                                val r = try { api?.updateXraySetting(xrayConfigJson, xrayTestUrl) }
                                    catch (e: Throwable) { editorError = e.message ?: "Network error"; null }
                                editorSaving = false
                                if (r?.success == true) showXray = false
                                else if (r != null) editorError = r.msg.ifBlank { "Save failed" }
                            }
                        },
                        onCancel = { showXray = false; editorError = null },
                    )
                } else if (showGeneralX) {
                    GeneralXrayScreen(
                        configJson = xrayConfigJson,
                        testUrl = xrayTestUrl,
                        loading = xrayLoading,
                        saving = editorSaving,
                        error = editorError,
                        onConfigChange = { xrayConfigJson = it },
                        onTestUrlChange = { xrayTestUrl = it },
                        onSave = { saveXrayThen { showGeneralX = false } },
                        onCancel = { showGeneralX = false; editorError = null },
                    )
                } else if (showDnsX) {
                    DnsXrayScreen(
                        configJson = xrayConfigJson,
                        loading = xrayLoading,
                        saving = editorSaving,
                        error = editorError,
                        onConfigChange = { xrayConfigJson = it },
                        onSave = { saveXrayThen { showDnsX = false } },
                        onCancel = { showDnsX = false; editorError = null },
                    )
                } else if (showRoutingX) {
                    RoutingXrayScreen(
                        configJson = xrayConfigJson,
                        loading = xrayLoading,
                        saving = editorSaving,
                        error = editorError,
                        onConfigChange = { xrayConfigJson = it },
                        onSave = { saveXrayThen { showRoutingX = false } },
                        onCancel = { showRoutingX = false; editorError = null },
                    )
                } else if (showPanelAdmin && api != null) {
                    PanelAdminScreen(api = api!!, lang = lang, onClose = { showPanelAdmin = false })
                } else {
                    val tabs = listOf("Dashboard", "Inbounds", "Clients", "Nodes", "More")
                    val icons = listOf("📊", "🔌", "👥", "🌐", "⚙️")
                    val traffic = trafficByNode(inbounds)
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
                                    onlineCount = onlines.size,
                                    onlineGroups = onlineGroups,
                                    onlineLoading = onlineLoading,
                                    onExpandOnline = { scope.launch { loadOnlineGroups() } },
                                    mainTraffic = traffic[0],
                                    geoFiles = GEO_FILES,
                                    geoUpdating = geoUpdating,
                                    onGeoUpdate = { f ->
                                        scope.launch {
                                            geoUpdating = geoUpdating + f
                                            try { api?.updateGeofile(f) } catch (e: Throwable) {}
                                            geoUpdating = geoUpdating - f
                                            refreshAll()
                                        }
                                    },
                                    refreshing = refreshing,
                                    error = error,
                                    onRefresh = { scope.launch { refreshing = true; refreshAll(); refreshing = false } },
                                    onDisconnect = doDisconnect,
                                )
                                1 -> InboundsListScreen(
                                    inbounds,
                                    onAdd = { editingInboundNew = true; editorError = null; editingInbound = InboundModel() },
                                    onEdit = { id ->
                                        scope.launch {
                                            editorError = null
                                            val r = try { api?.getInbound(id) } catch (e: Throwable) { null }
                                            if (r?.success == true && r.obj != null) { editingInboundNew = false; editingInbound = r.obj }
                                            else error = r?.msg?.ifBlank { null } ?: "Couldn't load inbound"
                                        }
                                    },
                                    onToggle = { id, en -> scope.launch { api?.setInboundEnable(id, en); refreshAll() } },
                                )
                                2 -> ClientsListScreen(
                                    clients,
                                    onAdd = { editorError = null; clientLinks = emptyList(); editingClientNew = true; editingClient = Client() },
                                    onEdit = { c -> editorError = null; clientLinks = emptyList(); editingClientNew = false; editingClient = c },
                                    onToggle = { c, en -> scope.launch { api?.updateClient(c.email, c.toModel().copy(enable = en)); refreshAll() } },
                                )
                                3 -> NodesListScreen(
                                    nodes,
                                    traffic = traffic,
                                    onAdd = { editingNodeNew = true; editorError = null; editingNode = NodeModel() },
                                    onEdit = { n -> editingNodeNew = false; editorError = null; editingNode = n.toModel() },
                                )
                                else -> MoreScreen(
                                    host = baseUrl,
                                    lang = lang,
                                    onLang = { lang = it; store.saveLang(it) },
                                    lock = lock,
                                    onXrayConfig = { showXray = true; editorError = null; scope.launch { loadXray() } },
                                    onGeneralX = { showGeneralX = true; editorError = null; scope.launch { loadXray() } },
                                    onDnsX = { showDnsX = true; editorError = null; scope.launch { loadXray() } },
                                    onRoutingX = { showRoutingX = true; editorError = null; scope.launch { loadXray() } },
                                    onPanelAdmin = { showPanelAdmin = true },
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
