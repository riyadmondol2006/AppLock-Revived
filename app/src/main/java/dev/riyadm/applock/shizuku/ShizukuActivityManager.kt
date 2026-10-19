package dev.riyadm.applock.shizuku

import android.app.ActivityManager
import android.app.IActivityTaskManager
import android.app.TaskInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Display
import android.view.IWindowManager
import dev.riyadm.applock.core.broadcast.DeviceUnlockReceiver
import dev.riyadm.applock.core.utils.LogUtils
import dev.riyadm.applock.data.repository.BackendImplementation
import androidx.core.content.ContextCompat
import dev.riyadm.applock.services.HandlerPollScheduler
import dev.riyadm.applock.services.LockConfig
import dev.riyadm.applock.services.PollingController
import dev.riyadm.applock.services.isDeviceLocked
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

class ShizukuActivityManager(
    private val context: Context,
    private val onScreenOff: () -> Unit = {},
    /** Called when polling pauses (screen off, protection off, nothing locked). */
    private val onPaused: () -> Unit = {},
    /**
     * Called every tick with the top (focused) activity and the packages of every visible task,
     * top first. Not called while our own lock screen is on top.
     */
    private val onVisibleTasks: (top: ComponentName, visiblePackages: List<String>, timeMillis: Long) -> Unit
) {
    private val TAG = "ShizukuActivityManager"
    private val config = LockConfig.get(context)
    private val powerManager: PowerManager? = context.getSystemService(PowerManager::class.java)
    private var deviceUnlockReceiver: DeviceUnlockReceiver? = null
    private var started = false

    private val handler = Handler(Looper.getMainLooper())
    private val checkForegroundRunnable: Runnable = Runnable {
        try {
            checkForegroundApp()
        } catch (e: Exception) {
            LogUtils.e(TAG, "Unhandled exception in foreground monitor", e)
        } finally {
            polling.onTickFinished()
        }
    }
    private val polling: PollingController =
        PollingController(POLL_INTERVAL_MS, HandlerPollScheduler(handler, checkForegroundRunnable))
    private val configListener: () -> Unit = { refreshPolling() }

    fun start(): Boolean {
        if (started) return true
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Shizuku is not available")
            return false
        }

        return try {
            registerEventReceivers()
            started = true
            config.addListener(configListener)
            refreshPolling()
            Log.d(TAG, "Foreground app monitoring started")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start monitoring", e)
            false
        }
    }

    private fun registerEventReceivers() {
        val unlockFilter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        val receiver = DeviceUnlockReceiver(
            onDeviceUnlocked = {
                refreshPolling()
                polling.tickNow()
            },
            onScreenOff = {
                onScreenOff()
                refreshPolling()
            },
            onScreenOn = ::refreshPolling
        )
        ContextCompat.registerReceiver(
            context,
            receiver,
            unlockFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        deviceUnlockReceiver = receiver
    }

    /** Main thread: polls only while the screen is on, protection is enabled and an app is locked. */
    private fun refreshPolling() {
        val transition = if (started) {
            polling.update(
                interactive = powerManager?.isInteractive ?: true,
                protectEnabled = config.protectEnabled && config.backend == BackendImplementation.SHIZUKU,
                hasLockedApps = config.hasLockedApps()
            )
        } else {
            polling.update(interactive = false, protectEnabled = false, hasLockedApps = false)
        }
        if (transition == PollingController.Transition.PAUSED) onPaused()
    }

    val windowManager: IWindowManager
        get() = SystemServiceHelper.getSystemService("window")
            .let(::ShizukuBinderWrapper)
            .let(IWindowManager.Stub::asInterface)

    private fun checkForegroundApp() {
        if (!config.protectEnabled) return
        if (config.backend != BackendImplementation.SHIZUKU) return
        if (context.isDeviceLocked()) return

        // No Shizuku.pingBinder() per tick: with a dead binder getTasks fails and returns nothing,
        // and the service's binder-dead listener stops monitoring.
        // Only the first visible task is the focused one. Iterating every visible task as if it
        // were the foreground made the tracked app alternate in split-screen / PiP / freeform.
        var top: ComponentName? = null
        val packages = ArrayList<String>(4)
        for (task in getTasksWrapper()) {
            if (!task.isRunning || !task.isVisible) continue
            val activity = task.topActivity ?: continue
            if (top == null) top = activity
            if (activity.packageName !in packages) packages.add(activity.packageName)
        }
        val focused = top ?: return
        if (focused.packageName == context.packageName) return

        onVisibleTasks(focused, packages, System.currentTimeMillis())
    }

    fun stop() {
        started = false
        config.removeListener(configListener)
        refreshPolling()
        handler.removeCallbacks(checkForegroundRunnable)

        deviceUnlockReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering device unlock receiver", e)
            }
            deviceUnlockReceiver = null
        }
        Log.d(TAG, "ShizukuActivityManager stopped")
    }

    private companion object {
        const val POLL_INTERVAL_MS = 500L
    }
}

val topActivity: ComponentName?
    get() = getTasksWrapper().firstOrNull()?.topActivity

private val activityTaskManager: IActivityTaskManager by lazy {
    SystemServiceHelper.getSystemService("activity_task")
        .let(::ShizukuBinderWrapper)
        .let(IActivityTaskManager.Stub::asInterface)
}

private fun getTasksWrapper(): List<ActivityManager.RunningTaskInfo> = when {
    Build.VERSION.SDK_INT < 31 -> runCatching { activityTaskManager.getTasks(8) }.getOrNull()
        .orEmpty()

    else -> runCatching { activityTaskManager.getTasks(8, false, false, Display.INVALID_DISPLAY) }
        .getOrNull()
        .orEmpty()
}
