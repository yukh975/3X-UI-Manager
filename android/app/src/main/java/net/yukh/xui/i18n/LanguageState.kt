package net.yukh.xui.i18n

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.yukh.xui.data.prefs.AppSettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/** Supported UI languages. Default is English. */
const val LANG_EN = "en"
const val LANG_RU = "ru"

/**
 * App-wide selected UI language, backed by [AppSettingsStore]. The root
 * composable observes [language] and republishes it through LocalAppLanguage
 * so the whole tree re-renders when it changes.
 */
@Singleton
class LanguageState @Inject constructor(
    private val store: AppSettingsStore,
) {
    private val _language = MutableStateFlow(store.getLanguage())
    val language: StateFlow<String> = _language.asStateFlow()

    fun set(lang: String) {
        store.setLanguage(lang)
        _language.value = lang
    }
}
