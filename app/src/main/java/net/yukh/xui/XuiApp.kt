package net.yukh.xui

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import net.yukh.xui.data.prefs.AppSettingsStore
import net.yukh.xui.work.AlertScheduler
import net.yukh.xui.work.Notifier

@HiltAndroidApp
class XuiApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settings: AppSettingsStore

    // WorkManager initializes on demand with this configuration (the automatic
    // initializer is removed in the manifest) so @HiltWorker workers get DI.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // (Re-)create channels on every start — also refreshes their names
        // after a language switch.
        Notifier.ensureChannels(this, settings.getLanguage())
        // Re-assert the periodic poll after reboots/updates (KEEP policy).
        if (settings.getAlertsEnabled()) AlertScheduler.ensureScheduled(this)
    }
}
