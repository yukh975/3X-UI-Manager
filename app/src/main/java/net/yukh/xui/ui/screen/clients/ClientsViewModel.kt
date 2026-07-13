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
import net.yukh.xui.data.api.dto.ClientIpInfo
import net.yukh.xui.data.api.dto.ClientModel
import net.yukh.xui.data.api.dto.InboundSlim
import net.yukh.xui.data.repo.PanelRepository

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
    val subUrlChecked: Boolean = false,
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
    val tgId: String = "",
    val group: String = "",
    val comment: String = "",
    val secret: String = "",
    val adTag: String = "",
    val allowedIps: String = "",
    val selectedInboundIds: Set<Int> = emptySet(),
    val availableInbounds: List<InboundSlim> = emptyList(),
    val availableGroups: List<String> = emptyList(),
    val inboundsLoading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean
        get() = !saving && email.isNotBlank() && (!isNew || selectedInboundIds.isNotEmpty())

    /** A selected inbound is MTProto — show the secret / ad-tag fields. */
    val isMtproto: Boolean
        get() = availableInbounds.any { it.id in selectedInboundIds && it.protocol == "mtproto" }

    /** A selected inbound is WireGuard — show the peer's allowed IPs. */
    val isWireguard: Boolean
        get() = availableInbounds.any { it.id in selectedInboundIds && it.protocol == "wireguard" }
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
                subUrlChecked = false,
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
            _state.update { it.copy(selectedSubUrl = subUrl, subUrlChecked = true) }
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
                subUrlChecked = false,
            )
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
                    totalGb = gb,
                    expiryTime = client.expiryTime,
                    reset = client.reset.toString(),
                    tgId = if (client.tgId != 0L) client.tgId.toString() else "",
                    group = client.group,
                    comment = client.comment,
                    secret = client.secret,
                    adTag = client.adTag,
                    allowedIps = client.allowedIPs,
                    selectedInboundIds = client.inboundIds.toSet(),
                    availableGroups = existingGroups(),
                    inboundsLoading = true,
                ),
            )
        }
        loadInboundsForEditor()
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

    fun closeEditor() = _state.update { it.copy(editor = null) }

    private fun updateEditor(transform: (ClientEditorState) -> ClientEditorState) {
        _state.update { s -> s.editor?.let { s.copy(editor = transform(it).copy(error = null)) } ?: s }
    }

    fun setEditorEmail(v: String) = updateEditor { it.copy(email = v) }
    fun setEditorEnable(v: Boolean) = updateEditor { it.copy(enable = v) }
    fun setEditorLimitIp(v: String) = updateEditor { it.copy(limitIp = v.filter(Char::isDigit)) }
    fun setEditorTotalGb(v: String) = updateEditor { it.copy(totalGb = v.filter { c -> c.isDigit() || c == '.' }) }
    fun setEditorReset(v: String) = updateEditor { it.copy(reset = v.filter(Char::isDigit)) }
    fun setEditorTgId(v: String) = updateEditor { it.copy(tgId = v.filter(Char::isDigit)) }
    fun setEditorGroup(v: String) = updateEditor { it.copy(group = v) }
    fun setEditorComment(v: String) = updateEditor { it.copy(comment = v) }
    fun setEditorExpiry(ms: Long) = updateEditor { it.copy(expiryTime = ms) }
    fun setEditorAdTag(v: String) = updateEditor { it.copy(adTag = v.filter { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' }.take(32)) }
    fun setEditorAllowedIps(v: String) = updateEditor { it.copy(allowedIps = v) }

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
        val gbBytes = (e.totalGb.toDoubleOrNull() ?: 0.0).let { (it * BYTES_PER_GB).toLong() }
        val base = e.source?.toModel() ?: ClientModel()
        val model = base.copy(
            email = e.email.trim(),
            enable = e.enable,
            limitIp = e.limitIp.toIntOrNull() ?: 0,
            totalGB = gbBytes,
            expiryTime = e.expiryTime,
            reset = e.reset.toIntOrNull() ?: 0,
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
        )
        _state.update { s -> s.editor?.let { s.copy(editor = it.copy(saving = true, error = null)) } ?: s }
        viewModelScope.launch {
            val result = if (e.isNew) {
                repo.addClient(model, e.selectedInboundIds.toList())
            } else {
                // Update by the ORIGINAL email (the lookup key); model.email carries
                // the possibly-renamed value. Then reconcile inbound membership.
                val origEmail = e.source?.email ?: e.email.trim()
                val newEmail = model.email
                repo.updateClient(origEmail, model).also { r ->
                    if (r.isSuccess) {
                        val original = e.source?.inboundIds?.toSet().orEmpty()
                        val selected = e.selectedInboundIds
                        val added = (selected - original).toList()
                        val removed = (original - selected).toList()
                        if (added.isNotEmpty()) repo.attachClient(newEmail, added)
                        if (removed.isNotEmpty()) repo.detachClient(newEmail, removed)
                    }
                }
            }
            result
                .onSuccess {
                    _state.update {
                        it.copy(
                            editor = null,
                            transientMessage = if (e.isNew) "Client created" else "Client updated",
                        )
                    }
                    load(force = true)
                }
                .onFailure { ex ->
                    _state.update { s -> s.editor?.let { s.copy(editor = it.copy(saving = false, error = ex.message)) } ?: s }
                }
        }
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

    fun bulkAdjust(addDays: Int, addBytes: Long, flow: String) =
        runBulk("Adjusted") { repo.bulkAdjustClients(it, addDays, addBytes, flow) }

    fun bulkDelete() = runBulk("Deleted") { repo.bulkDeleteClients(it) }

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
