package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/**
 * Slim shape returned by GET /panel/api/inbounds/list/slim — list pages use
 * this; detail pages should call /get/:id for the full settings JSON.
 */
@Serializable
data class InboundSlim(
    val id: Int = 0,
    val remark: String = "",
    val enable: Boolean = false,
    val listen: String = "",
    val port: Int = 0,
    val protocol: String = "",
    val up: Long = 0,
    val down: Long = 0,
    val total: Long = 0,
    val expiryTime: Long = 0,
    val tag: String = "",
    val clientStats: List<ClientStat> = emptyList(),
)

@Serializable
data class ClientStat(
    val id: Int = 0,
    val inboundId: Int = 0,
    val email: String = "",
    val enable: Boolean = true,
    val up: Long = 0,
    val down: Long = 0,
    val total: Long = 0,
    val expiryTime: Long = 0,
    val lastOnline: Long = 0,
)

@Serializable
data class EnableRequest(val enable: Boolean)
