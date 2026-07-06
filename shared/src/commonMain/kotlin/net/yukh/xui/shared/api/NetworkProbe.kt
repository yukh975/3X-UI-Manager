package net.yukh.xui.shared.api

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.utils.io.core.use
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull

/**
 * True if a TCP connection to [host]:[port] opens — i.e. the port is reachable.
 * A refused connection, timeout or DNS failure is "down".
 *
 * Used by the panel-alerts reachability check at the port level, NOT via the
 * panel management API: the panel is often firewalled off the phone (iptables),
 * while the public entry (port 443, or an inbound's own port) is what actually
 * serves clients. Works for any TCP service (HTTP, a raw proxy inbound, …).
 */
suspend fun tcpReachable(host: String, port: Int): Boolean {
    val selector = SelectorManager(Dispatchers.Default)
    return try {
        withTimeoutOrNull(8_000) {
            aSocket(selector).tcp().connect(host, port).use { true }
        } ?: false
    } catch (e: Throwable) {
        false
    } finally {
        selector.close()
    }
}
