package dev.riyadm.applock.features.intruder.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import dev.riyadm.applock.data.repository.UnlockLogRepository
import dev.riyadm.applock.features.intruder.data.IntruderPhotoStore
import dev.riyadm.applock.features.intruder.data.IntruderSettings
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decides when to take an intruder photo. Lock screens report every credential attempt through
 * AppLockRepository.recordUnlockAttempt, which forwards it here. This call is cheap and never
 * blocks: the camera work runs on a dedicated background thread.
 *
 * A photo is taken once a streak of consecutive wrong PINs/patterns/passwords for one app reaches
 * the configured threshold, at most once per streak and once per app every 30 seconds. Biometric
 * failures are ignored: the system prompt may itself be using the front camera for face unlock.
 */
object IntruderCapture {
    private const val TAG = "IntruderCapture"
    private const val STREAK_TIMEOUT_MS = 2 * 60_000L
    private const val PACKAGE_COOLDOWN_MS = 30_000L

    private class Streak(var failures: Int, var lastFailureAt: Long, var photoTaken: Boolean)

    private val lock = Any()
    private val streaks = HashMap<String, Streak>()
    private val lastCaptureAt = HashMap<String, Long>()
    private val capturing = AtomicBoolean(false)
    private val executor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "IntruderCapture").apply { isDaemon = true }
        }
    }

    @Volatile
    private var settings: IntruderSettings? = null

    private fun settings(context: Context): IntruderSettings =
        settings ?: IntruderSettings(context).also { settings = it }

    fun onUnlockAttempt(
        context: Context,
        packageName: String,
        success: Boolean,
        method: UnlockLogRepository.Method
    ) {
        try {
            if (success) {
                synchronized(lock) { streaks.clear() }
                return
            }
            if (method == UnlockLogRepository.Method.BIOMETRIC) return
            val prefs = settings(context)
            if (!prefs.isEnabled) {
                synchronized(lock) { streaks.clear() }
                return
            }
            val attempts = registerFailure(packageName, prefs.attemptThreshold) ?: return
            if (!canUseCamera(context)) return
            if (!capturing.compareAndSet(false, true)) return
            val appContext = context.applicationContext
            val timestamp = System.currentTimeMillis()
            executor.execute {
                try {
                    takePhoto(appContext, packageName, attempts, timestamp)
                } catch (t: Throwable) {
                    Log.w(TAG, "Intruder photo failed", t)
                } finally {
                    capturing.set(false)
                }
            }
        } catch (t: Throwable) {
            // Never let this feature interfere with unlocking
            Log.w(TAG, "Failed to handle unlock attempt", t)
        }
    }

    /** Returns the failure count if a photo is due now, else null. */
    private fun registerFailure(packageName: String, threshold: Int): Int? = synchronized(lock) {
        val now = SystemClock.elapsedRealtime()
        val streak = streaks[packageName]
            ?.takeIf { now - it.lastFailureAt <= STREAK_TIMEOUT_MS }
            ?: Streak(0, now, false).also { streaks[packageName] = it }
        streak.failures++
        streak.lastFailureAt = now
        if (streak.photoTaken || streak.failures < threshold) return null
        val last = lastCaptureAt[packageName]
        if (last != null && now - last < PACKAGE_COOLDOWN_MS) return null
        streak.photoTaken = true
        lastCaptureAt[packageName] = now
        streak.failures
    }

    private fun canUseCamera(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
    }

    private fun takePhoto(context: Context, packageName: String, attempts: Int, timestamp: Long) {
        val jpeg = FrontCameraCapture(context).capture() ?: return
        val photo = IntruderPhotoStore.save(context, jpeg, packageName, attempts, timestamp) ?: return
        IntruderPhotoStore.applyRetention(context)
        IntruderNotifier.notify(context, photo)
    }
}
