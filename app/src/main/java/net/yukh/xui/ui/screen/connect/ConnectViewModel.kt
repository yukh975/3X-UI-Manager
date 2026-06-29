package net.yukh.xui.ui.screen.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.yukh.xui.data.prefs.ConnectionAuth
import net.yukh.xui.data.prefs.ConnectionProfile
import net.yukh.xui.data.prefs.ConnectionStore
import net.yukh.xui.data.repo.PanelRepository

data class ConnectUiState(
    val url: String = "",
    val token: String = "",
    val allowInsecureTls: Boolean = false,
    val subBaseUrl: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = !submitting && url.isNotBlank() && token.isNotBlank()
}

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val store: ConnectionStore,
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    init {
        // Surface a mid-session auth loss (token revoked) that bounced the user
        // back here, so the Connect screen explains why.
        viewModelScope.launch {
            repo.authError.collect { msg -> if (msg != null) _state.update { it.copy(error = msg) } }
        }
    }

    private fun initialState(): ConnectUiState {
        val stored = store.getProfile() ?: return ConnectUiState()
        val auth = stored.auth as? ConnectionAuth.Token ?: return ConnectUiState()
        return ConnectUiState(
            url = stored.baseUrl,
            allowInsecureTls = stored.allowInsecureTls,
            subBaseUrl = stored.subBaseUrl,
            token = auth.token,
        )
    }

    /** Blank the form — used when adding another panel (vs. the initial state,
     *  which pre-fills the active profile for a quick reconnect). */
    fun clearForm() = _state.update { ConnectUiState() }

    fun setUrl(value: String) = _state.update { it.copy(url = value, error = null) }
    fun setToken(value: String) = _state.update { it.copy(token = value, error = null) }
    fun setAllowInsecureTls(value: Boolean) = _state.update { it.copy(allowInsecureTls = value, error = null) }
    fun setSubBaseUrl(value: String) = _state.update { it.copy(subBaseUrl = value, error = null) }

    fun submit(onSuccess: () -> Unit) {
        val s = _state.value
        if (!s.canSubmit) return
        val normalizedUrl = ConnectionProfile.normalizeUrl(s.url)
        _state.update { it.copy(submitting = true, error = null) }

        viewModelScope.launch {
            repo.connectWithToken(
                baseUrl = normalizedUrl,
                allowInsecureTls = s.allowInsecureTls,
                token = s.token.trim(),
                subBaseUrl = s.subBaseUrl.trim(),
            )
                .onSuccess {
                    _state.update { it.copy(submitting = false) }
                    onSuccess()
                }
                .onFailure { e ->
                    _state.update { it.copy(submitting = false, error = e.message ?: "Connection failed") }
                }
        }
    }
}
