package net.yukh.xui.app

// Desktop has no notification pipeline (yet) — alerts are an iOS/Android
// feature; the shared UI and checks compile, the platform hooks do nothing.

actual fun requestNotificationPermission() {}

actual fun postLocalNotification(id: String, title: String, body: String) {}

actual fun scheduleAlertsRefresh() {}

actual fun cancelAlertsRefresh() {}

actual fun epochNowMs(): Long = System.currentTimeMillis()
