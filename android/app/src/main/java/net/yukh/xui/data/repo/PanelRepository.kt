package net.yukh.xui.data.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.yukh.xui.data.api.ApiResponse
import net.yukh.xui.data.api.XuiApi
import net.yukh.xui.data.api.XuiApiFactory
import net.yukh.xui.data.api.dto.Client
import net.yukh.xui.data.api.dto.ClientLinks
import net.yukh.xui.data.api.dto.EnableRequest
import net.yukh.xui.data.api.dto.InboundSlim
import net.yukh.xui.data.api.dto.LoginRequest
import net.yukh.xui.data.api.dto.ServerStatus
import net.yukh.xui.data.auth.CsrfState
import net.yukh.xui.data.auth.InMemoryCookieJar
import net.yukh.xui.data.prefs.ConnectionAuth
import net.yukh.xui.data.prefs.ConnectionProfile
import net.yukh.xui.data.prefs.ConnectionStore
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single point of truth for talking to the panel.
 */
@Singleton
class PanelRepository @Inject constructor(
    private val store: ConnectionStore,
    private val json: Json,
) {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var api: XuiApi? = null
    private var cookieJar: InMemoryCookieJar? = null
    private var csrf: CsrfState? = null

    init {
        val stored = store.getProfile()
        if (stored != null && stored.auth is ConnectionAuth.Token) {
            bindTokenInternal(stored.baseUrl, stored.allowInsecureTls, stored.auth.token)
        }
    }

    // ---- Connection lifecycle ---------------------------------------------

    suspend fun connectWithToken(
        baseUrl: String,
        allowInsecureTls: Boolean,
        token: String,
    ): Result<ServerStatus> {
        val candidate = XuiApiFactory.tokenAuthed(baseUrl, allowInsecureTls, token, json)
        return safeCall { candidate.getServerStatus() }.onSuccess {
            store.saveProfile(
                ConnectionProfile(baseUrl, allowInsecureTls, ConnectionAuth.Token(token)),
            )
            cookieJar?.clear()
            cookieJar = null
            csrf = null
            api = candidate
            _connected.value = true
        }
    }

    suspend fun connectWithCredentials(
        baseUrl: String,
        allowInsecureTls: Boolean,
        username: String,
        password: String,
        twoFactorCode: String?,
    ): Result<ServerStatus> {
        val jar = InMemoryCookieJar()
        val csrfHolder = CsrfState()
        val candidate = XuiApiFactory.sessionAuthed(
            baseUrl, allowInsecureTls, jar, csrfHolder, json,
        )

        val csrfBootstrap = safeCall { candidate.getCsrfToken() }
        csrfBootstrap.onFailure { return Result.failure(it) }
            .onSuccess { csrfHolder.set(it) }

        val loginResult = safeCall { candidate.login(LoginRequest(username, password, twoFactorCode)) }
        loginResult.onFailure { return Result.failure(it) }

        safeCall { candidate.getCsrfToken() }.onSuccess { csrfHolder.set(it) }

        val status = safeCall { candidate.getServerStatus() }
        return status.onSuccess {
            store.saveProfile(
                ConnectionProfile(
                    baseUrl, allowInsecureTls,
                    ConnectionAuth.Credentials(username, password),
                ),
            )
            api = candidate
            cookieJar = jar
            csrf = csrfHolder
            _connected.value = true
        }
    }

    fun unbind() {
        api = null
        cookieJar?.clear()
        cookieJar = null
        csrf = null
        _connected.value = false
        store.clear()
    }

    // ---- Server -----------------------------------------------------------

    suspend fun getServerStatus(): Result<ServerStatus> =
        authedCall { it.getServerStatus() }

    suspend fun restartXray(): Result<Unit> =
        authedCallVoid { it.restartXray() }

    suspend fun stopXray(): Result<Unit> =
        authedCallVoid { it.stopXray() }

    // ---- Inbounds ---------------------------------------------------------

    suspend fun listInboundsSlim(): Result<List<InboundSlim>> =
        authedCall { it.listInboundsSlim() }

    suspend fun setInboundEnable(id: Int, enable: Boolean): Result<Unit> =
        authedCallVoid { it.setInboundEnable(id, EnableRequest(enable)) }

    // ---- Clients ----------------------------------------------------------

    suspend fun listClients(): Result<List<Client>> =
        authedCall { it.listClients() }

    suspend fun getClientLinks(email: String): Result<ClientLinks> =
        authedCall { it.getClientLinks(email) }

    suspend fun deleteClient(email: String): Result<Unit> =
        authedCallVoid { it.deleteClient(email) }

    suspend fun listOnlines(): Result<List<String>> =
        authedCall { it.listOnlines() }

    // ---- Internals --------------------------------------------------------

    private fun bindTokenInternal(baseUrl: String, allowInsecureTls: Boolean, token: String) {
        api = XuiApiFactory.tokenAuthed(baseUrl, allowInsecureTls, token, json)
        cookieJar = null
        csrf = null
        _connected.value = true
    }

    private suspend inline fun <T> authedCall(
        crossinline block: suspend (XuiApi) -> ApiResponse<T>,
    ): Result<T> {
        val current = api ?: return Result.failure(PanelError.NotConnected)
        return safeCall { block(current) }
    }

    private suspend inline fun authedCallVoid(
        crossinline block: suspend (XuiApi) -> ApiResponse<*>,
    ): Result<Unit> {
        val current = api ?: return Result.failure(PanelError.NotConnected)
        return try {
            val resp = block(current)
            if (resp.success) {
                Result.success(Unit)
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
    data class Network(val reason: String) : PanelError("Network error: $reason")
    data object BadResponse : PanelError("Unexpected response — check URL and base path")
    data class Rejected(val msg: String) : PanelError(msg)
}
