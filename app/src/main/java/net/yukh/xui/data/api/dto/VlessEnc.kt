package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/**
 * One generated VLESS-encryption key option from GET /panel/api/server/getNewVlessEnc.
 * The panel runs `xray vlessenc` and derives the xorpub/random variants, so each
 * [label] is e.g. "X25519, native" / "ML-KEM-768 (random)" with a ready key pair.
 */
@Serializable
data class VlessEncAuth(
    val id: String = "",
    val label: String = "",
    val decryption: String = "",
    val encryption: String = "",
)

/** Envelope `obj` of GET /panel/api/server/getNewVlessEnc. */
@Serializable
data class VlessEncResponse(val auths: List<VlessEncAuth> = emptyList())
