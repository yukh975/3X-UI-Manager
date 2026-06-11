package net.yukh.xui.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import net.yukh.xui.shared.dto.ApiAck
import net.yukh.xui.shared.dto.ApiResponse
import net.yukh.xui.shared.dto.ApiToken
import net.yukh.xui.shared.dto.Client
import net.yukh.xui.shared.dto.ClientCreatePayload
import net.yukh.xui.shared.dto.ClientModel
import net.yukh.xui.shared.dto.EnableRequest
import net.yukh.xui.shared.dto.InboundIdsRequest
import net.yukh.xui.shared.dto.InboundModel
import net.yukh.xui.shared.dto.InboundSlim
import net.yukh.xui.shared.dto.MetricPoint
import net.yukh.xui.shared.dto.Node
import net.yukh.xui.shared.dto.NodeModel
import net.yukh.xui.shared.dto.ServerStatus

/** Platform HTTP engine (Darwin on iOS, OkHttp on JVM). When [allowInsecure] is
 *  true the engine trusts self-signed / otherwise-invalid TLS certificates —
 *  mirroring the Android app's per-connection "allow self-signed TLS". */
expect fun platformHttpClient(allowInsecure: Boolean, block: HttpClientConfig<*>.() -> Unit): HttpClient

internal val sharedJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
    encodeDefaults = true
}

/**
 * Token-authenticated 3x-ui REST client, shared by Android and iOS. Mirrors the
 * Android `XuiApi` for the panel/api endpoints that work with a Bearer token.
 * (Session-only endpoints — settings, Xray config — are not here.)
 */
/** Thrown when the panel rejects our token mid-session (HTTP 401) — it was
 *  deleted or disabled in the panel. The app drops back to the Connect screen. */
class AuthExpiredException : Exception("Authentication is no longer valid")

/** A downloaded panel DB backup: raw bytes + the panel-chosen filename
 *  (x-ui.db for SQLite, x-ui.dump for PostgreSQL). */
class DbBackup(val filename: String, val bytes: ByteArray)

/** Pull the filename out of a Content-Disposition header (bare/quoted/RFC 5987). */
fun contentDispositionFilename(header: String?): String? {
    if (header.isNullOrBlank()) return null
    Regex("""filename\*=(?:UTF-8'')?["']?([^"';]+)""", RegexOption.IGNORE_CASE).find(header)?.groupValues?.get(1)?.let { return it.trim() }
    Regex("""filename=["']?([^"';]+)""", RegexOption.IGNORE_CASE).find(header)?.groupValues?.get(1)?.let { return it.trim() }
    return null
}

class PanelApi(baseUrl: String, private val token: String, private val allowInsecure: Boolean = false) {
    private val base = baseUrl.trimEnd('/')
    private val client = platformHttpClient(allowInsecure) {
        install(ContentNegotiation) { json(sharedJson) }
        // Mark requests as XHR (like the web UI) so the panel answers a rejected
        // token with 401 — not 404 — which we surface as AuthExpiredException.
        install(DefaultRequest) { header("X-Requested-With", "XMLHttpRequest") }
        HttpResponseValidator {
            validateResponse { resp ->
                if (resp.status == HttpStatusCode.Unauthorized) throw AuthExpiredException()
            }
        }
    }

