package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/**
 * A remote panel registered as a node. GET /panel/api/nodes/list returns a
 * flat array of these. The first block is user-editable; the rest are
 * read-only fields filled by the central panel's heartbeat.
 */
@Serializable
data class Node(
    val id: Int = 0,
    val name: String = "",
    val remark: String = "",
    val scheme: String = "https",
    val address: String = "",
    val port: Int = 0,
    val basePath: String = "/",
    val apiToken: String = "",
    val enable: Boolean = true,
    val allowPrivateAddress: Boolean = false,
    val tlsVerifyMode: String = "verify",
    val pinnedCertSha256: String = "",
    // Route the panel→node API connection through this Xray outbound tag
    // (empty = direct). Panel v3.4.0.
    val outboundTag: String = "",
    // ---- read-only heartbeat status ----
    val status: String = "",
    val latencyMs: Long = 0,
    val xrayVersion: String = "",
    val panelVersion: String = "",
    val cpuPct: Double = 0.0,
    val memPct: Double = 0.0,
    val uptimeSecs: Long = 0,
    val lastError: String = "",
    val inboundCount: Int = 0,
    val clientCount: Int = 0,
) {
    val online: Boolean get() = status.equals("online", ignoreCase = true)

    fun toModel(): NodeModel = NodeModel(
        id = id,
        name = name,
        remark = remark,
        scheme = scheme,
        address = address,
        port = port,
        basePath = basePath,
        apiToken = apiToken,
        enable = enable,
        allowPrivateAddress = allowPrivateAddress,
        tlsVerifyMode = tlsVerifyMode,
        pinnedCertSha256 = pinnedCertSha256,
    )
}

/** Response of POST /panel/api/nodes/mtls/ca — this panel's CA cert (PEM). */
@Serializable
data class MtlsCaResponse(val caCert: String = "")

/** Body for POST /panel/api/nodes/mtls/trustCA — the CA whose client certs this
 *  panel trusts (when it acts as a node); empty disables it. Applied on restart. */
@Serializable
data class MtlsTrustCaRequest(val caCert: String)

/** Body for POST /panel/api/nodes/updatePanel — node ids to self-update.
 *  `dev=true` installs the rolling dev-latest build instead of the stable release. */
@Serializable
data class NodeIdsRequest(val ids: List<Int>, val dev: Boolean = false)

/** Editable subset sent to POST /nodes/add and /nodes/update/:id. */
@Serializable
data class NodeModel(
    val id: Int = 0,
    val name: String = "",
    val remark: String = "",
    val scheme: String = "https",
    val address: String = "",
    val port: Int = 443,
    val basePath: String = "/",
    val apiToken: String = "",
    val enable: Boolean = true,
    val allowPrivateAddress: Boolean = false,
    val tlsVerifyMode: String = "verify",
    val pinnedCertSha256: String = "",
    val outboundTag: String = "",
)
