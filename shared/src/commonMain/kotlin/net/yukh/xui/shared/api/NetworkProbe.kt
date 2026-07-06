package net.yukh.xui.shared.api

import io.ktor.client.request.head

/**
 * True if [host] answers on **:443** — a completed request with any HTTP status
 * (Caddy replies even when it denies access, so any response = the public entry
 * point is up). A refused connection, timeout or DNS failure is "down".
 *
 * Used by the panel-alerts reachability check instead of the panel management
 * API: the panel is often firewalled off the phone (iptables), while Caddy on
 * :443 is what actually serves the inbounds. Respects the profile's
 * self-signed-TLS flag. Uses a throwaway client with no response validator, so
 * a 403/404 does not throw — only a genuine connection failure does.
 */
suspend fun caddyReachable(host: String, allowInsecure: Boolean): Boolean {
    val client = platformHttpClient(allowInsecure) {}
    return try {
        client.head("https://$host:443/")
        true
    } catch (e: Throwable) {
        false
    } finally {
        client.close()
    }
}
