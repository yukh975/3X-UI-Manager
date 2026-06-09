package net.yukh.xui.app

/**
 * App lock: a numeric passcode + optional biometric unlock (Face ID / Touch ID
 * on iOS). Mirrors the Android app-lock. Passcode persistence is platform-
 * specific (iOS: NSUserDefaults for now — TODO move to Keychain, same hardening
 * the token storage needs).
 */
expect class AppLock() {
    fun hasPasscode(): Boolean
    fun setPasscode(code: String)
    fun removePasscode()
    fun check(code: String): Boolean

    fun biometryAvailable(): Boolean
    fun biometryEnabled(): Boolean
    fun setBiometryEnabled(on: Boolean)

    /** Prompt biometric auth; [onResult] is invoked on the main thread. */
    fun authenticate(reason: String, onResult: (Boolean) -> Unit)
}
