package net.yukh.xui.data.api

import kotlinx.serialization.json.JsonElement
import net.yukh.xui.data.api.dto.LoginRequest
import net.yukh.xui.data.api.dto.ServerStatus
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface XuiApi {

    // ---- Authentication (session/cookie flow) ------------------------------

    /** Public; GETs are CSRF-safe so this works without prior state. */
    @GET("csrf-token")
    suspend fun getCsrfToken(): ApiResponse<String>

    /** Public; tells whether the panel expects a TOTP code on POST /login. */
    @POST("getTwoFactorEnable")
    suspend fun getTwoFactorEnable(): ApiResponse<Boolean>

    /** Sets the `3x-ui` session cookie when credentials are valid. */
    @POST("login")
    suspend fun login(@Body req: LoginRequest): ApiResponse<JsonElement>

    @POST("logout")
    suspend fun logout(): ApiResponse<JsonElement>

    // ---- Panel API ---------------------------------------------------------

    @GET("panel/api/server/status")
    suspend fun getServerStatus(): ApiResponse<ServerStatus>
}
