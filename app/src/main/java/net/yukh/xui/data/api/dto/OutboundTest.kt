package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/**
 * Result of POST /panel/api/xray/testOutbound (panel 3.5.0). [delay] is the
 * round-trip in ms; [egress] is populated by HTTP/Real-delay probes from
 * Cloudflare's trace endpoint (what an external service sees after the chain).
 */
@Serializable
data class TestOutboundResult(
    val tag: String = "",
    val success: Boolean = false,
    val delay: Long = 0,
    val error: String = "",
    val mode: String = "",
    val httpStatus: Int = 0,
    val connectMs: Long = 0,
    val tlsMs: Long = 0,
    val ttfbMs: Long = 0,
    val endpoints: List<TestEndpointResult> = emptyList(),
    val egress: TestEgressResult? = null,
)

@Serializable
data class TestEndpointResult(
    val address: String = "",
    val success: Boolean = false,
    val delay: Long = 0,
    val error: String = "",
)

@Serializable
data class TestEgressResult(
    val ipv4: String = "",
    val ipv6: String = "",
    val country: String = "",
    val warp: String = "",
)
