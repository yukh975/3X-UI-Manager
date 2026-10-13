package net.yukh.xui.shared.api

/** English keys of the notices after a save; the app's I18n translates them. */
const val NOTICE_SAVED_APPLIED = "Saved and applied"
const val NOTICE_SAVED_XRAY_STOPPED = "Saved — applies when Xray starts"
const val NOTICE_SAVED_RESTART_XRAY = "Saved — restart Xray to apply"
const val NOTICE_APPLIES_WITHIN_30S = "Saved — the panel applies it within 30 seconds"

/**
 * Panel v3.3.1+ applies a saved Xray template itself (POST xray/update reconciles a
 * running core, live where it can); v3.3.0 only stored it until the next restart.
 */
fun panelAppliesXrayOnSave(version: String): Boolean = panelVersionAtLeast(version, 3, 3, 1)

/** The notice after a successful POST xray/update. A stopped Xray stays stopped. */
fun xraySaveNotice(panelVersion: String, xrayRunning: Boolean): String = when {
    !panelAppliesXrayOnSave(panelVersion) -> NOTICE_SAVED_RESTART_XRAY
    !xrayRunning -> NOTICE_SAVED_XRAY_STOPPED
    else -> NOTICE_SAVED_APPLIED
}

/** A change on the outbound subscriptions screen. */
enum class OutboundSubChange { Create, Update, Delete, Refresh, Move }

/**
 * Delete, refresh and move flag Xray for the panel's 30-second reload; a new
 * subscription does too once the app fetches it (as the panel's web UI does).
 * An edit flags nothing, so its outbounds change only when Xray next reloads.
 */
fun outboundSubNotice(change: OutboundSubChange): String =
    if (change == OutboundSubChange.Update) NOTICE_SAVED_RESTART_XRAY else NOTICE_APPLIES_WITHIN_30S
