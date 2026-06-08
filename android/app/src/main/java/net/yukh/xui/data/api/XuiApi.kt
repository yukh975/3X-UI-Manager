package net.yukh.xui.data.api

import net.yukh.xui.data.api.dto.ApiAck
import net.yukh.xui.data.api.dto.Client
import net.yukh.xui.data.api.dto.ClientCreatePayload
import net.yukh.xui.data.api.dto.ClientModel
import net.yukh.xui.data.api.dto.EnableRequest
import net.yukh.xui.data.api.dto.InboundSlim
import net.yukh.xui.data.api.dto.LoginRequest
import net.yukh.xui.data.api.dto.PanelSettings
import net.yukh.xui.data.api.dto.ServerStatus
import retrofit2.http.Body
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

    @POST("panel/api/server/restartXrayService")
    suspend fun restartXray(): ApiAck

    @POST("panel/api/server/stopXrayService")
    suspend fun stopXray(): ApiAck

    // ---- Inbounds ---------------------------------------------------------

    // /list (not /list/slim): slim strips port/protocol/listen which the list
    // UI shows. The heavy settings JSON the full list adds is ignored.
    @GET("panel/api/inbounds/list")
    suspend fun listInbounds(): ApiResponse<List<InboundSlim>>

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

    @POST("panel/api/clients/onlines")
    suspend fun listOnlines(): ApiResponse<List<String>>

    // ---- Settings (session-auth only; token gets redirected to login) -----

    @POST("panel/setting/all")
    suspend fun getAllSettings(): ApiResponse<PanelSettings>
}
