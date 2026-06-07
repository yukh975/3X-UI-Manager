package net.yukh.xui.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Row in GET /panel/api/clients/list — a first-class client that may be
 * attached to multiple inbounds. Traffic counters and lastOnline come from
 * the same underlying ClientTraffic table on the panel side.
 */
@Serializable
data class Client(
    val id: Int = 0,
    val email: String = "",
    val uuid: String = "",
    val subId: String = "",
    val enable: Boolean = true,
    val up: Long = 0,
    val down: Long = 0,
    val total: Long = 0,
    val expiryTime: Long = 0,
    val lastOnline: Long = 0,
    val tgId: String = "",
    val limitIp: Int = 0,
)

/**
 * GET /panel/api/clients/links/:email — the panel renders subscription URLs
 * and a server-side QR for convenience. We use https_link as the canonical
 * subscription URL and regenerate the QR client-side with zxing so it stays
 * crisp regardless of base64 PNG resolution.
 */
@Serializable
data class ClientLinks(
    @SerialName("https_link") val httpsLink: String = "",
    @SerialName("raw_link") val rawLink: String = "",
    @SerialName("qr_code_base64") val qrCodeBase64: String = "",
)
