package net.yukh.xui.ui.screen.inbounds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.isActive
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import net.yukh.xui.data.api.dto.InboundModel
import net.yukh.xui.data.api.dto.InboundSlim
import net.yukh.xui.data.api.dto.InboundTemplates
import net.yukh.xui.data.api.dto.VlessEncAuth
import net.yukh.xui.data.json.asObject
import net.yukh.xui.data.json.bool
import net.yukh.xui.data.json.child
import net.yukh.xui.data.json.parseCsv
import net.yukh.xui.data.json.put
import net.yukh.xui.data.json.putBool
import net.yukh.xui.data.json.putString
import net.yukh.xui.data.json.putStrings
import net.yukh.xui.data.json.string
import net.yukh.xui.data.json.strings
import net.yukh.xui.data.repo.PanelRepository

private const val BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0
private val prettyJson = Json { prettyPrint = true; isLenient = true }

data class InboundsUiState(
    val items: List<InboundSlim> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val toggleInFlight: Set<Int> = emptySet(),
    val transientMessage: String? = null,
    // Live up/down speed (bytes/s) per inbound id, from the delta between polls.
    val speedByInbound: Map<Int, Pair<Long, Long>> = emptyMap(),
    val editor: InboundEditorState? = null,
)

/**
 * Inbound create/edit form. Scalars are plain fields; transport (streamSettings)
 * and sniffing are edited through structured controls that mutate a live
 * JsonObject (so unmodeled keys are preserved). The protocol `settings` minus
 * its clients array stays as advanced raw JSON; clients are managed on the
 * Clients tab and preserved via [originalClients].
 */
data class InboundEditorState(
    val isNew: Boolean,
    val id: Int = 0,
    val loading: Boolean = false,
    val remark: String = "",
    val enable: Boolean = true,
    val listen: String = "",
    val port: String = "",
    val protocol: String = "vless",
    val totalGb: String = "0",
    val expiryTime: Long = 0,
    val trafficReset: String = "never",
    val stream: JsonObject = JsonObject(emptyMap()),
    val sniffing: JsonObject = JsonObject(emptyMap()),
    val settingsText: String = "",
    val originalClients: JsonElement? = null,
    val saving: Boolean = false,
    val error: String? = null,
    // VLESS encryption key generator (lazy-loaded when the dialog opens)
    val vlessKeys: List<VlessEncAuth>? = null,
    val vlessKeysLoading: Boolean = false,
    val vlessKeysError: String? = null,
) {
    val canSave: Boolean
        get() = !saving && !loading && (port.toIntOrNull() ?: 0) in 1..65535

    val network: String get() = stream.string("network").ifBlank { "tcp" }
    val security: String get() = stream.string("security").ifBlank { "none" }
}

