package net.yukh.xui.shared.dto

import kotlinx.serialization.Serializable

/**
 * A panel API token. In the **list** [token] is the stored SHA-256 hash (not
 * usable); the plaintext is returned **only once**, in the create response, for
 * the user to copy.
 */
@Serializable
data class ApiToken(
    val id: Int = 0,
    val name: String = "",
    val token: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = 0,
)
