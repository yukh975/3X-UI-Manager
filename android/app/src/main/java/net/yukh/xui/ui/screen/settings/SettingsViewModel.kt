package net.yukh.xui.ui.screen.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import net.yukh.xui.i18n.LanguageState

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val languageState: LanguageState,
) : ViewModel() {
    val language: StateFlow<String> = languageState.language
    fun setLanguage(lang: String) = languageState.set(lang)
}
