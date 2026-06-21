package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/**
 * Envelope for endpoints that don't return a useful `obj` payload — login,
 * logout, restart/stop xray, set inbound enable, delete client.
 *
 * Splitting these out from [net.yukh.xui.data.api.ApiResponse] avoids the
 * `success && obj != null` check that's right for data endpoints but wrong
 * here (the panel returns `{"success": true, "msg": "Login successful",
 * "obj": null}` and that's a success, not a failure).
 */
@Serializable
data class ApiAck(
    val success: Boolean = false,
    val msg: String = "",
)
