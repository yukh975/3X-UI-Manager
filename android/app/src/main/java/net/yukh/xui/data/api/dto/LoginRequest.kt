package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/**
 * Body for POST /login.
 *
 * `twoFactorCode` is the current 6-digit TOTP code; leave null if the user
 * hasn't enabled 2FA on the panel.
 */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val twoFactorCode: String? = null,
)
