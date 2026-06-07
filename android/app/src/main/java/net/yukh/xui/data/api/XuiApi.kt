package net.yukh.xui.data.api

import kotlinx.serialization.json.JsonElement
import net.yukh.xui.data.api.dto.Client
import net.yukh.xui.data.api.dto.ClientLinks
import net.yukh.xui.data.api.dto.EnableRequest
import net.yukh.xui.data.api.dto.InboundSlim
import net.yukh.xui.data.api.dto.LoginRequest
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
    suspend fun login(@Body req: LoginRequest): ApiResponse<JsonElement>

    @POST("logout")
    suspend fun logout(): ApiResponse<JsonElement>

    // ---- Server -----------------------------------------------------------

    @GET("panel/api/server/status")
    suspend fun getServerStatus(): ApiResponse<ServerStatus>

    @POST("panel/api/server/restartXrayService")
    suspend fun restartXray(): ApiResponse<JsonElement>

    @POST("panel/api/server/stopXrayService")
    suspend fun stopXray(): ApiResponse<JsonElement>

    // ---- Inbounds ---------------------------------------------------------

    @GET("panel/api/inbounds/list/slim")
    suspend fun listInboundsSlim(): ApiResponse<List<InboundSlim>>

    @POST("panel/api/inbounds/setEnable/{id}")
    suspend fun setInboundEnable(
        @Path("id") id: Int,
        @Body body: EnableRequest,
    ): ApiResponse<JsonElement>

    // ---- Clients ----------------------------------------------------------

    @GET("panel/api/clients/list")
    suspend fun listClients(): ApiResponse<List<Client>>

    @GET("panel/api/clients/links/{email}")
    suspend fun getClientLinks(@Path("email") email: String): ApiResponse<ClientLinks>

    @POST("panel/api/clients/del/{email}")
    suspend fun deleteClient(@Path("email") email: String): ApiResponse<JsonElement>

    @POST("panel/api/clients/onlines")
    suspend fun listOnlines(): ApiResponse<List<String>>
}
