package net.yukh.xui.shared.dto

import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Xray outbound editing metadata + templates, ported from the Android app. The
 *  factory functions return JSON STRINGS (composeApp doesn't depend on
 *  kotlinx-serialization). 3x-ui uses a FLAT outbound-settings model
 *  (address/port/id/… directly under settings), not raw Xray vnext[]/servers[]. */

val OUTBOUND_PROTOCOLS = listOf(
    "vless", "vmess", "trojan", "shadowsocks", "socks", "http",
    "freedom", "blackhole", "wireguard", "dns", "loopback",
)
val PROXY_PROTOCOLS = setOf("vmess", "vless", "trojan", "shadowsocks", "socks", "http")
val FORMED_PROTOCOLS = setOf(
    "freedom", "blackhole", "socks", "http", "vmess", "vless", "trojan", "shadowsocks", "wireguard",
)
val FREEDOM_DOMAIN_STRATEGY = listOf(
    "AsIs", "UseIP", "UseIPv4", "UseIPv6", "ForceIP", "ForceIPv4", "ForceIPv6",
)
val BLACKHOLE_RESPONSE_TYPE = listOf("none", "http")
val SS_METHODS = listOf(
    "aes-256-gcm", "aes-128-gcm", "chacha20-poly1305", "chacha20-ietf-poly1305",
    "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305",
)
val VMESS_SECURITY = listOf("auto", "aes-128-gcm", "chacha20-poly1305", "none", "zero")
val VLESS_FLOW = listOf("", "xtls-rprx-vision", "xtls-rprx-vision-udp443")

private val obJson = Json { prettyPrint = true; prettyPrintIndent = "  "; encodeDefaults = true }
private fun enc(o: JsonObject): String = obJson.encodeToString(JsonElement.serializer(), o)

/** Minimal valid skeleton (JSON string) for a new outbound of [protocol]. */
fun defaultOutbound(protocol: String, tag: String): String = enc(
    buildJsonObject {
        put("tag", tag)
        put("protocol", protocol)
        when (protocol) {
            "freedom" -> putJsonObject("settings") { put("domainStrategy", "AsIs") }
            "blackhole" -> putJsonObject("settings") { putJsonObject("response") { put("type", "none") } }
            "socks" -> putJsonObject("settings") { put("address", ""); put("port", 1080) }
            "http" -> putJsonObject("settings") { put("address", ""); put("port", 8080) }
            "vmess" -> {
                putJsonObject("settings") { put("address", ""); put("port", 443); put("id", ""); put("security", "auto") }
                putJsonObject("streamSettings") { put("network", "tcp"); put("security", "none") }
            }
            "vless" -> {
                putJsonObject("settings") { put("address", ""); put("port", 443); put("id", ""); put("flow", ""); put("encryption", "none") }
                putJsonObject("streamSettings") { put("network", "tcp"); put("security", "none") }
            }
            "trojan" -> {
                putJsonObject("settings") { put("address", ""); put("port", 443); put("password", "") }
                putJsonObject("streamSettings") { put("network", "tcp"); put("security", "tls") }
            }
            "shadowsocks" -> putJsonObject("settings") {
                put("address", ""); put("port", 8388); put("method", "aes-256-gcm"); put("password", "")
            }
            "wireguard" -> putJsonObject("settings") {
                put("secretKey", "")
                putJsonArray("address") { add("10.0.0.2/32") }
                putJsonArray("peers") {
                    addJsonObject {
                        put("publicKey", ""); put("endpoint", "")
                        putJsonArray("allowedIPs") { add("0.0.0.0/0"); add("::/0") }
                    }
                }
                put("mtu", 1420)
            }
            "loopback" -> putJsonObject("settings") { put("inboundTag", "") }
            else -> putJsonObject("settings") {}
        }
    },
)

/**
 * Parse a `vless://uuid@host:port?params#remark` share link into an Xray vless
 * outbound (JSON string). Null if it isn't a usable vless link. Uses Ktor's
 * multiplatform URL parser (no java.net). Recognized params: type, security,
 * sni, fp, alpn, pbk, sid, spx, flow, path, host, serviceName, mode, headerType.
 */
fun parseVlessLink(raw: String): String? {
    val link = raw.trim()
    if (!link.startsWith("vless://", ignoreCase = true)) return null
    return try {
        val url = Url(link)
        val uuid = url.user?.takeIf { it.isNotBlank() } ?: return null
        val host = url.host.takeIf { it.isNotBlank() } ?: return null
        val port = url.specifiedPort.takeIf { it > 0 } ?: 443
        val q: (String) -> String? = { k -> url.parameters[k]?.takeIf { it.isNotBlank() } }
        val tag = url.fragment.takeIf { it.isNotBlank() } ?: "$host:$port"
        enc(
            buildJsonObject {
                put("tag", tag)
                put("protocol", "vless")
                putJsonObject("settings") {
                    put("address", host); put("port", port); put("id", uuid)
                    put("encryption", "none"); put("flow", q("flow") ?: "")
                }
                put("streamSettings", buildStream(q))
            },
        )
    } catch (e: Exception) {
        null
    }
}

private fun buildStream(q: (String) -> String?): JsonObject = buildJsonObject {
    val network = q("type") ?: "tcp"
    val security = q("security")?.takeIf { it in setOf("none", "tls", "reality") } ?: "none"
    put("network", network)
    put("security", security)
    when (network) {
        "ws" -> putJsonObject("wsSettings") { q("path")?.let { put("path", it) }; q("host")?.let { put("host", it) } }
        "grpc" -> putJsonObject("grpcSettings") { q("serviceName")?.let { put("serviceName", it) }; if (q("mode") == "multi") put("multiMode", true) }
        "httpupgrade" -> putJsonObject("httpupgradeSettings") { q("path")?.let { put("path", it) }; q("host")?.let { put("host", it) } }
        "xhttp" -> putJsonObject("xhttpSettings") { q("path")?.let { put("path", it) }; q("host")?.let { put("host", it) }; q("mode")?.let { put("mode", it) } }
        "tcp" -> if (q("headerType") == "http") putJsonObject("tcpSettings") { putJsonObject("header") { put("type", "http") } }
    }
    when (security) {
        "tls" -> putJsonObject("tlsSettings") {
            q("sni")?.let { put("serverName", it) }
            q("fp")?.let { put("fingerprint", it) }
            q("alpn")?.let { alpn -> putJsonArray("alpn") { alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) } } }
        }
        "reality" -> putJsonObject("realitySettings") {
            q("sni")?.let { put("serverName", it) }
            q("fp")?.let { put("fingerprint", it) }
            q("pbk")?.let { put("publicKey", it) }
            q("sid")?.let { put("shortId", it) }
            q("spx")?.let { put("spiderX", it) }
        }
    }
}
