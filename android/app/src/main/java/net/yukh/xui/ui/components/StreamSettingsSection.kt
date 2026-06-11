package net.yukh.xui.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import net.yukh.xui.data.json.bool
import net.yukh.xui.data.json.child
import net.yukh.xui.data.json.int
import net.yukh.xui.data.json.parseCsv
import net.yukh.xui.data.json.put
import net.yukh.xui.data.json.putArray
import net.yukh.xui.data.json.putBool
import net.yukh.xui.data.json.putInt
import net.yukh.xui.data.json.putString
import net.yukh.xui.data.json.putStrings
import net.yukh.xui.data.json.string
import net.yukh.xui.data.json.strings
import net.yukh.xui.i18n.tr

private val STREAM_NETWORKS = listOf("tcp", "kcp", "ws", "grpc", "httpupgrade", "xhttp")
private val STREAM_SECURITIES = listOf("none", "tls", "reality")
private val UTLS_FINGERPRINTS = listOf(
    "", "chrome", "firefox", "safari", "ios", "android", "edge", "360", "qq",
    "random", "randomized", "randomizednoalpn", "unsafe",
)
private val XHTTP_MODES = listOf("auto", "packet-up", "stream-up", "stream-one")

/**
 * Transport + security editor over an outbound's `streamSettings`, matching the
 * panel's outbound form (3x-ui flat model: wsSettings.host/path, kcpSettings.*,
 * tlsSettings/realitySettings as siblings). Preserves unknown keys via JsonEdit.
 */
