package net.yukh.xui.data.prefs

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Non-secret app preferences (UI language, etc.) in plain SharedPreferences —
 * separate from the encrypted connection store. Survives disconnect.
 */
@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getLanguage(): String = prefs.getString(KEY_LANG, DEFAULT_LANG) ?: DEFAULT_LANG
    fun setLanguage(lang: String) = prefs.edit { putString(KEY_LANG, lang) }

    private companion object {
        const val FILE = "xui_app_settings"
        const val KEY_LANG = "language"
        const val DEFAULT_LANG = "en"
    }
}
