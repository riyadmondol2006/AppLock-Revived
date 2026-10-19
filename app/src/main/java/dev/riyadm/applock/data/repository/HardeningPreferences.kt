package dev.riyadm.applock.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.content.edit

/**
 * Hardening options (closing locked apps, history retention, pattern size). Stored in the same
 * "app_lock_settings" file as the rest of the settings.
 */
class HardeningPreferences(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var closeLockedAppsOnScreenOff: Boolean
        get() = prefs.getBoolean(KEY_CLOSE_LOCKED_APPS, false)
        set(value) = prefs.edit { putBoolean(KEY_CLOSE_LOCKED_APPS, value) }

    /** Unlock history entries older than this many days are deleted. */
    var unlockLogRetentionDays: Int
        get() = prefs.getInt(KEY_LOG_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)
            .takeIf { it in RETENTION_OPTIONS } ?: DEFAULT_RETENTION_DAYS
        set(value) = prefs.edit {
            putInt(KEY_LOG_RETENTION_DAYS, value.takeIf { it in RETENTION_OPTIONS } ?: DEFAULT_RETENTION_DAYS)
        }

    var patternSize: String
        get() = prefs.getString(KEY_PATTERN_SIZE, PATTERN_MEDIUM)
            ?.takeIf { it in PATTERN_SIZES } ?: PATTERN_MEDIUM
        set(value) = prefs.edit {
            putString(KEY_PATTERN_SIZE, value.takeIf { it in PATTERN_SIZES } ?: PATTERN_MEDIUM)
        }

    /**
     * Unlock history used to be on by default and is now off by default. Users upgrading from an
     * older version who never touched the switch get the old behaviour written explicitly, so
     * only new installs start with it off. Runs once.
     */
    fun migrateUnlockLogDefault() {
        if (prefs.getBoolean(KEY_UNLOCK_LOG_MIGRATED, false)) return
        try {
            if (!prefs.contains(KEY_UNLOCK_LOG_ENABLED) && isUpgradeInstall()) {
                prefs.edit { putBoolean(KEY_UNLOCK_LOG_ENABLED, true) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unlock history migration failed", e)
        }
        prefs.edit { putBoolean(KEY_UNLOCK_LOG_MIGRATED, true) }
    }

    private fun isUpgradeInstall(): Boolean {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.getPackageInfo(
                appContext.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        }
        if (info.lastUpdateTime > info.firstInstallTime) return true
        // A credential or app list from an earlier version is another sign of an existing user.
        return appContext.getSharedPreferences(LOCK_PREFS_NAME, Context.MODE_PRIVATE).all.isNotEmpty()
    }

    /** Values included in settings backups. */
    fun exportValues(): Map<String, Any> = mapOf(
        KEY_CLOSE_LOCKED_APPS to closeLockedAppsOnScreenOff,
        KEY_LOG_RETENTION_DAYS to unlockLogRetentionDays,
        KEY_PATTERN_SIZE to patternSize,
    )

    /** Applies backed-up values after validating them. Returns how many were applied. */
    fun importValues(values: Map<String, Any?>): Int {
        var applied = 0
        (values[KEY_CLOSE_LOCKED_APPS] as? Boolean)?.let {
            closeLockedAppsOnScreenOff = it
            applied++
        }
        (values[KEY_LOG_RETENTION_DAYS] as? Number)?.toInt()?.takeIf { it in RETENTION_OPTIONS }?.let {
            unlockLogRetentionDays = it
            applied++
        }
        (values[KEY_PATTERN_SIZE] as? String)?.takeIf { it in PATTERN_SIZES }?.let {
            patternSize = it
            applied++
        }
        return applied
    }

    companion object {
        private const val TAG = "HardeningPreferences"
        private const val PREFS_NAME = "app_lock_settings"
        private const val LOCK_PREFS_NAME = "app_lock_prefs"

        private const val KEY_CLOSE_LOCKED_APPS = "close_locked_apps_on_screen_off"
        private const val KEY_LOG_RETENTION_DAYS = "unlock_log_retention_days"
        private const val KEY_PATTERN_SIZE = "pattern_size"
        private const val KEY_UNLOCK_LOG_MIGRATED = "unlock_log_default_migrated"

        /** Same key PreferencesRepository uses for the unlock history switch. */
        private const val KEY_UNLOCK_LOG_ENABLED = "unlock_log_enabled"

        const val DEFAULT_RETENTION_DAYS = 7
        val RETENTION_OPTIONS = listOf(1, 7, 30)

        const val PATTERN_SMALL = "small"
        const val PATTERN_MEDIUM = "medium"
        const val PATTERN_LARGE = "large"
        val PATTERN_SIZES = listOf(PATTERN_SMALL, PATTERN_MEDIUM, PATTERN_LARGE)
    }
}
