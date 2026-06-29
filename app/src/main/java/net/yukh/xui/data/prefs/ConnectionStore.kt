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

    /** All saved profiles. Migrates a pre-multi-profile single blob on first read. */
    fun getProfiles(): List<ConnectionProfile> {
        prefs.getString(KEY_PROFILES, null)?.let { raw ->
            return runCatching { json.decodeFromString<List<ConnectionProfile>>(raw) }.getOrDefault(emptyList())
        }
        val legacy = prefs.getString(KEY_PROFILE, null) ?: return emptyList()
        val profile = runCatching { json.decodeFromString<ConnectionProfile>(legacy) }.getOrNull()
            ?: return emptyList()
        val migrated = listOf(profile.ensureId())
        saveProfiles(migrated)
        setActiveId(migrated.first().id)
        prefs.edit().remove(KEY_PROFILE).apply()
        return migrated
    }

    fun saveProfiles(profiles: List<ConnectionProfile>) {
        prefs.edit().putString(KEY_PROFILES, json.encodeToString(profiles)).apply()
    }

    fun getActiveId(): String? = prefs.getString(KEY_ACTIVE_ID, null)

    fun setActiveId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    /** The active profile (or the first saved, or null). Kept for callers that
     *  only care about the current connection (repo init, lock state). */
    fun getProfile(): ConnectionProfile? {
        val all = getProfiles()
        return all.firstOrNull { it.id == getActiveId() } ?: all.firstOrNull()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun ConnectionProfile.ensureId(): ConnectionProfile =
        if (id.isNotBlank()) this else copy(id = java.util.UUID.randomUUID().toString())

    private companion object {
        const val FILE = "xui_connection"
        const val KEY_PROFILE = "profile"
        const val KEY_PROFILES = "profiles"
        const val KEY_ACTIVE_ID = "active_id"
    }
}
