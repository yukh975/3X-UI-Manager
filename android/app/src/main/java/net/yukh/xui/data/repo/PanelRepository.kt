package net.yukh.xui.data.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.yukh.xui.data.api.ApiResponse
import net.yukh.xui.data.api.XuiApi
import net.yukh.xui.data.api.XuiApiFactory
import net.yukh.xui.data.api.dto.ServerStatus
import net.yukh.xui.data.prefs.ConnectionProfile
import net.yukh.xui.data.prefs.ConnectionStore
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single point of truth for talking to the panel.
 *
 * Owns the active [XuiApi] instance; rebinds it when the user saves a new
 * profile. Errors from Retrofit/OkHttp/kotlinx.serialization are mapped to a
 * small set of [PanelError]s so the UI never has to know the network plumbing.
 */
@Singleton
class PanelRepository @Inject constructor(
    private val store: ConnectionStore,
    private val json: Json,
) {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var api: XuiApi? = null

    init {
        store.getProfile()?.let { bind(it) }
    }

    fun bind(profile: ConnectionProfile) {
        api = XuiApiFactory.create(profile, json)
        _connected.value = true
    }

    fun unbind() {
        api = null
        _connected.value = false
        store.clear()
    }

    /**
     * Verify a profile by calling the cheapest authenticated endpoint we have.
     * Does NOT persist or rebind on success — the caller decides.
     */
    suspend fun testConnection(profile: ConnectionProfile): Result<ServerStatus> {
        val tempApi = XuiApiFactory.create(profile, json)
        return safeCall { tempApi.getServerStatus() }
    }

    suspend fun getServerStatus(): Result<ServerStatus> {
        val current = api ?: return Result.failure(PanelError.NotConnected)
        return safeCall { current.getServerStatus() }
    }

    private suspend inline fun <T> safeCall(
        crossinline block: suspend () -> ApiResponse<T>,
    ): Result<T> = try {
        val resp = block()
        if (resp.success && resp.obj != null) {
            Result.success(resp.obj)
        } else {
            Result.failure(PanelError.Rejected(resp.msg.ifBlank { "Request rejected" }))
        }
    } catch (e: HttpException) {
        Result.failure(PanelError.Http(e.code()))
    } catch (e: IOException) {
        Result.failure(PanelError.Network(e.message ?: e.javaClass.simpleName))
    } catch (_: SerializationException) {
        Result.failure(PanelError.BadResponse)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

sealed class PanelError(message: String) : RuntimeException(message) {
    data object NotConnected : PanelError("Not connected to a panel")
    data class Http(val code: Int) : PanelError("HTTP $code")
    data class Network(val cause: String) : PanelError("Network error: $cause")
    data object BadResponse : PanelError("Unexpected response — check URL and base path")
    data class Rejected(val msg: String) : PanelError(msg)
}
