package net.yukh.xui.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the panel's `model.Client` — the body shape for client create
 * (wrapped in [ClientCreatePayload]) and update (sent directly).
 *
 * Leave [id]/[password]/[auth]/[subId] empty on create: the panel fills
 * them per the target inbound's protocol (fillProtocolDefaults in
 * web/service/client.go). [totalGB] is in BYTES despite the name — the panel
 * compares it directly against byte counters.
 */
@Serializable
data class ClientModel(
    val id: String = "",
    val email: String = "",
    val password: String = "",
    val auth: String = "",
    val secret: String = "",
    val adTag: String = "",
    val privateKey: String = "",
    val publicKey: String = "",
    val preSharedKey: String = "",
    val allowedIPs: List<String> = emptyList(),
    val keepAlive: Int = 0,
    val security: String = "auto",
    val flow: String = "",
    val limitIp: Int = 0,
    val totalGB: Long = 0,
    val expiryTime: Long = 0,
    val enable: Boolean = true,
    val tgId: Long = 0,
    val subId: String = "",
    val group: String = "",
    val comment: String = "",
    val reset: Int = 0,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
)

@Serializable
data class ClientCreatePayload(
    val client: ClientModel,
    val inboundIds: List<Int>,
)
