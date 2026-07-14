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
import net.yukh.xui.shared.api.AppUpdate
import net.yukh.xui.shared.api.AuthExpiredException
import net.yukh.xui.shared.api.PanelApi
import net.yukh.xui.shared.api.UpdateChecker
import net.yukh.xui.shared.dto.BulkAdjustRequest
import net.yukh.xui.shared.dto.BulkDelRequest
import net.yukh.xui.shared.dto.Client
import net.yukh.xui.shared.dto.ClientCreatePayload
import net.yukh.xui.shared.dto.ClientIpInfo
import net.yukh.xui.shared.dto.ClientModel
import net.yukh.xui.shared.dto.InboundModel
import net.yukh.xui.shared.dto.InboundSlim
import net.yukh.xui.shared.dto.Node
import net.yukh.xui.shared.dto.NodeModel
import net.yukh.xui.shared.dto.PanelSubSettings
import net.yukh.xui.shared.dto.ServerStatus
import net.yukh.xui.shared.dto.TrafficSummary
import net.yukh.xui.shared.dto.VlessEncAuth
import net.yukh.xui.shared.dto.parseXrayObj
import net.yukh.xui.shared.dto.trafficByNode
import kotlin.time.TimeSource

/** One server's currently-online clients, for the grouped online view. */
data class OnlineGroup(val server: String, val isMain: Boolean, val emails: List<String>)

/** Holds the previous inbound (up,down) totals + timestamp so live up/down speed
 *  can be derived from the delta between polls (panel v3.4.0; mirrors Android). */
