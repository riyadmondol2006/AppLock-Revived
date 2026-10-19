package dev.riyadm.applock.features.settings.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.BackendImplementation
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Observable snapshot of the settings shown on the settings screen; writes go straight to the repository. */
@Stable
class SettingsState(private val repository: AppLockRepository) {

    var maxBrightness by pref(repository.shouldUseMaxBrightness(), repository::setUseMaxBrightness)
    var biometricAuth by pref(repository.isBiometricAuthEnabled(), repository::setBiometricAuthEnabled)
    var biometricAutoPrompt by pref(
        repository.isBiometricAutoPromptEnabled(),
        repository::setBiometricAutoPromptEnabled
    )
    var scrambleKeypad by pref(repository.isScrambleKeypadEnabled(), repository::setScrambleKeypadEnabled)
    var disableHaptics by pref(repository.shouldDisableHaptics(), repository::setDisableHaptics)
    var autoUnlock by pref(repository.isAutoUnlockEnabled(), repository::setAutoUnlockEnabled)
    var unlockDuration by pref(repository.getGlobalUnlockTimeDuration(), repository::setUnlockTimeDuration)
    var maxFailedAttempts by pref(repository.getMaxFailedAttempts(), repository::setMaxFailedAttempts)
    var lockoutSeconds by pref(repository.getLockoutDurationSeconds(), repository::setLockoutDurationSeconds)
    var autoLockNewApps by pref(repository.isAutoLockNewAppsEnabled(), repository::setAutoLockNewAppsEnabled)
    var unlockLog by pref(repository.isUnlockLogEnabled(), repository::setUnlockLogEnabled)
    var updateCheck by pref(repository.isUpdateCheckEnabled(), repository::setUpdateCheckEnabled)
    var logging by pref(repository.isLoggingEnabled(), repository::setLoggingEnabled)

    private val antiUninstallState = mutableStateOf(repository.isAntiUninstallEnabled())
    var antiUninstall: Boolean
        get() = antiUninstallState.value
        set(value) {
            antiUninstallState.value = value
            repository.setAntiUninstallEnabled(value)
        }

    private val backendState = mutableStateOf(repository.getBackendImplementation())
    val backend: BackendImplementation get() = backendState.value

    val lockType: String = repository.getLockType()

    fun onBackendChanged(backend: BackendImplementation) {
        backendState.value = backend
    }

    /** Re-reads values that other screens or activities may have changed. */
    fun refreshExternal() {
        antiUninstallState.value = repository.isAntiUninstallEnabled()
        backendState.value = repository.getBackendImplementation()
    }

    private class Pref<T>(initial: T, private val save: (T) -> Unit) : ReadWriteProperty<Any?, T> {
        private val state = mutableStateOf(initial)
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            save(value)
        }
    }

    private fun <T> pref(initial: T, save: (T) -> Unit): ReadWriteProperty<Any?, T> = Pref(initial, save)
}
