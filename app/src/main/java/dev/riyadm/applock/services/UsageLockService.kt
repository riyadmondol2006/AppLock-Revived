package dev.riyadm.applock.services

import android.app.Notification
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dev.riyadm.applock.R
import dev.riyadm.applock.core.utils.LogUtils
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.core.utils.hasUsagePermission
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.AppLockRepository.Companion.shouldStartService
import dev.riyadm.applock.data.repository.BackendImplementation
import dev.riyadm.applock.features.lockscreen.ui.PasswordOverlayActivity
import java.util.Timer
import kotlin.concurrent.timerTask

class UsageLockService : Service() {
    private val TAG = "UsageLockService"
    private val NOTIFICATION_ID = 113
    private val CHANNEL_ID = "UsageLockServiceChannel"

    companion object {
        @Volatile
        var isServiceRunning = false

        private const val POLL_INTERVAL_MS = 250L
        private const val KEYBOARD_CACHE_MS = 10_000L
    }

    private val appLockRepository: AppLockRepository by lazy { applicationContext.appLockRepository() }
    private val usageStatsManager: UsageStatsManager by lazy { getSystemService()!! }
    private val mainHandler = Handler(Looper.getMainLooper())

    private var timer: Timer? = null

    @Volatile
    private var previousForegroundPackage = ""
    private var receiverRegistered = false

    @Volatile
    private var keyboardPackages: Set<String> = emptySet()
    private var keyboardPackagesLoadedAt = 0L

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                LogUtils.d(TAG, "Screen off detected in Usage Stats backend. Resetting AppLock state.")
                AppLockManager.onScreenOff()
                previousForegroundPackage = ""
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always promote to the foreground first: we may have been started with
        // startForegroundService(), and stopping before startForeground() crashes the process.
        val inForeground = startForegroundService()

        if (!inForeground ||
            !shouldStartService(appLockRepository, this::class.java) ||
            !hasUsagePermission()
        ) {
            Log.e(TAG, "Permissions missing, service not needed or not allowed in foreground. Stopping.")
            stopSelfCompletely()
            return START_NOT_STICKY
        }

        isServiceRunning = true
        appLockRepository.setActiveBackend(BackendImplementation.USAGE_STATS)
        AppLockManager.stopAllOtherServices(this, this::class.java)

        if (!receiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            ContextCompat.registerReceiver(
                this,
                screenStateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
            AppLockManager.isLockScreenShown.set(false)
        }
        NewAppAutoLocker.register(this, this)

        if (timer == null) startMonitoringTimer()

        return START_STICKY
    }

    private fun stopSelfCompletely() {
        isServiceRunning = false
        ServiceForeground.stop(this)
        stopSelf()
    }

