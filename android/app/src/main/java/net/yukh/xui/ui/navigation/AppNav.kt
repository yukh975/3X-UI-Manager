package net.yukh.xui.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.yukh.xui.data.repo.PanelRepository
import net.yukh.xui.ui.screen.connect.ConnectScreen
import net.yukh.xui.ui.screen.main.MainScreen

@HiltViewModel
class AppNavViewModel @Inject constructor(
    private val repo: PanelRepository,
) : ViewModel() {
    val connected: StateFlow<Boolean> = repo.connected

    private val _reconnecting = MutableStateFlow(false)
    val reconnecting: StateFlow<Boolean> = _reconnecting.asStateFlow()

    init {
        // Auto-relogin a stored login/password session at startup so the app
        // doesn't drop to Connect after the panel session expired / restart.
        if (!repo.connected.value && repo.hasStoredCredentials()) {
            _reconnecting.value = true
            viewModelScope.launch {
                repo.tryAutoReconnect()
                _reconnecting.value = false
            }
        }
    }
}

/**
 * Top-level routing is just two states driven by the repository's `connected`
 * flow — no NavHost needed. Connecting flips `connected` → Main; disconnecting
 * flips it back → Connect. A brief splash covers the startup auto-relogin.
 */
@Composable
fun AppNav(vm: AppNavViewModel = hiltViewModel()) {
    val connected by vm.connected.collectAsStateWithLifecycle()
    val reconnecting by vm.reconnecting.collectAsStateWithLifecycle()

    when {
        reconnecting && !connected ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        connected -> MainScreen(onDisconnect = {})
        else -> ConnectScreen(onConnected = {})
    }
}
