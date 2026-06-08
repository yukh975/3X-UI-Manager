package net.yukh.xui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import net.yukh.xui.i18n.LanguageState
import net.yukh.xui.i18n.LocalAppLanguage
import net.yukh.xui.ui.navigation.AppNav
import net.yukh.xui.ui.theme.XuiTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var languageState: LanguageState

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val lang by languageState.language.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppLanguage provides lang) {
                XuiTheme {
                    AppNav()
                }
            }
        }
    }
}
