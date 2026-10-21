package net.yukh.xui.ui.screen.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.yukh.xui.data.api.dto.Client
import net.yukh.xui.data.api.dto.ClientHwid
import net.yukh.xui.data.api.dto.ClientIpInfo
import net.yukh.xui.data.api.dto.ClientModel
import net.yukh.xui.data.api.dto.HAPP_SOURCE_TOO_LONG
import net.yukh.xui.data.api.dto.InboundSlim
import net.yukh.xui.data.api.dto.SubInfo
import net.yukh.xui.data.json.string
import net.yukh.xui.data.repo.PanelRepository
import net.yukh.xui.data.repo.isUnsupportedByPanel

private const val BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0
private const val POLL_INTERVAL_MS = 5_000L

data class ClientsUiState(
    val items: List<Client> = emptyList(),
    val online: Set<String> = emptySet(),
    val speedByClient: Map<String, Pair<Long, Long>> = emptyMap(),
    val searchQuery: String = "",
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val transientMessage: String? = null,
    val selectedClientEmail: String? = null,
    val selectedLinks: List<String> = emptyList(),
    val linksLoading: Boolean = false,
    val linksError: String? = null,
    val selectedSubUrl: String? = null,
    /** JSON subscription URL (null when the panel doesn't serve one): MASQUE is only there. */
    val selectedJsonSubUrl: String? = null,
    val subUrlChecked: Boolean = false,
    /** Customer's-eye subscription status (sub server `?format=info`, best-effort). */
    val selectedSubInfo: SubInfo? = null,
    val filters: ClientFilters = ClientFilters(),
    // Multi-select / bulk actions
    val selectionMode: Boolean = false,
    val selectedEmails: Set<String> = emptySet(),
    val bulkInFlight: Boolean = false,
    // Export-all dialog payload (null = closed)
    val exportJson: String? = null,
    // IP-log dialog (null email = closed)
    val ipLogEmail: String? = null,
    val ipLog: List<ClientIpInfo> = emptyList(),
    val ipLogLoading: Boolean = false,
    // Subscription devices (panel v3.7.0), same shape as the IP log above.
    val hwidEmail: String? = null,
    val hwids: List<ClientHwid> = emptyList(),
    val hwidsLoading: Boolean = false,
    val hwidsUnsupported: Boolean = false,
    /** The active panel is v3.8.0+: bulk HWID limit / ad tag and Happ links are offered. */
    val panel380: Boolean = false,
    // Encrypted Happ link in the share sheet (panel v3.8.0). happLinkEnabled stays
    // null until the panel's switch has been read.
    val happLinkEnabled: Boolean? = null,
    val happLink: String? = null,
    val happLinkLoading: Boolean = false,
    val happLinkError: String? = null,
    val editor: ClientEditorState? = null,
) {
    /** Distinct non-empty client groups in use, sorted — for the editor picker
     *  and the filter sheet. */
    val allGroups: List<String>
        get() = items.map { it.group.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedBy { it.lowercase() }

    /** Items narrowed by the search query (case-insensitive substring over email,
     *  comment, sub ID, UUID, password, auth and Telegram ID) and the active
     *  status/group [filters]. */
    val visibleItems: List<Client>
        get() {
            val q = searchQuery.trim()
            val now = System.currentTimeMillis()
            return items.filter { c ->
                (q.isEmpty() || c.matchesSearch(q)) &&
                    c.matches(filters, now, online)
            }
        }
}

private fun Client.matchesSearch(q: String): Boolean =
    email.contains(q, ignoreCase = true) ||
        comment.contains(q, ignoreCase = true) ||
        subId.contains(q, ignoreCase = true) ||
        uuid.contains(q, ignoreCase = true) ||
        password.contains(q, ignoreCase = true) ||
        auth.contains(q, ignoreCase = true) ||
        (tgId != 0L && tgId.toString().contains(q))

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }

private fun randomHex(bytes: Int): String {
    val b = ByteArray(bytes)
    java.security.SecureRandom().nextBytes(b)
    return b.toHex()
}

