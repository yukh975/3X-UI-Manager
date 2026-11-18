package net.yukh.xui.ui.screen.paneladmin

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
import net.yukh.xui.data.api.dto.ApiToken
import net.yukh.xui.data.api.dto.ApiTokenScope
import net.yukh.xui.data.json.int
import net.yukh.xui.data.json.putInt
import net.yukh.xui.data.json.putString
import net.yukh.xui.data.json.string
import net.yukh.xui.data.repo.PanelRepository
import net.yukh.xui.data.repo.isUnsupportedByPanel

internal data class PanelAdminUiState(
    val tokensLoading: Boolean = true,
    /** The panel predates API tokens (the endpoint 404s). */
    val tokensUnsupported: Boolean = false,
    val tokens: List<ApiToken> = emptyList(),
    val busy: Boolean = false,
    /** A freshly created token — its plaintext value is shown once for copying. */
    val newToken: ApiToken? = null,
    val message: String? = null,
    val error: String? = null,
    // Panel settings (round-tripped through the full AllSetting object). The
    // whole group loads together with the first getRawSettings call.
    val subLoaded: Boolean = false,
    val subAnnounce: String = "",
    val hosts: PanelHostsSettings = PanelHostsSettings(),
    val telegram: PanelBotSettings = PanelBotSettings(),
    val discord: PanelBotSettings = PanelBotSettings(),
    // Email / SMTP From header (panel v3.6.0).
    val smtpFrom: String = "",
    val smtpFromName: String = "",
    // Notifications: consecutive failed probes before an outbound.down alert
    // (panel v3.6.0). Kept as text so the field can be cleared while typing.
    val outboundDownThreshold: String = "3",
    // Addresses the IP limit never counts and never bans (panel v3.7.0).
    val ipLimitAllowlist: String = "",
)

/**
 * Panel administration over the token-accessible `/panel/api/setting/` surface:
 * change the admin credentials, manage API tokens, and restart the panel. All
 * mutate the live panel — the screen confirms the destructive ones first.
 */
