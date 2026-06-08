package net.yukh.xui.ui.screen.inbounds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import net.yukh.xui.data.api.dto.InboundModel
import net.yukh.xui.data.api.dto.InboundSlim
import net.yukh.xui.data.api.dto.InboundTemplates
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
    val editor: InboundEditorState? = null,
)

/** Inbound create/edit form. Scalars are structured; settings/streamSettings/
 *  sniffing are edited as raw JSON text. */
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
    val settingsText: String = "",
    val streamText: String = "",
    val sniffingText: String = "",
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean
        get() = !saving && !loading && (port.toIntOrNull() ?: 0) in 1..65535
}

@HiltViewModel
class InboundsViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(InboundsUiState())
    val state: StateFlow<InboundsUiState> = _state.asStateFlow()

    fun load(force: Boolean = false) {
        val current = _state.value
        if (current.refreshing) return
        val firstLoad = current.items.isEmpty() && current.error == null
        _state.update {
            it.copy(
                refreshing = true,
                loading = it.loading || firstLoad,
                error = if (force) null else it.error,
            )
        }
        viewModelScope.launch {
            repo.listInbounds()
                .onSuccess { list ->
                    _state.update {
                        it.copy(
                            items = list.sortedBy { ib -> ib.id },
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

    /**
     * Optimistic flip: update local state first, call the API, revert on failure.
     * The user gets immediate feedback for the common (success) case and a clear
     * snackbar message for the rare (failure) case.
     */
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
                        it.copy(
                            items = previous,
                            toggleInFlight = it.toggleInFlight - id,
                            transientMessage = "Toggle failed: ${e.message}",
                        )
                    }
                }
        }
    }

    // ---- Editor -----------------------------------------------------------

    fun openCreateEditor() {
        val proto = "vless"
        _state.update {
            it.copy(
                editor = InboundEditorState(
                    isNew = true,
                    protocol = proto,
                    settingsText = InboundTemplates.settings(proto),
                    streamText = InboundTemplates.streamSettings(proto),
                    sniffingText = InboundTemplates.sniffing(),
                ),
            )
        }
    }

    fun openEditEditor(id: Int) {
        _state.update { it.copy(editor = InboundEditorState(isNew = false, id = id, loading = true)) }
        viewModelScope.launch {
            repo.getInbound(id)
                .onSuccess { ib ->
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
                                settingsText = ib.settings.pretty(),
                                streamText = ib.streamSettings.pretty(),
                                sniffingText = ib.sniffing.pretty(),
                            ),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { s ->
                        s.copy(editor = s.editor?.copy(loading = false, error = e.message))
                    }
                }
        }
    }

    fun closeEditor() = _state.update { it.copy(editor = null) }

    private fun updateEditor(t: (InboundEditorState) -> InboundEditorState) {
        _state.update { s -> s.editor?.let { s.copy(editor = t(it).copy(error = null)) } ?: s }
    }

    fun setEditorRemark(v: String) = updateEditor { it.copy(remark = v) }
    fun setEditorEnable(v: Boolean) = updateEditor { it.copy(enable = v) }
    fun setEditorListen(v: String) = updateEditor { it.copy(listen = v) }
    fun setEditorPort(v: String) = updateEditor { it.copy(port = v.filter(Char::isDigit)) }
    fun setEditorTotalGb(v: String) = updateEditor { it.copy(totalGb = v.filter { c -> c.isDigit() || c == '.' }) }
    fun setEditorExpiry(ms: Long) = updateEditor { it.copy(expiryTime = ms) }
    fun setEditorTrafficReset(v: String) = updateEditor { it.copy(trafficReset = v) }
    fun setEditorSettings(v: String) = updateEditor { it.copy(settingsText = v) }
    fun setEditorStream(v: String) = updateEditor { it.copy(streamText = v) }
    fun setEditorSniffing(v: String) = updateEditor { it.copy(sniffingText = v) }

    /** On create, switching protocol swaps in that protocol's default JSON. */
    fun setEditorProtocol(v: String) = updateEditor {
        if (it.isNew) {
            it.copy(
                protocol = v,
                settingsText = InboundTemplates.settings(v),
                streamText = InboundTemplates.streamSettings(v),
            )
        } else {
            it.copy(protocol = v)
        }
    }

    fun saveEditor() {
        val e = _state.value.editor ?: return
        if (!e.canSave) return

        val settings = parseJsonOrNull(e.settingsText)
            ?: return failEditor("Settings is not valid JSON")
        val stream = parseJsonOrNull(e.streamText)
            ?: return failEditor("Stream settings is not valid JSON")
        val sniffing = parseJsonOrNull(e.sniffingText)
            ?: return failEditor("Sniffing is not valid JSON")

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
            streamSettings = stream,
            sniffing = sniffing,
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
                        it.copy(
                            editor = null,
                            items = it.items.filterNot { ib -> ib.id == id },
                            transientMessage = "Inbound deleted",
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(transientMessage = "Delete failed: ${e.message}") } }
        }
    }

    private fun failEditor(msg: String) {
        _state.update { s -> s.editor?.let { s.copy(editor = it.copy(error = msg)) } ?: s }
    }

    private fun parseJsonOrNull(text: String): JsonElement? = try {
        prettyJson.parseToJsonElement(text.ifBlank { "{}" })
    } catch (_: Exception) {
        null
    }

    private fun JsonElement.pretty(): String = try {
        prettyJson.encodeToString(JsonElement.serializer(), this)
    } catch (_: Exception) {
        toString()
    }

    fun dismissMessage() = _state.update { it.copy(transientMessage = null) }
}
