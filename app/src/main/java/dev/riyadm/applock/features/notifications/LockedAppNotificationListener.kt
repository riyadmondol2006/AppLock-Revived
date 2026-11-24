package dev.riyadm.applock.features.notifications

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.services.AppLockManager

/**
 * Hides the notifications of locked apps. A listener cannot edit another app's notification, so
 * the original is dismissed and, unless "hide completely" is chosen, replaced by a redacted
 * placeholder from this app (see [HiddenNotificationPlaceholder]).
 *
 * The system only binds this service while the user has granted notification access, and it
 * unbinds itself again when the setting is off, so it costs nothing in the default configuration.
 */
class LockedAppNotificationListener : NotificationListenerService() {

    private val recentKeys = RecentNotificationKeys()

    override fun onListenerConnected() {
        val repo = applicationContext.appLockRepository()
        if (!repo.isHideLockedNotificationsEnabled()) {
            // Nothing to do; stop receiving every notification on the device until it is turned on.
            HiddenNotificationPlaceholder.cancelAll(this)
            requestUnbind()
            return
        }
        recentKeys.clear()
        removeStalePlaceholders()
        try {
            activeNotifications?.forEach(::hideIfLocked)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the active notifications", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) hideIfLocked(sbn)
    }

    private fun hideIfLocked(sbn: StatusBarNotification) {
        try {
            val repo = applicationContext.appLockRepository()
            val enabled = repo.isHideLockedNotificationsEnabled()
            val pkg = sbn.packageName ?: return
            // Cheap checks first: this runs for every notification on the device.
            if (!enabled || pkg == packageName) return
            val locked = repo.getLockedApps()
            if (pkg !in locked) return

            val withinWindow = AppLockManager.isWithinUnlockDuration(
                pkg, repo.getUnlockTimeDuration(), System.currentTimeMillis()
            )
            val exempt = HideNotificationsPolicy.isExempt(sbn.notification.flags, sbn.notification.category)
            if (!HideNotificationsPolicy.shouldHide(enabled, packageName, pkg, locked, withinWindow, exempt)) return
            if (!recentKeys.markIfNew(sbn.key, sbn.postTime)) return

            if (!repo.isHideLockedNotificationsMute()) {
                // If the placeholder cannot be shown the original is still removed: privacy first.
                HiddenNotificationPlaceholder.post(applicationContext, pkg, sbn.postTime)
            }
            cancelNotification(sbn.key)
        } catch (e: Exception) {
            Log.w(TAG, "Could not hide a notification", e)
        }
    }

    /** Placeholders of apps that were unlocked for good, or all of them if the feature is off. */
    private fun removeStalePlaceholders() {
        val repo = applicationContext.appLockRepository()
        val stale = HideNotificationsPolicy.stalePlaceholderPackages(
            HiddenNotificationPlaceholder.shownPackages(this),
            repo.getLockedApps(),
            repo.isHideLockedNotificationsEnabled()
        )
        HiddenNotificationPlaceholder.cancel(this, stale)
    }

    companion object {
        private const val TAG = "LockedAppNotifListener"

        /** True while the user has granted this app notification access. */
        fun hasAccess(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        /**
         * Asks the system to bind the listener again after it unbound itself while the feature
         * was off. Does nothing without notification access.
         */
        fun requestRebindIfPossible(context: Context) {
            if (!hasAccess(context)) return
            try {
                NotificationListenerService.requestRebind(ComponentName(context, LockedAppNotificationListener::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Could not request a rebind", e)
            }
        }
        /** Removes the placeholders (originals that were dismissed cannot be restored). */
        fun onDisabled(context: Context) {
            HiddenNotificationPlaceholder.cancelAll(context.applicationContext)
        }
    }
}
