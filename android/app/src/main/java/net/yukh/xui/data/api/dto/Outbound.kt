package net.yukh.xui.data.api.dto

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.yukh.xui.data.json.asObject
import net.yukh.xui.data.json.child
import net.yukh.xui.data.json.int
import net.yukh.xui.data.json.string

/** Xray outbound protocols offered in the editor (most-used first). */
val OUTBOUND_PROTOCOLS = listOf(
    "vless", "vmess", "trojan", "shadowsocks", "socks", "http",
    "freedom", "blackhole", "wireguard", "dns", "loopback",
)

/** Protocols that may carry streamSettings (proxy protocols). The rest never do. */
val PROXY_PROTOCOLS = setOf("vmess", "vless", "trojan", "shadowsocks", "socks", "http")

/** Protocols with a dedicated structured form so far (Phase 1). Others use the
 *  raw-settings editor until their form lands. */
val FORMED_PROTOCOLS = setOf(
    "freedom", "blackhole", "socks", "http", "vmess", "vless", "trojan", "shadowsocks", "wireguard",
)

val FREEDOM_DOMAIN_STRATEGY = listOf(
    "AsIs", "UseIP", "UseIPv4", "UseIPv6", "UseIPv6v4", "UseIPv4v6",
    "ForceIP", "ForceIPv6v4", "ForceIPv6", "ForceIPv4v6", "ForceIPv4",
)
val BLACKHOLE_RESPONSE_TYPE = listOf("none", "http")
val SS_METHODS = listOf(
    "aes-256-gcm", "aes-128-gcm", "chacha20-poly1305", "chacha20-ietf-poly1305",
    "xchacha20-poly1305", "xchacha20-ietf-poly1305",
    "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305",
)
val VMESS_SECURITY = listOf("auto", "aes-128-gcm", "chacha20-poly1305", "none", "zero")
val VLESS_FLOW = listOf("", "xtls-rprx-vision", "xtls-rprx-vision-udp443")
val WG_DOMAIN_STRATEGY = listOf("", "ForceIP", "ForceIPv4", "ForceIPv4v6", "ForceIPv6", "ForceIPv6v4")
val MUX_XUDP_443 = listOf("reject", "allow", "skip")
val FREEDOM_FRAGMENT_PACKETS = listOf("tlshello", "1-1", "1-2", "1-3")

/** Minimal valid skeleton for a new outbound of [protocol], keeping [tag]. */
fun defaultOutbound(protocol: String, tag: String): JsonObject = buildJsonObject {
    put("tag", tag)
    put("protocol", protocol)
    // 3x-ui uses a FLAT outbound-settings model (address/port/id/… directly under
    // settings), not raw Xray's vnext[]/servers[] nesting. streamSettings is a
    // top-level sibling. (wireguard keeps its inherent address[]/peers[] arrays.)
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
        else -> putJsonObject("settings") {} // dns and anything else: empty settings
    }
}

// ---- List-row summaries (read-only; the editor writes through JsonEdit) --------

fun JsonObject.outboundTag(): String = string("tag")
fun JsonObject.outboundProtocol(): String = string("protocol")

/** Best-effort "host:port" for the list row. 3x-ui's flat model puts address/port
 *  directly under settings; wireguard uses peers[].endpoint. */
fun JsonObject.outboundAddressSummary(): String {
    val s = child("settings")
    val addr = s.string("address")
    if (addr.isNotBlank()) {
        val port = s.int("port")
        return if (port != null) "$addr:$port" else addr
    }
    (s["peers"] as? JsonArray)?.firstOrNull()?.asObject()?.string("endpoint")
        ?.takeIf { it.isNotBlank() }?.let { return it }
    return ""
}
