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
import android.util.Log
import android.view.Display
import android.view.IWindowManager
import dev.riyadm.applock.core.broadcast.DeviceUnlockReceiver
import dev.riyadm.applock.core.utils.LogUtils
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.BackendImplementation
import androidx.core.content.ContextCompat
import dev.riyadm.applock.services.isDeviceLocked
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

class ShizukuActivityManager(
    private val context: Context,
    private val appLockRepository: AppLockRepository,
    private val onScreenOff: () -> Unit = {},
    /**
     * Called every tick with the top (focused) activity and the packages of every visible task,
     * top first. Not called while our own lock screen is on top.
     */
    private val onVisibleTasks: (top: ComponentName, visiblePackages: List<String>, timeMillis: Long) -> Unit
) {
    private val TAG = "ShizukuActivityManager"
    private var deviceUnlockReceiver: DeviceUnlockReceiver? = null
    private var started = false

    private val handler = Handler(Looper.getMainLooper())
    private val checkForegroundRunnable = object : Runnable {
        override fun run() {
            try {
                checkForegroundApp()
            } catch (e: Exception) {
                LogUtils.e(TAG, "Unhandled exception in foreground monitor", e)
            } finally {
                if (started) handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    fun start(): Boolean {
        if (started) return true
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Shizuku is not available")
            return false
        }

        return try {
            registerEventReceivers()
            started = true
            handler.removeCallbacks(checkForegroundRunnable)
            handler.post(checkForegroundRunnable)
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
        }
        val receiver = DeviceUnlockReceiver(onDeviceUnlocked = {}, onScreenOff = onScreenOff)
        ContextCompat.registerReceiver(
            context,
            receiver,
            unlockFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        deviceUnlockReceiver = receiver
    }

    val windowManager: IWindowManager
        get() = SystemServiceHelper.getSystemService("window")
            .let(::ShizukuBinderWrapper)
            .let(IWindowManager.Stub::asInterface)

    private fun checkForegroundApp() {
        if (!appLockRepository.isProtectEnabled()) return
        if (appLockRepository.getBackendImplementation() != BackendImplementation.SHIZUKU) return

        if (!Shizuku.pingBinder()) {
            LogUtils.e(TAG, "Shizuku binder lost during foreground monitoring")
            return
        }

        if (context.isDeviceLocked()) return

        // Only the first visible task is the focused one. Iterating every visible task as if it
        // were the foreground made the tracked app alternate in split-screen / PiP / freeform.
        val visible = getTasksWrapper().filterVisible().mapNotNull { it.topActivity }
        val top = visible.firstOrNull() ?: return
        if (top.packageName == context.packageName) return

        val packages = visible.map { it.packageName }.distinct()
        onVisibleTasks(top, packages, System.currentTimeMillis())
    }

    fun stop() {
        started = false
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

private fun List<ActivityManager.RunningTaskInfo>.filterVisible(): List<ActivityManager.RunningTaskInfo> {
    return filter {
        it.isRunning && it.isVisible
    }
}

fun TaskInfo.isFreeform(): Boolean {
    try {
        return HiddenApiBypass.invoke(TaskInfo::class.java, this, "isFreeform") as Boolean
    } catch (e: Throwable) {
        e.printStackTrace()
        return false
    }
}

fun TaskInfo.isFocused(): Boolean {
    try {
        return HiddenApiBypass.getInstanceFields(TaskInfo::class.java)
            .firstOrNull { it.name == "isFocused" }!!
            .getBoolean(this)
    } catch (e: Throwable) {
        e.printStackTrace()
        return false
    }
}
