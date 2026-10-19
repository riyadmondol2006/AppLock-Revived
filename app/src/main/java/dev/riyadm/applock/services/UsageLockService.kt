package dev.riyadm.applock.services

import android.app.Notification
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dev.riyadm.applock.R
import dev.riyadm.applock.core.trusted.TrustedNetworkMonitor
import dev.riyadm.applock.core.utils.LogUtils
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.core.utils.hasUsagePermission
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.AppLockRepository.Companion.shouldStartService
import dev.riyadm.applock.data.repository.BackendImplementation
import dev.riyadm.applock.features.lockscreen.ui.PasswordOverlayActivity

/**
 * Usage Stats backend: polls the most recent foreground activity. Polling runs on its own
 * thread and only while the screen is on, protection is enabled and at least one app is locked
 * (see [PollingController]); screen on, keyguard dismissal and setting changes resume it at once.
 */
class UsageLockService : Service() {
    private val TAG = "UsageLockService"
    private val NOTIFICATION_ID = 113
    private val CHANNEL_ID = "UsageLockServiceChannel"

    companion object {
        @Volatile
        var isServiceRunning = false

        private const val POLL_INTERVAL_MS = 250L
        private const val QUERY_WINDOW_MS = 3_000L
    }

    private val appLockRepository: AppLockRepository by lazy { applicationContext.appLockRepository() }
    private val config: LockConfig by lazy { LockConfig.get(this) }
    private val usageStatsManager: UsageStatsManager by lazy { getSystemService()!! }
    private val powerManager: PowerManager? by lazy { getSystemService() }
    private val mainHandler = Handler(Looper.getMainLooper())

    private var pollThread: HandlerThread? = null

    @Volatile
    private var pollHandler: Handler? = null

    @Volatile
    private var polling: PollingController? = null
    private val tick = Runnable { runTick() }

    // Poll thread only.
    private var previousForegroundPackage = ""
    private val usageEvent = UsageEvents.Event()

    @Volatile
    private var keyboardPackages: Set<String> = emptySet()
    private var receiverRegistered = false
    private var imeObserver: ContentObserver? = null

