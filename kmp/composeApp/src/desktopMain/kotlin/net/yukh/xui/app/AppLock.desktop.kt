package net.yukh.xui.app

import java.util.prefs.Preferences

/** Desktop app lock — passcode only (no biometrics in a JVM desktop app). */
actual class AppLock {
    private val p = Preferences.userRoot().node("net/yukh/xui")

    actual fun hasPasscode(): Boolean = p.get(KEY_CODE, "").isNotEmpty()
    actual fun setPasscode(code: String) { p.put(KEY_CODE, code) }
    actual fun removePasscode() { p.remove(KEY_CODE) }
    actual fun check(code: String): Boolean = p.get(KEY_CODE, "") == code

    actual fun biometryAvailable(): Boolean = false
    actual fun biometryEnabled(): Boolean = false
    actual fun setBiometryEnabled(on: Boolean) {}
    actual fun authenticate(reason: String, onResult: (Boolean) -> Unit) { onResult(false) }

    private companion object {
        const val KEY_CODE = "lockCode"
    }
}
