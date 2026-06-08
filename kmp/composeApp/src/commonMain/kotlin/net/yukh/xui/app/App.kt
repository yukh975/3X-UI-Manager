package net.yukh.xui.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.yukh.xui.shared.api.PanelApi
import net.yukh.xui.shared.dto.ServerStatus

/**
 * Root of the shared iOS/Android Compose Multiplatform app. Holds the minimal
 * connect→dashboard flow inline (no DI framework yet); the Android app keeps its
 * own richer Hilt-based UI. This proves the shared networking + UI work on iOS.
 */
@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var baseUrl by remember { mutableStateOf("") }
            var token by remember { mutableStateOf("") }
            var connected by remember { mutableStateOf(false) }
            var busy by remember { mutableStateOf(false) }
            var refreshing by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            var status by remember { mutableStateOf<ServerStatus?>(null) }
            var api by remember { mutableStateOf<PanelApi?>(null) }
            val scope = rememberCoroutineScope()

            suspend fun fetch() {
                val a = api ?: return
                try {
                    val resp = a.serverStatus()
                    if (resp.success) {
                        status = resp.obj
                        error = null
                    } else {
                        error = resp.msg.ifBlank { "Request failed" }
                    }
                } catch (e: Throwable) {
                    error = e.message ?: "Network error"
                }
            }

            if (!connected) {
                ConnectScreen(
                    baseUrl = baseUrl,
                    token = token,
                    busy = busy,
                    error = error,
                    onBaseUrl = { baseUrl = it; error = null },
                    onToken = { token = it; error = null },
                    onConnect = {
                        scope.launch {
                            busy = true
                            error = null
                            val a = PanelApi(baseUrl.trim(), token.trim())
                            try {
                                val resp = a.serverStatus()
                                if (resp.success) {
                                    api = a
                                    status = resp.obj
                                    connected = true
                                } else {
                                    error = resp.msg.ifBlank { "Login failed — check URL / token" }
                                    a.close()
                                }
                            } catch (e: Throwable) {
                                error = e.message ?: "Network error"
                                a.close()
                            }
                            busy = false
                        }
                    },
                )
            } else {
                DashboardScreen(
                    host = baseUrl,
                    status = status,
                    refreshing = refreshing,
                    error = error,
                    onRefresh = { scope.launch { refreshing = true; fetch(); refreshing = false } },
                    onDisconnect = {
                        api?.close()
                        api = null
                        status = null
                        connected = false
                    },
                )
                LaunchedEffect(connected) {
                    while (connected) {
                        delay(5_000)
                        fetch()
                    }
                }
            }
        }
    }
}
