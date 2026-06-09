package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/**
 * Shape of an item in GET /panel/api/inbounds/list. The list endpoint also
 * returns settings/streamSettings/sniffing as JSON strings, which we ignore
 * (ignoreUnknownKeys). The /list/slim variant omits port/protocol/listen,
 * so for the list UI we use the full /list and skip the heavy fields.
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
    val trafficReset: String = "",
    // When this inbound's traffic counter was last zeroed (Unix ms; 0 = never).
    // For a trafficReset="monthly" inbound this is the start of the current
    // accounting month, so up+down == this month's traffic.
    val lastTrafficResetTime: Long = 0,
    // Which server owns this inbound: null/0 = the main panel's own inbound,
    // N = sub-node with that id. The central /inbounds/list mixes both.
    val nodeId: Int? = null,
    val clientStats: List<ClientStat> = emptyList(),
)

/**
 * Per-client traffic counters. Appears both inside an inbound's clientStats
 * and as the `traffic` sub-object of a /clients/list entry.
 */
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
    val reset: Int = 0,
    val lastOnline: Long = 0,
)

@Serializable
data class EnableRequest(val enable: Boolean)

/** Body for client attach/detach: the inbounds to add/remove the client from. */
@Serializable
data class InboundIdsRequest(val inboundIds: List<Int>)
