package net.yukh.xui.data.prefs

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Non-secret app preferences (UI language, etc.) in plain SharedPreferences —
 * separate from the encrypted connection store. Survives disconnect.
 */
@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getLanguage(): String = prefs.getString(KEY_LANG, DEFAULT_LANG) ?: DEFAULT_LANG
    fun setLanguage(lang: String) = prefs.edit { putString(KEY_LANG, lang) }

    // ---- Panel alerts (background polling → local notifications) ----

    fun getAlertsEnabled(): Boolean = prefs.getBoolean(KEY_ALERTS, false)
    fun setAlertsEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_ALERTS, enabled) }

    /** Warn when a client expires within this many days. */
    fun getAlertExpiryDays(): Int = prefs.getInt(KEY_ALERT_EXPIRY_DAYS, 3)
    fun setAlertExpiryDays(days: Int) = prefs.edit { putInt(KEY_ALERT_EXPIRY_DAYS, days) }

    /** Warn when a client has used at least this % of its traffic quota. */
    fun getAlertTrafficPct(): Int = prefs.getInt(KEY_ALERT_TRAFFIC_PCT, 90)
    fun setAlertTrafficPct(pct: Int) = prefs.edit { putInt(KEY_ALERT_TRAFFIC_PCT, pct) }

    /** The panel-host port probed for reachability (default 443 — the usual
     *  public entry; set it to whatever your panel's public port is). */
    fun getAlertPanelPort(): Int = prefs.getInt(KEY_ALERT_PANEL_PORT, 443)
    fun setAlertPanelPort(port: Int) = prefs.edit { putInt(KEY_ALERT_PANEL_PORT, port) }

    /** Show live speeds in bits/s (Kbit/s…) instead of bytes/s (KB/s). */
    fun getSpeedInBits(): Boolean = prefs.getBoolean(KEY_SPEED_BITS, false)
    fun setSpeedInBits(value: Boolean) = prefs.edit { putBoolean(KEY_SPEED_BITS, value) }

    private companion object {
        const val FILE = "xui_app_settings"
        const val KEY_LANG = "language"
        const val DEFAULT_LANG = "en"
        const val KEY_ALERTS = "alerts.enabled"
        const val KEY_ALERT_EXPIRY_DAYS = "alerts.expiryDays"
        const val KEY_ALERT_TRAFFIC_PCT = "alerts.trafficPct"
        const val KEY_ALERT_PANEL_PORT = "alerts.panelPort"
        const val KEY_SPEED_BITS = "ui.speedInBits"
    }
}