    private fun HttpRequestBuilder.auth() {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    suspend fun serverStatus(): ApiResponse<ServerStatus> =
        client.get("$base/panel/api/server/status") { auth() }.body()

    /** System-metric history for a chart. [metric] e.g. cpu/mem/diskUsage/load1/
     *  netUp/tcpCount; [bucket] = aggregation seconds (~60 points). */
    suspend fun metricHistory(metric: String, bucket: Int): ApiResponse<List<MetricPoint>> =
        client.get("$base/panel/api/server/history/$metric/$bucket") { auth() }.body()

    suspend fun onlines(): ApiResponse<List<String>> =
        client.post("$base/panel/api/clients/onlines") { auth() }.body()

    suspend fun inbounds(): ApiResponse<List<InboundSlim>> =
        client.get("$base/panel/api/inbounds/list") { auth() }.body()

    /** Full inbound (with settings/streamSettings/sniffing) for round-trip edits. */
    suspend fun getInbound(id: Int): ApiResponse<InboundModel> =
        client.get("$base/panel/api/inbounds/get/$id") { auth() }.body()

    suspend fun addInbound(inbound: InboundModel): ApiAck =
        client.post("$base/panel/api/inbounds/add") {
            auth(); contentType(ContentType.Application.Json); setBody(inbound)
        }.body()

    suspend fun updateInbound(id: Int, inbound: InboundModel): ApiAck =
        client.post("$base/panel/api/inbounds/update/$id") {
            auth(); contentType(ContentType.Application.Json); setBody(inbound)
        }.body()

    suspend fun deleteInbound(id: Int): ApiAck =
        client.post("$base/panel/api/inbounds/del/$id") { auth() }.body()

    suspend fun setInboundEnable(id: Int, enable: Boolean): ApiAck =
        client.post("$base/panel/api/inbounds/setEnable/$id") {
            auth(); contentType(ContentType.Application.Json); setBody(EnableRequest(enable))
        }.body()

    suspend fun clients(): ApiResponse<List<Client>> =
        client.get("$base/panel/api/clients/list") { auth() }.body()

    suspend fun addClient(payload: ClientCreatePayload): ApiAck =
        client.post("$base/panel/api/clients/add") {
            auth(); contentType(ContentType.Application.Json); setBody(payload)
        }.body()

    suspend fun updateClient(email: String, model: ClientModel): ApiAck =
        client.post("$base/panel/api/clients/update/$email") {
            auth(); contentType(ContentType.Application.Json); setBody(model)
        }.body()

    suspend fun deleteClient(email: String): ApiAck =
        client.post("$base/panel/api/clients/del/$email") { auth() }.body()

    /** Connection (share) links for a client, e.g. ["vless://…", …]. */
    suspend fun clientLinks(email: String): ApiResponse<List<String>> =
        client.get("$base/panel/api/clients/links/$email") { auth() }.body()

    suspend fun attachClient(email: String, inboundIds: List<Int>): ApiAck =
        client.post("$base/panel/api/clients/$email/attach") {
            auth(); contentType(ContentType.Application.Json); setBody(InboundIdsRequest(inboundIds))
        }.body()

    suspend fun detachClient(email: String, inboundIds: List<Int>): ApiAck =
        client.post("$base/panel/api/clients/$email/detach") {
            auth(); contentType(ContentType.Application.Json); setBody(InboundIdsRequest(inboundIds))
        }.body()

    suspend fun nodes(): ApiResponse<List<Node>> =
        client.get("$base/panel/api/nodes/list") { auth() }.body()

    suspend fun addNode(node: NodeModel): ApiAck =
        client.post("$base/panel/api/nodes/add") {
            auth(); contentType(ContentType.Application.Json); setBody(node)
        }.body()

    suspend fun updateNode(id: Int, node: NodeModel): ApiAck =
        client.post("$base/panel/api/nodes/update/$id") {
            auth(); contentType(ContentType.Application.Json); setBody(node)
        }.body()

    suspend fun deleteNode(id: Int): ApiAck =
        client.post("$base/panel/api/nodes/del/$id") { auth() }.body()

    suspend fun setNodeEnable(id: Int, enable: Boolean): ApiAck =
        client.post("$base/panel/api/nodes/setEnable/$id") {
            auth(); contentType(ContentType.Application.Json); setBody(EnableRequest(enable))
        }.body()

    /**
     * Online clients reported by a specific node, queried directly against that
     * node's own 3x-ui API (each node carries its own scheme/address/port/
     * basePath/apiToken). The central panel has no per-node onlines endpoint, and
     * since xray keys online by email per server, this is the only way to see
     * which node a multi-server client is actually on. Mirrors Android's
     * PanelRepository.listNodeOnlines.
     */
    suspend fun nodeOnlines(node: Node): ApiResponse<List<String>> {
        val bp = node.basePath.ifBlank { "/" }
            .let { if (it.startsWith("/")) it else "/$it" }
            .let { if (it.endsWith("/")) it else "$it/" }
        val nbase = "${node.scheme}://${node.address}:${node.port}$bp".trimEnd('/')
        val c = platformHttpClient(allowInsecure) { install(ContentNegotiation) { json(sharedJson) } }
        return try {
            c.post("$nbase/panel/api/clients/onlines") {
                header(HttpHeaders.Authorization, "Bearer ${node.apiToken}")
            }.body()
        } finally {
            c.close()
        }
    }

    /** Full Xray config — obj is a JSON string wrapping {xraySetting, outboundTestUrl};
     *  parse with parseXrayObj(). Token-accessible since panel v3.3.0. */
    suspend fun getXraySetting(): ApiResponse<String> =
        client.post("$base/panel/api/xray/") { auth() }.body()

    suspend fun updateXraySetting(xraySetting: String, outboundTestUrl: String): ApiAck =
        client.submitForm(
            url = "$base/panel/api/xray/update",
            formParameters = parameters {
                append("xraySetting", xraySetting)
                append("outboundTestUrl", outboundTestUrl)
            },
        ) { auth() }.body()

    suspend fun restartXray(): ApiAck =
        client.post("$base/panel/api/server/restartXrayService") { auth() }.body()

    suspend fun stopXray(): ApiAck =
        client.post("$base/panel/api/server/stopXrayService") { auth() }.body()

    /** Re-download one built-in geo database (.dat) and restart Xray. */
    suspend fun updateGeofile(fileName: String): ApiAck =
        client.post("$base/panel/api/server/updateGeofile/$fileName") { auth() }.body()

    // ---- Backup / restore -------------------------------------------------

    /** Download the panel database backup (the whole DB). Engine-agnostic: the
     *  panel returns x-ui.db (SQLite) or x-ui.dump (PostgreSQL); the filename
     *  comes back in Content-Disposition. */
    suspend fun getDb(): DbBackup {
        val resp = client.get("$base/panel/api/server/getDb") { auth() }
        val name = contentDispositionFilename(resp.headers[HttpHeaders.ContentDisposition]) ?: "x-ui.db"
        return DbBackup(name, resp.body())
    }

    /** Restore the panel from a backup file (multipart field `db`); the panel
     *  imports it under its own engine and restarts Xray. */
    suspend fun importDb(filename: String, bytes: ByteArray): ApiAck =
        client.submitFormWithBinaryData(
            url = "$base/panel/api/server/importDB",
            formData = formData {
                append("db", bytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                })
            },
        ) { auth() }.body()

