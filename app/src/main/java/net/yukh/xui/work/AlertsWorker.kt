package net.yukh.xui.work

import android.content.Context
import androidx.core.content.edit
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.yukh.xui.data.api.XuiApi
import net.yukh.xui.data.api.XuiApiFactory
import net.yukh.xui.data.api.dto.PanelSettings
import net.yukh.xui.data.auth.InsecureTls
import net.yukh.xui.data.prefs.AppSettingsStore
import net.yukh.xui.data.prefs.ConnectionAuth
import net.yukh.xui.data.prefs.ConnectionProfile
import net.yukh.xui.data.prefs.ConnectionStore
import net.yukh.xui.i18n.tr
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Background poll behind the "Panel alerts" setting: walks every saved panel
 * and raises local notifications for client expiry / traffic quota, an Xray /
 * node outage, and — the reachability signal — whether the public entry point
 * (Caddy on :443) answers.
 *
 * The **outage** check targets `:443`, NOT the panel management API: the panel
 * is frequently firewalled off the phone's network (iptables), so its
 * reachability is a poor health signal, while Caddy on :443 is what actually
 * serves the inbounds. Any response there (any HTTP status — Caddy decides
 * access) means the service is up. The panel-API checks (Xray / clients / nodes)
 * still run, but best-effort: if the API isn't reachable they're silently
 * skipped, never a false "unreachable".
 *
 * Each alert fires ONCE per incident: a fired key is remembered and only
 * re-armed when the condition clears (node back online, traffic reset, expiry
 * extended — the expiry/quota values are part of the key). Runs periodically
 * via [AlertScheduler] plus one immediate pass when the user enables alerts.
 */
@HiltWorker
class AlertsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val connections: ConnectionStore,
    private val settings: AppSettingsStore,
    private val json: Json,
) : CoroutineWorker(context, params) {

    private val state =
        applicationContext.getSharedPreferences("xui_alert_state", Context.MODE_PRIVATE)
    private val lang get() = settings.getLanguage()

    override suspend fun doWork(): Result {
        if (!settings.getAlertsEnabled()) return Result.success()
        for (profile in connections.getProfiles()) {
            runCatching { checkPanel(profile) }
        }
        return Result.success()
    }

    private suspend fun checkPanel(profile: ConnectionProfile) {
        val token = (profile.auth as? ConnectionAuth.Token)?.token ?: return
        val api = XuiApiFactory.tokenAuthed(profile.baseUrl, profile.allowInsecureTls, token, json)
        val panelName = profile.name.ifBlank { profile.baseUrl }
        val host = PanelSettings.hostOf(profile.baseUrl)

        // 1. Reachability = does the public entry point (Caddy :443) answer? Any
        //    response means the inbounds are being served; a refused connection /
        //    timeout after two consecutive misses is a real outage. (No cry-wolf
        //    on a single transient miss.)
        if (caddyReachable(host, profile.allowInsecureTls)) {
            resetMiss(profile.id)
            clear("p:${profile.id}:down")
        } else {
            val misses = state.getInt("miss:${profile.id}", 0) + 1
            state.edit { putInt("miss:${profile.id}", misses) }
            if (misses >= 2) {
                raise("p:${profile.id}:down", Notifier.CHANNEL_PANEL, tr(lang, "Inbounds unreachable"), "$panelName · :443")
            }
        }

        // 2. Panel-API health (Xray / clients / nodes) — best-effort. The panel is
        //    often firewalled off the phone, so an unreachable API (or a 401) is
        //    silently skipped, never a reachability alert.
        val status = runCatching { api.getServerStatus() }.getOrNull()
        if (status?.success != true || status.obj == null) return

        if (!status.obj.xrayRunning) {
            raise("p:${profile.id}:xray", Notifier.CHANNEL_PANEL, tr(lang, "Xray is down"), panelName)
        } else {
            clear("p:${profile.id}:xray")
        }

        checkClients(api, profile)
        checkNodes(api, profile, panelName)
    }

    /**
     * True if the host answers on :443 — a completed TLS handshake + any HTTP
     * response (Caddy replies even when it denies access). A refused connection,
     * timeout or DNS failure is "down". Uses the profile's self-signed-TLS flag.
     */
    private suspend fun caddyReachable(host: String, allowInsecure: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val builder = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
            if (allowInsecure) InsecureTls.apply(builder)
            val req = Request.Builder().url("https://$host:443/").head().build()
            runCatching { builder.build().newCall(req).execute().use { true } }.getOrDefault(false)
        }

    private suspend fun checkClients(api: XuiApi, profile: ConnectionProfile) {
        val clients = runCatching { api.listClients() }.getOrNull()?.obj ?: return
        val now = System.currentTimeMillis()
        val warnMs = settings.getAlertExpiryDays().toLong() * 24 * 60 * 60 * 1000
        val warnPct = settings.getAlertTrafficPct()

        for (c in clients) {
            if (!c.enable) continue

            // Expiry — the timestamp is in the key, so extending a client re-arms.
            if (c.expiryTime > 0) {
                val left = c.expiryTime - now
                when {
                    left <= 0 -> raise(
                        "c:${profile.id}:${c.email}:expired:${c.expiryTime}",
                        Notifier.CHANNEL_CLIENTS, tr(lang, "Client expired"), c.email,
                    )
                    left <= warnMs -> raise(
                        "c:${profile.id}:${c.email}:expiring:${c.expiryTime}",
                        Notifier.CHANNEL_CLIENTS, tr(lang, "Client expires soon"),
                        "${c.email} · ${(left + DAY_MS - 1) / DAY_MS} ${tr(lang, "d")}",
                    )
                }
            }

            // Traffic quota — quota value in the key re-arms on quota change;
            // a usage reset below the threshold clears the flag.
            if (c.quota > 0) {
                val pct = ((c.up + c.down) * 100 / c.quota).toInt()
                val key = "c:${profile.id}:${c.email}:traffic:${c.quota}"
                if (pct >= warnPct) {
                    raise(
                        key, Notifier.CHANNEL_CLIENTS,
                        tr(lang, "Traffic limit almost reached"), "${c.email} · $pct%",
                    )
                } else {
                    clear(key)
                }
            }
        }
    }

    private suspend fun checkNodes(api: XuiApi, profile: ConnectionProfile, panelName: String) {
        val nodes = runCatching { api.listNodes() }.getOrNull()?.obj ?: return
        for (n in nodes) {
            val key = "n:${profile.id}:${n.id}:down"
            if (n.enable && !n.online) {
                raise(key, Notifier.CHANNEL_PANEL, tr(lang, "Node offline"), "${n.name} · $panelName")
            } else {
                clear(key)
            }
        }
    }

    /** Clear the consecutive-miss counter once the panel answers again. */
    private fun resetMiss(id: String) {
        if (state.contains("miss:$id")) state.edit { remove("miss:$id") }
    }

    /** Notify once per incident: skip if this key already fired. */
    private fun raise(key: String, channel: String, title: String, text: String) {
        if (state.getBoolean(key, false)) return
        state.edit { putBoolean(key, true) }
        Notifier.notify(applicationContext, channel, key, title, text)
    }

    private fun clear(key: String) {
        if (state.contains(key)) state.edit { remove(key) }
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
