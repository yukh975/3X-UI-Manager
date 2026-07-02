@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.yukh.xui.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.timeIntervalSince1970
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

actual fun epochNowMs(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

// Strong reference on purpose — UNUserNotificationCenter.delegate is weak.
private val notifDelegate = object : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        // Show banners even while the app is in the foreground (the on-open
        // check would otherwise deliver invisibly).
        withCompletionHandler(
            UNNotificationPresentationOptionBanner or
                UNNotificationPresentationOptionList or
                UNNotificationPresentationOptionSound,
        )
    }
}

actual fun requestNotificationPermission() {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    center.delegate = notifDelegate
    center.requestAuthorizationWithOptions(
        UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
    ) { _, _ -> }
}

actual fun postLocalNotification(id: String, title: String, body: String) {
    val content = UNMutableNotificationContent().apply {
        setTitle(title)
        setBody(body)
        setSound(UNNotificationSound.defaultSound)
    }
    // null trigger = deliver immediately; a repeated id replaces, not stacks.
    val request = UNNotificationRequest.requestWithIdentifier(id, content, null)
    UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { }
}

// ---- Opportunistic background refresh (BGAppRefreshTask) ----
//
// iOS decides when (and whether) to run it, based on app usage. Registration
// must happen before the app finishes launching — iOSApp.swift calls
// MainViewController.kt's registerBgTasks() from its init.

private const val BG_TASK_ID = "net.yukh.xui.alerts.refresh"

internal fun registerAlertsBgTask() {
    BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(BG_TASK_ID, null) { task ->
        val refresh = task as? BGAppRefreshTask ?: return@registerForTaskWithIdentifier
        scheduleAlertsRefresh() // chain the next run
        val job = CoroutineScope(Dispatchers.Default).launch {
            runCatching { AlertsCheck.run(SessionStore()) }
            refresh.setTaskCompletedWithSuccess(true)
        }
        refresh.expirationHandler = { job.cancel() }
    }
    // Delegate must also be set on cold launches that skip the settings screen,
    // or foreground-delivered alerts stay invisible.
    UNUserNotificationCenter.currentNotificationCenter().delegate = notifDelegate
    scheduleAlertsRefresh()
}

actual fun scheduleAlertsRefresh() {
    if (!SessionStore().alertsEnabled()) return
    val request = BGAppRefreshTaskRequest(identifier = BG_TASK_ID).apply {
        earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(30.0 * 60)
    }
    runCatching { BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null) }
}

actual fun cancelAlertsRefresh() {
    BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(BG_TASK_ID)
}
