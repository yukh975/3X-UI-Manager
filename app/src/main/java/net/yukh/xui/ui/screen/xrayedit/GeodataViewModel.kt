package net.yukh.xui.ui.screen.xrayedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.yukh.xui.data.json.asObject
import net.yukh.xui.data.json.string
import net.yukh.xui.data.repo.PanelRepository
import net.yukh.xui.data.repo.isUnsupportedByPanel

/** One geo database Xray keeps up to date: where to fetch it and what to call it. */
data class GeoAsset(val url: String = "", val file: String = "")

data class GeodataUiState(
    val loading: Boolean = true,
    val available: Boolean = false,
    /** The panel is older than this feature (its endpoint 404s). */
    val unsupported: Boolean = false,
    val assets: List<GeoAsset> = emptyList(),
    val cron: String = DEFAULT_CRON,
    /** Optional outbound tag to download through; empty = Xray's default route. */
    val outbound: String = "",
    val outboundTags: List<String> = emptyList(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val savedMessage: String? = null,
) {
    /** Xray only accepts HTTPS sources, plain file names, and a 5-field cron. */
    val invalidUrl: Boolean get() = assets.any { !it.url.trim().startsWith("https://") }
    val invalidFile: Boolean get() = assets.any { a ->
        val f = a.file.trim()
        f.isEmpty() || f.contains('/') || f.contains('\\') || f.contains("..")
    }
    val invalidCron: Boolean get() = cron.trim().split(Regex("\\s+")).size != 5
    val canSave: Boolean get() = !saving && dirty &&
        (assets.isEmpty() || (!invalidUrl && !invalidFile && !invalidCron))

    companion object {
        const val DEFAULT_CRON = "0 4 * * *"
    }
}

/**
 * Xray's own geodata auto-update (panel 3.7.0): Xray re-downloads the listed
 * files on a schedule and hot-reloads them, so this is the scheduled counterpart
 * of the manual "update geo files" button on the dashboard.
 *
 * The settings live in the Xray config template under `geodata`, so the whole
 * config is round-tripped and only that key is touched.
 */
@HiltViewModel
class GeodataViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GeodataUiState())
    val state: StateFlow<GeodataUiState> = _state.asStateFlow()

    private var config: JsonObject = JsonObject(emptyMap())
    private var testUrl: String = ""

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.loadXrayConfig()
                .onSuccess { loaded ->
                    config = loaded.config
                    testUrl = loaded.testUrl
                    val geodata = loaded.config["geodata"].asObject()
                    val assets = (geodata["assets"] as? JsonArray).orEmpty().map {
                        val o = it.asObject()
                        GeoAsset(url = o.string("url"), file = o.string("file"))
                    }
                    val tags = (loaded.config["outbounds"] as? JsonArray).orEmpty()
                        .map { it.asObject().string("tag") }
                        .filter { it.isNotBlank() } + loaded.subscriptionOutboundTags
                    _state.update {
                        it.copy(
                            loading = false,
                            available = true,
                            assets = assets,
                            cron = geodata.string("cron").ifBlank { GeodataUiState.DEFAULT_CRON },
                            outbound = geodata.string("outbound"),
                            outboundTags = tags.distinct(),
                            dirty = false,
                            error = null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            available = false,
                            unsupported = e.isUnsupportedByPanel(),
                            error = if (e.isUnsupportedByPanel()) null else e.message ?: "Xray config unavailable",
                        )
                    }
                }
        }
    }

    fun setCron(v: String) = _state.update { it.copy(cron = v, dirty = true) }
    fun setOutbound(v: String) = _state.update { it.copy(outbound = v, dirty = true) }

    fun addAsset() = _state.update { it.copy(assets = it.assets + GeoAsset(), dirty = true) }

    fun updateAsset(index: Int, asset: GeoAsset) = _state.update { s ->
        s.copy(assets = s.assets.mapIndexed { i, a -> if (i == index) asset else a }, dirty = true)
    }

    fun removeAsset(index: Int) = _state.update { s ->
        s.copy(assets = s.assets.filterIndexed { i, _ -> i != index }, dirty = true)
    }

    fun save() {
        val st = _state.value
        if (!st.canSave) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val next = config.toMutableMap()
            val assets = st.assets.filter { it.url.isNotBlank() && it.file.isNotBlank() }
            if (assets.isEmpty()) {
                // No files means no schedule to keep — drop the key entirely, the
                // way the panel does, instead of leaving an empty block behind.
                next.remove("geodata")
            } else {
                val block = mutableMapOf<String, JsonElement>(
                    "assets" to JsonArray(
                        assets.map {
                            JsonObject(
                                mapOf(
                                    "url" to JsonPrimitive(it.url.trim()),
                                    "file" to JsonPrimitive(it.file.trim()),
                                ),
                            )
                        },
                    ),
                )
                st.cron.trim().takeIf { it.isNotEmpty() }?.let { block["cron"] = JsonPrimitive(it) }
                st.outbound.trim().takeIf { it.isNotEmpty() }?.let { block["outbound"] = JsonPrimitive(it) }
                next["geodata"] = JsonObject(block)
            }
            val updated = JsonObject(next)
            repo.saveXrayConfig(updated, testUrl)
                .onSuccess {
                    config = updated
                    _state.update { it.copy(saving = false, dirty = false, savedMessage = "Saved — restart Xray to apply") }
                }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.message ?: "Save failed") } }
        }
    }

    fun dismissMessage() = _state.update { it.copy(savedMessage = null, error = null) }
}

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
