package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/**
 * Row in GET /panel/api/clients/list on 3x-ui v3.x.
 *
 * Traffic counters live in the nested `traffic` object, NOT at the top
 * level. `tgId` is a numeric Telegram user id (0 when unset) — it is a
 * JSON number, not a string. `inboundIds` lists every inbound the client
 * is attached to.
 */
@Serializable
data class Client(
    val id: Int = 0,
    val email: String = "",
    val uuid: String = "",
    val subId: String = "",
    val flow: String = "",
    val enable: Boolean = true,
    val tgId: Long = 0,
    val limitIp: Int = 0,
    val totalGB: Long = 0,
    val expiryTime: Long = 0,
    val reset: Int = 0,
    val group: String = "",
    val comment: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val inboundIds: List<Int> = emptyList(),
    val traffic: ClientStat? = null,
) {
    val up: Long get() = traffic?.up ?: 0
    val down: Long get() = traffic?.down ?: 0
    /** Quota in bytes; 0 means unlimited. Prefer the traffic counter, fall back to totalGB. */
    val quota: Long get() = traffic?.total?.takeIf { it > 0 } ?: totalGB
    val lastOnline: Long get() = traffic?.lastOnline ?: 0
}