@Composable
fun OutboundStreamSettings(draft: JsonObject, onChange: (JsonObject) -> Unit) {
    val stream = draft.child("streamSettings")
    fun put(child: String, obj: JsonObject) = onChange(draft.put("streamSettings", stream.put(child, obj)))
    fun putKey(key: String, value: String) = onChange(draft.put("streamSettings", stream.putString(key, value)))

    val network = stream.string("network").ifBlank { "tcp" }
    val security = stream.string("security").ifBlank { "none" }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(tr("Transport"))
        LabeledDropdown(tr("Transmission"), network, STREAM_NETWORKS) { putKey("network", it) }

        when (network) {
            "tcp" -> {
                val tcp = stream.child("tcpSettings")
                val header = tcp.child("header")
                val isHttp = header.string("type") == "http"
                SwitchRow(tr("HTTP camouflage"), isHttp) { on ->
                    put("tcpSettings", tcp.put("header", header.putString("type", if (on) "http" else "none")))
                }
                if (isHttp) {
                    val req = header.child("request")
                    val host = req.child("headers").strings("Host").firstOrNull().orEmpty()
                    val path = req.strings("path").firstOrNull().orEmpty()
                    Field(tr("Host"), host) {
                        val newReq = req.put("headers", req.child("headers").putStrings("Host", listOf(it)))
                        put("tcpSettings", tcp.put("header", header.put("request", newReq)))
                    }
                    Field(tr("Path"), path) {
                        val newReq = req.putStrings("path", listOf(it))
                        put("tcpSettings", tcp.put("header", header.put("request", newReq)))
                    }
                }
            }
            "kcp" -> {
                val k = stream.child("kcpSettings")
                Field(tr("MTU"), k.int("mtu")?.toString() ?: "", numeric = true) { put("kcpSettings", k.putInt("mtu", it.toIntOrNull() ?: 1350)) }
                Field(tr("TTI (ms)"), k.int("tti")?.toString() ?: "", numeric = true) { put("kcpSettings", k.putInt("tti", it.toIntOrNull() ?: 20)) }
                Field(tr("Uplink (MB/s)"), k.int("uplinkCapacity")?.toString() ?: "", numeric = true) { put("kcpSettings", k.putInt("uplinkCapacity", it.toIntOrNull() ?: 5)) }
                Field(tr("Downlink (MB/s)"), k.int("downlinkCapacity")?.toString() ?: "", numeric = true) { put("kcpSettings", k.putInt("downlinkCapacity", it.toIntOrNull() ?: 20)) }
                Field(tr("CWND multiplier"), k.int("cwndMultiplier")?.toString() ?: "", numeric = true) { put("kcpSettings", k.putInt("cwndMultiplier", it.toIntOrNull() ?: 1)) }
                Field(tr("Max sending window"), k.int("maxSendingWindow")?.toString() ?: "", numeric = true) { put("kcpSettings", k.putInt("maxSendingWindow", it.toIntOrNull() ?: 1350)) }
            }
            "ws" -> {
                val ws = stream.child("wsSettings")
                Field(tr("Host"), ws.string("host")) { put("wsSettings", ws.putString("host", it)) }
                Field(tr("Path"), ws.string("path")) { put("wsSettings", ws.putString("path", it)) }
                Field(tr("Heartbeat (s)"), ws.int("heartbeatPeriod")?.toString() ?: "", numeric = true) {
                    put("wsSettings", ws.putInt("heartbeatPeriod", it.toIntOrNull() ?: 0))
                }
            }
            "grpc" -> {
                val g = stream.child("grpcSettings")
                Field(tr("Service name"), g.string("serviceName")) { put("grpcSettings", g.putString("serviceName", it)) }
                Field(tr("Authority"), g.string("authority")) { put("grpcSettings", g.putString("authority", it)) }
                SwitchRow(tr("Multi mode"), g.bool("multiMode")) { put("grpcSettings", g.putBool("multiMode", it)) }
            }
            "httpupgrade" -> {
                val hu = stream.child("httpupgradeSettings")
                Field(tr("Host"), hu.string("host")) { put("httpupgradeSettings", hu.putString("host", it)) }
                Field(tr("Path"), hu.string("path")) { put("httpupgradeSettings", hu.putString("path", it)) }
            }
            "xhttp" -> {
                val xh = stream.child("xhttpSettings")
                Field(tr("Host"), xh.string("host")) { put("xhttpSettings", xh.putString("host", it)) }
                Field(tr("Path"), xh.string("path")) { put("xhttpSettings", xh.putString("path", it)) }
                LabeledDropdown(tr("Mode"), xh.string("mode").ifBlank { "auto" }, XHTTP_MODES) {
                    put("xhttpSettings", xh.putString("mode", it))
                }
            }
        }

        SectionTitle(tr("Security"))
        LabeledDropdown(tr("Security"), security, STREAM_SECURITIES) { putKey("security", it) }
        when (security) {
            "tls" -> {
                val tls = stream.child("tlsSettings")
                Field(tr("SNI"), tls.string("serverName")) { put("tlsSettings", tls.putString("serverName", it)) }
                LabeledDropdown(tr("uTLS"), tls.string("fingerprint"), UTLS_FINGERPRINTS) {
                    put("tlsSettings", tls.putString("fingerprint", it))
                }
                Field(tr("ALPN (comma-separated)"), tls.strings("alpn").joinToString(", ")) {
                    put("tlsSettings", tls.putStrings("alpn", parseCsv(it)))
                }
                Field(tr("ECH"), tls.string("echConfigList")) { put("tlsSettings", tls.putString("echConfigList", it)) }
                Field(tr("Verify peer name"), tls.string("verifyPeerCertByName")) {
                    put("tlsSettings", tls.putString("verifyPeerCertByName", it))
                }
                Field(tr("Pinned SHA256"), tls.string("pinnedPeerCertSha256")) {
                    put("tlsSettings", tls.putString("pinnedPeerCertSha256", it))
                }
            }
            "reality" -> {
                val r = stream.child("realitySettings")
                Field(tr("SNI"), r.string("serverName")) { put("realitySettings", r.putString("serverName", it)) }
                LabeledDropdown(tr("uTLS"), r.string("fingerprint").ifBlank { "chrome" }, UTLS_FINGERPRINTS) {
                    put("realitySettings", r.putString("fingerprint", it))
                }
                Field(tr("Public key"), r.string("publicKey")) { put("realitySettings", r.putString("publicKey", it)) }
                Field(tr("Short ID"), r.string("shortId")) { put("realitySettings", r.putString("shortId", it)) }
                Field(tr("SpiderX"), r.string("spiderX")) { put("realitySettings", r.putString("spiderX", it)) }
                Field(tr("mldsa65 verify"), r.string("mldsa65Verify")) {
                    put("realitySettings", r.putString("mldsa65Verify", it))
                }
            }
        }
    }
}
