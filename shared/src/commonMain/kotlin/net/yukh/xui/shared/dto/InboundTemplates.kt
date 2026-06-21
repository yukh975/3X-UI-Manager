package net.yukh.xui.shared.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Minimal valid default JSON for a fresh inbound, per protocol (ported from the
 * Android app). The user tweaks settings/streamSettings/sniffing as JSON in the
 * editor; the panel validates on save. Clients are managed separately.
 */
object InboundTemplates {
    val PROTOCOLS = listOf("vless", "vmess", "trojan", "shadowsocks", "socks", "http")
    val TRAFFIC_RESET = listOf("never", "hourly", "daily", "weekly", "monthly")

    fun settings(protocol: String): String = when (protocol) {
        "vless" -> "{\n  \"clients\": [],\n  \"decryption\": \"none\",\n  \"fallbacks\": []\n}"
        "vmess" -> "{\n  \"clients\": []\n}"
        "trojan" -> "{\n  \"clients\": [],\n  \"fallbacks\": []\n}"
        "shadowsocks" -> "{\n  \"method\": \"2022-blake3-aes-256-gcm\",\n  \"password\": \"\",\n  \"network\": \"tcp,udp\",\n  \"clients\": []\n}"
        "socks" -> "{\n  \"auth\": \"password\",\n  \"accounts\": [],\n  \"udp\": true\n}"
        "http" -> "{\n  \"accounts\": []\n}"
        else -> "{}"
    }

    fun streamSettings(protocol: String): String = when (protocol) {
        "vless", "vmess", "trojan" -> "{\n  \"network\": \"tcp\",\n  \"security\": \"none\"\n}"
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
        total = totalBytes, trafficReset = trafficReset, enable = enable,
        settings = s, streamSettings = st, sniffing = sn,
    )
}

private fun parseJsonOrNull(text: String): JsonElement? =
    try { laxJson.parseToJsonElement(text) } catch (e: Throwable) { null }
