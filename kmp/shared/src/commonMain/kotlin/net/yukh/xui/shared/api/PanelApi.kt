package net.yukh.xui.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import net.yukh.xui.shared.dto.ApiAck
import net.yukh.xui.shared.dto.ApiResponse
import net.yukh.xui.shared.dto.Client
import net.yukh.xui.shared.dto.InboundSlim
import net.yukh.xui.shared.dto.Node
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

    suspend fun clients(): ApiResponse<List<Client>> =
        client.get("$base/panel/api/clients/list") { auth() }.body()

    suspend fun nodes(): ApiResponse<List<Node>> =
        client.get("$base/panel/api/nodes/list") { auth() }.body()

    suspend fun restartXray(): ApiAck =
        client.post("$base/panel/api/server/restartXrayService") { auth() }.body()

    suspend fun stopXray(): ApiAck =
        client.post("$base/panel/api/server/stopXrayService") { auth() }.body()

    fun close() = client.close()
}
