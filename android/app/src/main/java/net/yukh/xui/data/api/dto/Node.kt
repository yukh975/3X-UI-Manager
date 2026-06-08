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
)
