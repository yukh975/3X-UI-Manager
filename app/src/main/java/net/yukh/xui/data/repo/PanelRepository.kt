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
import net.yukh.xui.data.api.dto.ApiToken
import net.yukh.xui.data.api.dto.Client
import net.yukh.xui.data.api.dto.ClientCreatePayload
import net.yukh.xui.data.api.dto.ClientModel
import net.yukh.xui.data.api.dto.EnableRequest
import net.yukh.xui.data.api.dto.MetricPoint
import net.yukh.xui.data.api.dto.InboundIdsRequest
import net.yukh.xui.data.api.dto.InboundModel
import net.yukh.xui.data.api.dto.InboundSlim
import net.yukh.xui.data.api.dto.Node
import net.yukh.xui.data.api.dto.BulkAdjustRequest
import net.yukh.xui.data.api.dto.BulkDelRequest
import net.yukh.xui.data.api.dto.BulkEmailsRequest
import net.yukh.xui.data.api.dto.NodeIdsRequest
import net.yukh.xui.data.api.dto.VlessEncAuth
import net.yukh.xui.data.api.dto.NodeModel
import net.yukh.xui.data.api.dto.PanelSettings
import net.yukh.xui.data.api.dto.PanelUpdateInfo
import net.yukh.xui.data.api.dto.ServerStatus
import net.yukh.xui.data.api.dto.XraySettingEnvelope
import net.yukh.xui.data.prefs.ConnectionAuth
import net.yukh.xui.data.prefs.ConnectionProfile
import net.yukh.xui.data.prefs.ConnectionStore
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
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

    /** Set when a mid-session auth failure forces a drop to Connect (so that
     *  screen can explain why); cleared when a fresh connect is attempted. */
    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private var api: XuiApi? = null
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
            api = candidate
            currentBaseUrl = baseUrl
            currentSubBase = subBaseUrl
            cachedSettings = null
            _connected.value = true
            _authError.value = null
        }
    }

    fun unbind() {
        api = null
        currentBaseUrl = null
        currentSubBase = null
        cachedSettings = null
        _connected.value = false
        store.clear()
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

    /** Generate VLESS-encryption key options (X25519 / ML-KEM-768 × native/xorpub/random). */
    suspend fun getNewVlessEnc(): Result<List<VlessEncAuth>> =
        authedData { it.getNewVlessEnc() }.map { it.auths }

    suspend fun updatePanel(): Result<Unit> =
        authedAck { it.updatePanel() }

    /** Re-download one built-in geo database and restart Xray. [fileName] must be
     *  an allowlisted .dat name (e.g. "geoip.dat"); the panel rejects others. */
    suspend fun updateGeofile(fileName: String): Result<Unit> =
        authedAck { it.updateGeofile(fileName) }

    /** Re-download all built-in geo databases at once and restart Xray. */
    suspend fun updateAllGeofiles(): Result<Unit> =
        authedAck { it.updateAllGeofiles() }

    // ---- Backup / restore -------------------------------------------------

    /** Download the panel's database backup. The panel chooses the engine-
     *  specific filename (x-ui.db / x-ui.dump), returned in Content-Disposition;
     *  the whole config (settings, inbounds, clients, Xray config) lives in it. */
    suspend fun downloadDb(): Result<DbBackup> {
        val current = api ?: return Result.failure(PanelError.NotConnected)
        return catching {
            val resp = current.getDb()
            val body = resp.body()
            if (!resp.isSuccessful || body == null) {
                Result.failure(PanelError.Http(resp.code()))
            } else {
                val name = contentDispositionFilename(resp.headers()["Content-Disposition"]) ?: "x-ui.db"
                Result.success(DbBackup(name, body.bytes()))
            }
        }
    }

    /** Restore the panel from a backup file. The panel imports it under its own
     *  engine and restarts Xray (a brief connection drop). */
    suspend fun importDb(filename: String, bytes: ByteArray): Result<Unit> {
        val part = MultipartBody.Part.createFormData(
            "db", filename, bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()),
        )
        return authedAck { it.importDb(part) }
    }

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
     * Build a client's subscription URL. The base is either the user-set
     * override or read from panel settings; on v3.3.0 those settings are
     * token-readable (the setting call moved under /panel/api), so this works
     * with a token too — only older panels need a login session. The result is
     * cached so repeated share-sheet opens don't re-fetch settings.
     */
    suspend fun getSubscriptionUrl(client: Client): String? {
        if (client.subId.isBlank()) return null
        // Prefer the user-set subscription base (handy for reverse proxies).
        currentSubBase?.takeIf { it.isNotBlank() }?.let { base ->
            val b = if (base.endsWith("/")) base else "$base/"
            return b + client.subId
        }
        // Otherwise read the base from panel settings (token-readable on v3.3.0).
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

    // ---- Bulk client actions (panel v3.4.1) -------------------------------

    suspend fun bulkSetClientsEnabled(emails: List<String>, enable: Boolean): Result<Unit> =
        authedAck {
            val body = BulkEmailsRequest(emails)
            if (enable) it.bulkEnableClients(body) else it.bulkDisableClients(body)
        }

    suspend fun bulkAdjustClients(emails: List<String>, addDays: Int, addBytes: Long, flow: String): Result<Unit> =
        authedAck { it.bulkAdjustClients(BulkAdjustRequest(emails, addDays, addBytes, flow)) }

    suspend fun bulkDeleteClients(emails: List<String>): Result<Unit> =
        authedAck { it.bulkDeleteClients(BulkDelRequest(emails)) }

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

    /** Trigger a 3x-ui self-update on the given node(s) via the central panel.
     *  [dev] = install the rolling dev-latest build instead of the stable release. */
    suspend fun updateNodes(ids: List<Int>, dev: Boolean = false): Result<Unit> =
        authedAck { it.updateNodePanel(NodeIdsRequest(ids, dev)) }

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

    // ---- Panel admin (settings) -------------------------------------------

    /** Change the admin username + password; old credentials must match. */
    suspend fun changeCredentials(
        oldUsername: String, oldPassword: String, newUsername: String, newPassword: String,
    ): Result<Unit> = authedAck { it.updateUser(oldUsername, oldPassword, newUsername, newPassword) }

    /** Restart the panel service (the connection drops briefly). */
    suspend fun restartPanel(): Result<Unit> = authedAck { it.restartPanel() }

    suspend fun listApiTokens(): Result<List<ApiToken>> = authedData { it.listApiTokens() }

    /** Create a token; the result carries the plaintext value (shown once). */
    suspend fun createApiToken(name: String): Result<ApiToken> = authedData { it.createApiToken(name) }

    suspend fun deleteApiToken(id: Int): Result<Unit> = authedAck { it.deleteApiToken(id) }

    suspend fun setApiTokenEnabled(id: Int, enabled: Boolean): Result<Unit> =
        authedAck { it.setApiTokenEnabled(id, enabled) }

    // ---- Internals --------------------------------------------------------

    private fun bindTokenInternal(baseUrl: String, allowInsecureTls: Boolean, token: String) {
        api = XuiApiFactory.tokenAuthed(baseUrl, allowInsecureTls, token, json)
        currentBaseUrl = baseUrl
        cachedSettings = null
        _connected.value = true
        _authError.value = null
    }

    private suspend inline fun <T> authedData(
        crossinline block: suspend (XuiApi) -> ApiResponse<T>,
    ): Result<T> {
        val current = api ?: return Result.failure(PanelError.NotConnected)
        val r = safeData { block(current) }
        if (isUnauthorized(r)) onAuthLost()
        return r
    }

    private suspend inline fun authedAck(
        crossinline block: suspend (XuiApi) -> ApiAck,
    ): Result<Unit> {
        val current = api ?: return Result.failure(PanelError.NotConnected)
        val r = safeAck { block(current) }
        if (isUnauthorized(r)) onAuthLost()
        return r
    }

    /**
     * A mid-session 401 means the API token is no longer valid (deleted or
     * disabled in the panel). Drop to the Connect screen with a message instead
     * of leaving the app "connected" but failing every call. The profile stays
     * saved so the fields are pre-filled for a quick fix.
     */
    private fun onAuthLost() {
        if (!_connected.value) return
        _authError.value = "Your API token is no longer valid. Reconnect with a working one."
        _connected.value = false
    }

    private fun isUnauthorized(r: Result<*>): Boolean {
        val e = r.exceptionOrNull()
        return e is PanelError.Http && e.code == 401
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

/** A downloaded panel database backup: the raw bytes + the panel-chosen
 *  filename (x-ui.db for SQLite, x-ui.dump for PostgreSQL). */
class DbBackup(val filename: String, val bytes: ByteArray)

/** Pull the filename out of a Content-Disposition header value, handling the
 *  bare, quoted, and RFC 5987 (filename*=) forms. Null if absent. */
internal fun contentDispositionFilename(header: String?): String? {
    if (header.isNullOrBlank()) return null
    Regex("""filename\*=(?:UTF-8'')?["']?([^"';]+)""", RegexOption.IGNORE_CASE)
        .find(header)?.groupValues?.get(1)?.let { return it.trim() }
    Regex("""filename=["']?([^"';]+)""", RegexOption.IGNORE_CASE)
        .find(header)?.groupValues?.get(1)?.let { return it.trim() }
    return null
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
