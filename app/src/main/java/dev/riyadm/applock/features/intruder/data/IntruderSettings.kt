package dev.riyadm.applock.features.intruder.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Intruder selfie preferences. They live in the shared settings file but are deliberately
 * not part of the JSON backup allowlist, so a restore never silently turns the camera on.
 */
class IntruderSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    var attemptThreshold: Int
        get() = prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD).coerceIn(THRESHOLD_OPTIONS.first(), THRESHOLD_OPTIONS.last())
        set(value) = prefs.edit { putInt(KEY_THRESHOLD, value.coerceIn(THRESHOLD_OPTIONS.first(), THRESHOLD_OPTIONS.last())) }

    /** Days to keep photos; [KEEP_FOREVER] keeps them until deleted by hand. */
    var retentionDays: Int
        get() = prefs.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS).takeIf { it in RETENTION_OPTIONS }
            ?: DEFAULT_RETENTION_DAYS
        set(value) = prefs.edit { putInt(KEY_RETENTION_DAYS, value) }

    companion object {
        private const val PREFS_NAME = "app_lock_settings"
        private const val KEY_ENABLED = "intruder_selfie_enabled"
        private const val KEY_THRESHOLD = "intruder_selfie_threshold"
        private const val KEY_RETENTION_DAYS = "intruder_selfie_retention_days"

        const val KEEP_FOREVER = 0
        const val DEFAULT_THRESHOLD = 2
        const val DEFAULT_RETENTION_DAYS = 30
        val THRESHOLD_OPTIONS = listOf(1, 2, 3)
        val RETENTION_OPTIONS = listOf(7, 30, KEEP_FOREVER)
    }
}
