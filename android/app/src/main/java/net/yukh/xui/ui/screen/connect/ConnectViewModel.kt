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

enum class AuthMethod { Token, Credentials }

data class ConnectUiState(
    val url: String = "",
    val method: AuthMethod = AuthMethod.Token,
    val token: String = "",
    val username: String = "",
    val password: String = "",
    val twoFactorCode: String = "",
    val allowInsecureTls: Boolean = false,
    val subBaseUrl: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = if (submitting || url.isBlank()) {
            false
        } else when (method) {
            AuthMethod.Token -> token.isNotBlank()
            AuthMethod.Credentials -> username.isNotBlank() && password.isNotBlank()
        }
}

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val store: ConnectionStore,
    private val repo: PanelRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    init {
        // Surface a mid-session auth loss (token revoked / session expired) that
        // bounced the user back here, so the Connect screen explains why.
        viewModelScope.launch {
            repo.authError.collect { msg -> if (msg != null) _state.update { it.copy(error = msg) } }
        }
    }

    private fun initialState(): ConnectUiState {
        val stored = store.getProfile() ?: return ConnectUiState()
        return when (val auth = stored.auth) {
            is ConnectionAuth.Token -> ConnectUiState(
                url = stored.baseUrl,
                allowInsecureTls = stored.allowInsecureTls,
                subBaseUrl = stored.subBaseUrl,
                method = AuthMethod.Token,
                token = auth.token,
            )
            is ConnectionAuth.Credentials -> ConnectUiState(
                url = stored.baseUrl,
                allowInsecureTls = stored.allowInsecureTls,
                subBaseUrl = stored.subBaseUrl,
                method = AuthMethod.Credentials,
                username = auth.username,
                password = auth.password,
            )
        }
    }

    fun setUrl(value: String) = _state.update { it.copy(url = value, error = null) }
    fun setMethod(value: AuthMethod) = _state.update { it.copy(method = value, error = null) }
    fun setToken(value: String) = _state.update { it.copy(token = value, error = null) }
    fun setUsername(value: String) = _state.update { it.copy(username = value, error = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, error = null) }
    fun setTwoFactorCode(value: String) =
        _state.update { it.copy(twoFactorCode = value.filter { c -> c.isDigit() }.take(6), error = null) }
    fun setAllowInsecureTls(value: Boolean) =
        _state.update { it.copy(allowInsecureTls = value, error = null) }
    fun setSubBaseUrl(value: String) = _state.update { it.copy(subBaseUrl = value, error = null) }

    fun submit(onSuccess: () -> Unit) {
        val s = _state.value
        if (!s.canSubmit) return
        val normalizedUrl = ConnectionProfile.normalizeUrl(s.url)
        _state.update { it.copy(submitting = true, error = null) }

        viewModelScope.launch {
            val result = when (s.method) {
                AuthMethod.Token -> repo.connectWithToken(
                    baseUrl = normalizedUrl,
                    allowInsecureTls = s.allowInsecureTls,
                    token = s.token.trim(),
                    subBaseUrl = s.subBaseUrl.trim(),
                )
                AuthMethod.Credentials -> repo.connectWithCredentials(
                    baseUrl = normalizedUrl,
                    allowInsecureTls = s.allowInsecureTls,
                    username = s.username.trim(),
                    password = s.password,
                    twoFactorCode = s.twoFactorCode.takeIf { it.isNotBlank() },
                    subBaseUrl = s.subBaseUrl.trim(),
                )
            }
            result
                .onSuccess {
                    _state.update { it.copy(submitting = false) }
                    onSuccess()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(submitting = false, error = e.message ?: "Connection failed")
                    }
                }
        }
    }
}
