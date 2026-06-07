package net.yukh.xui.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Encrypted storage for the user's panel connection profile.
 *
 * EncryptedSharedPreferences gives AES-256 GCM on values, AES-256 SIV on
 * keys, master key in the Android Keystore. The whole profile (URL + auth)
 * is serialized to a single JSON blob — keeps the sealed [ConnectionAuth]
 * discriminator intact and means we only ever touch one prefs key.
 */
@Singleton
class ConnectionStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
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
        val raw = prefs.getString(KEY_PROFILE, null) ?: return null
        return runCatching { json.decodeFromString<ConnectionProfile>(raw) }.getOrNull()
    }

    fun saveProfile(profile: ConnectionProfile) {
        prefs.edit().putString(KEY_PROFILE, json.encodeToString(profile)).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE = "xui_connection"
        const val KEY_PROFILE = "profile"
    }
}