@HiltViewModel
class PanelAdminViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PanelAdminUiState())
    internal val state: StateFlow<PanelAdminUiState> = _state.asStateFlow()

    // Full settings object, kept verbatim so a save only changes the edited field.
    private var rawSettings: JsonObject? = null

    fun loadSubscription() {
        viewModelScope.launch {
            repo.getRawSettings()
                .onSuccess { obj ->
                    rawSettings = obj
                    _state.update {
                        it.copy(
                            subLoaded = true,
                            hosts = PanelHostsSettings.read(obj),
                            telegram = PanelBotSettings.read(obj, PanelBot.Telegram),
                            discord = PanelBotSettings.read(obj, PanelBot.Discord),
                            subAnnounce = obj.string("subAnnounce"),
                            smtpFrom = obj.string("smtpFrom"),
                            smtpFromName = obj.string("smtpFromName"),
                            outboundDownThreshold = (obj.int("outboundDownThreshold") ?: 3).toString(),
                            ipLimitAllowlist = obj.string("ipLimitAllowlist"),
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Couldn't load settings") } }
        }
    }

    internal fun setHosts(settings: PanelHostsSettings) =
        _state.update { it.copy(hosts = settings) }

    internal fun saveHosts() {
        val raw = rawSettings ?: return
        if (_state.value.busy) return
        val updated = _state.value.hosts.applyTo(raw)
        savePanelSettings(updated, "Panel host settings saved") {
            it.copy(hosts = PanelHostsSettings.read(updated))
        }
    }

    internal fun setBot(bot: PanelBot, settings: PanelBotSettings) =
        _state.update {
            when (bot) {
                PanelBot.Telegram -> it.copy(telegram = settings)
                PanelBot.Discord -> it.copy(discord = settings)
            }
        }

    internal fun saveBot(bot: PanelBot, replacement: String, clear: Boolean, onSaved: () -> Unit) {
        val raw = rawSettings ?: return
        val current = _state.value
        if (current.busy) return
        val draft = when (bot) {
            PanelBot.Telegram -> current.telegram
            PanelBot.Discord -> current.discord
        }
        if (!draft.supported) return
        val updated = bot.withTokenUpdate(draft.applyTo(raw, bot), replacement, clear)
        val cached = bot.afterSave(updated)
        savePanelSettings(updated, "Bot settings saved", cached, onSaved) {
            val saved = PanelBotSettings.read(cached, bot)
            when (bot) {
                PanelBot.Telegram -> it.copy(telegram = saved)
                PanelBot.Discord -> it.copy(discord = saved)
            }
        }
    }

    private fun savePanelSettings(
        submitted: JsonObject,
        message: String,
        cached: JsonObject = submitted,
        onSaved: () -> Unit = {},
        updateState: (PanelAdminUiState) -> PanelAdminUiState,
    ) {
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            repo.updateSettings(submitted)
                .onSuccess {
                    rawSettings = cached
                    _state.update { updateState(it).copy(busy = false, message = message) }
                    onSaved()
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message ?: "Couldn't save settings") } }
        }
    }

    fun setSubAnnounce(v: String) = _state.update { it.copy(subAnnounce = v) }

    fun saveSubAnnounce() {
        val raw = rawSettings ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            val updated = raw.putString("subAnnounce", _state.value.subAnnounce)
            repo.updateSettings(updated)
                .onSuccess {
                    rawSettings = updated
                    _state.update { it.copy(busy = false, message = "Subscription settings saved") }
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message ?: "Couldn't save settings") } }
        }
    }

    fun setSmtpFrom(v: String) = _state.update { it.copy(smtpFrom = v) }

    fun setSmtpFromName(v: String) = _state.update { it.copy(smtpFromName = v) }

    fun saveEmail() {
        val raw = rawSettings ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            val updated = raw
                .putString("smtpFrom", _state.value.smtpFrom.trim())
                .putString("smtpFromName", _state.value.smtpFromName.trim())
            repo.updateSettings(updated)
                .onSuccess {
                    rawSettings = updated
                    _state.update { it.copy(busy = false, message = "Email settings saved") }
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message ?: "Couldn't save settings") } }
        }
    }

    fun setIpLimitAllowlist(v: String) = _state.update { it.copy(ipLimitAllowlist = v) }

    fun saveIpLimitAllowlist() {
        val raw = rawSettings ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            val updated = raw.putString("ipLimitAllowlist", _state.value.ipLimitAllowlist.trim())
            repo.updateSettings(updated)
                .onSuccess {
                    rawSettings = updated
                    _state.update { it.copy(busy = false, message = "IP limit allowlist saved") }
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message ?: "Couldn't save settings") } }
        }
    }

    fun setOutboundDownThreshold(v: String) =
        _state.update { it.copy(outboundDownThreshold = v.filter { c -> c.isDigit() }.take(3)) }

    fun saveNotifications() {
        val raw = rawSettings ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            val threshold = (_state.value.outboundDownThreshold.toIntOrNull() ?: 3).coerceIn(1, 100)
            val updated = raw.putInt("outboundDownThreshold", threshold)
            repo.updateSettings(updated)
                .onSuccess {
                    rawSettings = updated
                    _state.update {
                        it.copy(busy = false, outboundDownThreshold = threshold.toString(), message = "Notification settings saved")
                    }
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message ?: "Couldn't save settings") } }
        }
    }

    fun loadTokens() {
        _state.update { it.copy(tokensLoading = true) }
        viewModelScope.launch {
            repo.listApiTokens()
                .onSuccess { rows -> _state.update { it.copy(tokensLoading = false, tokens = rows) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            tokensLoading = false,
                            tokensUnsupported = e.isUnsupportedByPanel(),
                            error = if (e.isUnsupportedByPanel()) null else e.message ?: "Couldn't load tokens",
                        )
                    }
                }
        }
    }

    fun changeCredentials(oldU: String, oldP: String, newU: String, newP: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            repo.changeCredentials(oldU.trim(), oldP, newU.trim(), newP)
                .onSuccess { _state.update { it.copy(busy = false, message = "Credentials updated") } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message ?: "Couldn't change credentials") } }
        }
    }

    fun restartPanel() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            repo.restartPanel()
                .onSuccess { _state.update { it.copy(busy = false, message = "Panel is restarting…") } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message ?: "Couldn't restart the panel") } }
        }
    }

    /** [scope] and [expiresAt] are panel v3.7.0 fields; an older panel ignores
     *  them and keeps minting full-access tokens, which is its old behaviour. */
    fun createToken(name: String, scope: String = ApiTokenScope.ADMIN, expiresAt: Long = 0) {
        if (_state.value.busy || name.isBlank()) return
        _state.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            repo.createApiToken(name.trim(), scope, expiresAt)
                .onSuccess { tok ->
                    _state.update { it.copy(busy = false, newToken = tok) }
                    loadTokens()
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message ?: "Couldn't create token") } }
        }
    }

    /** [scope] is the scope the list says this token has — panel v3.7.0 refuses
     *  the change unless the caller names it, so a stale list cannot toggle the
     *  wrong token. */
    fun setTokenEnabled(id: Int, enabled: Boolean, scope: String) {
        viewModelScope.launch {
            repo.setApiTokenEnabled(id, enabled, scope)
                .onSuccess { loadTokens() }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Couldn't update token") } }
        }
    }

    fun deleteToken(id: Int, scope: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            repo.deleteApiToken(id, scope)
                .onSuccess { _state.update { it.copy(busy = false, message = "Token deleted") }; loadTokens() }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message ?: "Couldn't delete token") } }
        }
    }

    fun dismissNewToken() = _state.update { it.copy(newToken = null) }
    fun dismissMessage() = _state.update { it.copy(message = null) }
    fun dismissError() = _state.update { it.copy(error = null) }
}
