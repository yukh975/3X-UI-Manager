package net.yukh.xui.data.prefs

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted storage for the app-lock passcode (stored as a SHA-256 hash, never
 * the PIN itself) and the biometric-unlock preference.
 */
@Singleton
class LockStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getPasscodeHash(): String? = prefs.getString(KEY_HASH, null)
    fun setPasscodeHash(hash: String?) = prefs.edit {
        if (hash == null) remove(KEY_HASH) else putString(KEY_HASH, hash)
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIO, false)
    fun setBiometricEnabled(value: Boolean) = prefs.edit { putBoolean(KEY_BIO, value) }

    private companion object {
        const val FILE = "xui_lock"
        const val KEY_HASH = "passcode_hash"
        const val KEY_BIO = "biometric_enabled"
    }
}
