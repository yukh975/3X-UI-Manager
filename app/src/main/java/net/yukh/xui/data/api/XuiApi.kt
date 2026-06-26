package net.yukh.xui.data.api

import net.yukh.xui.data.api.dto.ApiAck
import net.yukh.xui.data.api.dto.BulkAdjustRequest
import net.yukh.xui.data.api.dto.BulkDelRequest
import net.yukh.xui.data.api.dto.BulkEmailsRequest
import net.yukh.xui.data.api.dto.ClientImportRequest
import net.yukh.xui.data.api.dto.VlessEncResponse
import kotlinx.serialization.json.JsonElement
import net.yukh.xui.data.api.dto.ApiToken
import net.yukh.xui.data.api.dto.Client
import net.yukh.xui.data.api.dto.ClientCreatePayload
import net.yukh.xui.data.api.dto.ClientModel
import net.yukh.xui.data.api.dto.EnableRequest
import net.yukh.xui.data.api.dto.InboundIdsRequest
import net.yukh.xui.data.api.dto.InboundModel
import net.yukh.xui.data.api.dto.InboundSlim
import net.yukh.xui.data.api.dto.MetricPoint
import net.yukh.xui.data.api.dto.MtlsCaResponse
import net.yukh.xui.data.api.dto.MtlsTrustCaRequest
import net.yukh.xui.data.api.dto.Node
import net.yukh.xui.data.api.dto.NodeIdsRequest
import net.yukh.xui.data.api.dto.NodeModel
import net.yukh.xui.data.api.dto.PanelSettings
import net.yukh.xui.data.api.dto.PanelUpdateInfo
import net.yukh.xui.data.api.dto.ServerStatus
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming

interface XuiApi {

    // ---- Server -----------------------------------------------------------

    @GET("panel/api/server/status")
    suspend fun getServerStatus(): ApiResponse<ServerStatus>

    // System-metrics history for the dashboard charts. metric ∈ SystemMetricKeys
    // (cpu, mem, swap, netUp, netDown, diskUsage, tcpCount, udpCount, load1…);
    // bucket ∈ {2, 30, 60, 120, 180, 300} seconds (≤ 60 points returned).
    @GET("panel/api/server/history/{metric}/{bucket}")
    suspend fun metricHistory(
        @Path("metric") metric: String,
        @Path("bucket") bucket: Int,
    ): ApiResponse<List<MetricPoint>>

    @POST("panel/api/server/restartXrayService")
    suspend fun restartXray(): ApiAck

    @POST("panel/api/server/stopXrayService")
    suspend fun stopXray(): ApiAck

    @GET("panel/api/server/getPanelUpdateInfo")
    suspend fun getPanelUpdateInfo(): ApiResponse<PanelUpdateInfo>

    // Generate VLESS-encryption key options (X25519 / ML-KEM-768 × native /
    // xorpub / random) via `xray vlessenc` (panel v3.4.1, xray-core v26.6.21+).
    @GET("panel/api/server/getNewVlessEnc")
    suspend fun getNewVlessEnc(): ApiResponse<VlessEncResponse>

    @POST("panel/api/server/updatePanel")
    suspend fun updatePanel(): ApiAck

    // Re-download one built-in geo database (allowlisted .dat name) from its
    // upstream release and restart Xray. The no-path variant updates all of
    // them at once.
    @POST("panel/api/server/updateGeofile/{fileName}")
    suspend fun updateGeofile(@Path("fileName") fileName: String): ApiAck

    @POST("panel/api/server/updateGeofile")
    suspend fun updateAllGeofiles(): ApiAck

    // ---- Backup / restore -------------------------------------------------

    // Download the panel database backup. Streaming; the engine-specific
    // filename (x-ui.db on SQLite, x-ui.dump on PostgreSQL) is returned in the
    // Content-Disposition header — the panel picks it, so the client stays
    // engine-agnostic.
    @Streaming
    @GET("panel/api/server/getDb")
    suspend fun getDb(): Response<ResponseBody>

    // Restore the panel from a backup file (multipart form field `db`). The
    // panel imports it (under its own engine) and restarts the Xray service.
    @Multipart
    @POST("panel/api/server/importDB")
    suspend fun importDb(@Part file: MultipartBody.Part): ApiAck

    // ---- Inbounds ---------------------------------------------------------

    // /list (not /list/slim): slim strips port/protocol/listen which the list
    // UI shows. The heavy settings JSON the full list adds is ignored.
    @GET("panel/api/inbounds/list")
    suspend fun listInbounds(): ApiResponse<List<InboundSlim>>

    @GET("panel/api/inbounds/get/{id}")
    suspend fun getInbound(@Path("id") id: Int): ApiResponse<InboundModel>

    @POST("panel/api/inbounds/add")
    suspend fun addInbound(@Body inbound: InboundModel): ApiAck

    @POST("panel/api/inbounds/update/{id}")
    suspend fun updateInbound(@Path("id") id: Int, @Body inbound: InboundModel): ApiAck

    @POST("panel/api/inbounds/del/{id}")
    suspend fun deleteInbound(@Path("id") id: Int): ApiAck

    @POST("panel/api/inbounds/setEnable/{id}")
    suspend fun setInboundEnable(
        @Path("id") id: Int,
        @Body body: EnableRequest,
    ): ApiAck

    // ---- Clients ----------------------------------------------------------

    @GET("panel/api/clients/list")
    suspend fun listClients(): ApiResponse<List<Client>>

    @POST("panel/api/clients/add")
    suspend fun addClient(@Body payload: ClientCreatePayload): ApiAck

