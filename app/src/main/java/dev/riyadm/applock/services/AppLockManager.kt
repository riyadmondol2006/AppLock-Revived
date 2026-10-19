package dev.riyadm.applock.services

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import dev.riyadm.applock.core.utils.LogUtils
import dev.riyadm.applock.services.AppLockAccessibilityService.BiometricState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object AppLockConstants {
    val KNOWN_RECENTS_CLASSES = setOf(
        "com.android.systemui.recents.RecentsActivity",
        "com.android.quickstep.RecentsActivity",
        "com.android.systemui.recents.RecentsView",
        "com.android.systemui.recents.RecentsPanelView",
    )

    val EXCLUDED_APPS = setOf(
        "com.android.systemui",
        "com.android.intentresolver",
        "com.google.android.permissioncontroller",
        "android.uid.system:1000",
        "com.google.android.googlequicksearchbox",
        "android",
        "com.google.android.gms",
        "com.google.android.webview"
    )

    val ACCESSIBILITY_SETTINGS_CLASSES = setOf(
        "com.android.settings.accessibility.AccessibilitySettings",
        "com.android.settings.accessibility.AccessibilityMenuActivity",
        "com.android.settings.accessibility.AccessibilityShortcutActivity",
        "com.android.settings.Settings\$AccessibilitySettingsActivity"
    )

    const val LAUNCHER_ACTIVITY_CLASS = "com.android.launcher3.uioverrides.QuickstepLauncher"

    /** Unlock durations at or above this value mean "until the screen turns off". */
    const val UNLOCK_UNTIL_SCREEN_OFF_MINUTES = 10_000
}

fun Context.isDeviceLocked(): Boolean {
    val keyguardManager = getSystemService(KeyguardManager::class.java)
    return keyguardManager?.isKeyguardLocked ?: false
}

@Suppress("DEPRECATION")
fun Context.isServiceRunning(serviceClass: Class<*>): Boolean {
    val manager = getSystemService(ActivityManager::class.java) ?: return false
    return manager.getRunningServices(Int.MAX_VALUE)
        .any { serviceClass.name == it.service.className }
}

object AppLockManager {
    private const val TAG = "AppLockManager"

    /**
     * Apps that were unlocked and are still considered "in use". Several apps can be unlocked at
     * the same time in multi-window, so this is a set; [temporarilyUnlockedApp] is the most recent.
     */
    private val unlockedApps: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Volatile
    var temporarilyUnlockedApp: String = ""
        private set

    val appUnlockTimes = ConcurrentHashMap<String, Long>()
    val isLockScreenShown = AtomicBoolean(false)

    /** Time at which a backend last requested the lock screen; used to detect a stuck flag. */
    private val lockScreenRequestedAt = AtomicLong(0L)
    private const val STUCK_LOCK_SCREEN_MS = 2_500L

    @Volatile
    var currentBiometricState: BiometricState? = null

    // Grace period tracking
    @Volatile
    private var recentlyLeftApp: String = ""

    @Volatile
    private var recentlyLeftTime: Long = 0L
    private const val GRACE_PERIOD_MS = 300L

    fun setRecentlyLeftApp(packageName: String) {
        recentlyLeftApp = packageName
        recentlyLeftTime = System.currentTimeMillis()
        LogUtils.d(TAG, "Left app $packageName at $recentlyLeftTime")
    }

    fun checkAndRestoreRecentlyLeftApp(packageName: String): Boolean {
        if (packageName == recentlyLeftApp && packageName.isNotEmpty()) {
            val elapsed = System.currentTimeMillis() - recentlyLeftTime
            if (elapsed <= GRACE_PERIOD_MS) {
                LogUtils.d(TAG, "Restoring unlock state for $packageName (elapsed: ${elapsed}ms)")
                markUnlocked(packageName)
                recentlyLeftApp = ""
                recentlyLeftTime = 0L
                return true
            } else {
                recentlyLeftApp = ""
            }
        }
        return false
    }

    private val ALL_APP_LOCK_SERVICES = setOf(
        ShizukuAppLockService::class.java,
        UsageLockService::class.java
    )

    fun unlockApp(packageName: String) {
        markUnlocked(packageName)
        appUnlockTimes[packageName] = System.currentTimeMillis()
        isLockScreenShown.set(false)
        LogUtils.d(TAG, "App $packageName unlocked at ${appUnlockTimes[packageName]}")
        AppLockAccessibilityService.onAppUnlocked(packageName)
    }

    /** Marks an app as unlocked for the current session without starting an unlock-duration timer. */
    fun markUnlocked(packageName: String) {
        if (packageName.isEmpty()) return
        unlockedApps.add(packageName)
        temporarilyUnlockedApp = packageName
    }

    fun temporarilyUnlockAppWithBiometrics(packageName: String) {
        unlockApp(packageName)
        reportBiometricAuthFinished()
    }

