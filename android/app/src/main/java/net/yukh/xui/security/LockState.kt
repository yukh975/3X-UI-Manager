package net.yukh.xui.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.yukh.xui.data.prefs.LockStore
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-lock state. The app starts locked iff a passcode is set. Going to the
 * background re-locks (lockIfEnabled). Unlock succeeds via the correct PIN or
 * biometric.
 */
@Singleton
class LockState @Inject constructor(
    private val store: LockStore,
) {
    private val _locked = MutableStateFlow(store.getPasscodeHash() != null)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    fun isLockEnabled(): Boolean = store.getPasscodeHash() != null
    fun isBiometricEnabled(): Boolean = store.isBiometricEnabled()
    fun setBiometricEnabled(value: Boolean) = store.setBiometricEnabled(value)

    fun unlock() { _locked.value = false }

    fun lockIfEnabled() {
        if (isLockEnabled()) _locked.value = true
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
