package net.yukh.xui.app

import net.yukh.xui.shared.api.PanelApi
import net.yukh.xui.shared.api.tcpReachable
import net.yukh.xui.shared.dto.PanelSubSettings

// ---- Platform hooks (iOS: UNUserNotificationCenter + BGTaskScheduler;
//      desktop: no-ops) ----

/** Ask the OS for permission to show notifications (idempotent). */
expect fun requestNotificationPermission()

/** Post a local notification now; [id] keeps repeats replacing, not stacking. */
expect fun postLocalNotification(id: String, title: String, body: String)

/** (Re)queue the opportunistic background refresh, if the platform has one. */
expect fun scheduleAlertsRefresh()

/** Cancel the queued background refresh. */
expect fun cancelAlertsRefresh()

/** Wall-clock now in Unix ms (common code has no clock of its own). */
expect fun epochNowMs(): Long

// ---- Settings (SessionStore-backed, mirrors the Android AppSettingsStore) ----

private const val KEY_ALERTS_ENABLED = "xui.alerts.enabled"
private const val KEY_ALERTS_EXPIRY_DAYS = "xui.alerts.expiryDays"
private const val KEY_ALERTS_TRAFFIC_PCT = "xui.alerts.trafficPct"
private const val KEY_ALERTS_PANEL_PORT = "xui.alerts.panelPort"
private const val KEY_ALERTS_FIRED = "xui.alerts.fired"

fun SessionStore.alertsEnabled(): Boolean = getString(KEY_ALERTS_ENABLED) == "1"
fun SessionStore.setAlertsEnabled(on: Boolean) = putString(KEY_ALERTS_ENABLED, if (on) "1" else "0")

/** The panel-host port probed for reachability (default 443 — the usual public
 *  entry; set it to whatever your panel's public port is). */
fun SessionStore.alertPanelPort(): Int = getString(KEY_ALERTS_PANEL_PORT)?.toIntOrNull() ?: 443
fun SessionStore.setAlertPanelPort(port: Int) = putString(KEY_ALERTS_PANEL_PORT, port.toString())

/** Warn when a client expires within this many days. */
fun SessionStore.alertExpiryDays(): Int = getString(KEY_ALERTS_EXPIRY_DAYS)?.toIntOrNull() ?: 3
fun SessionStore.setAlertExpiryDays(days: Int) = putString(KEY_ALERTS_EXPIRY_DAYS, days.toString())

/** Warn when a client has used at least this % of its traffic quota. */
fun SessionStore.alertTrafficPct(): Int = getString(KEY_ALERTS_TRAFFIC_PCT)?.toIntOrNull() ?: 90
fun SessionStore.setAlertTrafficPct(pct: Int) = putString(KEY_ALERTS_TRAFFIC_PCT, pct.toString())

/**
 * Walks every saved panel and posts local notifications for client expiry /
 * traffic quota and panel / Xray / node outages — the same events and once-per-
 * incident semantics as the Android AlertsWorker: a fired key is remembered in
 * the store and re-armed only when the condition clears (or the expiry/quota
 * value changes, since it is part of the key). Runs on app open and from the
 * opportunistic background refresh.
 */
object AlertsCheck {

    suspend fun run(store: SessionStore) {
        if (!store.alertsEnabled()) return
        val lang = store.loadLang() ?: LANG_EN
        val nowMs = epochNowMs()
        val fired = store.getString(KEY_ALERTS_FIRED)
            ?.split('\n')?.filterTo(mutableSetOf()) { it.isNotBlank() } ?: mutableSetOf()

        for (profile in store.loadProfiles()) {
            runCatching { checkPanel(profile, store, lang, fired, nowMs) }
        }
        store.putString(KEY_ALERTS_FIRED, fired.joinToString("\n"))
    }

    private suspend fun checkPanel(
        p: SavedSession,
        store: SessionStore,
        lang: String,
        fired: MutableSet<String>,
        nowMs: Long,
    ) {
        fun raise(key: String, title: String, text: String) {
            if (fired.add(key)) postLocalNotification(key, title, text)
        }
        // Reachability with a two-miss grace (fired set): arm on the 1st miss,
        // alert only on the 2nd in a row; clear on success.
        fun reach(downKey: String, missKey: String, up: Boolean, title: String, text: String) {
            if (up) { fired.remove(missKey); fired.remove(downKey) }
            else if (!fired.add(missKey)) raise(downKey, title, text)
        }

        // 1. Reachability at the port level, NOT the panel API (often firewalled
        //    off the phone). 1a: the panel host on the configured port (default
        //    443 — the usual public entry).
        val host = PanelSubSettings.hostOf(p.baseUrl)
        val panelPort = store.alertPanelPort()
        reach(
            "p:${p.id}:down", "miss:${p.id}", tcpReachable(host, panelPort),
            tr(lang, "Panel unreachable"), "${p.label} · :$panelPort",
        )
        // 1b: per-inbound ports the user flagged (port snapshotted locally, so it
        //     works even when the panel API is unreachable).
        for (mi in store.monitoredInbounds(p.id)) {
            if (mi.port !in 1..65535) continue
            reach(
                "i:${p.id}:${mi.id}:down", "imiss:${p.id}:${mi.id}", tcpReachable(host, mi.port),
                tr(lang, "Inbound unreachable"), "${mi.remark.ifBlank { "inbound #${mi.id}" }} · :${mi.port}",
            )
        }

        // 2. Panel-API health (Xray / clients / nodes) — best-effort. If the API
        //    isn't reachable (firewalled) or the token expired (401 →
        //    AuthExpiredException, caught here), it's silently skipped.
        val api = PanelApi(p.baseUrl, p.token, p.allowInsecure)
        try {
            val status = runCatching { api.serverStatus() }.getOrNull()
            if (status?.success != true || status.obj == null) return

            if (!status.obj!!.xrayRunning) {
                raise("p:${p.id}:xray", tr(lang, "Xray is down"), p.label)
            } else {
                fired.remove("p:${p.id}:xray")
            }

            val warnMs = store.alertExpiryDays().toLong() * DAY_MS
            val warnPct = store.alertTrafficPct()
            runCatching { api.clients() }.getOrNull()?.obj?.forEach { c ->
                if (!c.enable) return@forEach
                if (c.expiryTime > 0) {
                    val left = c.expiryTime - nowMs
                    when {
                        left <= 0 -> raise(
                            "c:${p.id}:${c.email}:expired:${c.expiryTime}",
                            tr(lang, "Client expired"), c.email,
                        )
                        left <= warnMs -> raise(
                            "c:${p.id}:${c.email}:expiring:${c.expiryTime}",
                            tr(lang, "Client expires soon"),
                            "${c.email} · ${(left + DAY_MS - 1) / DAY_MS} ${tr(lang, "d")}",
                        )
                    }
                }
                if (c.quota > 0) {
                    val pct = ((c.up + c.down) * 100 / c.quota).toInt()
                    val key = "c:${p.id}:${c.email}:traffic:${c.quota}"
                    if (pct >= warnPct) {
                        raise(key, tr(lang, "Traffic limit almost reached"), "${c.email} · $pct%")
                    } else {
                        fired.remove(key)
                    }
                }
            }

            runCatching { api.nodes() }.getOrNull()?.obj?.forEach { n ->
                val key = "n:${p.id}:${n.id}:down"
                if (n.enable && !n.online) {
                    raise(key, tr(lang, "Node offline"), "${n.name} · ${p.label}")
                } else {
                    fired.remove(key)
                }
            }
        } finally {
            api.close()
        }
    }

    private const val DAY_MS = 24 * 60 * 60 * 1000L
}
