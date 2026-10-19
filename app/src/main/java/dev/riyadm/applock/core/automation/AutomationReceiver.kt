package dev.riyadm.applock.core.automation

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PermissionInfo
import android.os.Build
import android.util.Log
import dev.riyadm.applock.core.utils.LogUtils
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.core.automation.AutomationSettings.Outcome
import dev.riyadm.applock.services.AppLockManager

/**
 * Entry point for automation apps (Tasker, MacroDroid, ...).
 *
 * Security model:
 * - The manifest only delivers broadcasts from apps holding [PERMISSION], a "dangerous"
 *   permission the user has to grant to each automation app explicitly.
 * - The component is disabled unless "Allow automation apps" is on, and every command is
 *   checked against that setting again here.
 * - Every command is refused while an app targeting Android 5.1 or older holds the
 *   permission, because such apps can receive it without being asked (see [AutomationHolders]).
 * - Turning protection off needs a second, separate opt-in. Locking can always be requested
 *   because it only makes things stricter.
 * - Extras and data are ignored; only the action matters.
 */
class AutomationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == null || action !in ACTIONS) return
        val app = context.applicationContext
        val settings = AutomationSettings(app)
        val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) sentFromPackage else null

        val outcome = try {
            when {
                !isPermissionDefinedByUs(app) -> Outcome.IGNORED_INVALID
                !settings.isEnabled() -> Outcome.IGNORED_DISABLED
                // A legacy app may hold the permission without the user's consent.
                AutomationHolders.blocksCommands(AutomationHolders.find(app)) -> Outcome.IGNORED_LEGACY_HOLDER
                else -> execute(app, settings, action)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Automation command failed", e)
            Outcome.IGNORED_INVALID
        }

        settings.recordCommand(action, outcome, sender)
        LogUtils.d(TAG, "Automation command ${action.substringAfterLast('.')} from ${sender ?: "unknown"}: $outcome")
        if (isOrderedBroadcast) {
            resultCode = if (outcome == Outcome.APPLIED) Activity.RESULT_OK else Activity.RESULT_CANCELED
        }
    }

    private fun execute(context: Context, settings: AutomationSettings, action: String): Outcome {
        val repository = context.appLockRepository()
        return when (action) {
            ACTION_LOCK_ALL_NOW -> {
                AppLockManager.lockAllNow(closeApps = true)
                Outcome.APPLIED
            }

            ACTION_ENABLE_PROTECTION -> {
                repository.setProtectEnabled(true)
                AppLockManager.lockAllNow()
                Outcome.APPLIED
            }

            ACTION_DISABLE_PROTECTION -> {
                if (!settings.isDisableAllowed()) return Outcome.IGNORED_NOT_ALLOWED
                repository.setProtectEnabled(false)
                Outcome.APPLIED
            }

            else -> Outcome.IGNORED_INVALID
        }
    }

    /**
     * Defence in depth: the permission must be the one this app declares, at "dangerous" level.
     * If it were somehow owned by another package, it could be granted without the user knowing.
     */
    @Suppress("DEPRECATION")
    private fun isPermissionDefinedByUs(context: Context): Boolean = try {
        val info = context.packageManager.getPermissionInfo(PERMISSION, 0)
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.protection
        } else {
            info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
        }
        info.packageName == context.packageName && base == PermissionInfo.PROTECTION_DANGEROUS
    } catch (_: Exception) {
        false
    }

    companion object {
        private const val TAG = "AutomationReceiver"

        const val PERMISSION = "dev.riyadm.applock.permission.AUTOMATION"
        const val ACTION_LOCK_ALL_NOW = "dev.riyadm.applock.action.LOCK_ALL_NOW"
        const val ACTION_ENABLE_PROTECTION = "dev.riyadm.applock.action.ENABLE_PROTECTION"
        const val ACTION_DISABLE_PROTECTION = "dev.riyadm.applock.action.DISABLE_PROTECTION"

        val ACTIONS = listOf(ACTION_LOCK_ALL_NOW, ACTION_ENABLE_PROTECTION, ACTION_DISABLE_PROTECTION)
    }
}
