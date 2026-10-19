package dev.riyadm.applock.services

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dev.riyadm.applock.R
import dev.riyadm.applock.core.trusted.TrustedNetworkMonitor
import dev.riyadm.applock.core.utils.LogUtils
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.AppLockRepository.Companion.shouldStartService
import dev.riyadm.applock.data.repository.BackendImplementation
import dev.riyadm.applock.features.lockscreen.ui.PasswordOverlayActivity
import dev.riyadm.applock.shizuku.ShizukuActivityManager
import rikka.shizuku.Shizuku

/**
 * Shizuku backend. Shizuku (non-root) has to be restarted by the user after every reboot and can
 * die at any time, so instead of giving up when it isn't ready this service stays in the
 * foreground and resumes monitoring as soon as the Shizuku binder (re)appears.
 */
class ShizukuAppLockService : Service() {
    private val appLockRepository: AppLockRepository by lazy { applicationContext.appLockRepository() }
    private var shizukuActivityManager: ShizukuActivityManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var previousForegroundPackage = ""
    private var listenersRegistered = false
    private var waitingForShizuku = false

    companion object {
        private const val TAG = "ShizukuAppLockService"
        private const val NOTIFICATION_ID = 112
        private const val CHANNEL_ID = "ShizukuAppLockServiceChannel"

        @Volatile
        var isServiceRunning = false
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        mainHandler.post { tryStartMonitoring() }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        mainHandler.post {
            LogUtils.d(TAG, "Shizuku binder died; waiting for it to come back")
            stopMonitoring()
            setWaiting(true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppLockManager.isLockScreenShown.set(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote first: we may have been started with startForegroundService().
        val inForeground = startForegroundService(waiting = !isShizukuAvailable())

        if (!inForeground || !shouldStartService(appLockRepository, this::class.java)) {
            Log.e(TAG, "Service not needed or not allowed in foreground. Stopping service.")
            stopSelfCompletely()
            return START_NOT_STICKY
        }

        if (Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Shizuku permission not granted. Stopping service.")
            stopSelfCompletely()
            return START_NOT_STICKY
        }

        isServiceRunning = true
        appLockRepository.setActiveBackend(BackendImplementation.SHIZUKU)
        AppLockManager.stopAllOtherServices(this, this::class.java)
        NewAppAutoLocker.register(this, this)
        TrustedNetworkMonitor.register(this, this)

        if (!listenersRegistered) {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener, mainHandler)
            Shizuku.addBinderDeadListener(binderDeadListener, mainHandler)
            listenersRegistered = true
        }
        tryStartMonitoring()

        return START_STICKY
    }

    private fun tryStartMonitoring() {
        if (shizukuActivityManager != null) return
        if (!shouldStartService(appLockRepository, this::class.java)) {
            stopSelfCompletely()
            return
        }
        if (!Shizuku.pingBinder()) {
            setWaiting(true)
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Shizuku permission revoked. Stopping service.")
            stopSelfCompletely()
            return
        }

        val manager = createShizukuActivityManager()
        if (manager.start()) {
            shizukuActivityManager = manager
            setWaiting(false)
        } else {
            Log.e(TAG, "Shizuku monitoring failed to start; waiting for Shizuku")
            setWaiting(true)
        }
    }

    private fun stopMonitoring() {
        shizukuActivityManager?.stop()
        shizukuActivityManager = null
    }

    private fun setWaiting(waiting: Boolean) {
        if (waitingForShizuku == waiting) return
        waitingForShizuku = waiting
        try {
            getSystemService<NotificationManager>()?.notify(NOTIFICATION_ID, createNotification(waiting))
        } catch (_: Exception) {
        }
    }

    private fun stopSelfCompletely() {
        isServiceRunning = false
        stopMonitoring()
        ServiceForeground.stop(this)
        stopSelf()
    }

    override fun onDestroy() {
        LogUtils.d(TAG, "ShizukuAppLockService destroyed.")
        stopMonitoring()
        if (listenersRegistered) {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            listenersRegistered = false
        }
        NewAppAutoLocker.unregister(this, this)
        TrustedNetworkMonitor.unregister(this)
        mainHandler.removeCallbacksAndMessages(null)
        isServiceRunning = false
        ServiceForeground.stop(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun isShizukuAvailable(): Boolean {
        return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    private fun startForegroundService(waiting: Boolean): Boolean {
        ServiceForeground.ensureChannel(this, CHANNEL_ID, getString(R.string.service_channel_name_shizuku))
        waitingForShizuku = waiting
        return ServiceForeground.start(this, NOTIFICATION_ID, createNotification(waiting))
    }

    private fun createNotification(waiting: Boolean): Notification {
        val text = if (waiting) {
            R.string.service_notification_waiting_shizuku
        } else {
            R.string.service_notification_text_shizuku
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(text))
            .setSmallIcon(R.drawable.baseline_shield_24)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createShizukuActivityManager(): ShizukuActivityManager {
        return ShizukuActivityManager(
            context = this,
            appLockRepository = appLockRepository,
            onScreenOff = { previousForegroundPackage = "" },
            onVisibleTasks = ::onVisibleTasks
        )
    }

    private fun onVisibleTasks(top: ComponentName, visiblePackages: List<String>, timeMillis: Long) {
        val packageName = top.packageName
        val previous = previousForegroundPackage
        previousForegroundPackage = packageName

        if (packageName != previous) {
            val triggerExclusions = appLockRepository.getTriggerExcludedApps()
            if (previous in triggerExclusions) {
                LogUtils.d(TAG, "Trigger app $previous is excluded, not locking $packageName")
                if (appLockRepository.isAppLocked(packageName)) {
                    AppLockManager.markUnlocked(packageName)
                }
            } else if (packageName !in triggerExclusions) {
                // Apps that are no longer on screen end their unlocked session.
                AppLockManager.unlockedAppsSnapshot()
                    .filter { it !in visiblePackages }
                    .forEach(AppLockManager::revokeTemporaryUnlock)
            }
        }

        // We only get here when our own lock screen is not on top.
        AppLockManager.resetLockScreenFlagIfStuck()

        for (candidate in visiblePackages) {
            if (candidate == this.packageName || candidate in AppLockConstants.EXCLUDED_APPS) continue
            if (checkAndLockApp(candidate, previous, timeMillis)) break
        }
    }

    private fun checkAndLockApp(packageName: String, triggeringPackage: String, currentTime: Long): Boolean {
        if (packageName !in appLockRepository.getLockedApps()) return false
        if (TrustedNetworkMonitor.isTrusted() && !appLockRepository.policyFor(packageName).requireBoth) return false
        if (AppLockManager.isAppTemporarilyUnlocked(packageName)) return false
        if (AppLockManager.isWithinUnlockDuration(
                packageName,
                appLockRepository.getUnlockTimeDuration(),
                currentTime
            )
        ) {
            return false
        }
        if (AppLockManager.isBiometricAuthInProgress()) return false
        if (AppLockManager.isLockScreenShown.get()) return false

        LogUtils.d(TAG, "Locked app detected: $packageName. Showing overlay.")
        AppLockManager.markLockScreenRequested()

        val intent = Intent(this, PasswordOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_FROM_BACKGROUND or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("locked_package", packageName)
            putExtra("triggering_package", triggeringPackage)
        }

        return try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            AppLockManager.isLockScreenShown.set(false)
            Log.e(TAG, "Failed to start password overlay: ${e.message}", e)
            false
        }
    }
}
