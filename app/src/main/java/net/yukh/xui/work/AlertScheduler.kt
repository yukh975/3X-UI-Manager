package net.yukh.xui.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Schedules the [AlertsWorker] poll behind the "Panel alerts" setting. */
object AlertScheduler {
    private const val PERIODIC = "panel_alerts_poll"
    private const val ONCE = "panel_alerts_once"

    private val online =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Idempotent (KEEP) — safe to call on every app start. The first run is
     *  delayed a full period so it doesn't fire a duplicate burst alongside the
     *  immediate [runNow] pass when the user enables alerts. */
    fun ensureScheduled(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AlertsWorker>(30, TimeUnit.MINUTES)
                .setConstraints(online)
                .setInitialDelay(30, TimeUnit.MINUTES)
                .build(),
        )
    }

    /** One immediate pass — instant feedback when the user flips the toggle on. */
    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONCE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AlertsWorker>().setConstraints(online).build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }
}