private class SpeedTracker {
    var prevTotals: Map<Int, Pair<Long, Long>> = emptyMap()
    var prevClientTotals: Map<String, Pair<Long, Long>> = emptyMap()
    var prevMark: TimeSource.Monotonic.ValueTimeMark? = null
}

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
            var speedInBits by remember { mutableStateOf(false) }
            var status by remember { mutableStateOf<ServerStatus?>(null) }
            var inbounds by remember { mutableStateOf<List<InboundSlim>>(emptyList()) }
            var inboundSpeeds by remember { mutableStateOf<Map<Int, Pair<Long, Long>>>(emptyMap()) }
            var clientSpeeds by remember { mutableStateOf<Map<String, Pair<Long, Long>>>(emptyMap()) }
            val speedTracker = remember { SpeedTracker() }
            var vlessEncAuths by remember { mutableStateOf<List<VlessEncAuth>?>(null) }
            var vlessEncLoading by remember { mutableStateOf(false) }
            var clients by remember { mutableStateOf<List<Client>>(emptyList()) }
            var nodes by remember { mutableStateOf<List<Node>>(emptyList()) }
            var onlines by remember { mutableStateOf<List<String>>(emptyList()) }
            var onlineGroups by remember { mutableStateOf<List<OnlineGroup>>(emptyList()) }
            var onlineLoading by remember { mutableStateOf(false) }
            var editingNode by remember { mutableStateOf<NodeModel?>(null) }
            var editingNodeNew by remember { mutableStateOf(false) }
            var editingInbound by remember { mutableStateOf<InboundModel?>(null) }
            var editingInboundNew by remember { mutableStateOf(false) }
            var editingInboundMonitored by remember { mutableStateOf(false) }
            var showXray by remember { mutableStateOf(false) }
            var showGeneralX by remember { mutableStateOf(false) }
            var showDnsX by remember { mutableStateOf(false) }
            var showRoutingX by remember { mutableStateOf(false) }
            var showOutboundsX by remember { mutableStateOf(false) }
            var metricChart by remember { mutableStateOf<MetricChartState?>(null) }
            var showBackup by remember { mutableStateOf(false) }
            var showPanelAdmin by remember { mutableStateOf(false) }
            var showMtls by remember { mutableStateOf(false) }
            // Pre-sign-in settings, reached from the Connect screen's gear.
            var showConnectSettings by remember { mutableStateOf(false) }
            var xrayConfigJson by remember { mutableStateOf("") }
            var xrayTestUrl by remember { mutableStateOf("") }
            var xrayLoading by remember { mutableStateOf(false) }
            var editingClient by remember { mutableStateOf<Client?>(null) }
            var editingClientNew by remember { mutableStateOf(false) }
            var clientLinks by remember { mutableStateOf<List<String>>(emptyList()) }
            var clientLinksLoading by remember { mutableStateOf(false) }
            var clientSubUrl by remember { mutableStateOf<String?>(null) }
            var clientIps by remember { mutableStateOf<List<ClientIpInfo>>(emptyList()) }
            var clientIpsLoading by remember { mutableStateOf(false) }
            var geoUpdating by remember { mutableStateOf<Set<String>>(emptySet()) }
            var geoAllUpdating by remember { mutableStateOf(false) }
            var updatingNodeIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
            var bulkBusy by remember { mutableStateOf(false) }
            var xrayBusy by remember { mutableStateOf(false) }
            // Optimistic Xray running-state, pinned ~6 s after a control action so a
            // lagging 5 s poll doesn't flicker the state back.
            var xrayOverride by remember { mutableStateOf<Boolean?>(null) }
            var editorSaving by remember { mutableStateOf(false) }
            var editorError by remember { mutableStateOf<String?>(null) }
            var api by remember { mutableStateOf<PanelApi?>(null) }
            // Multi-profile (multi-instance): saved panels + the active one's id.
            var profiles by remember { mutableStateOf<List<SavedSession>>(emptyList()) }
            var activeId by remember { mutableStateOf<String?>(null) }
            // "Add another panel" overlay form (separate from the active connection).
            var addingPanel by remember { mutableStateOf(false) }
            var addUrl by remember { mutableStateOf("") }
            var addToken by remember { mutableStateOf("") }
            var addInsecure by remember { mutableStateOf(false) }
            var addBusy by remember { mutableStateOf(false) }
            var addError by remember { mutableStateOf<String?>(null) }
            val store = remember { SessionStore() }
            val lock = remember { AppLock() }
            // Start unlocked; the lock is armed only when a saved session is
            // auto-restored at launch (see LaunchedEffect below) or on ON_STOP
            // while connected. A fresh manual sign-in must not trigger it.
            var locked by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            // Self-update: check the app's GitLab releases (iOS can't install an
            // unsigned .ipa itself, so we notify + open the release page).
            var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
            // Newest release any check has seen — survives dialog dismissal so the
            // Dashboard app-version card keeps hinting until the user updates.
            var lastAvailableUpdate by remember { mutableStateOf<AppUpdate?>(null) }
            // Swap the English release body for the changelog section in the UI
            // language (falls back to the release body when the fetch fails).
            suspend fun localizedUpdate(u: AppUpdate): AppUpdate {
                val notes = UpdateChecker.localizedNotes(u.version, lang == LANG_RU) ?: u.notes
                return u.copy(notes = UpdateChecker.reflowNotes(notes))
            }
            fun checkUpdatesManual() {
                updateState = UpdateUiState.Checking
                scope.launch {
                    val latest = try { UpdateChecker.fetchLatest() } catch (e: Throwable) { null }
                    updateState = when {
                        latest == null -> UpdateUiState.Error
                        UpdateChecker.isNewer(latest.version, appVersionName()) -> {
                            val u = localizedUpdate(latest)
                            lastAvailableUpdate = u
                            UpdateUiState.Available(u)
                        }
                        else -> UpdateUiState.UpToDate
                    }
                }
            }
            // Silent check once on launch — only surfaces when a newer build exists.
            LaunchedEffect(Unit) {
                val upd = try { UpdateChecker.latestIfNewer(appVersionName()) } catch (e: Throwable) { null }
                if (upd != null) {
                    val u = localizedUpdate(upd)
                    lastAvailableUpdate = u
                    if (updateState == UpdateUiState.Idle) {
                        updateState = UpdateUiState.Available(u)
                    }
                }
            }

            // Panel alerts: one pass per app open — iOS backgrounds run only
            // opportunistically (BGAppRefresh), so on-open is the reliable path.
            LaunchedEffect(Unit) {
                runCatching { AlertsCheck.run(store) }
            }

            // Re-lock on returning from the background — but only while signed in
            // and only after a 30 s grace period, so a quick switch (e.g. to another
            // app to copy a panel URL) and back doesn't prompt for the passcode. The
            // returning-user lock at cold launch is handled separately (below).
            var backgroundedAt by remember { mutableStateOf<TimeSource.Monotonic.ValueTimeMark?>(null) }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> if (connected) backgroundedAt = TimeSource.Monotonic.markNow()
                        Lifecycle.Event.ON_START -> {
                            val at = backgroundedAt
                            backgroundedAt = null
                            if (connected && lock.hasPasscode() && at != null &&
                                (TimeSource.Monotonic.markNow() - at).inWholeSeconds >= 30
                            ) {
                                locked = true
                            }
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // Validate URL+token by fetching status; on success keep the client,
            // mark connected and persist the session. Returns success.
            suspend fun connect(url: String, tok: String, insecure: Boolean, save: Boolean = true): Boolean {
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
                if (ok && save) {
                    val s = SavedSession(url.trim(), tok.trim(), insecure)
                    store.upsertActive(s)
                    profiles = store.loadProfiles()
                    activeId = s.id
                    baseUrl = s.baseUrl; token = s.token; allowInsecure = s.allowInsecure
                }
                return ok
            }

            suspend fun refreshAll() {
                val a = api ?: return
                try {
                    a.serverStatus().let { if (it.success) status = it.obj }
                    // Live up/down speed (bytes/s) from the delta between polls —
                    // shared mark/dt so inbound and client speeds line up.
                    val now = TimeSource.Monotonic.markNow()
                    val prevMark = speedTracker.prevMark
                    val dt = prevMark?.let { m -> (now - m).inWholeMilliseconds / 1000.0 } ?: 0.0
                    val liveDelta = prevMark != null && dt > 0.5
                    a.inbounds().let {
                        if (it.success) {
                            val fresh = it.obj ?: emptyList()
                            if (liveDelta) {
                                inboundSpeeds = fresh.mapNotNull { ib ->
                                    val p = speedTracker.prevTotals[ib.id] ?: return@mapNotNull null
                                    val up = ((ib.up - p.first).coerceAtLeast(0) / dt).toLong()
                                    val down = ((ib.down - p.second).coerceAtLeast(0) / dt).toLong()
                                    ib.id to (up to down)
                                }.toMap()
                            }
                            speedTracker.prevTotals = fresh.associate { ib -> ib.id to (ib.up to ib.down) }
                            inbounds = fresh
                        }
                    }
                    a.clients().let {
                        if (it.success) {
                            val fresh = it.obj ?: emptyList()
                            if (liveDelta) {
                                clientSpeeds = fresh.mapNotNull { c ->
                                    val p = speedTracker.prevClientTotals[c.email] ?: return@mapNotNull null
                                    val up = ((c.up - p.first).coerceAtLeast(0) / dt).toLong()
                                    val down = ((c.down - p.second).coerceAtLeast(0) / dt).toLong()
                                    c.email to (up to down)
                                }.toMap()
                            }
                            speedTracker.prevClientTotals = fresh.associate { c -> c.email to (c.up to c.down) }
                            clients = fresh
                        }
                    }
                    speedTracker.prevMark = now
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

            // Xray control: [running]=true → start/restart (restartXrayService),
            // false → stop. Optimistically pin the new state ~6 s so the poll
            // doesn't flicker it back, then refresh.
            fun xrayCtl(running: Boolean) {
                if (xrayBusy) return
                xrayBusy = true; xrayOverride = running
                scope.launch {
                    try { if (running) api?.restartXray() else api?.stopXray() } catch (e: Throwable) {}
                    xrayBusy = false
                    delay(6_000)
                    xrayOverride = null
                    refreshAll()
                }
            }

            fun geoUpdateAll() {
                if (geoAllUpdating) return
                geoAllUpdating = true
                scope.launch {
                    try { api?.updateAllGeofiles() } catch (e: Throwable) {}
                    geoAllUpdating = false
                    refreshAll()
                }
            }

            // Online clients grouped BY SERVER (main panel + each node), mirroring
            // the Android "Online by server" dialog. Preferred path (panel v3.4+):
            // /clients/onlinesByGuid returns {panelGuid: [emails]} with each email
            // under EXACTLY ONE server, so a client can never appear on several
            // servers at once. Keys matching a node.guid are that node; everything
            // else (incl. the master's own guid) is the main panel. Fallback for
            // panels < 3.4 (no byGuid): main = online ∩ clients of main-panel
            // inbounds (nodeId 0); each node is queried directly for its own
            // онлайн, falling back to inbound-membership if unreachable.
            suspend fun loadOnlineGroups() {
                val a = api ?: return
                onlineLoading = true
                val byGuid = try { a.onlinesByGuid() } catch (e: Throwable) { null }
                if (byGuid?.success == true && byGuid.obj != null) {
                    val map = byGuid.obj!!
                    val nodeByGuid = nodes.filter { it.guid.isNotBlank() }.associateBy { it.guid }
                    val mainEmails = mutableListOf<String>()
                    val perNode = mutableMapOf<Int, MutableList<String>>()
                    for ((guid, emails) in map) {
                        val node = nodeByGuid[guid]
                        if (node != null) perNode.getOrPut(node.id) { mutableListOf() }.addAll(emails)
                        else mainEmails.addAll(emails)
                    }
                    val main = OnlineGroup("", isMain = true, mainEmails.distinct().sorted())
                    val nodeGroups = nodes.filter { it.enable }.map { node ->
                        OnlineGroup(node.remark.ifBlank { node.name }, isMain = false,
                            (perNode[node.id] ?: emptyList()).distinct().sorted())
                    }
                    onlineGroups = listOf(main) + nodeGroups
                    onlineLoading = false
                    return
                }
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

            // Fetch the open metric chart's series (one call per line), keeping the
            // result only if the user hasn't switched block/interval meanwhile.
            suspend fun loadMetricSeries() {
                val mc = metricChart ?: return
                val a = api ?: return
                val series = coroutineScope {
                    mc.block.series.map { def ->
                        async {
                            val pts = runCatching { a.metricHistory(def.key, mc.bucket) }.getOrNull()
                                ?.let { if (it.success) it.obj ?: emptyList() else emptyList() } ?: emptyList()
                            ChartSeries(def.label, pts)
                        }
                    }.awaitAll()
                }
                val cur = metricChart
                if (cur != null && cur.block == mc.block && cur.bucket == mc.bucket) {
                    metricChart = cur.copy(series = series, loading = false)
                }
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
                speedInBits = store.loadSpeedInBits()
                if (!connected) {
                    profiles = store.loadProfiles()
                    store.activeProfile()?.let { saved ->
                        baseUrl = saved.baseUrl
                        token = saved.token
                        allowInsecure = saved.allowInsecure
                        activeId = saved.id
                        // Returning user with a saved session: arm the lock before
                        // the panel can render. A fresh manual sign-in (onConnect)
                        // never sets this, so it won't prompt right after login.
                        if (lock.hasPasscode()) locked = true
                        connect(saved.baseUrl, saved.token, saved.allowInsecure, save = false)
                    }
                }
            }

            // Wipe the previous panel's data so every screen reloads from scratch.
            fun clearPanelData() {
                status = null
                inbounds = emptyList(); clients = emptyList(); nodes = emptyList()
                onlines = emptyList(); onlineGroups = emptyList()
                inboundSpeeds = emptyMap()
                clientSpeeds = emptyMap()
                speedTracker.prevMark = null
                speedTracker.prevTotals = emptyMap()
                speedTracker.prevClientTotals = emptyMap()
                error = null
            }

            // Switch the active connection to another saved panel and refresh all screens.
            fun switchProfile(p: SavedSession) {
                scope.launch {
                    api?.close()
                    api = PanelApi(p.baseUrl, p.token, p.allowInsecure)
                    baseUrl = p.baseUrl; token = p.token; allowInsecure = p.allowInsecure
                    activeId = p.id; store.setActiveId(p.id)
                    connected = true
                    clearPanelData()
                    refreshAll()
                }
            }

            // Tear down the connection entirely (no profile selected → Connect screen).
            fun clearBinding() {
                api?.close(); api = null
                clearPanelData()
                tab = 0
                connected = false
                activeId = null
            }

            // Sign out of a panel: forget it; if it was active, fall back to another
            // saved one or drop to the Connect screen when none remain. This is the
            // single "leave a panel" path (the old global Disconnect was removed).
            fun deleteProfile(p: SavedSession) {
                val left = store.removeProfile(p.id)
                profiles = left
                // Signing out of the last panel returns the app to its fresh-install
                // state, so drop the app-lock passcode too (kept while any panel remains).
                if (left.isEmpty()) { lock.removePasscode(); locked = false }
                if (p.id == activeId) {
                    val next = left.firstOrNull()
                    if (next != null) switchProfile(next) else clearBinding()
                }
            }

            // Validate + save a second/Nth panel from the "Add panel" form, then switch to it.
            fun connectAdd() {
                if (addUrl.isBlank() || addToken.isBlank()) return
                scope.launch {
                    addBusy = true; addError = null
                    val a = PanelApi(addUrl.trim(), addToken.trim(), addInsecure)
                    val ok = try { a.serverStatus().success }
                        catch (e: Throwable) { addError = e.message ?: "Network error"; false }
                    a.close()
                    if (ok) {
                        val s = SavedSession(addUrl.trim(), addToken.trim(), addInsecure)
                        store.upsertActive(s); profiles = store.loadProfiles()
                        addingPanel = false; addUrl = ""; addToken = ""; addInsecure = false; addError = null
                        switchProfile(s)
                    } else if (addError == null) {
                        addError = "Login failed — check URL / token"
                    }
                    addBusy = false
                }
            }

            CompositionLocalProvider(LocalAppLanguage provides lang, LocalSpeedInBits provides speedInBits) {
                // The lock only gates the signed-in UI. When not connected the
                // Connect screen (no panel data) is shown without a passcode.
                if (locked && connected) {
                    LockScreen(
                        biometryEnabled = lock.biometryEnabled(),
                        onUnlock = { code -> if (lock.check(code)) { locked = false; true } else false },
                        onBiometric = { lock.authenticate("Unlock 3X-UI Manager") { ok -> if (ok) locked = false } },
                    )
                } else if (!connected && showConnectSettings) {
                    ConnectSettingsScreen(
                        lang = lang,
                        onLang = { lang = it; store.saveLang(it) },
                        onCheckUpdates = { checkUpdatesManual() },
                        onClose = { showConnectSettings = false },
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
                        onSettings = { showConnectSettings = true },
                    )
                } else if (editingClient != null) {
                    ClientEditorScreen(
                        source = editingClient!!,
                        isNew = editingClientNew,
                        availableInbounds = inbounds,
                        availableGroups = clients.mapNotNull { it.group.ifBlank { null } }.distinct().sorted(),
                        saving = editorSaving,
                        error = editorError,
                        links = clientLinks,
                        linksLoading = clientLinksLoading,
                        subUrl = clientSubUrl,
                        onShowLinks = {
                            scope.launch {
                                clientLinksLoading = true
                                val r = try { api?.clientLinks(editingClient!!.email) } catch (e: Throwable) { null }
                                clientLinks = r?.obj ?: emptyList()
                                clientLinksLoading = false
                            }
                            // Subscription URL needs panel settings (token-readable
                            // v3.3.0+) — fetch in parallel, degrade silently.
                            scope.launch {
                                val c = editingClient
                                val s = try { api?.subSettings() } catch (e: Throwable) { null }
                                clientSubUrl =
                                    if (c != null && s?.success == true && s.obj != null)
                                        s.obj!!.subscriptionUrl(PanelSubSettings.hostOf(baseUrl), c.subId)
                                    else null
                            }
                        },
                        ips = clientIps,
                        ipsLoading = clientIpsLoading,
                        onShowIps = {
                            scope.launch {
                                clientIpsLoading = true
                                val r = try { api?.clientIps(editingClient!!.email) } catch (e: Throwable) { null }
                                clientIps = r?.obj ?: emptyList()
                                clientIpsLoading = false
                            }
                        },
                        onClearIps = {
                            scope.launch {
                                clientIpsLoading = true
                                try { api?.clearClientIps(editingClient!!.email) } catch (e: Throwable) {}
                                clientIps = emptyList()
                                clientIpsLoading = false
                            }
                        },
                        onSave = { model, inboundIds ->
                            scope.launch {
                                editorSaving = true; editorError = null
                                val r = try {
                                    if (editingClientNew) {
                                        api?.addClient(ClientCreatePayload(model, inboundIds))
                                    } else {
                                        // Update by the ORIGINAL email (the lookup key); the model may
                                        // carry a renamed email. Then reconcile inbound membership:
                                        // attach the newly-checked, detach the unchecked.
                                        val origEmail = editingClient!!.email
                                        val newEmail = model.email
                                        api?.updateClient(origEmail, model)?.also { res ->
                                            if (res.success) {
                                                val original = editingClient!!.inboundIds.toSet()
                                                val selected = inboundIds.toSet()
                                                val added = (selected - original).toList()
                                                val removed = (original - selected).toList()
                                                if (added.isNotEmpty()) api?.attachClient(newEmail, added)
                                                if (removed.isNotEmpty()) api?.detachClient(newEmail, removed)
                                            }
                                        }
                                    }
                                } catch (e: Throwable) { editorError = e.message ?: "Network error"; null }
                                editorSaving = false
                                if (r?.success == true) { editingClient = null; clientLinks = emptyList(); clientSubUrl = null; clientIps = emptyList(); refreshAll() }
                                else if (r != null) editorError = r.msg.ifBlank { "Save failed" }
                            }
                        },
                        onDelete = {
                            scope.launch {
                                editorSaving = true; editorError = null
                                val r = try { api?.deleteClient(editingClient!!.email) }
                                    catch (e: Throwable) { editorError = e.message ?: "Network error"; null }
                                editorSaving = false
                                if (r?.success == true) { editingClient = null; clientLinks = emptyList(); clientSubUrl = null; clientIps = emptyList(); refreshAll() }
                                else if (r != null) editorError = r.msg.ifBlank { "Delete failed" }
                            }
                        },
                        onCancel = { editingClient = null; clientLinks = emptyList(); clientSubUrl = null; clientIps = emptyList(); editorError = null },
                    )
                } else if (editingInbound != null) {
                    InboundEditorScreen(
                        initial = editingInbound!!,
                        isNew = editingInboundNew,
                        saving = editorSaving,
                        error = editorError,
                        vlessEncAuths = vlessEncAuths,
                        vlessEncLoading = vlessEncLoading,
                        onGenVlessEnc = {
                            scope.launch {
                                vlessEncLoading = true; vlessEncAuths = null
                                val r = try { api?.getNewVlessEnc() } catch (e: Throwable) { null }
                                vlessEncAuths = r?.obj?.auths ?: emptyList()
                                vlessEncLoading = false
                            }
                        },
                        onClearVlessEnc = { vlessEncAuths = null },
                        monitored = editingInboundMonitored,
                        showMonitor = !editingInboundNew,
                        onMonitoredChange = { on ->
                            val ib = editingInbound
                            val pid = activeId
                            if (ib != null && pid != null) {
                                store.setInboundMonitored(pid, MonitoredInbound(ib.id, ib.port, ib.remark), on)
                                editingInboundMonitored = on
                            }
                        },
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
                        onCancel = { editingInbound = null; editorError = null; vlessEncAuths = null },
                    )
                } else if (editingNode != null) {
                    NodeEditorScreen(
                        initial = editingNode!!,
                        isNew = editingNodeNew,
                        saving = editorSaving,
                        error = editorError,
                        inboundCount = inbounds.count { it.nodeId == editingNode!!.id },
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
                    val testApi = api
                    RoutingXrayScreen(
                        configJson = xrayConfigJson,
                        loading = xrayLoading,
                        saving = editorSaving,
                        error = editorError,
                        onConfigChange = { xrayConfigJson = it },
                        onSave = { saveXrayThen { showRoutingX = false } },
                        onCancel = { showRoutingX = false; editorError = null },
                        inbounds = inbounds,
                        onRouteTest = if (testApi != null) {
                            { domain, ip, port, network, inboundTag ->
                                testApi.routeTest(domain, ip, port, network, inboundTag).obj
                            }
                        } else {
                            null
                        },
                    )
                } else if (showOutboundsX) {
                    val testApi = api
                    OutboundsXrayScreen(
                        configJson = xrayConfigJson,
                        loading = xrayLoading,
                        saving = editorSaving,
                        error = editorError,
                        onConfigChange = { xrayConfigJson = it },
                        onSave = { saveXrayThen { showOutboundsX = false } },
                        onCancel = { showOutboundsX = false; editorError = null },
                        onTestOutbound = if (testApi != null) {
                            { ob, mode -> testApi.testOutbound(ob, mode).obj }
                        } else {
                            null
                        },
                    )
                } else if (showPanelAdmin && api != null) {
                    PanelAdminScreen(api = api!!, lang = lang, onClose = { showPanelAdmin = false })
                } else if (showMtls && api != null) {
                    MtlsScreen(api = api!!, lang = lang, onClose = { showMtls = false })
                } else if (showBackup && api != null) {
                    BackupScreen(api = api!!, lang = lang, onClose = { showBackup = false })
                } else if (addingPanel) {
                    // "Add another panel" — same form as Connect, but saves a new
                    // profile and switches to it instead of replacing the session.
                    ConnectScreen(
                        baseUrl = addUrl,
                        token = addToken,
                        allowInsecure = addInsecure,
                        busy = addBusy,
                        error = addError,
                        onBaseUrl = { addUrl = it; addError = null },
                        onToken = { addToken = it; addError = null },
                        onAllowInsecure = { addInsecure = it },
                        onConnect = { connectAdd() },
                        addMode = true,
                        onClose = { addingPanel = false; addError = null },
                    )
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
                                0 -> { DashboardScreen(
                                    host = baseUrl,
                                    status = status,
                                    clients = clients,
                                    onlineCount = onlines.size,
                                    onlineGroups = onlineGroups,
                                    onlineLoading = onlineLoading,
                                    onExpandOnline = { scope.launch { loadOnlineGroups() } },
                                    mainTraffic = traffic[0],
                                    geoFiles = GEO_FILES,
                                    geoUpdating = geoUpdating,
                                    geoUpdatingAll = geoAllUpdating,
                                    onGeoUpdate = { f ->
                                        scope.launch {
                                            geoUpdating = geoUpdating + f
                                            try { api?.updateGeofile(f) } catch (e: Throwable) {}
                                            geoUpdating = geoUpdating - f
                                            refreshAll()
                                        }
                                    },
                                    onGeoUpdateAll = { geoUpdateAll() },
                                    xrayRunning = xrayOverride ?: (status?.xrayRunning ?: false),
                                    xrayBusy = xrayBusy,
                                    onXrayStart = { xrayCtl(true) },
                                    onXrayStop = { xrayCtl(false) },
                                    onXrayRestart = { xrayCtl(true) },
                                    refreshing = refreshing,
                                    error = error,
                                    onRefresh = { scope.launch { refreshing = true; refreshAll(); refreshing = false } },
                                    onMetric = { block ->
                                        metricChart = MetricChartState(block = block, bucket = 2, loading = true)
                                        scope.launch { loadMetricSeries() }
                                    },
                                    appUpdateVersion = lastAvailableUpdate?.version,
                                    onAppUpdate = {
                                        lastAvailableUpdate?.let { updateState = UpdateUiState.Available(it) }
                                    },
                                )
                                metricChart?.let { mc ->
                                    MetricHistoryDialog(
                                        state = mc,
                                        onBucket = { b -> metricChart = mc.copy(bucket = b, loading = true); scope.launch { loadMetricSeries() } },
                                        onDismiss = { metricChart = null },
                                    )
                                } }
                                1 -> InboundsListScreen(
                                    inbounds,
                                    speeds = inboundSpeeds,
                                    onAdd = { editingInboundNew = true; editorError = null; editingInboundMonitored = false; editingInbound = InboundModel() },
                                    onEdit = { id ->
                                        scope.launch {
                                            editorError = null
                                            val r = try { api?.getInbound(id) } catch (e: Throwable) { null }
                                            if (r?.success == true && r.obj != null) {
                                                editingInboundNew = false
                                                editingInboundMonitored =
                                                    activeId?.let { store.isInboundMonitored(it, r.obj!!.id) } ?: false
                                                editingInbound = r.obj
                                            } else error = r?.msg?.ifBlank { null } ?: "Couldn't load inbound"
                                        }
                                    },
                                    onToggle = { id, en -> scope.launch { api?.setInboundEnable(id, en); refreshAll() } },
                                )
                                2 -> ClientsListScreen(
                                    clients,
                                    speeds = clientSpeeds,
                                    onlineEmails = onlines.toSet(),
                                    onAdd = { editorError = null; clientLinks = emptyList(); clientSubUrl = null; clientIps = emptyList(); editingClientNew = true; editingClient = Client() },
                                    onEdit = { c -> editorError = null; clientLinks = emptyList(); clientSubUrl = null; clientIps = emptyList(); editingClientNew = false; editingClient = c },
                                    onToggle = { c, en -> scope.launch { api?.updateClient(c.email, c.toModel().copy(enable = en)); refreshAll() } },
                                    onExport = {
                                        scope.launch {
                                            val r = try { api?.exportClients() } catch (e: Throwable) { null }
                                            if (r?.success == true && r.obj != null) {
                                                platformExportFile("clients.json", r.obj.toString().encodeToByteArray())
                                            }
                                        }
                                    },
                                    onImport = {
                                        platformPickFile { _, bytes ->
                                            scope.launch {
                                                try { api?.importClients(bytes.decodeToString()) } catch (e: Throwable) {}
                                                refreshAll()
                                            }
                                        }
                                    },
                                    onDeleteOrphans = {
                                        scope.launch {
                                            try { api?.delOrphanClients() } catch (e: Throwable) {}
                                            refreshAll()
                                        }
                                    },
                                    bulkBusy = bulkBusy,
                                    onBulkEnable = { emails ->
                                        scope.launch { bulkBusy = true; try { api?.bulkEnableClients(emails) } catch (e: Throwable) {}; bulkBusy = false; refreshAll() }
                                    },
                                    onBulkDisable = { emails ->
                                        scope.launch { bulkBusy = true; try { api?.bulkDisableClients(emails) } catch (e: Throwable) {}; bulkBusy = false; refreshAll() }
                                    },
                                    onBulkAdjust = { emails, days, bytes, flow ->
                                        scope.launch { bulkBusy = true; try { api?.bulkAdjustClients(BulkAdjustRequest(emails, days, bytes, flow)) } catch (e: Throwable) {}; bulkBusy = false; refreshAll() }
                                    },
                                    onBulkDelete = { emails ->
                                        scope.launch { bulkBusy = true; try { api?.bulkDelClients(BulkDelRequest(emails)) } catch (e: Throwable) {}; bulkBusy = false; refreshAll() }
                                    },
                                )
                                3 -> NodesListScreen(
                                    nodes,
                                    traffic = traffic,
                                    masterVersion = status?.panelVersion ?: "",
                                    updatingNodeIds = updatingNodeIds,
                                    onAdd = { editingNodeNew = true; editorError = null; editingNode = NodeModel() },
                                    onEdit = { n -> editingNodeNew = false; editorError = null; editingNode = n.toModel() },
                                    onUpdateNode = { n, dev ->
                                        scope.launch {
                                            updatingNodeIds = updatingNodeIds + n.id
                                            try { api?.updateNodePanel(listOf(n.id), dev) } catch (e: Throwable) {}
                                            updatingNodeIds = updatingNodeIds - n.id
                                            refreshAll()
                                        }
                                    },
                                )
                                else -> MoreScreen(
                                    host = baseUrl,
                                    lang = lang,
                                    onLang = { lang = it; store.saveLang(it) },
                                    speedInBits = speedInBits,
                                    onSpeedUnit = { speedInBits = it; store.saveSpeedInBits(it) },
                                    lock = lock,
                                    profiles = profiles,
                                    activeId = activeId,
                                    onSwitch = { p -> if (p.id != activeId) switchProfile(p) },
                                    onAddPanel = { addUrl = ""; addToken = ""; addInsecure = false; addError = null; addingPanel = true },
                                    onDeleteProfile = { p -> deleteProfile(p) },
                                    onXrayConfig = { showXray = true; editorError = null; scope.launch { loadXray() } },
                                    onGeneralX = { showGeneralX = true; editorError = null; scope.launch { loadXray() } },
                                    onDnsX = { showDnsX = true; editorError = null; scope.launch { loadXray() } },
                                    onRoutingX = { showRoutingX = true; editorError = null; scope.launch { loadXray() } },
                                    onOutboundsX = { showOutboundsX = true; editorError = null; scope.launch { loadXray() } },
                                    onPanelAdmin = { showPanelAdmin = true },
                                    onNodeMtls = { showMtls = true },
                                    onBackup = { showBackup = true },
                                    onCheckUpdates = { checkUpdatesManual() },
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

                // Update prompt overlays whatever screen is showing (Connect or panel).
                UpdateDialog(
                    state = updateState,
                    onOpenPage = { platformOpenUrl(it.pageUrl); updateState = UpdateUiState.Idle },
                    onDismiss = { updateState = UpdateUiState.Idle },
                )
            }
        }
    }
}
