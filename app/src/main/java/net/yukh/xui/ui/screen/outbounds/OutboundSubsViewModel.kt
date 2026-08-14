package net.yukh.xui.ui.screen.outbounds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import net.yukh.xui.data.api.dto.OutboundSubscription
import net.yukh.xui.data.json.string
import net.yukh.xui.data.repo.PanelRepository
import net.yukh.xui.data.repo.isUnsupportedByPanel

/** One line of a subscription preview: the tag the panel would assign and the
 *  protocol behind it. */
data class PreviewedOutbound(val tag: String, val protocol: String)

data class OutboundSubsUiState(
    val loading: Boolean = true,
    /** The panel is older than the outbound-subscription feature (404). */
    val unsupported: Boolean = false,
    val items: List<OutboundSubscription> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    /** Non-null while the add/edit sheet is open. */
    val editor: OutboundSubscription? = null,
    val previewing: Boolean = false,
    val preview: List<PreviewedOutbound>? = null,
)

/**
 * The panel's "Subscriptions" panel on the Outbounds page: remote lists of
 * outbounds the panel fetches on a timer and merges into the Xray config.
 *
 * Every mutation makes the panel flag Xray for a restart, so the screen tells
 * the user and offers to do it.
 */
@HiltViewModel
class OutboundSubsViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OutboundSubsUiState())
    val state: StateFlow<OutboundSubsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val r = repo.listOutboundSubs()
            r.onSuccess { list ->
                _state.update { it.copy(loading = false, unsupported = false, items = list.sortedBy { s -> s.priority }) }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        loading = false,
                        unsupported = r.isUnsupportedByPanel(),
                        error = if (r.isUnsupportedByPanel()) null else e.message,
                    )
                }
            }
        }
    }

    // ---- Editor ----------------------------------------------------------

    fun openNew() = _state.update { it.copy(editor = OutboundSubscription(), preview = null) }

    fun openEdit(sub: OutboundSubscription) = _state.update { it.copy(editor = sub, preview = null) }

    fun closeEditor() = _state.update { it.copy(editor = null, preview = null, previewing = false) }

    fun editDraft(transform: (OutboundSubscription) -> OutboundSubscription) =
        _state.update { s -> s.copy(editor = s.editor?.let(transform)) }

    /** Fetch and parse the URL without saving, so the user sees what it yields. */
    fun preview() {
        val draft = _state.value.editor ?: return
        if (draft.url.isBlank() || _state.value.previewing) return
        _state.update { it.copy(previewing = true, preview = null, error = null) }
        viewModelScope.launch {
            repo.previewOutboundSub(draft.url, draft.allowPrivate, draft.allowInsecure)
                .onSuccess { list ->
                    _state.update { it.copy(previewing = false, preview = list.map(::toPreview)) }
                }
                .onFailure { e -> _state.update { it.copy(previewing = false, error = e.message) } }
        }
    }

    fun save() {
        val draft = _state.value.editor ?: return
        if (draft.url.isBlank() || _state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val r = if (draft.id == 0) repo.createOutboundSub(draft) else repo.updateOutboundSub(draft)
            r.onSuccess {
                _state.update { it.copy(busy = false, editor = null, preview = null, message = RESTART_HINT) }
                load()
            }.onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    // ---- Row actions -----------------------------------------------------

    fun setEnabled(sub: OutboundSubscription, enabled: Boolean) = mutate {
        repo.updateOutboundSub(sub.copy(enabled = enabled))
    }

    fun refresh(id: Int) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            repo.refreshOutboundSub(id)
                .onSuccess { obs ->
                    _state.update { it.copy(busy = false, message = "$RESTART_HINT (${obs.size})") }
                    load()
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    fun refreshAll() {
        val ids = _state.value.items.filter { it.enabled }.map { it.id }
        if (ids.isEmpty() || _state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            var failed = 0
            ids.forEach { id -> repo.refreshOutboundSub(id).onFailure { failed++ } }
            _state.update {
                it.copy(busy = false, message = if (failed == 0) RESTART_HINT else "$failed failed")
            }
            load()
        }
    }

    fun move(id: Int, up: Boolean) = mutate { repo.moveOutboundSub(id, up) }

    fun delete(id: Int) = mutate { repo.deleteOutboundSub(id) }

    fun restartXray() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            repo.restartXray()
                .onSuccess { _state.update { it.copy(busy = false, message = "Xray is restarting…") } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    private fun mutate(block: suspend () -> Result<Unit>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            block()
                .onSuccess { _state.update { it.copy(busy = false, message = RESTART_HINT) }; load() }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }
    fun dismissError() = _state.update { it.copy(error = null) }

    private companion object {
        /** Every change only reaches Xray on its next (re)start — the panel just
         *  flags it, so say so rather than letting the user wonder. */
        const val RESTART_HINT = "Saved — restart Xray to apply"
    }
}

private fun toPreview(o: JsonObject) = PreviewedOutbound(
    tag = o.string("tag"),
    protocol = o.string("protocol"),
)