    // ---- Panel admin (settings) -------------------------------------------

    suspend fun updateUser(oldUsername: String, oldPassword: String, newUsername: String, newPassword: String): ApiAck =
        client.submitForm(
            url = "$base/panel/api/setting/updateUser",
            formParameters = parameters {
                append("oldUsername", oldUsername)
                append("oldPassword", oldPassword)
                append("newUsername", newUsername)
                append("newPassword", newPassword)
            },
        ) { auth() }.body()

    suspend fun restartPanel(): ApiAck =
        client.post("$base/panel/api/setting/restartPanel") { auth() }.body()

    suspend fun listApiTokens(): ApiResponse<List<ApiToken>> =
        client.get("$base/panel/api/setting/apiTokens") { auth() }.body()

    suspend fun createApiToken(name: String): ApiResponse<ApiToken> =
        client.submitForm(
            url = "$base/panel/api/setting/apiTokens/create",
            formParameters = parameters { append("name", name) },
        ) { auth() }.body()

    suspend fun deleteApiToken(id: Int): ApiAck =
        client.post("$base/panel/api/setting/apiTokens/delete/$id") { auth() }.body()

    suspend fun setApiTokenEnabled(id: Int, enabled: Boolean): ApiAck =
        client.submitForm(
            url = "$base/panel/api/setting/apiTokens/setEnabled/$id",
            formParameters = parameters { append("enabled", enabled.toString()) },
        ) { auth() }.body()

    fun close() = client.close()
}