@HiltViewModel
class InboundsViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(InboundsUiState())
    val state: StateFlow<InboundsUiState> = _state.asStateFlow()

    init {
        // Reload when the user switches to another panel.
        viewModelScope.launch {
            repo.activeProfileId.drop(1).collect { load(force = true) }
        }
    }

    // Previous (up,down) totals + timestamp, to derive live speed between polls.
    private var prevTotals: Map<Int, Pair<Long, Long>> = emptyMap()
    private var prevTime: Long = 0L
    private var pollJob: kotlinx.coroutines.Job? = null

    /** Live up/down speed per inbound while the screen is on-screen (panel v3.4.0). */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(3_000L)
                if (_state.value.editor == null && !_state.value.refreshing) load()
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel(); pollJob = null
    }

    fun load(force: Boolean = false) {
        val current = _state.value
        if (current.refreshing) return
        val firstLoad = current.items.isEmpty() && current.error == null
        _state.update {
            it.copy(refreshing = true, loading = it.loading || firstLoad, error = if (force) null else it.error)
        }
        viewModelScope.launch {
            repo.listInbounds()
                .onSuccess { list ->
                    val sorted = list.sortedBy { ib -> ib.id }
                    val now = System.currentTimeMillis()
                    val dt = (now - prevTime) / 1000.0
                    val speeds = if (prevTime > 0 && dt > 0.5) {
                        sorted.mapNotNull { ib ->
                            val prev = prevTotals[ib.id] ?: return@mapNotNull null
                            val up = (((ib.up - prev.first).coerceAtLeast(0)) / dt).toLong()
                            val down = (((ib.down - prev.second).coerceAtLeast(0)) / dt).toLong()
                            ib.id to (up to down)
                        }.toMap()
                    } else emptyMap()
                    prevTotals = sorted.associate { it.id to (it.up to it.down) }
                    prevTime = now
                    _state.update {
                        it.copy(items = sorted, speedByInbound = speeds, loading = false, refreshing = false, error = null)
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, refreshing = false, error = e.message) }
                }
        }
    }

    /** Optimistic enable/disable; revert + toast on failure. */
    fun toggle(id: Int, target: Boolean) {
        if (id in _state.value.toggleInFlight) return
        val previous = _state.value.items
        _state.update {
            it.copy(
                items = it.items.map { ib -> if (ib.id == id) ib.copy(enable = target) else ib },
                toggleInFlight = it.toggleInFlight + id,
            )
        }
        viewModelScope.launch {
            repo.setInboundEnable(id, target)
                .onSuccess {
                    _state.update {
                        it.copy(
                            toggleInFlight = it.toggleInFlight - id,
                            transientMessage = if (target) "Inbound enabled" else "Inbound disabled",
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(items = previous, toggleInFlight = it.toggleInFlight - id, transientMessage = "Toggle failed: ${e.message}")
                    }
                }
        }
    }

    // ---- Editor: open/close -----------------------------------------------

    fun openCreateEditor() {
        val proto = "vless"
        _state.update {
            it.copy(
                editor = InboundEditorState(
                    isNew = true,
                    protocol = proto,
                    stream = JsonObject(emptyMap()).putString("network", "tcp").putString("security", "none"),
                    sniffing = defaultSniffing(),
                    settingsText = settingsWithoutClients(parse(InboundTemplates.settings(proto))),
                    originalClients = null,
                ),
            )
        }
    }

    fun openEditEditor(id: Int) {
        _state.update { it.copy(editor = InboundEditorState(isNew = false, id = id, loading = true)) }
        viewModelScope.launch {
            repo.getInbound(id)
                .onSuccess { ib ->
                    val settingsObj = ib.settings.asObject()
                    val gb = if (ib.total > 0) {
                        (ib.total / BYTES_PER_GB).let { v -> if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(v) }
                    } else "0"
                    _state.update { s ->
                        s.copy(
                            editor = InboundEditorState(
                                isNew = false,
                                id = ib.id,
                                loading = false,
                                remark = ib.remark,
                                enable = ib.enable,
                                listen = ib.listen,
                                port = ib.port.toString(),
                                protocol = ib.protocol,
                                totalGb = gb,
                                expiryTime = ib.expiryTime,
                                trafficReset = ib.trafficReset.ifBlank { "never" },
                                stream = ib.streamSettings.asObject(),
                                sniffing = ib.sniffing.asObject(),
                                settingsText = settingsWithoutClients(settingsObj),
                                originalClients = settingsObj["clients"],
                            ),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { s -> s.copy(editor = s.editor?.copy(loading = false, error = e.message)) }
                }
        }
    }

    fun closeEditor() = _state.update { it.copy(editor = null) }

    // ---- Editor: scalar setters -------------------------------------------

    private fun edit(t: (InboundEditorState) -> InboundEditorState) {
        _state.update { s -> s.editor?.let { s.copy(editor = t(it).copy(error = null)) } ?: s }
    }

    fun setEditorRemark(v: String) = edit { it.copy(remark = v) }
    fun setEditorEnable(v: Boolean) = edit { it.copy(enable = v) }
    fun setEditorListen(v: String) = edit { it.copy(listen = v) }
    fun setEditorPort(v: String) = edit { it.copy(port = v.filter(Char::isDigit)) }
    fun setEditorTotalGb(v: String) = edit { it.copy(totalGb = v.filter { c -> c.isDigit() || c == '.' }) }
    fun setEditorExpiry(ms: Long) = edit { it.copy(expiryTime = ms) }
    fun setEditorTrafficReset(v: String) = edit { it.copy(trafficReset = v) }
    fun setEditorSettings(v: String) = edit { it.copy(settingsText = v) }

    /** Replace the whole streamSettings object (raw-JSON advanced editor) — covers
     *  TLS/REALITY/sockopt/XHTTP/FinalMask fields not exposed as structured fields. */
    fun setEditorStreamRaw(stream: JsonObject) = edit { it.copy(stream = stream) }

    /** Fetch VLESS-encryption key options for the generator dialog. */
    fun loadVlessKeys() {
        edit { it.copy(vlessKeysLoading = true, vlessKeysError = null) }
        viewModelScope.launch {
            repo.getNewVlessEnc()
                .onSuccess { keys -> edit { it.copy(vlessKeys = keys, vlessKeysLoading = false) } }
                .onFailure { e -> edit { it.copy(vlessKeysLoading = false, vlessKeysError = e.message ?: "Failed") } }
        }
    }

    fun clearVlessKeys() = edit { it.copy(vlessKeys = null, vlessKeysError = null) }

    fun setEditorProtocol(v: String) = edit {
        if (it.isNew) it.copy(protocol = v, settingsText = settingsWithoutClients(parse(InboundTemplates.settings(v))))
        else it.copy(protocol = v)
    }

    // ---- Editor: structured transport / security --------------------------

    private fun editStream(t: (JsonObject) -> JsonObject) = edit { it.copy(stream = t(it.stream)) }
    private fun editSniff(t: (JsonObject) -> JsonObject) = edit { it.copy(sniffing = t(it.sniffing)) }

    fun setNetwork(v: String) = editStream { it.putString("network", v) }
    fun setSecurity(v: String) = editStream { it.putString("security", v) }

    fun setWsPath(v: String) = editStream { it.put("wsSettings", it.child("wsSettings").putString("path", v)) }
    fun setWsHost(v: String) = editStream { it.put("wsSettings", it.child("wsSettings").putString("host", v)) }
    fun setGrpcService(v: String) = editStream { it.put("grpcSettings", it.child("grpcSettings").putString("serviceName", v)) }
    fun setHttpPath(v: String) = editStream { it.put("httpupgradeSettings", it.child("httpupgradeSettings").putString("path", v)) }
    fun setHttpHost(v: String) = editStream { it.put("httpupgradeSettings", it.child("httpupgradeSettings").putString("host", v)) }

    fun setTlsServerName(v: String) = editStream { it.put("tlsSettings", it.child("tlsSettings").putString("serverName", v)) }

    fun setRealityDest(v: String) = editReality { it.putString("dest", v) }
    fun setRealityServerNames(v: String) = editReality { it.putStrings("serverNames", parseCsv(v)) }
    fun setRealityShortIds(v: String) = editReality { it.putStrings("shortIds", parseCsv(v)) }
    fun setRealityPrivateKey(v: String) = editReality { it.putString("privateKey", v) }
    fun setRealityPublicKey(v: String) = editReality { it.put("settings", it.child("settings").putString("publicKey", v)) }
    fun setRealityFingerprint(v: String) = editReality { it.put("settings", it.child("settings").putString("fingerprint", v)) }

    private fun editReality(t: (JsonObject) -> JsonObject) =
        editStream { it.put("realitySettings", t(it.child("realitySettings"))) }

    // ---- Editor: sniffing -------------------------------------------------

    fun setSniffEnabled(v: Boolean) = editSniff { it.putBool("enabled", v) }
    fun toggleDestOverride(item: String) = editSniff {
        val cur = it.strings("destOverride")
        it.putStrings("destOverride", if (item in cur) cur - item else cur + item)
    }

    // ---- Editor: save / delete --------------------------------------------

    fun saveEditor() {
        val e = _state.value.editor ?: return
        if (!e.canSave) return

        val settingsBase = parse(e.settingsText.ifBlank { "{}" })
            ?: return failEditor("Settings is not valid JSON")
        // Re-attach the clients array we hid from the editor (create → empty).
        val settings = settingsBase.put("clients", e.originalClients ?: JsonArray(emptyList()))

        val gbBytes = (e.totalGb.toDoubleOrNull() ?: 0.0).let { (it * BYTES_PER_GB).toLong() }
        val model = InboundModel(
            id = e.id,
            remark = e.remark.trim(),
            enable = e.enable,
            listen = e.listen.trim(),
            port = e.port.toIntOrNull() ?: 0,
            protocol = e.protocol,
            expiryTime = e.expiryTime,
            total = gbBytes,
            trafficReset = e.trafficReset,
            settings = settings,
            streamSettings = e.stream,
            sniffing = e.sniffing,
        )
        _state.update { s -> s.editor?.let { s.copy(editor = it.copy(saving = true, error = null)) } ?: s }
        viewModelScope.launch {
            val result = if (e.isNew) repo.addInbound(model) else repo.updateInbound(e.id, model)
            result
                .onSuccess {
                    _state.update {
                        it.copy(editor = null, transientMessage = if (e.isNew) "Inbound created" else "Inbound updated")
                    }
                    load(force = true)
                }
                .onFailure { ex ->
                    _state.update { s -> s.editor?.let { s.copy(editor = it.copy(saving = false, error = ex.message)) } ?: s }
                }
        }
    }

    fun deleteInbound(id: Int) {
        viewModelScope.launch {
            repo.deleteInbound(id)
                .onSuccess {
                    _state.update {
                        it.copy(editor = null, items = it.items.filterNot { ib -> ib.id == id }, transientMessage = "Inbound deleted")
                    }
                }
                .onFailure { e -> _state.update { it.copy(transientMessage = "Delete failed: ${e.message}") } }
        }
    }

    // ---- Helpers ----------------------------------------------------------

    private fun failEditor(msg: String) {
        _state.update { s -> s.editor?.let { s.copy(editor = it.copy(error = msg)) } ?: s }
    }

    private fun parse(text: String): JsonObject? = try {
        prettyJson.parseToJsonElement(text.ifBlank { "{}" }) as? JsonObject
    } catch (_: Exception) {
        null
    }

    private fun settingsWithoutClients(settings: JsonObject?): String {
        val obj = (settings ?: JsonObject(emptyMap())).put("clients", null)
        return prettyJson.encodeToString(JsonObject.serializer(), obj)
    }

    private fun defaultSniffing(): JsonObject =
        JsonObject(emptyMap())
            .putBool("enabled", false)
            .putStrings("destOverride", listOf("http", "tls", "quic"))

    fun dismissMessage() = _state.update { it.copy(transientMessage = null) }
}
