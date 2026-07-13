package net.yukh.xui.ui.screen.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import net.yukh.xui.data.prefs.AppSettingsStore
import net.yukh.xui.data.prefs.SpeedUnitState
import net.yukh.xui.i18n.LanguageState
import net.yukh.xui.security.LockState

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val languageState: LanguageState,
    private val lockState: LockState,
    private val settings: AppSettingsStore,
    private val speedUnitState: SpeedUnitState,
) : ViewModel() {
    val language: StateFlow<String> = languageState.language
    fun setLanguage(lang: String) = languageState.set(lang)

    val speedInBits: StateFlow<Boolean> = speedUnitState.inBits
    fun setSpeedInBits(value: Boolean) = speedUnitState.set(value)

    fun hasPasscode(): Boolean = lockState.isLockEnabled()
    fun biometricEnabled(): Boolean = lockState.isBiometricEnabled()
    fun setBiometricEnabled(value: Boolean) = lockState.setBiometricEnabled(value)
    fun setPasscode(pin: String) = lockState.setPasscode(pin)
    fun removePasscode() = lockState.removePasscode()

    fun alertsEnabled(): Boolean = settings.getAlertsEnabled()
    fun setAlertsEnabled(value: Boolean) = settings.setAlertsEnabled(value)
    fun alertExpiryDays(): Int = settings.getAlertExpiryDays()
    fun setAlertExpiryDays(days: Int) = settings.setAlertExpiryDays(days)
    fun alertTrafficPct(): Int = settings.getAlertTrafficPct()
    fun setAlertTrafficPct(pct: Int) = settings.setAlertTrafficPct(pct)
    fun alertPanelPort(): Int = settings.getAlertPanelPort()
    fun setAlertPanelPort(port: Int) = settings.setAlertPanelPort(port)
}