/**
 * A subscription id in the panel's own format: 16 lowercase letters/digits,
 * matching the web UI's `RandomUtil.randomLowerAndNum(16)`. Sent on create so
 * an app-made client gets the same kind of subId as a panel-made one — left
 * empty, the panel falls back to a raw `uuid.NewString()`, which puts a
 * UUID-shaped id in the subscription URL (reported as GitHub issue #3).
 */
private fun randomSubId(len: Int = 16): String {
    val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
    val rnd = java.security.SecureRandom()
    return buildString(len) { repeat(len) { append(alphabet[rnd.nextInt(alphabet.length)]) } }
}

/** The fronting domain encoded in a FakeTLS secret ("ee"+16 bytes+domain hex),
 *  or "" if the secret is empty/malformed. */
private fun mtprotoSecretDomain(secret: String): String {
    var s = secret
    if (s.startsWith("ee") || s.startsWith("dd")) s = s.substring(2)
    if (s.length <= 32) return ""
    return runCatching {
        s.substring(32).chunked(2).map { (it.toInt(16) and 0xFF).toByte() }.toByteArray().decodeToString()
    }.getOrDefault("")
}

/** Form state for creating or editing a client. Numeric inputs are kept as
 *  strings so partial/empty typing doesn't fight the user. */
data class ClientEditorState(
    val isNew: Boolean,
    val source: Client? = null,
    val email: String = "",
    val enable: Boolean = true,
    val limitIp: String = "0",
    val totalGb: String = "0",
    val expiryTime: Long = 0,
    val reset: String = "0",
    // Panel v3.7.0 client fields.
    val resetDay: String = "0",
    val resetMax: String = "0",
    val trafficReset: String = "never",
    val trafficResetDay: String = "1",
    val limitHwid: String = "0",
    val forwardedPorts: String = "",
    /** The panel's configured time zone, when it names a real one. Only used to
     *  show the expiry in the panel's clock as well; "Local" (the default) says
     *  nothing an outside caller can resolve, so it stays empty. */
    val panelTimeZone: String = "",
    val tgId: String = "",
    val group: String = "",
    val comment: String = "",
    val secret: String = "",
    val adTag: String = "",
    val allowedIps: String = "",
    /** PersistentKeepalive seconds for WireGuard/AmneziaWG peers. A new client gets 25,
     *  like the panel's own form; 0 turns keepalives off. */
    val keepAlive: String = "25",
    val selectedInboundIds: Set<Int> = emptySet(),
    val availableInbounds: List<InboundSlim> = emptyList(),
    val availableGroups: List<String> = emptyList(),
    val inboundsLoading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean
        get() = !saving && email.isNotBlank() && (!isNew || selectedInboundIds.isNotEmpty()) &&
            !masqueEmailInvalid

    /** A selected inbound is MASQUE, which logs users in with the email as the
     *  Basic-auth name: panel 3.10.0 refuses an email holding ":" there. */
    val masqueEmailInvalid: Boolean
        get() = ':' in email &&
            availableInbounds.any { it.id in selectedInboundIds && it.protocol == "masque" }

    /** The client renews weekly (panel v3.9.0, set in the web panel). The panel refuses
     *  it next to a reset period or a renewal day, so setting either turns it off. */
    val weeklyRenewal: Boolean
        get() = (source?.resetWeekday ?: 0) > 0

    /** A selected inbound is MTProto — show the secret / ad-tag fields. */
    val isMtproto: Boolean
        get() = availableInbounds.any { it.id in selectedInboundIds && it.protocol == "mtproto" }

    /** A selected inbound is AmneziaWG — show the peer's forwarded ports. */
    val isAmneziawg: Boolean
        get() = availableInbounds.any { it.id in selectedInboundIds && it.protocol == "amneziawg" }

    /** A selected inbound is WireGuard — show the peer's allowed IPs. */
    val isWireguard: Boolean
        get() = availableInbounds.any { it.id in selectedInboundIds && it.protocol == "wireguard" }

    /** A selected inbound is TUIC — the panel can't enforce per-client traffic or IP
     *  limits there (panel v3.8.0); the limit belongs on the inbound. */
    val isTuic: Boolean
        get() = availableInbounds.any { it.id in selectedInboundIds && it.protocol == "tuic" }
}

/**
 * The create/update body for this form. An edit starts from the stored client so
 * fields the form doesn't show (weekly renewal, VLESS reverse, keys) survive.
 * [newSubId] is used on create only.
 */
