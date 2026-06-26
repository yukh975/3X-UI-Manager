package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/** Body for POST /panel/api/clients/bulkEnable | bulkDisable. */
@Serializable
data class BulkEmailsRequest(val emails: List<String>)

/**
 * Body for POST /panel/api/clients/bulkAdjust. Shifts each client's expiry by
 * [addDays] (may be negative) and traffic limit by [addBytes] (may be negative);
 * [flow] sets the XTLS flow — "" leaves it untouched, "none" clears it, and the
 * vision values set it (only on flow-eligible inbounds).
 */
@Serializable
data class BulkAdjustRequest(
    val emails: List<String>,
    val addDays: Int = 0,
    val addBytes: Long = 0,
    val flow: String = "",
)

/** Body for POST /panel/api/clients/bulkDel. */
@Serializable
data class BulkDelRequest(val emails: List<String>, val keepTraffic: Boolean = false)

/** Body for POST /panel/api/clients/import — [data] is the stringified JSON array
 *  of {client, inboundIds} entries (the same shape GET /clients/export returns). */
@Serializable
data class ClientImportRequest(val data: String)

/** One entry of a client's IP log (POST /panel/api/clients/ips/:email).
 *  [node] is the node the IP connects through, or "" on the local panel. */
@Serializable
data class ClientIpInfo(val ip: String = "", val time: String = "", val node: String = "")
