package dev.riyadm.applock.features.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import dev.riyadm.applock.R

/**
 * The redacted stand-in App Lock Revived shows for a locked app's notification: "Notification
 * hidden - <app>". Tapping it opens the app, which brings up the normal lock screen.
 */
internal object HiddenNotificationPlaceholder {
    private const val TAG = "HiddenNotification"
    private const val CHANNEL_ID = "hidden_notifications"
    private const val NOTIFICATION_ID = 0x4e1

    /** Posts or updates the placeholder of [packageName]. Returns false if it could not be shown. */
    fun post(context: Context, packageName: String, whenMs: Long): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        return try {
            ensureChannel(context)
            val label = labelOf(context, packageName)

            // What the lock screen shows: no app name either.
            val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.hide_notifications_placeholder_title_public))
                .build()

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.hide_notifications_placeholder_title, label))
                .setContentText(context.getString(R.string.hide_notifications_placeholder_text))
                .setWhen(whenMs)
                .setShowWhen(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setGroup(GROUP_KEY)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setAutoCancel(true)
            iconOf(context, packageName)?.let(builder::setLargeIcon)
            launchIntentOf(context, packageName)?.let(builder::setContentIntent)

            manager.notify(HideNotificationsPolicy.placeholderTag(packageName), NOTIFICATION_ID, builder.build())
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not post the placeholder for $packageName", e)
            false
        }
    }

    /** Packages that currently have a placeholder on screen. */
    fun shownPackages(context: Context): List<String> = try {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications
            .filter { it.id == NOTIFICATION_ID }
            .mapNotNull { HideNotificationsPolicy.packageOfPlaceholderTag(it.tag) }
    } catch (e: Exception) {
        Log.w(TAG, "Could not list the placeholders", e)
        emptyList()
    }

    fun cancel(context: Context, packageNames: Collection<String>) {
        val manager = NotificationManagerCompat.from(context)
        packageNames.forEach { manager.cancel(HideNotificationsPolicy.placeholderTag(it), NOTIFICATION_ID) }
    }

    fun cancelAll(context: Context) = cancel(context, shownPackages(context))

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.hide_notifications_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.hide_notifications_channel_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
    }

    private fun labelOf(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: Exception) {
        packageName
    }

    private fun iconOf(context: Context, packageName: String) = try {
        context.packageManager.getApplicationIcon(packageName).toBitmap(ICON_SIZE_PX, ICON_SIZE_PX)
    } catch (_: Exception) {
        null
    }

    private fun launchIntentOf(context: Context, packageName: String): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            packageName.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private const val GROUP_KEY = "hidden_notifications"
    private const val ICON_SIZE_PX = 128
}
