package net.yukh.xui.shared.dto

import kotlinx.serialization.Serializable

/**
 * Subset of POST /panel/api/setting/all needed to build a client's
 * subscription URL (the endpoint returns many more fields —
 * ignoreUnknownKeys drops them). Token-readable since panel v3.3.0.
 * Mirrors the Android app's PanelSettings.
 */
@Serializable
data class PanelSubSettings(
    val subEnable: Boolean = false,
    val subURI: String = "",
    val subPath: String = "/sub/",
    val subPort: Int = 0,
    val subDomain: String = "",
    val subCertFile: String = "",
    val subKeyFile: String = "",
) {
    /**
     * Build the subscription URL for a client, replicating the panel's
     * BuildSubURIBase logic (web/service/setting.go). Returns null when
     * subscriptions are disabled or the client has no subId.
     *
     * @param panelHost hostname from the connection base URL, used only when
     *   subDomain is unset.
     */
    fun subscriptionUrl(panelHost: String, subId: String): String? {
        if (!subEnable || subId.isBlank()) return null
        val base = if (subURI.isNotBlank()) {
            if (subURI.endsWith("/")) subURI else "$subURI/"
        } else {
            val tls = subCertFile.isNotBlank() && subKeyFile.isNotBlank()
            val scheme = if (tls) "https" else "http"
            val domain = subDomain.ifBlank { panelHost }
            val authority = when {
                subPort == 443 && tls -> domain
                subPort == 80 && !tls -> domain
                subPort > 0 -> "$domain:$subPort"
                else -> domain
            }
            val path = subPath.ifBlank { "/sub/" }
                .let { if (it.startsWith("/")) it else "/$it" }
                .let { if (it.endsWith("/")) it else "$it/" }
            "$scheme://$authority$path"
        }
        return base + subId
    }

    companion object {
        /** Bare hostname from a base URL like `https://host:2053/x-ui/`. */
        fun hostOf(baseUrl: String): String =
            baseUrl.substringAfter("://").substringBefore("/").substringBefore(":")
                .ifBlank { baseUrl }
    }
}
