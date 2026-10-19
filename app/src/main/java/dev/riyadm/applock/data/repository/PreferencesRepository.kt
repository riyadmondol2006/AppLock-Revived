package dev.riyadm.applock.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.riyadm.applock.core.utils.SecurityUtils

/**
 * Repository for managing application preferences and settings.
 * Handles all SharedPreferences operations with proper separation of concerns.
 */
class PreferencesRepository(context: Context) {

    private val appLockPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME_APP_LOCK, Context.MODE_PRIVATE)

    private val settingsPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME_SETTINGS, Context.MODE_PRIVATE)

    fun setPassword(password: String) {
        appLockPrefs.edit(commit = true) {
            putString(KEY_PASSWORD, SecurityUtils.hashPassword(password))
        }
    }

    fun getPassword(): String? {
        return appLockPrefs.getString(KEY_PASSWORD, null)
    }

    fun validatePassword(input: String): Boolean {
        val stored = getPassword()
        if (stored.isNullOrBlank()) return false

        val sanitizedInput = SecurityUtils.sanitizePassword(input)

        if (SecurityUtils.isHashed(stored)) {
            val valid = SecurityUtils.verifyPassword(sanitizedInput, stored)
            if (valid && SecurityUtils.needsRehash(stored)) setPassword(sanitizedInput)
            return valid
        }

        // Legacy plaintext storage from very old versions
        if (stored == input || stored == sanitizedInput) {
            setPassword(sanitizedInput)
            return true
        }

        return false
    }

    fun setPattern(pattern: String) {
        appLockPrefs.edit(commit = true) {
            putString(KEY_PATTERN, SecurityUtils.hashPassword(pattern))
        }
    }

    fun getPattern(): String? {
        return appLockPrefs.getString(KEY_PATTERN, null)
    }

    fun validatePattern(inputPattern: String): Boolean {
        val stored = getPattern()
        if (stored.isNullOrEmpty() || inputPattern.isEmpty()) return false

        if (SecurityUtils.isHashed(stored)) {
            val valid = SecurityUtils.verifyPassword(inputPattern, stored)
            if (valid && SecurityUtils.needsRehash(stored)) setPattern(inputPattern)
            return valid
        }

        // Patterns used to be stored in plaintext; migrate on first successful unlock
        if (inputPattern == stored) {
            setPattern(inputPattern)
            return true
        }
        return false
    }

    fun setLockType(lockType: String) {
        settingsPrefs.edit(commit = true) { putString(KEY_LOCK_TYPE, lockType) }
    }

    fun getLockType(): String {
        return settingsPrefs.getString(KEY_LOCK_TYPE, LOCK_TYPE_PIN) ?: LOCK_TYPE_PIN
    }

    fun setBiometricAuthEnabled(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_BIOMETRIC_AUTH_ENABLED, enabled) }
    }

    fun isBiometricAuthEnabled(): Boolean {
        return settingsPrefs.getBoolean(KEY_BIOMETRIC_AUTH_ENABLED, false)
    }

    fun setUseMaxBrightness(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_USE_MAX_BRIGHTNESS, enabled) }
    }

    fun shouldUseMaxBrightness(): Boolean {
        return settingsPrefs.getBoolean(KEY_USE_MAX_BRIGHTNESS, false)
    }

    fun setDisableHaptics(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_DISABLE_HAPTICS, enabled) }
    }

    fun shouldDisableHaptics(): Boolean {
        return settingsPrefs.getBoolean(KEY_DISABLE_HAPTICS, false)
    }

    fun setShowSystemApps(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_SHOW_SYSTEM_APPS, enabled) }
    }

    fun shouldShowSystemApps(): Boolean {
        return settingsPrefs.getBoolean(KEY_SHOW_SYSTEM_APPS, false)
    }

    fun setAntiUninstallEnabled(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_ANTI_UNINSTALL, enabled) }
    }

    fun isAntiUninstallEnabled(): Boolean {
        return settingsPrefs.getBoolean(KEY_ANTI_UNINSTALL, false)
    }

    fun setProtectEnabled(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_APPLOCK_ENABLED, enabled) }
    }

    fun isProtectEnabled(): Boolean {
        return settingsPrefs.getBoolean(KEY_APPLOCK_ENABLED, DEFAULT_PROTECT_ENABLED)
    }

    fun setUnlockTimeDuration(minutes: Int) {
        settingsPrefs.edit { putInt(KEY_UNLOCK_TIME_DURATION, minutes) }
    }

    fun getUnlockTimeDuration(): Int {
        return settingsPrefs.getInt(KEY_UNLOCK_TIME_DURATION, DEFAULT_UNLOCK_DURATION)
    }

    fun setAutoUnlockEnabled(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_AUTO_UNLOCK, enabled) }
    }

    fun isAutoUnlockEnabled(): Boolean {
        return settingsPrefs.getBoolean(KEY_AUTO_UNLOCK, false)
    }

    fun setBackendImplementation(backend: BackendImplementation) {
        settingsPrefs.edit { putString(KEY_BACKEND_IMPLEMENTATION, backend.name) }
    }

    fun getBackendImplementation(): BackendImplementation {
        val backend = settingsPrefs.getString(
            KEY_BACKEND_IMPLEMENTATION,
            BackendImplementation.ACCESSIBILITY.name
        )
        return try {
            BackendImplementation.valueOf(backend ?: BackendImplementation.ACCESSIBILITY.name)
        } catch (_: IllegalArgumentException) {
            BackendImplementation.ACCESSIBILITY
        }
    }

    fun isShowCommunityLink(): Boolean {
        return !settingsPrefs.getBoolean(KEY_COMMUNITY_LINK_SHOWN, false)
    }

    fun setCommunityLinkShown(shown: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_COMMUNITY_LINK_SHOWN, shown) }
    }

    fun isShowDonateLink(context: Context): Boolean {
        return settingsPrefs.getBoolean(KEY_SHOW_DONATE_LINK, false)
    }

    fun setShowDonateLink(context: Context, show: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_SHOW_DONATE_LINK, show) }
    }

    fun isLoggingEnabled(): Boolean {
        return settingsPrefs.getBoolean(KEY_LOGGING_ENABLED, false)
    }

    fun setLoggingEnabled(enabled: Boolean) {
        settingsPrefs.edit { putBoolean(KEY_LOGGING_ENABLED, enabled) }
    }

    // Appearance
    fun getThemeMode(): String = settingsPrefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM
    fun setThemeMode(mode: String) = settingsPrefs.edit { putString(KEY_THEME_MODE, mode) }
    fun isDynamicColorEnabled(): Boolean = settingsPrefs.getBoolean(KEY_DYNAMIC_COLOR, true)
    fun setDynamicColorEnabled(enabled: Boolean) =
        settingsPrefs.edit { putBoolean(KEY_DYNAMIC_COLOR, enabled) }

    fun isAmoledBlackEnabled(): Boolean = settingsPrefs.getBoolean(KEY_AMOLED_BLACK, false)
    fun setAmoledBlackEnabled(enabled: Boolean) =
        settingsPrefs.edit { putBoolean(KEY_AMOLED_BLACK, enabled) }

    // Lock screen behaviour
    fun isScrambleKeypadEnabled(): Boolean = settingsPrefs.getBoolean(KEY_SCRAMBLE_KEYPAD, false)
    fun setScrambleKeypadEnabled(enabled: Boolean) =
        settingsPrefs.edit { putBoolean(KEY_SCRAMBLE_KEYPAD, enabled) }

    fun isBiometricAutoPromptEnabled(): Boolean =
        settingsPrefs.getBoolean(KEY_BIOMETRIC_AUTO_PROMPT, true)

    fun setBiometricAutoPromptEnabled(enabled: Boolean) =
        settingsPrefs.edit { putBoolean(KEY_BIOMETRIC_AUTO_PROMPT, enabled) }

    fun isAutoLockNewAppsEnabled(): Boolean = settingsPrefs.getBoolean(KEY_AUTO_LOCK_NEW_APPS, false)
    fun setAutoLockNewAppsEnabled(enabled: Boolean) =
        settingsPrefs.edit { putBoolean(KEY_AUTO_LOCK_NEW_APPS, enabled) }

    fun isUnlockLogEnabled(): Boolean = settingsPrefs.getBoolean(KEY_UNLOCK_LOG, true)
    fun setUnlockLogEnabled(enabled: Boolean) =
        settingsPrefs.edit { putBoolean(KEY_UNLOCK_LOG, enabled) }

    // Brute-force protection. 0 attempts = disabled.
    fun getMaxFailedAttempts(): Int =
        settingsPrefs.getInt(KEY_MAX_FAILED_ATTEMPTS, DEFAULT_MAX_FAILED_ATTEMPTS)

    fun setMaxFailedAttempts(attempts: Int) =
        settingsPrefs.edit { putInt(KEY_MAX_FAILED_ATTEMPTS, attempts) }

    fun getLockoutDurationSeconds(): Int =
        settingsPrefs.getInt(KEY_LOCKOUT_DURATION, DEFAULT_LOCKOUT_SECONDS)

    fun setLockoutDurationSeconds(seconds: Int) =
        settingsPrefs.edit { putInt(KEY_LOCKOUT_DURATION, seconds) }

    fun getFailedAttemptCount(): Int = appLockPrefs.getInt(KEY_FAILED_ATTEMPTS, 0)
    fun getLockoutUntil(): Long = appLockPrefs.getLong(KEY_LOCKOUT_UNTIL, 0L)

    /** Records a failed attempt and returns the lockout end time (0 if not locked out). */
    @Synchronized
    fun registerFailedAttempt(): Long {
        val max = getMaxFailedAttempts()
        val count = getFailedAttemptCount() + 1
        if (max > 0 && count >= max) {
            // Each further lockout in the same streak doubles, capped at 16x
            val streak = appLockPrefs.getInt(KEY_LOCKOUT_STREAK, 0)
            val multiplier = 1L shl streak.coerceAtMost(4)
            val until = System.currentTimeMillis() + getLockoutDurationSeconds() * 1000L * multiplier
            appLockPrefs.edit(commit = true) {
                putInt(KEY_FAILED_ATTEMPTS, 0)
                putInt(KEY_LOCKOUT_STREAK, streak + 1)
                putLong(KEY_LOCKOUT_UNTIL, until)
            }
            return until
        }
        appLockPrefs.edit(commit = true) { putInt(KEY_FAILED_ATTEMPTS, count) }
        return 0L
    }

    @Synchronized
    fun resetFailedAttempts() {
        appLockPrefs.edit(commit = true) {
            putInt(KEY_FAILED_ATTEMPTS, 0)
            putInt(KEY_LOCKOUT_STREAK, 0)
            putLong(KEY_LOCKOUT_UNTIL, 0L)
        }
    }

    /** Remaining lockout in milliseconds, or 0 if input is allowed. */
    fun getRemainingLockoutMs(): Long {
        val remaining = getLockoutUntil() - System.currentTimeMillis()
        // Guard against the clock being moved back to escape a lockout
        val cap = getLockoutDurationSeconds() * 1000L * 16
        return remaining.coerceIn(0L, cap)
    }

    /** Raw access used by settings backup/restore. Credentials are never exported. */
    internal fun exportSettings(): Map<String, Any?> = settingsPrefs.all.filterKeys {
        it in EXPORTABLE_SETTINGS_KEYS
    }

    internal fun importSettings(values: Map<String, Any?>) {
        settingsPrefs.edit(commit = true) {
            values.forEach { (key, value) ->
                if (key !in EXPORTABLE_SETTINGS_KEYS) return@forEach
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is String -> putString(key, value)
                }
            }
        }
    }

    companion object {
        private const val PREFS_NAME_APP_LOCK = "app_lock_prefs"
        private const val PREFS_NAME_SETTINGS = "app_lock_settings"

        private const val KEY_PASSWORD = "password"
        private const val KEY_PATTERN = "pattern"
        private const val KEY_BIOMETRIC_AUTH_ENABLED = "use_biometric_auth"
        private const val KEY_DISABLE_HAPTICS = "disable_haptics"
        private const val KEY_USE_MAX_BRIGHTNESS = "use_max_brightness"
        private const val KEY_ANTI_UNINSTALL = "anti_uninstall"
        private const val KEY_UNLOCK_TIME_DURATION = "unlock_time_duration"
        private const val KEY_BACKEND_IMPLEMENTATION = "backend_implementation"
        private const val KEY_COMMUNITY_LINK_SHOWN = "community_link_shown"
        private const val KEY_SHOW_DONATE_LINK = "show_donate_link"
        private const val KEY_LOGGING_ENABLED = "logging_enabled"
        private const val LAST_VERSION_CODE = "last_version_code"
        private const val KEY_APPLOCK_ENABLED = "applock_enabled"
        private const val KEY_AUTO_UNLOCK = "auto_unlock"
        private const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
        private const val KEY_LOCK_TYPE = "lock_type"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_AMOLED_BLACK = "amoled_black"
        private const val KEY_SCRAMBLE_KEYPAD = "scramble_keypad"
        private const val KEY_BIOMETRIC_AUTO_PROMPT = "biometric_auto_prompt"
        private const val KEY_AUTO_LOCK_NEW_APPS = "auto_lock_new_apps"
        private const val KEY_UNLOCK_LOG = "unlock_log_enabled"
        private const val KEY_MAX_FAILED_ATTEMPTS = "max_failed_attempts"
        private const val KEY_LOCKOUT_DURATION = "lockout_duration_seconds"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_STREAK = "lockout_streak"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"

        // Settings that are safe to export. Lock type is excluded because the
        // credential itself is never exported, so restoring it could lock the user out.
        private val EXPORTABLE_SETTINGS_KEYS = setOf(
            KEY_BIOMETRIC_AUTH_ENABLED, KEY_DISABLE_HAPTICS, KEY_USE_MAX_BRIGHTNESS,
            KEY_UNLOCK_TIME_DURATION, KEY_BACKEND_IMPLEMENTATION, KEY_APPLOCK_ENABLED,
            KEY_AUTO_UNLOCK, KEY_SHOW_SYSTEM_APPS, KEY_THEME_MODE, KEY_DYNAMIC_COLOR,
            KEY_AMOLED_BLACK, KEY_SCRAMBLE_KEYPAD, KEY_BIOMETRIC_AUTO_PROMPT,
            KEY_AUTO_LOCK_NEW_APPS, KEY_UNLOCK_LOG, KEY_MAX_FAILED_ATTEMPTS,
            KEY_LOCKOUT_DURATION
        )

        private const val DEFAULT_PROTECT_ENABLED = true
        private const val DEFAULT_UNLOCK_DURATION = 0
        private const val DEFAULT_MAX_FAILED_ATTEMPTS = 5
        private const val DEFAULT_LOCKOUT_SECONDS = 30

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val LOCK_TYPE_PIN = "pin"
        const val LOCK_TYPE_PATTERN = "pattern"
        const val LOCK_TYPE_PASSWORD = "password"
    }
}
