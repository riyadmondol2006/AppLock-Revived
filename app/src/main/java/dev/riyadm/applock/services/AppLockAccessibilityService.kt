package dev.riyadm.applock.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dev.riyadm.applock.R
import dev.riyadm.applock.core.broadcast.DeviceAdmin
import dev.riyadm.applock.core.trusted.TrustedNetworkMonitor
import dev.riyadm.applock.core.utils.LogUtils
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.core.utils.enableAccessibilityServiceWithShizuku
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.BackendImplementation
import dev.riyadm.applock.features.lockscreen.ui.LockScreenOverlayManager
import dev.riyadm.applock.services.AppLockConstants.ACCESSIBILITY_SETTINGS_CLASSES
import dev.riyadm.applock.services.AppLockConstants.EXCLUDED_APPS
import rikka.shizuku.Shizuku

@SuppressLint("AccessibilityPolicy")
class AppLockAccessibilityService : AccessibilityService() {
    private val appLockRepository: AppLockRepository by lazy { applicationContext.appLockRepository() }
    private val config: LockConfig by lazy { LockConfig.get(this) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val appLabel: String by lazy { getString(R.string.app_name) }

    @Volatile
    private var keyboardPackages: Set<String> = emptySet()
    private var launcherPackages: Set<String> = emptySet()
    private var launcherPackagesLoadedAt = 0L

    /** Package of the last application window that came to the foreground. */
    private var lastForegroundPackage = ""
    private var triggeringPackage = ""

    private var overlayManager: LockScreenOverlayManager? = null

    /** Package the lock overlay currently protects (also while it is hidden for a biometric prompt). */
    private var overlayPackage: String? = null

    /** Apps the user backed out of from the lock screen; scans don't re-lock them until reopened. */
    private val exitedPackages = mutableSetOf<String>()
    private var exitSuppressedUntil = 0L

    private var receiversRegistered = false
    private var observerRegistered = false

    enum class BiometricState {
        IDLE, AUTH_STARTED
    }

    companion object {
        private const val TAG = "AppLockAccessibility"
        private const val SETTINGS_PACKAGE = "com.android.settings"
        private val PACKAGE_INSTALLER_PACKAGES = setOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller"
        )
        private const val WINDOW_SCAN_DELAY_MS = 150L
        private const val EXIT_SUPPRESSION_MS = 1_000L
        private const val EXIT_VERIFY_DELAY_MS = 1_200L
        private const val LAUNCHER_CACHE_MS = 60_000L

        @Volatile
        var isServiceRunning = false

        @Volatile
        private var instance: AppLockAccessibilityService? = null

        /**
         * Shows the lock overlay for [packageName] again, e.g. after a biometric prompt started
         * from the overlay was cancelled. Safe to call from any thread; does nothing if the app
         * was unlocked meanwhile or is no longer on screen. When the accessibility service is not
         * running, the lock-screen state is reset so a polling backend re-locks on its next tick.
         */
        fun requestRelock(packageName: String) {
            val service = instance
            if (service == null) {
                AppLockManager.reportBiometricAuthFinished()
                AppLockManager.isLockScreenShown.set(false)
                return
            }
            service.mainHandler.post { service.relock(packageName) }
        }

        /** Re-evaluates what is on screen, locking anything that is no longer unlocked. */
        fun recheckForegroundApp() {
            val service = instance ?: return
            service.mainHandler.post { service.scanVisibleWindows() }
        }

        internal fun onAppUnlocked(packageName: String) {
            val service = instance ?: return
            service.mainHandler.post {
                if (service.overlayPackage == packageName) {
                    service.dismissOverlay()
                }
            }
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        LogUtils.d(TAG, "Screen off detected. Resetting AppLock state.")
                        AppLockManager.onScreenOff()
                        exitedPackages.clear()
                    }

                    Intent.ACTION_USER_PRESENT -> {
                        // An app opened from a lock-screen notification is already in the
                        // foreground once the keyguard goes away, without a new window event.
                        lastForegroundPackage = ""
                        scheduleWindowScan()
                    }
                }
            } catch (e: Exception) {
                logError("Error in screenStateReceiver", e)
            }
        }
    }

    private val imeObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            refreshKeyboardPackages()
        }
    }

    private val windowScanRunnable = Runnable { scanVisibleWindows() }

    override fun onCreate() {
        super.onCreate()
        try {
            isServiceRunning = true
            AppLockManager.reportBiometricAuthFinished()
            AppLockManager.isLockScreenShown.set(false)
            startPrimaryBackendService()

            overlayManager = LockScreenOverlayManager(this)

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            ContextCompat.registerReceiver(
                this,
                screenStateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiversRegistered = true
        } catch (e: Exception) {
            logError("Error in onCreate", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            serviceInfo = serviceInfo.apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_WINDOWS_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                packageNames = null
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }

            instance = this
            isServiceRunning = true
            refreshKeyboardPackages()
            registerImeObserver()
            NewAppAutoLocker.register(this, this)
            TrustedNetworkMonitor.register(this, this)

            Log.d(TAG, "Accessibility service connected")
            appLockRepository.setActiveBackend(BackendImplementation.ACCESSIBILITY)

            // A locked app may already be open (e.g. right after boot); check what is on screen.
            lastForegroundPackage = ""
            scheduleWindowScan()
        } catch (e: Exception) {
            logError("Error in onServiceConnected", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            handleAccessibilityEvent(event)
        } catch (e: Exception) {
            logError("Unhandled error in onAccessibilityEvent", e)
        }
    }

    private fun handleAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        if ((packageName == SETTINGS_PACKAGE || packageName in PACKAGE_INSTALLER_PACKAGES) &&
            config.antiUninstallEnabled
        ) {
            checkForDeviceAdminDeactivation(event, packageName)
        }

        if (!config.protectEnabled || !shouldAccessibilityHandleLocking()) {
            dismissOverlay()
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> scheduleWindowScan()
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindowStateChanged(event, packageName)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> onWindowContentChanged(event, packageName)
        }
    }

    private fun onWindowStateChanged(event: AccessibilityEvent, packageName: String) {
        if (packageName in launcherPackages()) {
            scheduleWindowScan()
            // Home/recents reached: the exit is done, so returning to the app (e.g. from its
            // recents card) is a real reopen and must lock at once, not after the exit check.
            if (exitedPackages.isNotEmpty()) {
                exitedPackages.clear()
                exitSuppressedUntil = 0L
            }
        }
        if (packageName == this.packageName) return
        if (handleDeviceLocked()) return
        if (isIgnoredPackage(packageName)) return

        val className = event.className?.toString().orEmpty()
        if (className.startsWith("android.widget.Toast")) return

        when (windowTypeOf(event.windowId)) {
            null, AccessibilityWindowInfo.TYPE_APPLICATION -> onForegroundChanged(packageName)

            AccessibilityWindowInfo.TYPE_SYSTEM -> {
                // Floating windows / overlays drawn by other apps: they never change the
                // foreground app, but a locked app must not show content in one either.
                if (packageName !in exitedPackages) maybeLock(packageName)
            }

            else -> Unit // keyboards, split-screen divider, other accessibility overlays
        }
    }

    /**
     * Content changes are by far the most frequent event, so everything that ends here for an
     * app that isn't locked, or is unlocked, only reads memory: no binder calls, no copies.
     */
    private fun onWindowContentChanged(event: AccessibilityEvent, packageName: String) {
        if (isLauncherPaneChange(event) && packageName in launcherPackages()) {
            scheduleWindowScan()
        }
        if (!config.isLocked(packageName)) return
        if (packageName == this.packageName || isIgnoredPackage(packageName)) return
        if (packageName in exitedPackages) return
        if (AppLockManager.isAppTemporarilyUnlocked(packageName)) return
        if (AppLockManager.isWithinUnlockDuration(
                packageName,
                appLockRepository.getUnlockTimeDuration(),
                System.currentTimeMillis()
            )
        ) {
            return
        }
        if (packageName == overlayPackage && overlayManager?.isShowing == true) {
            // Already covered: only re-assert the overlay (cheap, no binder calls), e.g. after a
            // cancelled biometric prompt. The keyguard check below is not needed for that.
            maybeLock(packageName)
            return
        }
        // On a trusted network maybeLock would decline anyway; skip the binder calls below.
        if (TrustedNetworkMonitor.isTrusted() && !appLockRepository.policyFor(packageName).requireBoth) return
        if (handleDeviceLocked()) return

        // Stale events from an app that just left the screen must not lock the new foreground.
        if (packageName != lastForegroundPackage && packageName != overlayPackage) {
            val snapshot = visibleWindows()
            if (packageName !in snapshot.appPackages && packageName !in snapshot.floatingPackages) return
        }
        maybeLock(packageName)
    }

    private fun onForegroundChanged(packageName: String) {
        val previous = lastForegroundPackage
        lastForegroundPackage = packageName
        if (previous != packageName) triggeringPackage = previous

        if (packageName in exitedPackages && SystemClock.uptimeMillis() >= exitSuppressedUntil) {
            exitedPackages.remove(packageName)
        }

        val triggerExcluded = appLockRepository.getTriggerExcludedApps()
        if (previous != packageName && previous in triggerExcluded) {
            // Opened from a trigger-excluded app: don't lock it for this session.
            if (config.isLocked(packageName)) {
                AppLockManager.markUnlocked(packageName)
            }
            return
        }

        val unlocked = AppLockManager.unlockedAppsSnapshot() - packageName
        val overlay = overlayPackage
        if ((unlocked.isNotEmpty() && packageName !in triggerExcluded) || (overlay != null && overlay != packageName)) {
            val snapshot = visibleWindows()
            if (snapshot.complete) {
                if (packageName !in triggerExcluded) {
                    unlocked.filter { it !in snapshot.appPackages }
                        .forEach(AppLockManager::revokeTemporaryUnlock)
                }
                if (overlay != null && overlay != packageName && overlay !in snapshot.appPackages) {
                    dismissOverlay()
                }
            }
            // Windows that are animating out are still listed; look again once they settle.
            scheduleWindowScan()
        }

        if (packageName !in exitedPackages) {
            maybeLock(packageName)
        }
    }

    /** Shows the lock overlay for [packageName] if it is locked and not currently unlocked. */
    private fun maybeLock(packageName: String): Boolean {
        if (!config.isLocked(packageName)) return false
        if (AppLockManager.isBiometricAuthInProgress()) return false
        if (AppLockManager.isAppTemporarilyUnlocked(packageName)) return false
        if (AppLockManager.isWithinUnlockDuration(
                packageName,
                appLockRepository.getUnlockTimeDuration(),
                System.currentTimeMillis()
            )
        ) {
            return false
        }
        if (TrustedNetworkMonitor.isTrusted() && !appLockRepository.policyFor(packageName).requireBoth) return false
        showLockScreenOverlay(packageName)
        return true
    }

    private fun relock(packageName: String) {
        AppLockManager.reportBiometricAuthFinished()
        if (!config.protectEnabled || !shouldAccessibilityHandleLocking()) {
            AppLockManager.isLockScreenShown.set(false)
            return
        }
        if (AppLockManager.isAppTemporarilyUnlocked(packageName)) {
            if (overlayPackage == packageName) dismissOverlay()
            return
        }
        val snapshot = visibleWindows()
        val onScreen = packageName == lastForegroundPackage ||
                packageName in snapshot.appPackages ||
                packageName in snapshot.floatingPackages
        if (onScreen) {
            exitedPackages.remove(packageName)
            if (!maybeLock(packageName)) {
                AppLockManager.isLockScreenShown.set(false)
            }
        } else {
            dismissOverlay()
        }
    }

    private fun showLockScreenOverlay(packageName: String) {
        val manager = overlayManager ?: return
        val current = overlayPackage
        if (current != null && current != packageName) {
            // A different locked app came to the front while the overlay was up (e.g. rapid
            // recents switching): retarget so unlocking can't reveal the wrong app.
            manager.removeOverlay()
        }
        if (current != packageName) {
            LogUtils.d(TAG, "Showing overlay for: $packageName")
        }

        overlayPackage = packageName
        AppLockManager.isLockScreenShown.set(true)
        manager.showOverlay(
            lockedPackageName = packageName,
            triggeringPackageName = triggeringPackage,
            onUnlock = {
                if (overlayPackage == packageName) overlayPackage = null
                AppLockManager.unlockApp(packageName)
                AppLockManager.isLockScreenShown.set(false)
            },
            onExit = {
                if (overlayPackage == packageName) overlayPackage = null
                exitedPackages.add(packageName)
                exitSuppressedUntil = SystemClock.uptimeMillis() + EXIT_SUPPRESSION_MS
                performGlobalAction(GLOBAL_ACTION_HOME)
                mainHandler.postDelayed({
                    AppLockManager.isLockScreenShown.set(false)
                    verifyLeftAfterExit(packageName)
                }, EXIT_VERIFY_DELAY_MS)
            }
        )
    }

    /** If going home after "close" didn't work, the app is still focused: lock it again. */
    private fun verifyLeftAfterExit(packageName: String) {
        try {
            val activePackage = rootInActiveWindow?.packageName?.toString()
            if (activePackage == packageName) {
                exitedPackages.remove(packageName)
                maybeLock(packageName)
            }
        } catch (e: Exception) {
            logError("Error verifying exit", e)
        }
    }

    private fun dismissOverlay() {
        if (overlayPackage == null) return
        overlayPackage = null
        overlayManager?.removeOverlay()
        AppLockManager.isLockScreenShown.set(false)
    }

    private fun scheduleWindowScan() {
        mainHandler.removeCallbacks(windowScanRunnable)
        mainHandler.postDelayed(windowScanRunnable, WINDOW_SCAN_DELAY_MS)
    }

    /**
     * Reconciles lock state with what is actually on screen. Handles multi-window, freeform and
     * picture-in-picture (every visible app window is checked, not only the focused one), apps
     * already open when the service connects or the keyguard goes away, and overlays left behind
     * after the protected app went away.
     */
    private fun scanVisibleWindows() {
        try {
            if (!config.protectEnabled || !shouldAccessibilityHandleLocking()) {
                dismissOverlay()
                return
            }
            if (handleDeviceLocked()) return

            val overlay = overlayPackage
            if (overlay != null && AppLockManager.isAppTemporarilyUnlocked(overlay)) {
                dismissOverlay()
            }

            val snapshot = visibleWindows()
            if (lastForegroundPackage.isEmpty()) {
                val active = try {
                    rootInActiveWindow?.packageName?.toString()
                } catch (_: Exception) {
                    null
                }
                if (active != null && active != packageName && !isIgnoredPackage(active)) {
                    onForegroundChanged(active)
                }
            }

            if (!snapshot.complete) return

            val foreground = lastForegroundPackage
            val inExcludedApp = foreground in appLockRepository.getTriggerExcludedApps() &&
                    foreground in snapshot.appPackages
            if (!inExcludedApp) {
                AppLockManager.unlockedAppsSnapshot()
                    .filter { it !in snapshot.appPackages && it !in snapshot.floatingPackages }
                    .forEach(AppLockManager::revokeTemporaryUnlock)
            }

            val currentOverlay = overlayPackage
            if (currentOverlay != null &&
                currentOverlay !in snapshot.appPackages &&
                currentOverlay !in snapshot.floatingPackages &&
                currentOverlay != foreground
            ) {
                dismissOverlay()
            }

            if (overlayPackage == null && !AppLockManager.isBiometricAuthInProgress()) {
                for (pkg in snapshot.appPackages) {
                    if (pkg == packageName || isIgnoredPackage(pkg) || pkg in exitedPackages) continue
                    if (maybeLock(pkg)) break
                }
            } else if (overlayPackage != null) {
                // Re-show after a cancelled biometric prompt; no-op while it is already visible.
                overlayPackage?.let { maybeLock(it) }
            }
        } catch (e: Exception) {
            logError("Error scanning windows", e)
        }
    }

    private class WindowSnapshot(
        val appPackages: List<String>,
        val floatingPackages: Set<String>,
        val complete: Boolean
    )

    private fun visibleWindows(): WindowSnapshot {
        return try {
            val appPackages = mutableListOf<String>()
            val floating = mutableSetOf<String>()
            var complete = true
            var sawApplication = false
            for (window in windows) {
                when (window.type) {
                    AccessibilityWindowInfo.TYPE_APPLICATION -> {
                        sawApplication = true
                        val pkg = window.root?.packageName?.toString()
                        if (pkg == null) {
                            complete = false
                        } else if (pkg !in appPackages) {
                            appPackages.add(pkg)
                        }
                    }

                    AccessibilityWindowInfo.TYPE_SYSTEM -> {
                        window.root?.packageName?.toString()?.let(floating::add)
                    }
                }
            }
            WindowSnapshot(appPackages, floating, complete && sawApplication)
        } catch (e: Exception) {
            logError("Error reading windows", e)
            WindowSnapshot(emptyList(), emptySet(), false)
        }
    }

    private fun windowTypeOf(windowId: Int): Int? {
        if (windowId < 0) return null
        return try {
            windows.firstOrNull { it.id == windowId }?.type
        } catch (_: Exception) {
            null
        }
    }

    private fun handleDeviceLocked(): Boolean {
        if (!applicationContext.isDeviceLocked()) return false
        AppLockManager.appUnlockTimes.clear()
        AppLockManager.clearTemporarilyUnlockedApp()
        return true
    }

    private fun isIgnoredPackage(packageName: String): Boolean {
        return packageName == this.packageName ||
                packageName in keyboardPackages ||
                packageName in EXCLUDED_APPS
    }

    private fun shouldAccessibilityHandleLocking(): Boolean {
        return config.backend == BackendImplementation.ACCESSIBILITY
    }

    @SuppressLint("InlinedApi")
    private fun isLauncherPaneChange(event: AccessibilityEvent): Boolean {
        val types = event.contentChangeTypes
        return types and AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED != 0 ||
                types and AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED != 0
    }

    private fun checkForDeviceAdminDeactivation(event: AccessibilityEvent, packageName: String) {
        if (isDeactivationAttempt(event, packageName)) {
            Log.d(TAG, "Blocking accessibility service deactivation")
            blockDeactivationAttempt()
            return
        }

        if (packageName == SETTINGS_PACKAGE && isDeviceAdminPage(event)) {
            blockDeviceAdminDeactivation()
        }
    }

    private fun mentionsThisApp(event: AccessibilityEvent): Boolean {
        val texts = event.text.mapNotNull { it?.toString() } +
                listOfNotNull(event.contentDescription?.toString())
        return texts.any { it.contains(appLabel, ignoreCase = true) || it.contains("App Lock", ignoreCase = true) }
    }

    private fun isDeactivationAttempt(event: AccessibilityEvent, packageName: String): Boolean {
        val className = event.className?.toString() ?: return false
        val isSettingsPage = packageName == SETTINGS_PACKAGE &&
                (className in ACCESSIBILITY_SETTINGS_CLASSES || className == "com.android.settings.SubSettings")
        val isInstallerDialog = packageName in PACKAGE_INSTALLER_PACKAGES &&
                className == "android.app.AlertDialog"
        return (isSettingsPage || isInstallerDialog) && mentionsThisApp(event)
    }

    @SuppressLint("InlinedApi")
    private fun blockDeactivationAttempt() {
        try {
            performGlobalAction(GLOBAL_ACTION_BACK)
            performGlobalAction(GLOBAL_ACTION_HOME)
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } catch (e: Exception) {
            logError("Error blocking deactivation attempt", e)
        }
    }

    private fun isDeviceAdminPage(event: AccessibilityEvent): Boolean {
        val className = event.className?.toString().orEmpty()
        val hasDeviceAdminDescription =
            event.contentDescription?.toString()?.contains("Device admin app", ignoreCase = true) == true &&
                    className == "android.widget.FrameLayout"
        val isAdminConfigClass =
            className.contains("DeviceAdminAdd") || className.contains("DeviceAdminSettings")
        return hasDeviceAdminDescription || isAdminConfigClass
    }

    @SuppressLint("InlinedApi")
    private fun blockDeviceAdminDeactivation() {
        try {
            val dpm: DevicePolicyManager? = getSystemService()
            val component = ComponentName(this, DeviceAdmin::class.java)

            if (dpm?.isAdminActive(component) == true) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_HOME)
                mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }, 100)
                Toast.makeText(this, R.string.anti_uninstall_blocked_toast, Toast.LENGTH_LONG).show()
                Log.w(TAG, "Blocked device admin deactivation attempt.")
            }
        } catch (e: Exception) {
            logError("Error blocking device admin deactivation", e)
        }
    }

    private fun registerImeObserver() {
        if (observerRegistered) return
        try {
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS), false, imeObserver
            )
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, imeObserver
            )
            observerRegistered = true
        } catch (e: Exception) {
            logError("Error registering IME observer", e)
        }
    }

    private fun refreshKeyboardPackages() {
        keyboardPackages = try {
            getSystemService<InputMethodManager>()?.enabledInputMethodList
                ?.map { it.packageName }?.toSet() ?: emptySet()
        } catch (e: Exception) {
            logError("Error getting keyboard package names", e)
            emptySet()
        }
    }

    private fun launcherPackages(): Set<String> {
        val now = SystemClock.uptimeMillis()
        if (launcherPackages.isNotEmpty() && now - launcherPackagesLoadedAt < LAUNCHER_CACHE_MS) {
            return launcherPackages
        }
        launcherPackages = try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val defaultHome = packageManager
                .resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
            val systemHome = packageManager
                .queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                .firstOrNull {
                    (it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                            it.activityInfo.packageName != packageName
                }?.activityInfo?.packageName
            setOfNotNull(defaultHome, systemHome)
                .filter { it != packageName && it != "android" }
                .toSet()
        } catch (e: Exception) {
            logError("Error getting launcher packages", e)
            emptySet()
        }
        launcherPackagesLoadedAt = now
        return launcherPackages
    }

    private fun startPrimaryBackendService() {
        try {
            AppLockManager.stopAllOtherServices(this, AppLockAccessibilityService::class.java)

            val serviceClass = when (appLockRepository.getBackendImplementation()) {
                BackendImplementation.SHIZUKU -> ShizukuAppLockService::class.java
                BackendImplementation.USAGE_STATS -> UsageLockService::class.java
                else -> null
            } ?: return

            val intent = Intent(this, serviceClass)
            try {
                startService(intent)
            } catch (_: IllegalStateException) {
                ContextCompat.startForegroundService(this, intent)
            }
        } catch (e: Exception) {
            logError("Error starting primary backend service", e)
        }
    }

    override fun onInterrupt() {
        LogUtils.d(TAG, "Accessibility service interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return try {
            Log.d(TAG, "Accessibility service unbound")
            isServiceRunning = false
            if (instance === this) instance = null
            dismissOverlay()

            if (Shizuku.pingBinder() && appLockRepository.isAntiUninstallEnabled()) {
                enableAccessibilityServiceWithShizuku(ComponentName(packageName, javaClass.name))
            }

            super.onUnbind(intent)
        } catch (e: Exception) {
            logError("Error in onUnbind", e)
            super.onUnbind(intent)
        }
    }

    override fun onDestroy() {
        try {
            isServiceRunning = false
            if (instance === this) instance = null
            LogUtils.d(TAG, "Accessibility service destroyed")

            mainHandler.removeCallbacksAndMessages(null)
            overlayManager?.removeOverlay()
            overlayPackage = null

            if (receiversRegistered) {
                try {
                    unregisterReceiver(screenStateReceiver)
                } catch (_: IllegalArgumentException) {
                }
                receiversRegistered = false
            }
            if (observerRegistered) {
                contentResolver.unregisterContentObserver(imeObserver)
                observerRegistered = false
            }
            NewAppAutoLocker.unregister(this, this)
            TrustedNetworkMonitor.unregister(this)

            AppLockManager.isLockScreenShown.set(false)
        } catch (e: Exception) {
            logError("Error in onDestroy", e)
        }
        super.onDestroy()
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}