internal fun ClientEditorState.toClientModel(newSubId: String = ""): ClientModel {
    val e = this
    val gbBytes = (e.totalGb.toDoubleOrNull() ?: 0.0).let { (it * BYTES_PER_GB).toLong() }
    val base = e.source?.toModel() ?: ClientModel()
    val reset = e.reset.toIntOrNull() ?: 0
    val resetDay = (e.resetDay.toIntOrNull() ?: 0).coerceIn(0, 31)
    return base.copy(
        email = e.email.trim(),
        enable = e.enable,
        limitIp = e.limitIp.toIntOrNull() ?: 0,
        totalGB = gbBytes,
        expiryTime = e.expiryTime,
        reset = reset,
        // 3.7.0: a day of 0 keeps the old rolling interval, so the fields
        // coexist; the panel picks whichever is set.
        resetDay = resetDay,
        // The panel refuses weekly renewal next to a period or a day: those win.
        resetWeekday = if (reset > 0 || resetDay > 0) 0 else base.resetWeekday,
        resetMax = (e.resetMax.toIntOrNull() ?: 0).coerceAtLeast(0),
        trafficReset = e.trafficReset,
        trafficResetDay = (e.trafficResetDay.toIntOrNull() ?: 1).coerceIn(1, 31),
        limitHwid = (e.limitHwid.toIntOrNull() ?: 0).coerceAtLeast(0),
        forwardedPorts = if (e.isAmneziawg) e.forwardedPorts.trim() else base.forwardedPorts,
        tgId = e.tgId.toLongOrNull() ?: 0,
        group = e.group.trim(),
        comment = e.comment.trim(),
        secret = if (e.isMtproto) e.secret.trim() else base.secret,
        adTag = if (e.isMtproto) e.adTag.trim() else base.adTag,
        allowedIPs = if (e.isWireguard) {
            e.allowedIps.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            base.allowedIPs
        },
        // Sent whenever a tunnel inbound is selected: panel v3.8.0 treats an explicit
        // 0 as "off" and a missing value as "keep", so other clients keep theirs.
        keepAlive = if (e.isWireguard || e.isAmneziawg) {
            (e.keepAlive.toIntOrNull() ?: 0).coerceIn(0, 65535)
        } else {
            base.keepAlive
        },
        // Mint the subscription id ourselves on create, in the panel's own
        // 16-char format. Sending it empty makes the panel fall back to a
        // uuid, which is what put a UUID-shaped id in the subscription URL.
        subId = if (e.isNew) newSubId else base.subId,
    )
}

