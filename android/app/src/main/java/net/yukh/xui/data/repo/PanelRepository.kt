package net.yukh.xui.data.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.yukh.xui.data.api.ApiResponse
import net.yukh.xui.data.api.XuiApi
import net.yukh.xui.data.api.XuiApiFactory
import net.yukh.xui.data.api.dto.ApiAck
import net.yukh.xui.data.api.dto.Client
import net.yukh.xui.data.api.dto.EnableRequest
import net.yukh.xui.data.api.dto.InboundSlim
import net.yukh.xui.data.api.dto.LoginRequest
import net.yukh.xui.data.api.dto.PanelSettings
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
    private var currentBaseUrl: String? = null

    /** Cached panel settings (sub config). Null until first successful fetch. */
    private var cachedSettings: PanelSettings? = null

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
        return safeData { candidate.getServerStatus() }.onSuccess {
            store.saveProfile(
                ConnectionProfile(baseUrl, allowInsecureTls, ConnectionAuth.Token(token)),
            )
            cookieJar?.clear()
            cookieJar = null
            csrf = null
            api = candidate
            currentBaseUrl = baseUrl
            cachedSettings = null
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

        // 1. Seed CSRF. GET → no token needed yet, server creates session.
        val csrfBootstrap = safeData { candidate.getCsrfToken() }
        csrfBootstrap.onFailure { return Result.failure(it) }
            .onSuccess { csrfHolder.set(it) }

        // 2. Login. Success returns {success:true, msg:"…", obj:null} — that's
        //    an ApiAck shape; treating it through safeData would incorrectly
        //    mark a missing obj as failure.
        val loginResult = safeAck { candidate.login(LoginRequest(username, password, twoFactorCode)) }
        loginResult.onFailure { return Result.failure(it) }

        // 3. Refresh CSRF — panel rotates it on session creation.
        safeData { candidate.getCsrfToken() }.onSuccess { csrfHolder.set(it) }

        // 4. Verify the session actually works.
        val status = safeData { candidate.getServerStatus() }
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
            currentBaseUrl = baseUrl
            cachedSettings = null
            _connected.value = true
        }
    }

    fun unbind() {
        api = null
        cookieJar?.clear()
        cookieJar = null
        csrf = null
        currentBaseUrl = null
        cachedSettings = null
        _connected.value = false
        store.clear()
    }

    // ---- Server -----------------------------------------------------------

    suspend fun getServerStatus(): Result<ServerStatus> =
        authedData { it.getServerStatus() }

    suspend fun restartXray(): Result<Unit> =
        authedAck { it.restartXray() }

    suspend fun stopXray(): Result<Unit> =
        authedAck { it.stopXray() }

    // ---- Inbounds ---------------------------------------------------------

    suspend fun listInbounds(): Result<List<InboundSlim>> =
        authedData { it.listInbounds() }

    suspend fun setInboundEnable(id: Int, enable: Boolean): Result<Unit> =
        authedAck { it.setInboundEnable(id, EnableRequest(enable)) }

    // ---- Clients ----------------------------------------------------------

    suspend fun listClients(): Result<List<Client>> =
        authedData { it.listClients() }

    suspend fun getClientLinks(email: String): Result<List<String>> =
        authedData { it.getClientLinks(email) }

    /**
     * Build a client's subscription URL. Needs panel sub settings, which are
     * only reachable with session (login/password) auth — with a token the
     * settings call is redirected to login and this returns null. The result
     * is cached so repeated share-sheet opens don't re-fetch settings.
     */
    suspend fun getSubscriptionUrl(client: Client): String? {
        val host = currentBaseUrl?.let { PanelSettings.hostOf(it) } ?: return null
        val settings = cachedSettings ?: authedData { it.getAllSettings() }
            .getOrNull()
            ?.also { cachedSettings = it }
        return settings?.subscriptionUrl(host, client.subId)
    }

    suspend fun deleteClient(email: String): Result<Unit> =
        authedAck { it.deleteClient(email) }

    suspend fun listOnlines(): Result<List<String>> =
        authedData { it.listOnlines() }

    // ---- Internals --------------------------------------------------------

    private fun bindTokenInternal(baseUrl: String, allowInsecureTls: Boolean, token: String) {
        api = XuiApiFactory.tokenAuthed(baseUrl, allowInsecureTls, token, json)
        cookieJar = null
        csrf = null
        currentBaseUrl = baseUrl
        cachedSettings = null
        _connected.value = true
    }

    private suspend inline fun <T> authedData(
        crossinline block: suspend (XuiApi) -> ApiResponse<T>,
    ): Result<T> {
        val current = api ?: return Result.failure(PanelError.NotConnected)
        return safeData { block(current) }
    }

    private suspend inline fun authedAck(
        crossinline block: suspend (XuiApi) -> ApiAck,
    ): Result<Unit> {
        val current = api ?: return Result.failure(PanelError.NotConnected)
        return safeAck { block(current) }
    }

    /**
     * Run a data-fetching call: success requires both `success: true` AND a
     * non-null `obj` (the caller specifically wants the payload).
     */
    private suspend inline fun <T> safeData(
        crossinline block: suspend () -> ApiResponse<T>,
    ): Result<T> = catching {
        val resp = block()
        if (resp.success && resp.obj != null) {
            Result.success(resp.obj)
        } else {
            Result.failure(PanelError.Rejected(resp.msg.ifBlank { "Request rejected" }))
        }
    }

    /**
     * Run an ack-only call: success requires just `success: true`. The
     * panel uses null `obj` plus a status message for login/logout, xray
     * controls, toggles, and deletes.
     */
    private suspend inline fun safeAck(
        crossinline block: suspend () -> ApiAck,
    ): Result<Unit> = catching {
        val resp = block()
        if (resp.success) {
            Result.success(Unit)
        } else {
            Result.failure(PanelError.Rejected(resp.msg.ifBlank { "Request rejected" }))
        }
    }

    /**
     * Common exception → PanelError mapping. Keeps the typed error surface
     * narrow so the UI never has to know the Retrofit/OkHttp plumbing.
     */
    private inline fun <T> catching(block: () -> Result<T>): Result<T> = try {
        block()
    } catch (e: HttpException) {
        Result.failure(PanelError.Http(e.code()))
    } catch (e: IOException) {
        Result.failure(PanelError.Network(e.message ?: e.javaClass.simpleName))
    } catch (e: SerializationException) {
        // Surface what the parser actually choked on — usually "Unexpected JSON
        // token at offset N" or "Expected start of an object…", which makes it
        // obvious whether the panel returned HTML (wrong base path / auth
        // wall) or just a schema-shape we don't handle yet.
        Result.failure(PanelError.BadResponse(e.message?.lines()?.firstOrNull()))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

sealed class PanelError(message: String) : RuntimeException(message) {
    data object NotConnected : PanelError("Not connected to a panel")
    data class Http(val code: Int) : PanelError("HTTP $code")
    data class Network(val reason: String) : PanelError("Network error: $reason")
    data class BadResponse(val detail: String?) : PanelError(
        buildString {
            append("Unexpected response — check URL and base path")
            if (!detail.isNullOrBlank()) {
                append(" (")
                append(detail)
                append(")")
            }
        },
    )
    data class Rejected(val msg: String) : PanelError(msg)
}
