package net.yukh.xui.data.api

import net.yukh.xui.data.api.dto.ServerStatus
import retrofit2.http.GET

interface XuiApi {

    @GET("panel/api/server/status")
    suspend fun getServerStatus(): ApiResponse<ServerStatus>
}
