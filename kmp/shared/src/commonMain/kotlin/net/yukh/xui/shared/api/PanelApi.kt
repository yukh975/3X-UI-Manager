package net.yukh.xui.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import net.yukh.xui.shared.dto.ApiAck
import net.yukh.xui.shared.dto.ApiResponse
import net.yukh.xui.shared.dto.Client
import net.yukh.xui.shared.dto.ClientCreatePayload
import net.yukh.xui.shared.dto.ClientModel
import net.yukh.xui.shared.dto.EnableRequest
import net.yukh.xui.shared.dto.InboundIdsRequest
import net.yukh.xui.shared.dto.InboundModel
import net.yukh.xui.shared.dto.InboundSlim
import net.yukh.xui.shared.dto.Node
import net.yukh.xui.shared.dto.NodeModel
import net.yukh.xui.shared.dto.ServerStatus

/** Platform HTTP engine (Darwin on iOS, OkHttp on JVM). */
expect fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient

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
class PanelApi(baseUrl: String, private val token: String) {
    private val base = baseUrl.trimEnd('/')
    private val client = platformHttpClient {
        install(ContentNegotiation) { json(sharedJson) }
    }

    private fun HttpRequestBuilder.auth() {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    suspend fun serverStatus(): ApiResponse<ServerStatus> =
        client.get("$base/panel/api/server/status") { auth() }.body()

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
        val c = platformHttpClient { install(ContentNegotiation) { json(sharedJson) } }
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

    fun close() = client.close()
}