    @POST("panel/api/clients/update/{email}")
    suspend fun updateClient(
        @Path("email") email: String,
        @Body client: ClientModel,
    ): ApiAck

    // obj is an array of subscription link strings, e.g. ["vless://…"].
    @GET("panel/api/clients/links/{email}")
    suspend fun getClientLinks(@Path("email") email: String): ApiResponse<List<String>>

    @POST("panel/api/clients/del/{email}")
    suspend fun deleteClient(@Path("email") email: String): ApiAck

    @POST("panel/api/clients/{email}/attach")
    suspend fun attachClient(
        @Path("email") email: String,
        @Body body: InboundIdsRequest,
    ): ApiAck

    @POST("panel/api/clients/{email}/detach")
    suspend fun detachClient(
        @Path("email") email: String,
        @Body body: InboundIdsRequest,
    ): ApiAck

    // Bulk actions over a set of client emails (panel v3.4.1).
    @POST("panel/api/clients/bulkEnable")
    suspend fun bulkEnableClients(@Body body: BulkEmailsRequest): ApiAck

    @POST("panel/api/clients/bulkDisable")
    suspend fun bulkDisableClients(@Body body: BulkEmailsRequest): ApiAck

    @POST("panel/api/clients/bulkAdjust")
    suspend fun bulkAdjustClients(@Body body: BulkAdjustRequest): ApiAck

    @POST("panel/api/clients/bulkDel")
    suspend fun bulkDeleteClients(@Body body: BulkDelRequest): ApiAck

    // Export/import all clients + delete unbound (orphan) clients (panel v3.4.0).
    @GET("panel/api/clients/export")
    suspend fun exportClients(): ApiResponse<JsonElement>

    @POST("panel/api/clients/import")
    suspend fun importClients(@Body body: ClientImportRequest): ApiAck

    @POST("panel/api/clients/delOrphans")
    suspend fun deleteOrphanClients(): ApiAck

    @POST("panel/api/clients/onlines")
    suspend fun listOnlines(): ApiResponse<List<String>>

    // ---- Nodes ------------------------------------------------------------

    @GET("panel/api/nodes/list")
    suspend fun listNodes(): ApiResponse<List<Node>>

    @POST("panel/api/nodes/add")
    suspend fun addNode(@Body node: NodeModel): ApiAck

    @POST("panel/api/nodes/update/{id}")
    suspend fun updateNode(@Path("id") id: Int, @Body node: NodeModel): ApiAck

    @POST("panel/api/nodes/del/{id}")
    suspend fun deleteNode(@Path("id") id: Int): ApiAck

    @POST("panel/api/nodes/setEnable/{id}")
    suspend fun setNodeEnable(@Path("id") id: Int, @Body body: EnableRequest): ApiAck

    // Trigger a 3x-ui self-update on the given node(s); the central panel relays
    // it to each node. Body: {"ids":[...]}.
    @POST("panel/api/nodes/updatePanel")
    suspend fun updateNodePanel(@Body body: NodeIdsRequest): ApiAck

    // Mutual-TLS between panels (panel v3.4.0): fetch this panel's CA to register
    // on a node, and set the CA whose client certs this panel trusts as a node.
    @POST("panel/api/nodes/mtls/ca")
    suspend fun nodeMtlsCa(): ApiResponse<MtlsCaResponse>

    @POST("panel/api/nodes/mtls/trustCA")
    suspend fun setNodeMtlsTrustCA(@Body body: MtlsTrustCaRequest): ApiAck

    // ---- Settings & Xray config -------------------------------------------
    // Panel v3.3.0 (upstream c6f15cd5) moved these under /panel/api/* so they now
    // accept a Bearer token too (were session-only at /panel/setting/* and
    // /panel/xray/*, which v3.3.0 removed). Requires panel ≥ v3.3.0.

    @POST("panel/api/setting/all")
    suspend fun getAllSettings(): ApiResponse<PanelSettings>

    /** Returns the wrapped config as a JSON string in `obj` (parse twice). */
    @POST("panel/api/xray/")
    suspend fun getXraySetting(): ApiResponse<String>

    @FormUrlEncoded
    @POST("panel/api/xray/update")
    suspend fun updateXraySetting(
        @Field("xraySetting") xraySetting: String,
        @Field("outboundTestUrl") outboundTestUrl: String,
    ): ApiAck

    // ---- Panel admin (settings) -------------------------------------------

    // Change the admin username + password. The panel verifies the old
    // credentials against the logged-in (token → first admin) user.
    @FormUrlEncoded
    @POST("panel/api/setting/updateUser")
    suspend fun updateUser(
        @Field("oldUsername") oldUsername: String,
        @Field("oldPassword") oldPassword: String,
        @Field("newUsername") newUsername: String,
        @Field("newPassword") newPassword: String,
    ): ApiAck

    // Restart the panel service (after a short delay) — the app's connection
    // drops briefly while it comes back.
    @POST("panel/api/setting/restartPanel")
    suspend fun restartPanel(): ApiAck

    @GET("panel/api/setting/apiTokens")
    suspend fun listApiTokens(): ApiResponse<List<ApiToken>>

    // Returns the new token with its plaintext value (shown only once).
    @FormUrlEncoded
    @POST("panel/api/setting/apiTokens/create")
    suspend fun createApiToken(@Field("name") name: String): ApiResponse<ApiToken>

    @POST("panel/api/setting/apiTokens/delete/{id}")
    suspend fun deleteApiToken(@Path("id") id: Int): ApiAck

    @FormUrlEncoded
    @POST("panel/api/setting/apiTokens/setEnabled/{id}")
    suspend fun setApiTokenEnabled(@Path("id") id: Int, @Field("enabled") enabled: Boolean): ApiAck
}
