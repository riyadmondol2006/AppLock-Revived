package dev.riyadm.applock.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.riyadm.applock.MainActivity
import dev.riyadm.applock.R
import dev.riyadm.applock.core.utils.appLockRepository
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Locks newly installed launcher apps when "auto-lock new apps" is on.
 *
 * ACTION_PACKAGE_ADDED is not delivered to manifest receivers on API 26+, so the running lock
 * services register this helper dynamically. Several services may run at once (e.g. the
 * accessibility service for anti-uninstall plus a polling backend), so a single receiver is
 * shared and kept alive while at least one owner is registered.
 */
object NewAppAutoLocker {
    private const val TAG = "NewAppAutoLocker"
    private const val CHANNEL_ID = "auto_lock_new_apps"

    private val owners: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())
    private var receiver: BroadcastReceiver? = null

    @Synchronized
    fun register(context: Context, owner: Any) {
        owners.add(owner)
        if (receiver != null) return

        val appContext = context.applicationContext
        val packageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                try {
                    handlePackageAdded(appContext, intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to auto-lock new app", e)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
        try {
            ContextCompat.registerReceiver(
                appContext,
                packageReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiver = packageReceiver
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register package receiver", e)
        }
    }

    @Synchronized
    fun unregister(context: Context, owner: Any) {
        owners.remove(owner)
        if (owners.isNotEmpty()) return
        receiver?.let {
            try {
                context.applicationContext.unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        receiver = null
    }

    private fun handlePackageAdded(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return

        val packageName = intent.data?.schemeSpecificPart ?: return
        if (packageName == context.packageName) return

        val repository = context.appLockRepository()
        if (!repository.isAutoLockNewAppsEnabled()) return
        if (repository.isAppLocked(packageName)) return

        val pm = context.packageManager
        if (pm.getLaunchIntentForPackage(packageName) == null) return

        repository.addLockedApp(packageName)
        Log.d(TAG, "Auto-locked newly installed app $packageName")

        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
        postNotification(context, packageName, label)
    }

    private fun postNotification(context: Context, packageName: String, label: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.auto_lock_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.auto_lock_channel_description)
        }
        notificationManager.createNotificationChannel(channel)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.auto_lock_notification_title, label))
            .setContentText(context.getString(R.string.auto_lock_notification_text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        try {
            notificationManager.notify(packageName.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission revoked", e)
        }
    }
}
