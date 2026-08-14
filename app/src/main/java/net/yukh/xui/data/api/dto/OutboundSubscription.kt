package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/**
 * A remote outbound list the panel fetches and merges into the Xray config
 * (`GET /panel/api/xray/outbound-subs`) — the panel's "Subscriptions" button on
 * the Outbounds page.
 *
 * `lastFetchedOutbounds` (the cached outbound JSON) is deliberately not modelled:
 * it is large and the app reads the outbounds through refresh/preview instead.
 */
@Serializable
data class OutboundSubscription(
    val id: Int = 0,
    val remark: String = "",
    val url: String = "",
    val enabled: Boolean = true,
    /** Prefix given to every imported outbound tag, e.g. `hk-`. */
    val tagPrefix: String = "",
    /** Seconds between automatic refreshes; the panel defaults to 600. */
    val updateInterval: Int = 600,
    /** Order among subscriptions in the merged config (lower = earlier). */
    val priority: Int = 0,
    /** Place this subscription's outbounds before the manually defined ones. */
    val prepend: Boolean = false,
    /** Permit a private/LAN subscription URL — off by default (SSRF guard). */
    val allowPrivate: Boolean = false,
    /** Skip TLS verification when fetching the subscription URL. */
    val allowInsecure: Boolean = false,
    /** Unix ms of the last successful fetch; 0 = never fetched. */
    val lastUpdated: Long = 0,
    /** Message from the last failed fetch, empty when the last one succeeded. */
    val lastError: String = "",
    /** How many outbounds the last fetch produced. */
    val outboundCount: Int = 0,
)
