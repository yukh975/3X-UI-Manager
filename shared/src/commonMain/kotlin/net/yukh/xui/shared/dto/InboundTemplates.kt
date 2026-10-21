package net.yukh.xui.shared.dto

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import net.yukh.xui.shared.secureRandomIndex

/**
 * Minimal valid default JSON for a fresh inbound, per protocol (ported from the
 * Android app). The user tweaks settings/streamSettings/sniffing as JSON in the
 * editor; the panel validates on save. Clients are managed separately.
 */
object InboundTemplates {
    // The panel's protocol validator knows "mixed" (SOCKS + HTTP), never "socks".
    val PROTOCOLS = listOf("vless", "vmess", "trojan", "shadowsocks", "mixed", "http")

    /** The protocols a new inbound can take on this panel: TUIC arrived in 3.8.0, MASQUE in 3.10.0. */
    fun protocols(panel380: Boolean, panel3100: Boolean = false): List<String> =
        PROTOCOLS + listOfNotNull("tuic".takeIf { panel380 }, "masque".takeIf { panel3100 })
    val TRAFFIC_RESET = listOf("never", "hourly", "daily", "weekly", "monthly")

    /** The panel's inbound method list: 2022-blake3-chacha20-poly1305 is refused on a multi-user inbound. */
    val SS_INBOUND_METHODS = listOf(
        "aes-256-gcm", "chacha20-poly1305", "chacha20-ietf-poly1305", "xchacha20-ietf-poly1305",
        "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm",
    )
    const val SS_DEFAULT_METHOD = "2022-blake3-aes-256-gcm"

    fun isSs2022(method: String): Boolean = method.startsWith("2022")

    /** A random server key for [method], as the panel makes it: base64 of 16 bytes for aes-128, else 32. */
    @OptIn(ExperimentalEncodingApi::class)
    fun shadowsocksKey(method: String): String {
        val size = if (method == "2022-blake3-aes-128-gcm") 16 else 32
        return Base64.encode(ByteArray(size) { secureRandomIndex(256).toByte() })
    }

    const val MASQUE_DEFAULT_PATH = "/.well-known/masque/ip/*/*/"
    val MASQUE_DEFAULT_ADDRESS_POOL = listOf("10.14.0.1/24", "fd14::1/64")
    val MASQUE_MTU_RANGE = 1280..65535

    private fun randomLowerAndNum(len: Int): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        return buildString(len) { repeat(len) { append(alphabet[secureRandomIndex(alphabet.length)]) } }
    }

    fun settings(protocol: String): String = when (protocol) {
        "vless" -> "{\n  \"clients\": [],\n  \"decryption\": \"none\",\n  \"fallbacks\": []\n}"
        "vmess" -> "{\n  \"clients\": []\n}"
        "trojan" -> "{\n  \"clients\": [],\n  \"fallbacks\": []\n}"
        "shadowsocks" -> "{\n  \"method\": \"$SS_DEFAULT_METHOD\",\n  \"password\": \"${shadowsocksKey(SS_DEFAULT_METHOD)}\",\n  \"network\": \"tcp,udp\",\n  \"clients\": [],\n  \"ivCheck\": false\n}"
        "mixed" -> "{\n  \"auth\": \"password\",\n  \"accounts\": [{\"user\": \"${randomLowerAndNum(8)}\", \"pass\": \"${randomLowerAndNum(12)}\"}],\n  \"udp\": false,\n  \"ip\": \"127.0.0.1\"\n}"
        "http" -> "{\n  \"accounts\": []\n}"
        // Panel 3.8.0 serves TUIC from its own tuic-server process. These are the web
        // form's defaults; the certificate and key paths must be filled in before use.
        "tuic" -> "{\n  \"server\": {\n    \"certificate\": \"\",\n    \"private_key\": \"\",\n    \"congestion_control\": \"bbr\",\n    \"alpn\": [\"h3\", \"spdy/3.1\"],\n    \"udp_relay_mode\": \"native\",\n    \"zero_rtt_handshake\": true,\n    \"log_level\": \"info\",\n    \"max_idle_time\": 15,\n    \"authentication_timeout\": 3,\n    \"max_udp_relay_packet_size\": 1500,\n    \"sni\": \"\"\n  },\n  \"clients\": []\n}"
        "masque" -> "{\n  \"clients\": [],\n  \"address\": [${MASQUE_DEFAULT_ADDRESS_POOL.joinToString(", ") { "\"$it\"" }}]\n}"
        else -> "{}"
    }

    fun streamSettings(protocol: String): String = when (protocol) {
        "vless", "vmess", "trojan" -> "{\n  \"network\": \"tcp\",\n  \"security\": \"none\"\n}"
        // MASQUE is HTTP/3 over QUIC and TLS-only; the panel seeds Hysteria's QUIC TLS (ALPN h3).
        "masque" -> """{
  "network": "masque",
  "security": "tls",
  "masqueSettings": {"path": "$MASQUE_DEFAULT_PATH"},
  "tlsSettings": {
    "serverName": "",
    "minVersion": "1.2",
    "maxVersion": "1.3",
    "certificates": [
      {"certificateFile": "", "keyFile": "", "ocspStapling": 0, "oneTimeLoading": false, "usage": "encipherment", "buildChain": false}
    ],
    "alpn": ["h3"],
    "settings": {"fingerprint": ""}
  }
}"""
        else -> "{}"
    }

    fun sniffing(): String =
        "{\n  \"enabled\": false,\n  \"destOverride\": [\"http\", \"tls\", \"quic\"],\n  \"metadataOnly\": false,\n  \"routeOnly\": false\n}"
}