@HiltViewModel
class ClientsViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ClientsUiState())
    val state: StateFlow<ClientsUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    // Previous (up,down) totals per client email + timestamp, to derive live speed between polls.
    private var prevTotals: Map<String, Pair<Long, Long>> = emptyMap()
    private var prevTime: Long = 0L

    init {
        // Reload when the user switches to another panel.
        viewModelScope.launch {
            repo.activeProfileId.drop(1).collect { load(force = true) }
        }
    }

    /** Background auto-refresh while the Clients screen is on-screen (panel 3.4.0). */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                // Skip while editing or already refreshing so we don't fight the user.
                if (_state.value.editor == null && !_state.value.refreshing) load()
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun load(force: Boolean = false) {
        val current = _state.value
        if (current.refreshing) return
        // A forced reload (e.g. panel switch) resets the speed baseline so the first
        // sample after the switch isn't a bogus delta against the previous panel.
        if (force) {
            prevTotals = emptyMap()
            prevTime = 0L
        }
        val firstLoad = current.items.isEmpty() && current.error == null
        _state.update {
            it.copy(
                refreshing = true,
                loading = it.loading || firstLoad,
                error = if (force) null else it.error,
            )
        }
        viewModelScope.launch {
            val clients = repo.listClients()
            val onlines = repo.listOnlines()
            val panel380 = repo.isPanel380()

            clients
                .onSuccess { list ->
                    val sorted = list.sortedBy { c -> c.email.lowercase() }
                    val now = System.currentTimeMillis()
                    val dt = (now - prevTime) / 1000.0
                    val speeds = if (prevTime > 0 && dt > 0.5) {
                        sorted.mapNotNull { c ->
                            val prev = prevTotals[c.email] ?: return@mapNotNull null
                            val up = (((c.up - prev.first).coerceAtLeast(0)) / dt).toLong()
                            val down = (((c.down - prev.second).coerceAtLeast(0)) / dt).toLong()
                            c.email to (up to down)
                        }.toMap()
                    } else {
                        emptyMap()
                    }
                    prevTotals = sorted.associate { it.email to (it.up to it.down) }
                    prevTime = now
                    _state.update {
                        it.copy(
                            items = sorted,
                            speedByClient = speeds,
                            online = onlines.getOrNull()?.toSet().orEmpty(),
                            panel380 = panel380,
                            loading = false,
                            refreshing = false,
                            error = null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, refreshing = false, error = e.message)
                    }
                }
        }
    }

    fun openShareSheet(email: String) {
        val client = _state.value.items.firstOrNull { it.email == email }
        _state.update {
            it.copy(
                selectedClientEmail = email,
                selectedLinks = emptyList(),
                linksLoading = true,
                linksError = null,
                selectedSubUrl = null,
                selectedJsonSubUrl = null,
                subUrlChecked = false,
                selectedSubInfo = null,
                happLinkEnabled = null,
                happLink = null,
                happLinkLoading = false,
                happLinkError = null,
            )
        }
        viewModelScope.launch {
            repo.getClientLinks(email)
                .onSuccess { links ->
                    _state.update { it.copy(selectedLinks = links, linksLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(linksLoading = false, linksError = e.message) }
                }
        }
        // Subscription URL needs panel settings (token-readable on v3.3.0) —
        // fetch in parallel and degrade silently if unavailable (old panels).
        viewModelScope.launch {
            val subUrl = client?.let { repo.getSubscriptionUrl(it) }
            val jsonSubUrl = client?.let { repo.getJsonSubscriptionUrl(it) }
            _state.update { it.copy(selectedSubUrl = subUrl, selectedJsonSubUrl = jsonSubUrl, subUrlChecked = true) }
        }
        // Customer's-eye live status (v3.6.0 sub server) — best-effort, silent on
        // failure so setups where the sub port isn't reachable just omit it.
        viewModelScope.launch {
            val info = client?.let { repo.getSubscriptionInfo(it) }
            if (info != null && _state.value.selectedClientEmail == email) {
                _state.update { it.copy(selectedSubInfo = info) }
            }
        }
        // Encrypted Happ link (panel v3.8.0): offered only once the panel's switch is known.
        viewModelScope.launch {
            if (!repo.isPanel380()) return@launch
            val enabled = repo.happLinkEnabled().getOrNull() ?: return@launch
            if (_state.value.selectedClientEmail == email) {
                _state.update { it.copy(happLinkEnabled = enabled) }
            }
        }
    }

    fun closeShareSheet() {
        _state.update {
            it.copy(
                selectedClientEmail = null,
                selectedLinks = emptyList(),
                linksLoading = false,
                linksError = null,
                selectedSubUrl = null,
                selectedJsonSubUrl = null,
                subUrlChecked = false,
                selectedSubInfo = null,
                happLinkEnabled = null,
                happLink = null,
                happLinkLoading = false,
                happLinkError = null,
            )
        }
    }

    /** Ask the panel for an encrypted Happ link for the open client (panel v3.8.0).
     *  The panel keeps nothing, so every press returns a fresh link. */
    fun generateHappLink() {
        val email = _state.value.selectedClientEmail ?: return
        val client = _state.value.items.firstOrNull { it.email == email } ?: return
        if (_state.value.happLinkLoading) return
        _state.update { it.copy(happLinkLoading = true, happLinkError = null) }
        viewModelScope.launch {
            val r = repo.happLink(client.id)
            if (_state.value.selectedClientEmail != email) return@launch
            r.onSuccess { link -> _state.update { it.copy(happLink = link, happLinkLoading = false) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(happLinkLoading = false, happLinkError = e.message ?: "error")
                    }
                }
        }
    }

    fun deleteClient(email: String) {
        viewModelScope.launch {
            repo.deleteClient(email)
                .onSuccess {
                    _state.update {
                        it.copy(
                            items = it.items.filterNot { c -> c.email == email },
                            transientMessage = "Client $email deleted",
                            selectedClientEmail = null,
                            selectedLinks = emptyList(),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(transientMessage = "Delete failed: ${e.message}")
                    }
                }
        }
    }

    // ---- Editor -----------------------------------------------------------

    /** Distinct non-empty client groups already in use, for the editor's picker. */
    private fun existingGroups(): List<String> = _state.value.allGroups

    fun openCreateEditor() {
        _state.update {
            it.copy(editor = ClientEditorState(isNew = true, inboundsLoading = true, availableGroups = existingGroups()))
        }
        loadInboundsForEditor()
        loadPanelGroupsForEditor()
        loadPanelTimeZone()
    }

    /** The panel's own time zone, for the expiry caption. Best-effort: an older
     *  panel, a missing key or "Local" all just leave the caption showing the
     *  phone's clock alone. */
    private fun loadPanelTimeZone() {
        viewModelScope.launch {
            val zone = repo.getRawSettings().getOrNull()?.string("timeLocation").orEmpty()
            if (zone.isBlank() || zone.equals("Local", ignoreCase = true)) return@launch
            _state.update { s -> s.editor?.let { s.copy(editor = it.copy(panelTimeZone = zone)) } ?: s }
        }
    }

    fun openEditEditor(email: String) {
        val client = _state.value.items.firstOrNull { it.email == email } ?: return
        val gb = if (client.totalGB > 0) {
            (client.totalGB / BYTES_PER_GB).let { v -> if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(v) }
        } else "0"
        _state.update {
            it.copy(
                selectedClientEmail = null, // close share sheet if open
                editor = ClientEditorState(
                    isNew = false,
                    source = client,
                    email = client.email,
                    enable = client.enable,
                    limitIp = client.limitIp.toString(),
                    resetDay = client.resetDay.toString(),
                    resetMax = client.resetMax.toString(),
                    trafficReset = client.trafficReset.ifBlank { "never" },
                    trafficResetDay = client.trafficResetDay.coerceAtLeast(1).toString(),
                    limitHwid = client.limitHwid.toString(),
                    forwardedPorts = client.forwardedPorts,
                    totalGb = gb,
                    expiryTime = client.expiryTime,
                    reset = client.reset.toString(),
                    tgId = if (client.tgId != 0L) client.tgId.toString() else "",
                    group = client.group,
                    comment = client.comment,
                    secret = client.secret,
                    adTag = client.adTag,
                    allowedIps = client.allowedIPs,
                    keepAlive = client.keepAlive.toString(),
                    selectedInboundIds = client.inboundIds.toSet(),
                    availableGroups = existingGroups(),
                    inboundsLoading = true,
                ),
            )
        }
        loadInboundsForEditor()
        loadPanelGroupsForEditor()
        loadPanelTimeZone()
    }

    private fun loadInboundsForEditor() {
        viewModelScope.launch {
            val inbounds = repo.listInbounds().getOrNull().orEmpty()
            _state.update { s ->
                s.editor?.let { e ->
                    s.copy(editor = e.copy(availableInbounds = inbounds, inboundsLoading = false))
                } ?: s
            }
        }
    }

    /** The panel's own group list — unlike the clients on screen it also names groups
     *  nobody is in yet. Best-effort: on failure the editor keeps the groups in use. */
    private fun loadPanelGroupsForEditor() {
        viewModelScope.launch {
            val names = repo.listClientGroups().getOrNull()?.map { it.name.trim() } ?: return@launch
            _state.update { s ->
                s.editor?.let { e ->
                    val merged = (e.availableGroups + names)
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .sortedBy { it.lowercase() }
                    s.copy(editor = e.copy(availableGroups = merged))
                } ?: s
            }
        }
    }

    fun closeEditor() = _state.update { it.copy(editor = null) }

    private fun updateEditor(transform: (ClientEditorState) -> ClientEditorState) {
        _state.update { s -> s.editor?.let { s.copy(editor = transform(it).copy(error = null)) } ?: s }
    }

    fun setEditorEmail(v: String) = updateEditor { it.copy(email = v) }
    fun setEditorEnable(v: Boolean) = updateEditor { it.copy(enable = v) }
    fun setEditorLimitIp(v: String) = updateEditor { it.copy(limitIp = v.filter(Char::isDigit)) }
    fun setEditorResetDay(v: String) = updateEditor { it.copy(resetDay = v.filter(Char::isDigit).take(2)) }
    fun setEditorResetMax(v: String) = updateEditor { it.copy(resetMax = v.filter(Char::isDigit).take(4)) }
    fun setEditorTrafficReset(v: String) = updateEditor { it.copy(trafficReset = v) }
    fun setEditorTrafficResetDay(v: String) = updateEditor { it.copy(trafficResetDay = v.filter(Char::isDigit).take(2)) }
    fun setEditorLimitHwid(v: String) = updateEditor { it.copy(limitHwid = v.filter(Char::isDigit).take(4)) }
    fun setEditorForwardedPorts(v: String) = updateEditor { it.copy(forwardedPorts = v) }
    fun setEditorTotalGb(v: String) = updateEditor { it.copy(totalGb = v.filter { c -> c.isDigit() || c == '.' }) }
    fun setEditorReset(v: String) = updateEditor { it.copy(reset = v.filter(Char::isDigit)) }
    fun setEditorTgId(v: String) = updateEditor { it.copy(tgId = v.filter(Char::isDigit)) }
    fun setEditorGroup(v: String) = updateEditor { it.copy(group = v) }
    fun setEditorComment(v: String) = updateEditor { it.copy(comment = v) }
    fun setEditorExpiry(ms: Long) = updateEditor { it.copy(expiryTime = ms) }
    fun setEditorAdTag(v: String) = updateEditor { it.copy(adTag = v.filter { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' }.take(32)) }
    fun setEditorAllowedIps(v: String) = updateEditor { it.copy(allowedIps = v) }
    fun setEditorKeepAlive(v: String) = updateEditor { it.copy(keepAlive = v.filter(Char::isDigit).take(5)) }

    /** Regenerate the MTProto FakeTLS secret, keeping the fronting domain from
     *  the current secret (or cloudflare's if there isn't one yet). Format:
     *  "ee" + 16 random bytes (hex) + domain (hex). */
    fun regenerateSecret() = updateEditor {
        val domain = mtprotoSecretDomain(it.secret).ifBlank { "www.cloudflare.com" }
        it.copy(secret = "ee" + randomHex(16) + domain.encodeToByteArray().toHex())
    }
    fun toggleEditorInbound(id: Int) = updateEditor {
        it.copy(
            selectedInboundIds = if (id in it.selectedInboundIds) it.selectedInboundIds - id
            else it.selectedInboundIds + id,
        )
    }

    fun saveEditor() {
        val e = _state.value.editor ?: return
        if (!e.canSave) return
        val model = e.toClientModel(newSubId = if (e.isNew) randomSubId() else "")
        _state.update { s -> s.editor?.let { s.copy(editor = it.copy(saving = true, error = null)) } ?: s }
        viewModelScope.launch {
            if (e.isNew) {
                repo.addClient(model, e.selectedInboundIds.toList())
                    .onSuccess { finishSave("Client created") }
                    .onFailure { ex -> failSave(ex) }
                return@launch
            }
            // Update by the ORIGINAL email (the lookup key); model.email carries
            // the possibly-renamed value. Then reconcile inbound membership.
            val origEmail = e.source?.email ?: e.email.trim()
            val updated = repo.updateClient(origEmail, model)
            if (updated.isFailure) {
                failSave(updated.exceptionOrNull())
                return@launch
            }
            val original = e.source?.inboundIds?.toSet().orEmpty()
            val added = (e.selectedInboundIds - original).toList()
            val removed = (original - e.selectedInboundIds).toList()
            val membershipError = listOfNotNull(
                added.takeIf { it.isNotEmpty() }?.let { repo.attachClient(model.email, it).exceptionOrNull() },
                removed.takeIf { it.isNotEmpty() }?.let { repo.detachClient(model.email, it).exceptionOrNull() },
            ).firstOrNull()
            // The edit itself is saved; the panel refused only the inbound change.
            finishSave(
                if (membershipError == null) "Client updated"
                else "Client updated, but the inbound change failed: ${membershipError.message}",
            )
        }
    }

    private fun finishSave(message: String) {
        _state.update { it.copy(editor = null, transientMessage = message) }
        load(force = true)
    }

    private fun failSave(ex: Throwable?) {
        _state.update { s -> s.editor?.let { s.copy(editor = it.copy(saving = false, error = ex?.message)) } ?: s }
    }

    fun setSearchQuery(q: String) = _state.update { it.copy(searchQuery = q) }

    // ---- Filters ----------------------------------------------------------

    fun toggleStatusFilter(s: ClientStatus) = _state.update {
        val cur = it.filters.statuses
        it.copy(filters = it.filters.copy(statuses = if (s in cur) cur - s else cur + s))
    }

    fun toggleGroupFilter(g: String) = _state.update {
        val cur = it.filters.groups
        it.copy(filters = it.filters.copy(groups = if (g in cur) cur - g else cur + g))
    }

    fun clearFilters() = _state.update { it.copy(filters = ClientFilters()) }

    // ---- Multi-select / bulk actions --------------------------------------

    fun startSelection(email: String) = _state.update {
        it.copy(selectionMode = true, selectedEmails = setOf(email))
    }

    fun toggleSelected(email: String) = _state.update {
        val next = if (email in it.selectedEmails) it.selectedEmails - email else it.selectedEmails + email
        it.copy(selectedEmails = next, selectionMode = next.isNotEmpty())
    }

    fun selectAllVisible() = _state.update {
        it.copy(selectionMode = true, selectedEmails = it.visibleItems.map { c -> c.email }.toSet())
    }

    fun exitSelection() = _state.update { it.copy(selectionMode = false, selectedEmails = emptySet()) }

    private fun runBulk(verb: String, block: suspend (List<String>) -> Result<Unit>) {
        val emails = _state.value.selectedEmails.toList()
        if (emails.isEmpty() || _state.value.bulkInFlight) return
        _state.update { it.copy(bulkInFlight = true) }
        viewModelScope.launch {
            block(emails)
                .onSuccess {
                    _state.update {
                        it.copy(
                            bulkInFlight = false,
                            selectionMode = false,
                            selectedEmails = emptySet(),
                            transientMessage = "$verb: ${emails.size}",
                        )
                    }
                    load(force = true)
                }
                .onFailure { e ->
                    _state.update { it.copy(bulkInFlight = false, transientMessage = "Bulk action failed: ${e.message}") }
                }
        }
    }

    fun bulkSetEnabled(enable: Boolean) =
        runBulk(if (enable) "Enabled" else "Disabled") { repo.bulkSetClientsEnabled(it, enable) }

    fun toggleClientEnabled(email: String, enable: Boolean) {
        viewModelScope.launch {
            repo.bulkSetClientsEnabled(listOf(email), enable)
                .onSuccess { load(force = true) }
                .onFailure { e -> _state.update { it.copy(transientMessage = "Failed: ${e.message}") } }
        }
    }

    /** Bulk adjust with the panel's report: how many changed and, if any were skipped,
     *  the first reason (e.g. an ad tag on a client without an MTProto inbound). */
    fun bulkAdjust(addDays: Int, addBytes: Long, flow: String, limitHwid: Int? = null, adTag: String = "") {
        val emails = _state.value.selectedEmails.toList()
        if (emails.isEmpty() || _state.value.bulkInFlight) return
        _state.update { it.copy(bulkInFlight = true) }
        viewModelScope.launch {
            repo.bulkAdjustClients(emails, addDays, addBytes, flow, limitHwid, adTag)
                .onSuccess { report ->
                    val skipped = report.skipped
                    _state.update {
                        it.copy(
                            bulkInFlight = false,
                            selectionMode = false,
                            selectedEmails = emptySet(),
                            transientMessage = if (skipped.isEmpty()) "Adjusted: ${report.adjusted}"
                            else "Adjusted: ${report.adjusted}, skipped: ${skipped.size} (${skipped.first().reason})",
                        )
                    }
                    load(force = true)
                }
                .onFailure { e ->
                    _state.update { it.copy(bulkInFlight = false, transientMessage = "Bulk action failed: ${e.message}") }
                }
        }
    }

    fun bulkDelete() = runBulk("Deleted") { repo.bulkDeleteClients(it) }

    fun bulkResetTraffic() = runBulk("Traffic reset") { repo.bulkResetClientsTraffic(it) }

    fun resetClientTraffic(email: String) {
        viewModelScope.launch {
            repo.resetClientTraffic(email)
                .onSuccess {
                    _state.update { it.copy(transientMessage = "Traffic reset: $email") }
                    load(force = true)
                }
                .onFailure { e -> _state.update { it.copy(transientMessage = "Reset failed: ${e.message}") } }
        }
    }

    // ---- Export / import / delete-unbound (panel 3.4.0) -------------------

    fun exportClients() {
        viewModelScope.launch {
            repo.exportClients()
                .onSuccess { json -> _state.update { it.copy(exportJson = json) } }
                .onFailure { e -> _state.update { it.copy(transientMessage = "Export failed: ${e.message}") } }
        }
    }

    fun dismissExport() = _state.update { it.copy(exportJson = null) }

    fun openIpLog(email: String) {
        _state.update { it.copy(ipLogEmail = email, ipLog = emptyList(), ipLogLoading = true) }
        viewModelScope.launch {
            repo.clientIps(email)
                .onSuccess { ips -> _state.update { it.copy(ipLog = ips, ipLogLoading = false) } }
                .onFailure { e -> _state.update { it.copy(ipLogLoading = false, transientMessage = "IP log failed: ${e.message}") } }
        }
    }

    fun clearIpLog() {
        val email = _state.value.ipLogEmail ?: return
        viewModelScope.launch {
            repo.clearClientIps(email)
                .onSuccess { _state.update { it.copy(ipLog = emptyList(), transientMessage = "IP log cleared") } }
                .onFailure { e -> _state.update { it.copy(transientMessage = "Clear failed: ${e.message}") } }
        }
    }

    fun closeIpLog() = _state.update { it.copy(ipLogEmail = null, ipLog = emptyList()) }

    // ---- Subscription devices (HWID, panel v3.7.0) -------------------------

    fun openHwids(email: String) {
        _state.update {
            it.copy(hwidEmail = email, hwids = emptyList(), hwidsLoading = true, hwidsUnsupported = false)
        }
        viewModelScope.launch {
            val r = repo.listClientHwids(email)
            r.onSuccess { list -> _state.update { it.copy(hwids = list, hwidsLoading = false) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            hwidsLoading = false,
                            hwidsUnsupported = r.isUnsupportedByPanel(),
                            transientMessage = if (r.isUnsupportedByPanel()) null else "Devices failed: ${e.message}",
                        )
                    }
                }
        }
    }

    fun removeHwid(id: Int) {
        val email = _state.value.hwidEmail ?: return
        viewModelScope.launch {
            repo.deleteClientHwid(email, id)
                .onSuccess {
                    _state.update { it.copy(hwids = it.hwids.filterNot { d -> d.id == id }, transientMessage = "Device removed") }
                }
                .onFailure { e -> _state.update { it.copy(transientMessage = "Remove failed: ${e.message}") } }
        }
    }

    fun clearHwids() {
        val email = _state.value.hwidEmail ?: return
        viewModelScope.launch {
            repo.clearClientHwids(email)
                .onSuccess { _state.update { it.copy(hwids = emptyList(), transientMessage = "Devices removed") } }
                .onFailure { e -> _state.update { it.copy(transientMessage = "Remove failed: ${e.message}") } }
        }
    }

    fun closeHwids() = _state.update { it.copy(hwidEmail = null, hwids = emptyList()) }

    fun importClients(jsonText: String) {
        viewModelScope.launch {
            repo.importClients(jsonText)
                .onSuccess { _state.update { it.copy(transientMessage = "Clients imported") }; load(force = true) }
                .onFailure { e -> _state.update { it.copy(transientMessage = "Import failed: ${e.message}") } }
        }
    }

    fun deleteOrphanClients() {
        viewModelScope.launch {
            repo.deleteOrphanClients()
                .onSuccess { _state.update { it.copy(transientMessage = "Unbound clients deleted") }; load(force = true) }
                .onFailure { e -> _state.update { it.copy(transientMessage = "Delete failed: ${e.message}") } }
        }
    }

    fun dismissMessage() = _state.update { it.copy(transientMessage = null) }
}
