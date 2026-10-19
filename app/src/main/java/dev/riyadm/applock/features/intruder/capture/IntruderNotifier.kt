package dev.riyadm.applock.features.intruder.capture

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.riyadm.applock.MainActivity
import dev.riyadm.applock.R
import dev.riyadm.applock.features.intruder.data.IntruderPhoto

/** Posts "Someone tried to open <app>" after a photo was saved, if notifications are allowed. */
internal object IntruderNotifier {
    private const val TAG = "IntruderNotifier"
    private const val CHANNEL_ID = "intruder_alerts"
    private const val NOTIFICATION_ID = 0x1d7

    fun notify(context: Context, photo: IntruderPhoto) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.intruder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.intruder_channel_description) }
        )

        val label = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(photo.packageName, 0)).toString()
        } catch (_: Exception) {
            photo.packageName
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Shown on the lock screen instead of the details
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.intruder_notification_public_text))
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.intruder_notification_title, label))
            .setContentText(
                context.resources.getQuantityString(
                    R.plurals.intruder_notification_text,
                    photo.attempts,
                    photo.attempts
                )
            )
            .setWhen(photo.timestamp)
            .setShowWhen(true)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        try {
            manager.notify(photo.packageName, NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission revoked", e)
        }
    }
}
