package net.yukh.xui.data.api

import kotlinx.serialization.Serializable

/**
 * Common response envelope used by every 3x-ui REST endpoint.
 * See web/entity/Msg.go on the panel side.
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean = false,
    val msg: String = "",
    val obj: T? = null,
)