private val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  "; isLenient = true; ignoreUnknownKeys = true }
private val laxJson = Json { isLenient = true; ignoreUnknownKeys = true }

// Pretty-printed JSON of an inbound's sub-objects, for the editor's text fields.
fun InboundModel.settingsText(): String = prettyJson.encodeToString(JsonElement.serializer(), settings)
fun InboundModel.streamSettingsText(): String = prettyJson.encodeToString(JsonElement.serializer(), streamSettings)
fun InboundModel.sniffingText(): String = prettyJson.encodeToString(JsonElement.serializer(), sniffing)

/**
 * Build an inbound from the editor's string fields; returns null if any of the
 * three JSON blocks is invalid (the editor then blocks Save). Keeps all
 * kotlinx-serialization use inside :shared so composeApp stays JSON-free.
 */
fun buildInbound(
    base: InboundModel,
    protocol: String,
    remark: String,
    listen: String,
    port: Int,
    totalBytes: Long,
    trafficReset: String,
    disableFlow: Boolean,
    enable: Boolean,
    settingsText: String,
    streamSettingsText: String,
    sniffingText: String,
): InboundModel? {
    val s = parseJsonOrNull(settingsText) ?: return null
    val st = parseJsonOrNull(streamSettingsText) ?: return null
    val sn = parseJsonOrNull(sniffingText) ?: return null
    return base.copy(
        protocol = protocol, remark = remark, listen = listen, port = port,
        total = totalBytes, trafficReset = trafficReset, disableFlow = disableFlow, enable = enable,
        settings = s, streamSettings = st, sniffing = sn,
    )
}

private fun parseJsonOrNull(text: String): JsonElement? =
    try { laxJson.parseToJsonElement(text) } catch (e: Throwable) { null }

/**
 * Which inbound form sections apply to a protocol, after the panel's
 * protocol-capabilities.ts, so the editor cannot turn e.g. MASQUE into tcp.
 */
object InboundCapabilities {
    private val STREAM_PROTOCOLS = setOf("vmess", "vless", "trojan", "shadowsocks", "hysteria", "wireguard", "tunnel", "masque")
    private val FIXED_TRANSPORT = setOf("hysteria", "masque", "wireguard", "tunnel")
    private val TLS_PROTOCOLS = setOf("vmess", "vless", "trojan", "shadowsocks")
    private val TLS_NETWORKS = setOf("tcp", "ws", "http", "grpc", "httpupgrade", "xhttp")
    private val REALITY_PROTOCOLS = setOf("vless", "trojan")
    private val REALITY_NETWORKS = setOf("tcp", "http", "grpc", "xhttp")

    /** Xray streamSettings apply (TUN, mixed, http, TUIC, MTProto… have none). */
    fun hasStream(protocol: String): Boolean = protocol in STREAM_PROTOCOLS

    fun hasSniffing(protocol: String): Boolean = protocol !in setOf("mtproto", "amneziawg", "tuic")

    fun hasSelectableNetwork(protocol: String): Boolean = hasStream(protocol) && protocol !in FIXED_TRANSPORT

    /** Security choices for this protocol + network; empty = no security section at all. */
    fun securities(protocol: String, network: String): List<String> = when {
        protocol == "wireguard" || protocol == "tunnel" -> emptyList()
        protocol == "hysteria" || protocol == "masque" -> listOf("tls")
        // xray-core would read an XDRIVE listener's TLS as the storage API's client TLS.
        network == "xdrive" -> listOf("none")
        else -> listOfNotNull(
            "none",
            "tls".takeIf { protocol in TLS_PROTOCOLS && network in TLS_NETWORKS },
            "reality".takeIf { protocol in REALITY_PROTOCOLS && network in REALITY_NETWORKS },
        )
    }
}

