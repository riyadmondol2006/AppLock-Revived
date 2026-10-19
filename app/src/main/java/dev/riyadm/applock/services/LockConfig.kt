package dev.riyadm.applock.services

import android.content.Context
import android.content.SharedPreferences
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.BackendImplementation
import dev.riyadm.applock.data.repository.LockedAppsRepository
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The settings every lock decision reads, kept in memory for the hot paths (accessibility
 * events, polling ticks), which used to copy the locked-apps set and re-parse settings each time.
 *
 * The locked-apps set is read straight from SharedPreferences without a copy. SharedPreferences
 * stores a new set on every edit and never mutates a stored one, so the read is always current
 * and allocation-free; callers must not modify it. Protection, backend and anti-uninstall are
 * cached and refreshed by a change listener, which also tells the polling backends to resume or
 * pause. All writes happen in this process, so the listener sees every change.
 */
internal class LockConfig(context: Context, private val repository: AppLockRepository) {

    private val lockedAppsPrefs: SharedPreferences =
        context.getSharedPreferences(LockedAppsRepository.PREFS_NAME, Context.MODE_PRIVATE)
    private val settingsPrefs: SharedPreferences =
        context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    var protectEnabled: Boolean = true
        private set

    @Volatile
    var backend: BackendImplementation = BackendImplementation.ACCESSIBILITY
        private set

    @Volatile
    var antiUninstallEnabled: Boolean = false
        private set

    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    // Held as a field: SharedPreferences only keeps weak references to its listeners.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        reload()
        listeners.forEach { it() }
    }

    init {
        reload()
        lockedAppsPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
        settingsPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    /** The active profile's locked apps. Read-only; never modify the returned set. */
    fun lockedApps(): Set<String> =
        lockedAppsPrefs.getStringSet(LockedAppsRepository.KEY_LOCKED_APPS, null) ?: emptySet()

    fun isLocked(packageName: String): Boolean = packageName in lockedApps()

    fun hasLockedApps(): Boolean = lockedApps().isNotEmpty()

    /** [listener] runs on the main thread after any lock setting changed. */
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun reload() {
        protectEnabled = repository.isProtectEnabled()
        backend = repository.getBackendImplementation()
        antiUninstallEnabled = repository.isAntiUninstallEnabled()
    }

    companion object {
        /** The file [dev.riyadm.applock.data.repository.PreferencesRepository] keeps settings in. */
        internal const val SETTINGS_PREFS_NAME = "app_lock_settings"

        @Volatile
        private var instance: LockConfig? = null

        fun get(context: Context): LockConfig = instance ?: synchronized(this) {
            instance ?: LockConfig(
                context.applicationContext,
                context.applicationContext.appLockRepository()
            ).also { instance = it }
        }
    }
}
