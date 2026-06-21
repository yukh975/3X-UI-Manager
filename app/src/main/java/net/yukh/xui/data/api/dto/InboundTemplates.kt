package net.yukh.xui.data.api.dto

/**
 * Minimal valid default JSON for a fresh inbound, per protocol. The user
 * tweaks these in the editor's JSON fields; the panel validates on save.
 * Kept deliberately small — clients are added separately, TLS/Reality is
 * configured by editing streamSettings.
 */
object InboundTemplates {

    val PROTOCOLS = listOf("vless", "vmess", "trojan", "shadowsocks", "socks", "http")

    val TRAFFIC_RESET = listOf("never", "hourly", "daily", "weekly", "monthly")

    fun settings(protocol: String): String = when (protocol) {
        "vless" -> """{
  "clients": [],
  "decryption": "none",
  "fallbacks": []
}"""
        "vmess" -> """{
  "clients": []
}"""
        "trojan" -> """{
  "clients": [],
  "fallbacks": []
}"""
        "shadowsocks" -> """{
  "method": "2022-blake3-aes-256-gcm",
  "password": "",
  "network": "tcp,udp",
  "clients": []
}"""
        "socks" -> """{
  "auth": "password",
  "accounts": [],
  "udp": true
}"""
        "http" -> """{
  "accounts": []
}"""
        else -> "{}"
    }

    fun streamSettings(protocol: String): String = when (protocol) {
        "vless", "vmess", "trojan" -> """{
  "network": "tcp",
  "security": "none"
}"""
        else -> "{}"
    }

    fun sniffing(): String = """{
  "enabled": false,
  "destOverride": ["http", "tls", "quic"],
  "metadataOnly": false,
  "routeOnly": false
}"""
}
