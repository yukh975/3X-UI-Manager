@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.yukh.xui.app

import platform.Foundation.NSUserDefaults
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual class AppLock {
    private val d = NSUserDefaults.standardUserDefaults

    actual fun hasPasscode(): Boolean = !(d.stringForKey(KEY_CODE) ?: "").isEmpty()
    actual fun setPasscode(code: String) { d.setObject(code, forKey = KEY_CODE) }
    actual fun removePasscode() {
        d.removeObjectForKey(KEY_CODE)
        d.setBool(false, forKey = KEY_BIO)
    }
    actual fun check(code: String): Boolean = (d.stringForKey(KEY_CODE) ?: "") == code

    actual fun biometryAvailable(): Boolean =
        LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)

    actual fun biometryEnabled(): Boolean = d.boolForKey(KEY_BIO)
    actual fun setBiometryEnabled(on: Boolean) { d.setBool(on, forKey = KEY_BIO) }

    actual fun authenticate(reason: String, onResult: (Boolean) -> Unit) {
        val ctx = LAContext()
        if (!ctx.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)) {
            onResult(false); return
        }
        ctx.evaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, reason) { success, _ ->
            dispatch_async(dispatch_get_main_queue()) { onResult(success) }
        }
    }

    private companion object {
        const val KEY_CODE = "xui.lockCode"
        const val KEY_BIO = "xui.lockBio"
    }
}
