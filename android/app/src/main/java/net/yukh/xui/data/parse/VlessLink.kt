package net.yukh.xui.data.parse

import java.net.URI
import java.net.URLDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Parse a `vless://uuid@host:port?params#remark` share link into an Xray vless
 * outbound JsonObject. Returns null if it isn't a usable vless link. Recognized
 * query params: type (network), security, sni, fp, alpn, pbk, sid, spx, flow,
 * encryption, path, host, serviceName, mode. Designed to extend to vmess/trojan/ss.
 */
fun parseVlessLink(raw: String): JsonObject? {
    val link = raw.trim()
    if (!link.startsWith("vless://", ignoreCase = true)) return null
    return try {
        val uri = URI(link)
        val uuid = uri.userInfo?.takeIf { it.isNotBlank() } ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port.takeIf { it > 0 } ?: 443 // vless links commonly omit the default port
        val q = parseQuery(uri.rawQuery)
        val tag = uri.fragment
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
            ?: "$host:$port"
        buildJsonObject {
            put("tag", tag)
            put("protocol", "vless")
            // 3x-ui flat outbound-settings model (not raw Xray vnext/users).
            putJsonObject("settings") {
                put("address", host)
                put("port", port)
                put("id", uuid)
                put("encryption", "none")
                put("flow", q["flow"] ?: "")
            }
            put("streamSettings", buildStream(q))
        }
    } catch (_: Exception) {
        null
    }
}

private fun parseQuery(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank()) return emptyMap()
    return raw.split("&").mapNotNull { part ->
        val i = part.indexOf('=')
        if (i < 0) return@mapNotNull null
        val k = runCatching { URLDecoder.decode(part.substring(0, i), "UTF-8") }.getOrNull() ?: return@mapNotNull null
        val v = runCatching { URLDecoder.decode(part.substring(i + 1), "UTF-8") }.getOrNull() ?: return@mapNotNull null
        k to v
    }.toMap()
}

private fun buildStream(q: Map<String, String>): JsonObject = buildJsonObject {
    val network = q["type"] ?: "tcp"
    val security = q["security"]?.takeIf { it in setOf("none", "tls", "reality") } ?: "none"
    put("network", network)
    put("security", security)
    when (network) {
        "ws" -> putJsonObject("wsSettings") {
            q["path"]?.let { put("path", it) }
            q["host"]?.let { put("host", it) }
        }
        "grpc" -> putJsonObject("grpcSettings") {
            q["serviceName"]?.let { put("serviceName", it) }
            if (q["mode"] == "multi") put("multiMode", true)
        }
        "httpupgrade" -> putJsonObject("httpupgradeSettings") {
            q["path"]?.let { put("path", it) }
            q["host"]?.let { put("host", it) }
        }
        "xhttp" -> putJsonObject("xhttpSettings") {
            q["path"]?.let { put("path", it) }
            q["host"]?.let { put("host", it) }
            q["mode"]?.let { put("mode", it) }
        }
        "tcp" -> if (q["headerType"] == "http") putJsonObject("tcpSettings") {
            putJsonObject("header") { put("type", "http") }
        }
    }
    when (security) {
        "tls" -> putJsonObject("tlsSettings") {
            q["sni"]?.let { put("serverName", it) }
            q["fp"]?.let { put("fingerprint", it) }
            q["alpn"]?.let { alpn ->
                putJsonArray("alpn") { alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) } }
            }
        }
        "reality" -> putJsonObject("realitySettings") {
            q["sni"]?.let { put("serverName", it) }
            q["fp"]?.let { put("fingerprint", it) }
            q["pbk"]?.let { put("publicKey", it) }
            q["sid"]?.let { put("shortId", it) }
            q["spx"]?.let { put("spiderX", it) }
        }
    }
}
