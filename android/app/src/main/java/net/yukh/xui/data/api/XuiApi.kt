package net.yukh.xui.data.api

import net.yukh.xui.data.api.dto.ApiAck
import net.yukh.xui.data.api.dto.Client
import net.yukh.xui.data.api.dto.ClientCreatePayload
import net.yukh.xui.data.api.dto.ClientModel
import net.yukh.xui.data.api.dto.EnableRequest
import net.yukh.xui.data.api.dto.InboundIdsRequest
import net.yukh.xui.data.api.dto.InboundModel
import net.yukh.xui.data.api.dto.InboundSlim
import net.yukh.xui.data.api.dto.LoginRequest
import net.yukh.xui.data.api.dto.MetricPoint
import net.yukh.xui.data.api.dto.Node
import net.yukh.xui.data.api.dto.NodeIdsRequest
import net.yukh.xui.data.api.dto.NodeModel
import net.yukh.xui.data.api.dto.PanelSettings
import net.yukh.xui.data.api.dto.PanelUpdateInfo
import net.yukh.xui.data.api.dto.ServerStatus
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface XuiApi {

    // ---- Authentication (session/cookie flow) ------------------------------

    @GET("csrf-token")
    suspend fun getCsrfToken(): ApiResponse<String>

    @POST("getTwoFactorEnable")
    suspend fun getTwoFactorEnable(): ApiResponse<Boolean>

    @POST("login")
    suspend fun login(@Body req: LoginRequest): ApiAck

    @POST("logout")
    suspend fun logout(): ApiAck

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

    @POST("panel/api/server/updatePanel")
    suspend fun updatePanel(): ApiAck

    // Re-download one built-in geo database (allowlisted .dat name) from its
    // upstream release and restart Xray. The panel also has a no-path variant
    // that updates all of them at once; the app updates per file.
    @POST("panel/api/server/updateGeofile/{fileName}")
    suspend fun updateGeofile(@Path("fileName") fileName: String): ApiAck

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
}
