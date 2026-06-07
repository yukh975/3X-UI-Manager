package net.yukh.xui.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted storage for the user's panel connection profile.
 *
 * Uses Jetpack's EncryptedSharedPreferences (AES-256 GCM for values,
 * AES-256 SIV for keys). The master key lives in the Android Keystore,
 * so reading the raw prefs file off a rooted device still doesn't
 * expose the panel's API token.
 */
@Singleton
class ConnectionStore @Inject constructor(
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

    fun getProfile(): ConnectionProfile? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        if (url.isBlank() || token.isBlank()) return null
        return ConnectionProfile(
            baseUrl = url,
            token = token,
            allowInsecureTls = prefs.getBoolean(KEY_INSECURE, false),
        )
    }

    fun saveProfile(profile: ConnectionProfile) {
        prefs.edit()
            .putString(KEY_URL, profile.baseUrl)
            .putString(KEY_TOKEN, profile.token)
            .putBoolean(KEY_INSECURE, profile.allowInsecureTls)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE = "xui_connection"
        const val KEY_URL = "base_url"
        const val KEY_TOKEN = "api_token"
        const val KEY_INSECURE = "allow_insecure_tls"
    }
}