    /** Delivered on the poll thread. */
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    LogUtils.d(TAG, "Screen off detected in Usage Stats backend. Resetting AppLock state.")
                    AppLockManager.onScreenOff()
                    previousForegroundPackage = ""
                    refreshPolling()
                }

                Intent.ACTION_SCREEN_ON -> refreshPolling()

                Intent.ACTION_USER_PRESENT -> {
                    refreshPolling()
                    polling?.tickNow()
                }
            }
        }
    }

    /** Runs on the main thread whenever protection, the backend or the locked apps change. */
    private val configListener: () -> Unit = {
        pollHandler?.post { refreshPolling() }
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
        NewAppAutoLocker.register(this, this)
        TrustedNetworkMonitor.register(this, this)

        if (pollThread == null) startMonitoring()

        return START_STICKY
    }

    private fun startMonitoring() {
        AppLockManager.isLockScreenShown.set(false)
        val thread = HandlerThread("AppLockUsageStatsMonitor").apply { start() }
        val handler = Handler(thread.looper)
        pollThread = thread
        pollHandler = handler
        polling = PollingController(POLL_INTERVAL_MS, HandlerPollScheduler(handler, tick))

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            filter,
            null,
            handler,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
        registerImeObserver(handler)
        config.addListener(configListener)

        handler.post {
            refreshKeyboardPackages()
            refreshPolling()
        }
    }

    private fun stopSelfCompletely() {
        isServiceRunning = false
        ServiceForeground.stop(this)
        stopSelf()
    }

    override fun onDestroy() {
        isServiceRunning = false
        config.removeListener(configListener)
        LogUtils.d(TAG, "Service destroyed")

        if (receiverRegistered) {
            try {
                unregisterReceiver(screenStateReceiver)
            } catch (_: IllegalArgumentException) {
            }
            receiverRegistered = false
        }
        imeObserver?.let(contentResolver::unregisterContentObserver)
        imeObserver = null

        pollHandler?.let { handler ->
            handler.removeCallbacks(tick)
            handler.post { polling?.shutdown() }
        }
        pollThread?.quitSafely()
        pollThread = null
        pollHandler = null

        NewAppAutoLocker.unregister(this, this)
        TrustedNetworkMonitor.unregister(this)

        AppLockManager.isLockScreenShown.set(false)
        ServiceForeground.stop(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Poll thread: starts or pauses polling to match the screen and the settings. */
    private fun refreshPolling() {
        val controller = polling ?: return
        if (config.backend != BackendImplementation.USAGE_STATS) {
            stopForBackendSwitch()
            return
        }
        val transition = controller.update(
            interactive = powerManager?.isInteractive ?: true,
            protectEnabled = config.protectEnabled,
            hasLockedApps = config.hasLockedApps()
        )
        if (transition == PollingController.Transition.PAUSED) {
            // Whatever was in front is stale by the time polling resumes.
            previousForegroundPackage = ""
        }
    }

    private fun stopForBackendSwitch() {
        // The user switched backends; don't run two lockers at once.
        polling?.shutdown()
        mainHandler.post { stopSelfCompletely() }
    }

    private fun runTick() {
        try {
            monitorForegroundApp()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in Usage Stats monitoring task", e)
        } finally {
            polling?.onTickFinished()
        }
    }

    private fun monitorForegroundApp() {
        if (config.backend != BackendImplementation.USAGE_STATS) {
            stopForBackendSwitch()
            return
        }

        if (!config.protectEnabled) return
        if (applicationContext.isDeviceLocked()) {
            AppLockManager.appUnlockTimes.clear()
            AppLockManager.clearTemporarilyUnlockedApp()
            previousForegroundPackage = ""
            return
        }

        val currentPackage = getCurrentForegroundPackage() ?: return
        val triggeringPackage = previousForegroundPackage
        previousForegroundPackage = currentPackage

        if (isExclusionApp(currentPackage)) return

        if (currentPackage != triggeringPackage) {
            val triggerExcluded = appLockRepository.getTriggerExcludedApps()
            if (triggeringPackage in triggerExcluded) {
                if (config.isLocked(currentPackage)) {
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
    }

    private fun isExclusionApp(packageName: String): Boolean {
        return packageName == this.packageName ||
                packageName in keyboardPackages ||
                packageName in AppLockConstants.EXCLUDED_APPS
    }

    private fun registerImeObserver(handler: Handler) {
        if (imeObserver != null) return
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) = refreshKeyboardPackages()
        }
        try {
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS), false, observer
            )
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, observer
            )
            imeObserver = observer
        } catch (e: Exception) {
            Log.e(TAG, "Error registering IME observer", e)
        }
    }

    private fun refreshKeyboardPackages() {
        keyboardPackages = try {
            getSystemService<InputMethodManager>()?.enabledInputMethodList
                ?.mapTo(HashSet()) { it.packageName }
                ?: emptySet()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting keyboard package names", e)
            emptySet()
        }
    }

    /** Returns the foreground package, or null if there is nothing new to act on. */
    private fun getCurrentForegroundPackage(): String? {
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - QUERY_WINDOW_MS, time)
        val event = usageEvent
        val lockedApps = config.lockedApps()
        var recentPackage: String? = null
        var recentTime = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            val type = event.eventType
            if (type != UsageEvents.Event.ACTIVITY_RESUMED && type != UsageEvents.Event.USER_INTERACTION) continue

            if (event.packageName == packageName || event.className in AppLockConstants.KNOWN_RECENTS_CLASSES) {
                recentPackage = null
                continue
            }

            if (event.className == AppLockConstants.LAUNCHER_ACTIVITY_CLASS && event.timeStamp != recentTime) {
                recentPackage = null
                AppLockManager.clearTemporarilyUnlockedApp()
                continue
            }

            if (recentTime == event.timeStamp && recentPackage != null && recentPackage in lockedApps) {
                continue
            }

            recentTime = event.timeStamp
            recentPackage = event.packageName
        }
        return recentPackage
    }

    private fun checkAndLockApp(packageName: String, triggeringPackage: String, currentTime: Long) {
        if (!config.isLocked(packageName)) return
        if (TrustedNetworkMonitor.isTrusted() && !appLockRepository.policyFor(packageName).requireBoth) return

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
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
