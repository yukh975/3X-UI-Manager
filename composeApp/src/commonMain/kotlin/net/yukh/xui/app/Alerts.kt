package net.yukh.xui.app

import net.yukh.xui.shared.api.PanelApi
import net.yukh.xui.shared.api.caddyReachable
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
private const val KEY_ALERTS_FIRED = "xui.alerts.fired"

fun SessionStore.alertsEnabled(): Boolean = getString(KEY_ALERTS_ENABLED) == "1"
fun SessionStore.setAlertsEnabled(on: Boolean) = putString(KEY_ALERTS_ENABLED, if (on) "1" else "0")

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

        // 1. Reachability = does the public entry point (Caddy :443) answer? Any
        //    response means the inbounds are served; the panel API is often
        //    firewalled off the phone, so we alert on :443, not on the API.
        //    Grace: arm on the first miss, alert only on the second in a row.
        val host = PanelSubSettings.hostOf(p.baseUrl)
        if (caddyReachable(host, p.allowInsecure)) {
            fired.remove("miss:${p.id}")
            fired.remove("p:${p.id}:down")
        } else if (!fired.add("miss:${p.id}")) {
            raise("p:${p.id}:down", tr(lang, "Inbounds unreachable"), "${p.label} · :443")
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
