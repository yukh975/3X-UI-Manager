package net.yukh.xui.security

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.yukh.xui.data.prefs.ConnectionStore
import net.yukh.xui.data.prefs.LockStore
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-lock state. The app starts locked only when a passcode is set AND there is
 * a saved session that gets auto-restored at launch — so a returning user is
 * asked for the passcode, but a fresh manual sign-in (the user just typed the
 * token) is not. Going to the background re-locks (lockIfEnabled, gated on the
 * connected state in MainActivity). Unlock succeeds via the correct PIN or
 * biometric.
 */
@Singleton
class LockState @Inject constructor(
    private val store: LockStore,
    connectionStore: ConnectionStore,
) {
    private val _locked = MutableStateFlow(
        store.getPasscodeHash() != null && connectionStore.getProfile() != null,
    )
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    fun isLockEnabled(): Boolean = store.getPasscodeHash() != null
    fun isBiometricEnabled(): Boolean = store.isBiometricEnabled()
    fun setBiometricEnabled(value: Boolean) = store.setBiometricEnabled(value)

    fun unlock() { _locked.value = false }

    fun lockIfEnabled() {
        if (isLockEnabled()) _locked.value = true
    }

    // ---- Background grace period ------------------------------------------
    // Held in this @Singleton (not the Activity) so it survives activity
    // recreation: a quick switch away and back does not re-arm the lock.

    private var backgroundedAtMs = 0L

    /** Mark that the app went to the background (while signed in). */
    fun onBackgrounded() {
        backgroundedAtMs = SystemClock.elapsedRealtime()
    }

    /** On returning to the foreground, re-lock only if the app was backgrounded
     *  for at least [graceMs] — so a brief switch (e.g. to copy a panel URL)
     *  doesn't prompt for the passcode. */
    fun lockIfBackgroundedFor(graceMs: Long) {
        val at = backgroundedAtMs
        backgroundedAtMs = 0L
        if (at != 0L && SystemClock.elapsedRealtime() - at >= graceMs) lockIfEnabled()
    }

    fun verify(pin: String): Boolean {
        val stored = store.getPasscodeHash() ?: return false
        return stored == sha256(pin)
    }

    fun setPasscode(pin: String) = store.setPasscodeHash(sha256(pin))

    fun removePasscode() {
        store.setPasscodeHash(null)
        store.setBiometricEnabled(false)
        _locked.value = false
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