    @Volatile
    private var biometricStartedAt: Long = 0L
    private const val STUCK_BIOMETRIC_MS = 60_000L

    fun reportBiometricAuthStarted() {
        biometricStartedAt = System.currentTimeMillis()
        currentBiometricState = BiometricState.AUTH_STARTED
    }

    fun reportBiometricAuthFinished() {
        currentBiometricState = BiometricState.IDLE
    }

    /** True while a biometric prompt is up; a prompt that never reported back is ignored after a minute. */
    fun isBiometricAuthInProgress(): Boolean {
        if (currentBiometricState != BiometricState.AUTH_STARTED) return false
        if (System.currentTimeMillis() - biometricStartedAt > STUCK_BIOMETRIC_MS) {
            currentBiometricState = BiometricState.IDLE
            return false
        }
        return true
    }

    fun isAppTemporarilyUnlocked(packageName: String): Boolean =
        packageName.isNotEmpty() && packageName in unlockedApps

    fun unlockedAppsSnapshot(): Set<String> = unlockedApps.toSet()

    /** Ends the unlocked session of a single app (it will need auth again, subject to the unlock duration). */
    fun revokeTemporaryUnlock(packageName: String) {
        if (unlockedApps.remove(packageName)) {
            LogUtils.d(TAG, "Revoked temporary unlock for $packageName")
        }
        if (temporarilyUnlockedApp == packageName) {
            temporarilyUnlockedApp = unlockedApps.firstOrNull() ?: ""
        }
    }

    fun clearTemporarilyUnlockedApp() {
        unlockedApps.clear()
        temporarilyUnlockedApp = ""
    }

    fun clearAppUnlockState(packageName: String) {
        revokeTemporaryUnlock(packageName)
        appUnlockTimes.remove(packageName)
        if (packageName == recentlyLeftApp) {
            recentlyLeftApp = ""
            recentlyLeftTime = 0L
        }
        LogUtils.d(TAG, "Cleared stale unlock state for $packageName")
    }

    /**
     * Forgets every unlock (session and unlock-duration timers) so each locked app requires
     * authentication again. Never disables protection.
     *
     * [closeApps] also closes locked apps (if the user opted in). Only pass true for an explicit
     * "lock all now" request; automatic re-locks (trust lost, profile switch, restore) must not
     * force-stop the app in use.
     */
    fun lockAllNow(closeApps: Boolean = false) {
        clearTemporarilyUnlockedApp()
        appUnlockTimes.clear()
        recentlyLeftApp = ""
        recentlyLeftTime = 0L
        LogUtils.d(TAG, "All apps locked on request")
        AppLockAccessibilityService.recheckForegroundApp()
        if (closeApps) dev.riyadm.applock.features.hardening.LockedAppCloser.onLockAllNow()
    }

    /**
     * Returns true when [packageName] is still within its unlock duration. Clears the timestamp
     * once the duration has expired.
     */
    fun isWithinUnlockDuration(packageName: String, durationMinutes: Int, now: Long): Boolean {
        val unlockTimestamp = appUnlockTimes[packageName] ?: return false
        if (durationMinutes <= 0 || unlockTimestamp <= 0L) return false
        if (durationMinutes >= AppLockConstants.UNLOCK_UNTIL_SCREEN_OFF_MINUTES) return true

        val elapsed = now - unlockTimestamp
        if (elapsed in 0 until durationMinutes.toLong() * 60_000L) return true

        LogUtils.d(TAG, "Unlock duration expired for $packageName")
        appUnlockTimes.remove(packageName)
        return false
    }

    /** Called by polling backends right before launching the lock screen activity. */
    fun markLockScreenRequested() {
        lockScreenRequestedAt.set(System.currentTimeMillis())
        isLockScreenShown.set(true)
    }

    /**
     * A polling backend sees a foreign app in the foreground while [isLockScreenShown] is set.
     * If the lock screen was requested long enough ago it never appeared (e.g. the background
     * activity start was blocked) or it died without resetting the flag, so reset it.
     */
    fun resetLockScreenFlagIfStuck(): Boolean {
        if (!isLockScreenShown.get() || isBiometricAuthInProgress()) return false
        val requestedAt = lockScreenRequestedAt.get()
        if (System.currentTimeMillis() - requestedAt < STUCK_LOCK_SCREEN_MS) return false
        LogUtils.d(TAG, "Lock screen flag was stuck, resetting")
        isLockScreenShown.set(false)
        return true
    }

    fun onScreenOff() {
        isLockScreenShown.set(false)
        clearTemporarilyUnlockedApp()
        appUnlockTimes.clear()
    }

    fun stopAllOtherServices(context: Context, excludeService: Class<*>) {
        ALL_APP_LOCK_SERVICES
            .filter { it != excludeService }
            .forEach {
                context.stopService(Intent(context, it))
            }
        LogUtils.d(TAG, "Stopped all main app lock services except ${excludeService.simpleName}.")
    }
}