// ---- Structured edits over the editor's JSON texts --------------------------
// Each returns the input unchanged when it is not a JSON object, so a half-typed
// raw JSON block is never wiped by a field edit.

private fun parseObjectOrNull(text: String): JsonObject? =
    try { laxJson.parseToJsonElement(text.ifBlank { "{}" }) as? JsonObject } catch (e: Throwable) { null }

private fun JsonObject.with(key: String, value: JsonElement?): JsonObject =
    JsonObject(LinkedHashMap(this).also { if (value == null) it.remove(key) else it[key] = value })

private fun JsonObject.obj(key: String): JsonObject = this[key] as? JsonObject ?: JsonObject(emptyMap())

private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

private inline fun editObject(text: String, edit: (JsonObject) -> JsonObject): String {
    val obj = parseObjectOrNull(text) ?: return text
    return prettyJson.encodeToString(JsonElement.serializer(), edit(obj))
}

/** A new Shadowsocks method needs a key of its own length, so it gets a fresh one (as the panel does). */
fun ssSettingsWithMethod(settingsJson: String, method: String): String = editObject(settingsJson) {
    it.with("method", JsonPrimitive(method)).with("password", JsonPrimitive(InboundTemplates.shadowsocksKey(method)))
}

fun ssSettingsWithNewKey(settingsJson: String): String = editObject(settingsJson) {
    val method = it.str("method").ifBlank { InboundTemplates.SS_DEFAULT_METHOD }
    it.with("password", JsonPrimitive(InboundTemplates.shadowsocksKey(method)))
}

/** Digits only; a blank MTU removes the key so the panel's default (1280) applies. */
fun masqueSettingsWithMtu(settingsJson: String, text: String): String = editObject(settingsJson) {
    val digits = text.filter(Char::isDigit).take(5)
    it.with("mtu", if (digits.isEmpty()) null else JsonPrimitive(digits.toInt()))
}

fun mtprotoSettingsWithPendingHandshakes(settingsJson: String, text: String): String = editObject(settingsJson) {
    val digits = text.filter(Char::isDigit).take(6)
    it.with("pendingHandshakesMaxPerIp", if (digits.isEmpty()) null else JsonPrimitive(digits.toInt()))
}

/** MASQUE needs an address pool, and an MTU (when set) inside the panel's 1280–65535. */
fun masqueSettingsValid(settingsJson: String): Boolean {
    val s = parseObjectOrNull(settingsJson) ?: return true
    val pool = (s["address"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }.orEmpty()
    val mtu = s["mtu"]
    return pool.isNotEmpty() && (mtu == null || ((mtu as? JsonPrimitive)?.intOrNull ?: 0) in InboundTemplates.MASQUE_MTU_RANGE)
}

/** A field of the server certificate, which is tlsSettings.certificates[0]. */
fun inboundTlsCertField(streamJson: String, key: String): String {
    val certs = parseObjectOrNull(streamJson)?.obj("tlsSettings")?.get("certificates") as? JsonArray
    return (certs?.firstOrNull() as? JsonObject)?.str(key) ?: ""
}

/** The panel refuses a TLS inbound without a certificate, so a missing certificates[0] is created. */
fun inboundStreamWithTlsCertField(streamJson: String, key: String, value: String): String = editObject(streamJson) { stream ->
    val tls = stream.obj("tlsSettings")
    val certs = (tls["certificates"] as? JsonArray).orEmpty()
    val first = certs.firstOrNull() as? JsonObject ?: JsonObject(mapOf("usage" to JsonPrimitive("encipherment")))
    stream.with("tlsSettings", tls.with("certificates", JsonArray(listOf(first.with(key, JsonPrimitive(value))) + certs.drop(1))))
}

/** WebSocket handshakes over HTTP/1.1; TLS's default h2 ALPN breaks it (panel #6782). */
fun inboundStreamWithWsTlsAlpn(streamJson: String): String {
    val stream = parseObjectOrNull(streamJson) ?: return streamJson
    if (stream.str("network") != "ws" || stream.str("security") != "tls") return streamJson
    val tls = stream.obj("tlsSettings").with("alpn", JsonArray(listOf(JsonPrimitive("http/1.1"))))
    return prettyJson.encodeToString(JsonElement.serializer(), stream.with("tlsSettings", tls))
}
