package dev.riyadm.applock.core.automation

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.edit

/**
 * Settings for the automation interface, kept in the shared "app_lock_settings" file. They are
 * not in the backup allow-list, so importing a backup can never open the interface.
 */
class AutomationSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class LastCommand(val action: String, val outcome: Outcome, val timestamp: Long, val sender: String?)

    enum class Outcome { APPLIED, IGNORED_DISABLED, IGNORED_NOT_ALLOWED, IGNORED_INVALID }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    /** Also enables or disables the receiver component so it can't be reached while off. */
    fun setEnabled(enabled: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(KEY_ENABLED, enabled)
            if (!enabled) putBoolean(KEY_ALLOW_DISABLE, false)
        }
        syncReceiverState()
    }

    fun isDisableAllowed(): Boolean = isEnabled() && prefs.getBoolean(KEY_ALLOW_DISABLE, false)

    fun setDisableAllowed(allowed: Boolean) =
        prefs.edit(commit = true) { putBoolean(KEY_ALLOW_DISABLE, allowed && isEnabled()) }

    fun getLastCommand(): LastCommand? {
        val action = prefs.getString(KEY_LAST_ACTION, null) ?: return null
        val outcome = prefs.getString(KEY_LAST_OUTCOME, null)
            ?.let { name -> Outcome.entries.firstOrNull { it.name == name } } ?: return null
        return LastCommand(
            action = action,
            outcome = outcome,
            timestamp = prefs.getLong(KEY_LAST_TIME, 0L),
            sender = prefs.getString(KEY_LAST_SENDER, null)
        )
    }

    fun recordCommand(action: String, outcome: Outcome, sender: String?) {
        prefs.edit {
            putString(KEY_LAST_ACTION, action)
            putString(KEY_LAST_OUTCOME, outcome.name)
            putLong(KEY_LAST_TIME, System.currentTimeMillis())
            putString(KEY_LAST_SENDER, sender)
        }
    }

    fun syncReceiverState() {
        val state = if (isEnabled()) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        try {
            appContext.packageManager.setComponentEnabledSetting(
                ComponentName(appContext, AutomationReceiver::class.java),
                state,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update automation receiver state", e)
        }
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        private const val TAG = "AutomationSettings"
        private const val PREFS_NAME = "app_lock_settings"
        private const val KEY_ENABLED = "automation_enabled"
        private const val KEY_ALLOW_DISABLE = "automation_allow_disable"
        private const val KEY_LAST_ACTION = "automation_last_action"
        private const val KEY_LAST_OUTCOME = "automation_last_outcome"
        private const val KEY_LAST_TIME = "automation_last_time"
        private const val KEY_LAST_SENDER = "automation_last_sender"
        val LAST_COMMAND_KEYS = setOf(KEY_LAST_ACTION, KEY_LAST_OUTCOME, KEY_LAST_TIME, KEY_LAST_SENDER)
    }
}
