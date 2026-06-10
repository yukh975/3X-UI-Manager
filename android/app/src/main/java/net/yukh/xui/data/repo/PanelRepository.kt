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
import net.yukh.xui.data.api.dto.ClientCreatePayload
import net.yukh.xui.data.api.dto.ClientModel
import net.yukh.xui.data.api.dto.EnableRequest
import net.yukh.xui.data.api.dto.MetricPoint
import net.yukh.xui.data.api.dto.InboundIdsRequest
import net.yukh.xui.data.api.dto.InboundModel
import net.yukh.xui.data.api.dto.InboundSlim
import net.yukh.xui.data.api.dto.LoginRequest
import net.yukh.xui.data.api.dto.Node
import net.yukh.xui.data.api.dto.NodeIdsRequest
import net.yukh.xui.data.api.dto.NodeModel
import net.yukh.xui.data.api.dto.PanelSettings
import net.yukh.xui.data.api.dto.PanelUpdateInfo
import net.yukh.xui.data.api.dto.ServerStatus
import net.yukh.xui.data.api.dto.XraySettingEnvelope
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

    /** Optional user-set subscription base URL from the active profile. */
    private var currentSubBase: String? = null

    /** Cached panel settings (sub config). Null until first successful fetch. */
    private var cachedSettings: PanelSettings? = null

    init {
        val stored = store.getProfile()
        if (stored != null && stored.auth is ConnectionAuth.Token) {
            bindTokenInternal(stored.baseUrl, stored.allowInsecureTls, stored.auth.token)
            currentSubBase = stored.subBaseUrl
        }
    }

    // ---- Connection lifecycle ---------------------------------------------

    suspend fun connectWithToken(
        baseUrl: String,
        allowInsecureTls: Boolean,
        token: String,
        subBaseUrl: String = "",
    ): Result<ServerStatus> {
        val candidate = XuiApiFactory.tokenAuthed(baseUrl, allowInsecureTls, token, json)
        return safeData { candidate.getServerStatus() }.onSuccess {
            store.saveProfile(
                ConnectionProfile(baseUrl, allowInsecureTls, ConnectionAuth.Token(token), subBaseUrl),
            )
            cookieJar?.clear()
            cookieJar = null
            csrf = null
            api = candidate
            currentBaseUrl = baseUrl
            currentSubBase = subBaseUrl
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
        subBaseUrl: String = "",
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
                    subBaseUrl,
                ),
            )
            api = candidate
            cookieJar = jar
            csrf = csrfHolder
            currentBaseUrl = baseUrl
            currentSubBase = subBaseUrl
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
        currentSubBase = null
        cachedSettings = null
        _connected.value = false
        store.clear()
    }

    /** A login/password profile is saved (so we can auto-relogin). */
    fun hasStoredCredentials(): Boolean =
        store.getProfile()?.auth is ConnectionAuth.Credentials

    /**
     * At app start, silently re-establish a session from a stored
     * login/password profile so the user isn't dropped to the connect screen
     * after the panel's session expired or the app restarted. No-op (returns
     * false) for token profiles, 2FA accounts, or if already connected.
     */
    suspend fun tryAutoReconnect(): Boolean {
        if (_connected.value) return true
        return reauth()
    }

    // ---- Server -----------------------------------------------------------

    suspend fun getServerStatus(): Result<ServerStatus> =
        authedData { it.getServerStatus() }

    /** System-metrics history for the dashboard charts (one metric, one bucket). */
    suspend fun metricHistory(metric: String, bucket: Int): Result<List<MetricPoint>> =
        authedData { it.metricHistory(metric, bucket) }

    suspend fun restartXray(): Result<Unit> =
        authedAck { it.restartXray() }

    suspend fun stopXray(): Result<Unit> =
        authedAck { it.stopXray() }

    suspend fun getPanelUpdateInfo(): Result<PanelUpdateInfo> =
        authedData { it.getPanelUpdateInfo() }

    suspend fun updatePanel(): Result<Unit> =
        authedAck { it.updatePanel() }

    /** Re-download one built-in geo database and restart Xray. [fileName] must be
     *  an allowlisted .dat name (e.g. "geoip.dat"); the panel rejects others. */
    suspend fun updateGeofile(fileName: String): Result<Unit> =
        authedAck { it.updateGeofile(fileName) }

    /** Re-download all built-in geo databases at once and restart Xray. */
    suspend fun updateAllGeofiles(): Result<Unit> =
        authedAck { it.updateAllGeofiles() }

    // ---- Inbounds ---------------------------------------------------------

    suspend fun listInbounds(): Result<List<InboundSlim>> =
        authedData { it.listInbounds() }

    /**
     * Proxied (VPN) traffic for the current month, grouped by server. The central
     * /inbounds/list already mixes main-panel and node inbounds (each tagged with
     * `nodeId`), so one call yields every group with no per-node queries:
     *   - key 0  = the main panel's own inbounds (nodeId null/0),
     *   - key N  = the sub-node with that id.
     * Every inbound is expected to be trafficReset="monthly", so up+down == this
     * month; [ServerTraffic.allMonthly] flags a group where that doesn't hold (the
     * sum then includes all-time counters for the offending inbounds).
     */
    suspend fun monthlyTrafficByServer(): Result<Map<Int, ServerTraffic>> =
        listInbounds().map { inbounds ->
            inbounds.groupBy { it.nodeId ?: 0 }.mapValues { (nodeId, group) ->
                ServerTraffic(
                    nodeId = nodeId,
                    bytes = group.sumOf { it.up + it.down },
                    sinceMillis = group.mapNotNull { it.lastTrafficResetTime.takeIf { t -> t > 0 } }
                        .maxOrNull() ?: 0L,
                    allMonthly = group.all { it.trafficReset.equals("monthly", ignoreCase = true) },
                )
            }
        }

    suspend fun getInbound(id: Int): Result<InboundModel> =
        authedData { it.getInbound(id) }

    suspend fun addInbound(inbound: InboundModel): Result<Unit> =
        authedAck { it.addInbound(inbound) }

    suspend fun updateInbound(id: Int, inbound: InboundModel): Result<Unit> =
        authedAck { it.updateInbound(id, inbound) }

    suspend fun deleteInbound(id: Int): Result<Unit> =
        authedAck { it.deleteInbound(id) }

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
        if (client.subId.isBlank()) return null
        // Prefer the user-set subscription base (works with API token).
        currentSubBase?.takeIf { it.isNotBlank() }?.let { base ->
            val b = if (base.endsWith("/")) base else "$base/"
            return b + client.subId
        }
        // Fall back to reading panel sub settings (login/password only).
        val host = currentBaseUrl?.let { PanelSettings.hostOf(it) } ?: return null
        val settings = cachedSettings ?: authedData { it.getAllSettings() }
            .getOrNull()
            ?.also { cachedSettings = it }
        return settings?.subscriptionUrl(host, client.subId)
    }

    suspend fun deleteClient(email: String): Result<Unit> =
        authedAck { it.deleteClient(email) }

    suspend fun addClient(client: ClientModel, inboundIds: List<Int>): Result<Unit> =
        authedAck { it.addClient(ClientCreatePayload(client, inboundIds)) }

    suspend fun updateClient(email: String, client: ClientModel): Result<Unit> =
        authedAck { it.updateClient(email, client) }

    suspend fun attachClient(email: String, inboundIds: List<Int>): Result<Unit> =
        authedAck { it.attachClient(email, InboundIdsRequest(inboundIds)) }

    suspend fun detachClient(email: String, inboundIds: List<Int>): Result<Unit> =
        authedAck { it.detachClient(email, InboundIdsRequest(inboundIds)) }

    suspend fun listOnlines(): Result<List<String>> =
        authedData { it.listOnlines() }

    // ---- Nodes ------------------------------------------------------------

    suspend fun listNodes(): Result<List<Node>> =
        authedData { it.listNodes() }

    suspend fun addNode(node: NodeModel): Result<Unit> =
        authedAck { it.addNode(node) }

    suspend fun updateNode(id: Int, node: NodeModel): Result<Unit> =
        authedAck { it.updateNode(id, node) }

    suspend fun deleteNode(id: Int): Result<Unit> =
        authedAck { it.deleteNode(id) }

    suspend fun setNodeEnable(id: Int, enable: Boolean): Result<Unit> =
        authedAck { it.setNodeEnable(id, EnableRequest(enable)) }

    /** Trigger a 3x-ui self-update on the given node(s) via the central panel. */
    suspend fun updateNodes(ids: List<Int>): Result<Unit> =
        authedAck { it.updateNodePanel(NodeIdsRequest(ids)) }

    /**
     * Online clients on a specific node, queried directly against that node's own
     * 3x-ui API (each node carries its own scheme/address/port/basePath/token).
     * The central panel has no per-node onlines endpoint, but a node reports its
     * own онлайн — and since xray keys online by email per server, this is the
     * only way to see which server a multi-server client is actually using.
     */
    suspend fun listNodeOnlines(node: Node): Result<List<String>> {
        val bp = node.basePath.ifBlank { "/" }
            .let { if (it.startsWith("/")) it else "/$it" }
            .let { if (it.endsWith("/")) it else "$it/" }
        val base = "${node.scheme}://${node.address}:${node.port}$bp"
        val insecure = node.tlsVerifyMode.equals("skip", ignoreCase = true)
        val nodeApi = XuiApiFactory.tokenAuthed(base, insecure, node.apiToken, json)
        return safeData { nodeApi.listOnlines() }
    }

    // ---- Xray config (session-auth only) ----------------------------------

    /** Fetch the full Xray config (outbounds/routing/dns) + test URL. The
     *  endpoint double-wraps it: response obj is a JSON string that itself
     *  holds the config object. */
    suspend fun getXraySetting(): Result<XraySettingEnvelope> {
        val current = api ?: return Result.failure(PanelError.NotConnected)
        return catching {
            val resp = current.getXraySetting()
            val objStr = resp.obj
            if (!resp.success || objStr.isNullOrBlank()) {
                Result.failure(PanelError.Rejected(resp.msg.ifBlank { "Request rejected" }))
            } else {
                Result.success(json.decodeFromString(XraySettingEnvelope.serializer(), objStr))
            }
        }
    }

    suspend fun updateXraySetting(configJson: String, testUrl: String): Result<Unit> =
        authedAck { it.updateXraySetting(configJson, testUrl) }

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
        val r = safeData { block(current) }
        if (isUnauthorized(r) && reauth()) {
            val again = api ?: return r
            return safeData { block(again) }
        }
        return r
    }

    private suspend inline fun authedAck(
        crossinline block: suspend (XuiApi) -> ApiAck,
    ): Result<Unit> {
        val current = api ?: return Result.failure(PanelError.NotConnected)
        val r = safeAck { block(current) }
        if (isUnauthorized(r) && reauth()) {
            val again = api ?: return r
            return safeAck { block(again) }
        }
        return r
    }

    private fun isUnauthorized(r: Result<*>): Boolean {
        val e = r.exceptionOrNull()
        return e is PanelError.Http && e.code == 401
    }

    /**
     * Re-establish a session from stored login/password credentials (no 2FA).
     * Used on a 401 mid-session and at app start. Token profiles and
     * 2FA-protected accounts return false (the user must act).
     */
    private suspend fun reauth(): Boolean {
        val p = store.getProfile() ?: return false
        val auth = p.auth as? ConnectionAuth.Credentials ?: return false
        return connectWithCredentials(
            p.baseUrl, p.allowInsecureTls, auth.username, auth.password, null, p.subBaseUrl,
        ).isSuccess
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

/**
 * One server's proxied traffic for the current month (Σ up+down over its
 * inbounds). [nodeId] 0 = the main panel; otherwise the sub-node id.
 */
data class ServerTraffic(
    val nodeId: Int,
    val bytes: Long,
    /** Start of the accounting period (latest inbound reset, Unix ms; 0 if unknown). */
    val sinceMillis: Long,
    /** False if any inbound in the group isn't trafficReset="monthly" — then
     *  [bytes] mixes this-month and all-time counters and should be flagged. */
    val allMonthly: Boolean,
)

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
