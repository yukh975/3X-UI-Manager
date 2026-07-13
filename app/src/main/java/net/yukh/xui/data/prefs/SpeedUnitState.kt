package net.yukh.xui.data.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide speed-unit choice (bits/s vs bytes/s), backed by [AppSettingsStore].
 * The root composable observes [inBits] and republishes it through
 * LocalSpeedInBits so every live-speed label re-renders when it changes.
 */
@Singleton
class SpeedUnitState @Inject constructor(
    private val store: AppSettingsStore,
) {
    private val _inBits = MutableStateFlow(store.getSpeedInBits())
    val inBits: StateFlow<Boolean> = _inBits.asStateFlow()

    fun set(bits: Boolean) {
        store.setSpeedInBits(bits)
        _inBits.value = bits
    }
}
