package dev.riyadm.applock.services

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import dev.riyadm.applock.core.broadcast.DeviceAdmin

/**
 * Shared foreground-service plumbing for the polling backends.
 *
 * systemExempted is only allowed when the app can schedule exact alarms (Android 14+ policy);
 * an active device admin alone is not enough, and using it anyway throws a SecurityException
 * that crashes the whole process (taking the accessibility service down with it).
 */
internal object ServiceForeground {
    private const val TAG = "ServiceForeground"

    fun ensureChannel(context: Context, channelId: String, name: CharSequence) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(channelId) != null) return
        val channel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** Returns false when the service could not be promoted to the foreground. */
    fun start(service: Service, notificationId: Int, notification: Notification): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return try {
                service.startForeground(notificationId, notification)
                true
            } catch (e: Exception) {
                Log.e(TAG, "startForeground failed", e)
                false
            }
        }

        val types = buildList {
            if (canUseSystemExempted(service)) add(ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
            add(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }
        for (type in types) {
            try {
                service.startForeground(notificationId, notification, type)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "startForeground with type $type failed", e)
            }
        }
        return false
    }

    fun stop(service: Service) {
        try {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
    }

    private fun canUseSystemExempted(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService<DevicePolicyManager>()
            val adminActive = dpm?.isAdminActive(ComponentName(context, DeviceAdmin::class.java)) == true
            val alarms = context.getSystemService<AlarmManager>()
            adminActive && alarms?.canScheduleExactAlarms() == true
        } catch (_: Exception) {
            false
        }
    }
}
