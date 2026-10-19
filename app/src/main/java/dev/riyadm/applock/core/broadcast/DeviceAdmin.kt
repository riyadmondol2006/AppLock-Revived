package dev.riyadm.applock.core.broadcast

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit

class DeviceAdmin : DeviceAdminReceiver() {
    companion object {
        private const val TAG = "DeviceAdmin"
        private const val PREFS_NAME = "dev.riyadm.applock.admin_prefs"
        private const val KEY_PASSWORD_VERIFIED = "password_verified"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        context.getSharedPreferences("app_lock_settings", Context.MODE_PRIVATE).edit(commit = true) {
            putBoolean("anti_uninstall", true)
        }

        // setUninstallBlocked() is only allowed for device/profile owners; a regular device admin
        // gets a SecurityException, which used to crash the process here.
        try {
            val dpm = getManager(context)
            val pkg = context.packageName
            if (dpm.isDeviceOwnerApp(pkg) || dpm.isProfileOwnerApp(pkg)) {
                dpm.setUninstallBlocked(ComponentName(context, DeviceAdmin::class.java), pkg, true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not block uninstall", e)
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        context.getSharedPreferences("app_lock_settings", Context.MODE_PRIVATE).edit(commit = true) {
            putBoolean("anti_uninstall", false)
        }
    }

    fun setPasswordVerified(context: Context, verified: Boolean) {
        getSharedPreferences(context).edit { putBoolean(KEY_PASSWORD_VERIFIED, verified) }
    }

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
