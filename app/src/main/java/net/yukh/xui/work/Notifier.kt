package net.yukh.xui.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import net.yukh.xui.MainActivity
import net.yukh.xui.R
import net.yukh.xui.i18n.tr

/**
 * Local notifications for panel alerts. Two channels so the user can mute
 * client noise (expiry/traffic) separately from infrastructure problems
 * (panel/Xray/node down) in system settings.
 */
object Notifier {
    const val CHANNEL_CLIENTS = "client_alerts"
    const val CHANNEL_PANEL = "panel_alerts"

    /** Idempotent; also updates channel names after a language switch. */
    fun ensureChannels(context: Context, lang: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CLIENTS,
                tr(lang, "Client alerts"),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = tr(lang, "Client expiry and traffic limits") },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PANEL,
                tr(lang, "Panel alerts"),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = tr(lang, "Panel, Xray and node status") },
        )
    }

    /** Post a notification; [key] makes the id stable so repeats replace, not stack. */
    fun notify(context: Context, channel: String, key: String, title: String, text: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(key.hashCode(), n) }
    }
}