    override fun onDestroy() {
        isServiceRunning = false
        timer?.cancel()
        timer = null
        LogUtils.d(TAG, "Service destroyed")

        if (receiverRegistered) {
            try {
                unregisterReceiver(screenStateReceiver)
            } catch (_: IllegalArgumentException) {
            }
            receiverRegistered = false
        }
        NewAppAutoLocker.unregister(this, this)

        AppLockManager.isLockScreenShown.set(false)
        ServiceForeground.stop(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoringTimer() {
        timer?.cancel()
        timer = Timer("AppLockUsageStatsMonitor", true).apply {
            schedule(timerTask { safeMonitorForegroundApp() }, 0, POLL_INTERVAL_MS)
        }
    }

    private fun safeMonitorForegroundApp() {
        try {
            if (appLockRepository.getBackendImplementation() != BackendImplementation.USAGE_STATS) {
                // The user switched backends; don't run two lockers at once.
                mainHandler.post { stopSelfCompletely() }
                timer?.cancel()
                timer = null
                return
            }

            if (!appLockRepository.isProtectEnabled()) return
            if (applicationContext.isDeviceLocked()) {
                AppLockManager.appUnlockTimes.clear()
                AppLockManager.clearTemporarilyUnlockedApp()
                previousForegroundPackage = ""
                return
            }

            val foregroundApp = getCurrentForegroundAppPackage() ?: return
            val currentPackage = foregroundApp.first
            val triggeringPackage = previousForegroundPackage
            previousForegroundPackage = currentPackage

            if (isExclusionApp(currentPackage)) return

            val triggerExcluded = appLockRepository.getTriggerExcludedApps()
            if (currentPackage != triggeringPackage) {
                if (triggeringPackage in triggerExcluded) {
                    if (appLockRepository.isAppLocked(currentPackage)) {
                        AppLockManager.markUnlocked(currentPackage)
                    }
                    return
                }
                // Leaving an unlocked app ends its session (unlock duration still applies).
                if (currentPackage !in triggerExcluded) {
                    (AppLockManager.unlockedAppsSnapshot() - currentPackage)
                        .forEach(AppLockManager::revokeTemporaryUnlock)
                }
            }

            if (AppLockManager.isAppTemporarilyUnlocked(currentPackage)) return

            checkAndLockApp(currentPackage, triggeringPackage, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in Usage Stats monitoring task", e)
        }
    }

    private fun isExclusionApp(packageName: String): Boolean {
        return packageName == this.packageName ||
                packageName in currentKeyboardPackages() ||
                packageName in AppLockConstants.EXCLUDED_APPS
    }

    private fun currentKeyboardPackages(): Set<String> {
        val now = SystemClock.uptimeMillis()
        if (now - keyboardPackagesLoadedAt > KEYBOARD_CACHE_MS || keyboardPackagesLoadedAt == 0L) {
            keyboardPackages = getSystemService<InputMethodManager>()
                ?.enabledInputMethodList
                ?.map { it.packageName }
                ?.toSet()
                ?: emptySet()
            keyboardPackagesLoadedAt = now
        }
        return keyboardPackages
    }

    /**
     * Returns the foreground package name and class name, or null if filtered.
     */
    private fun getCurrentForegroundAppPackage(): Pair<String, String>? {
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - 3000, time)
        val event = UsageEvents.Event()
        var recentApp: Pair<String, String>? = null
        var recentAppTime = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED && event.eventType != UsageEvents.Event.USER_INTERACTION) continue

            if (event.packageName == packageName || event.className in AppLockConstants.KNOWN_RECENTS_CLASSES) {
                recentApp = null
                continue
            }

            if (event.className == AppLockConstants.LAUNCHER_ACTIVITY_CLASS && event.timeStamp != recentAppTime) {
                recentApp = null
                AppLockManager.clearTemporarilyUnlockedApp()
                continue
            }

            if (recentAppTime == event.timeStamp && recentApp?.first != null && appLockRepository.isAppLocked(
                    recentApp!!.first
                )
            ) {
                continue
            }

            recentAppTime = event.timeStamp
            recentApp = Pair(event.packageName, event.className)
        }
        return recentApp
    }

    private fun checkAndLockApp(packageName: String, triggeringPackage: String, currentTime: Long) {
        if (packageName !in appLockRepository.getLockedApps()) return

        if (AppLockManager.isWithinUnlockDuration(
                packageName,
                appLockRepository.getUnlockTimeDuration(),
                currentTime
            )
        ) {
            return
        }

        if (AppLockManager.isBiometricAuthInProgress()) return
        if (AppLockManager.isLockScreenShown.get() && !AppLockManager.resetLockScreenFlagIfStuck()) {
            return
        }

        LogUtils.d(TAG, "Locked app: $packageName. Showing overlay.")
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

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting overlay for: $packageName", e)
            AppLockManager.isLockScreenShown.set(false)
        }
    }

    private fun startForegroundService(): Boolean {
        ServiceForeground.ensureChannel(this, CHANNEL_ID, getString(R.string.service_channel_name_usage))
        return ServiceForeground.start(this, NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.baseline_shield_24)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
