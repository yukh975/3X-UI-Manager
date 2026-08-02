package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/**
 * The subscription server's `?format=info` snapshot (panel v3.6.0+): the same
 * live status a subscriber's own client app sees when it refreshes the link,
 * minus the config links. Fetched best-effort from the public sub URL, so the
 * app can show the customer's-eye view next to the share QR.
 *
 * Only the human-readable status fields are modeled; the endpoint returns more
 * (byte counters, sub URLs, announce, …) which lenient parsing drops. Every
 * field the server sends here is a string or bool, so the shape is stable.
 */
@Serializable
data class SubInfo(
    val isOnline: Boolean = false,
    val enabled: Boolean = true,
    val used: String = "",
    val total: String = "",
    val remained: String = "",
    val download: String = "",
    val upload: String = "",
)
