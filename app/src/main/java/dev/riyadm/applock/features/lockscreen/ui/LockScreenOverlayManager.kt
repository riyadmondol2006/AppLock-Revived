package dev.riyadm.applock.features.lockscreen.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.RequiresApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.doOnPreDraw
import androidx.core.view.isNotEmpty
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.data.repository.UnlockLogRepository
import dev.riyadm.applock.services.AppLockManager
import dev.riyadm.applock.ui.theme.AppLockTheme
import java.lang.ref.WeakReference
import kotlin.concurrent.thread

/**
 * Full-screen lock overlay drawn by the accessibility service above every app.
 *
 * The overlay only goes away after successful authentication or after the user is sent home.
 * While a biometric prompt is up it is hidden (not removed), and it is shown again whenever the
 * prompt ends without success, so cancelling or "Use PIN" can never expose the locked app.
 */
@SuppressLint("ViewConstructor")
class LockScreenOverlayManager(private val context: Context) :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, OnBackPressedDispatcherOwner {

    private class Session(
        val packageName: String,
        val token: Long,
        val onUnlock: () -> Unit,
        val onExit: () -> Unit,
    )

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val repository = context.appLockRepository()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private var isStateRestored = false

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val onBackPressedDispatcher = OnBackPressedDispatcher { exitToHome() }

    private var rootView: OverlayRootView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var backInvokedCallback: Any? = null
    private var session by mutableStateOf<Session?>(null)
    private var biometricAvailable by mutableStateOf(false)
    private var appName by mutableStateOf("")

    private var biometricToken = 0L
    private var hiddenForBiometric = false
    private var leaving = false

    private val biometricWatchdog = Runnable {
        // The biometric activity never came up: forget the attempt, the overlay is still visible.
        if (biometricToken != 0L && !hiddenForBiometric) endBiometric()
    }

    val isShowing: Boolean get() = rootView != null

    fun showOverlay(
        lockedPackageName: String,
        triggeringPackageName: String,
        onUnlock: () -> Unit,
        onExit: () -> Unit
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showOverlay(lockedPackageName, triggeringPackageName, onUnlock, onExit) }
            return
        }

        val current = session
        if (rootView != null && current != null && !leaving) {
            if (current.packageName == lockedPackageName) {
                if (biometricToken == 0L) showContent()
                AppLockManager.isLockScreenShown.set(true)
                return
            }
            // Retarget to the app that is now in front.
            endBiometric()
            session = newSession(lockedPackageName, onUnlock, onExit)
            AppLockManager.isLockScreenShown.set(true)
            maybeAutoPromptBiometric()
            return
        }
        if (rootView != null) removeOverlay()

        if (!isStateRestored) {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            isStateRestored = true
        }

        session = newSession(lockedPackageName, onUnlock, onExit)
        biometricAvailable = repository.isBiometricAuthEnabled() && context.canUseBiometrics()
        leaving = false

        // The window starts as an opaque shield in the lock screen's surface colour. Its first
        // frame is a plain fill, so it covers the app right away; the Compose lock screen is
        // attached only once that frame is on its way to the display.
        val requestedAt = SystemClock.uptimeMillis()
        val root = OverlayRootView(context) { onBackPressedDispatcher.onBackPressed() }.apply {
            setViewTreeLifecycleOwner(this@LockScreenOverlayManager)
            setViewTreeSavedStateRegistryOwner(this@LockScreenOverlayManager)
            setViewTreeViewModelStoreOwner(this@LockScreenOverlayManager)
            setBackgroundColor(LockShield.surfaceColor(context))
            isFocusableInTouchMode = true
        }
        val params = createLayoutParams()

        try {
            windowManager.addView(root, params)
            rootView = root
            layoutParams = params
            LockShield.afterFirstFrame(root) { committed ->
                Log.d(
                    TAG, "Shield frame ${if (committed) "committed" else "timed out"} after " +
                            "${SystemClock.uptimeMillis() - requestedAt} ms"
                )
                attachLockUi(root, requestedAt)
            }
            AppLockManager.isLockScreenShown.set(true)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerBackCallback(root)
            root.requestFocus()
            maybeAutoPromptBiometric()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to show lock overlay", e)
            rootView = null
            layoutParams = null
            session = null
            AppLockManager.isLockScreenShown.set(false)
        }
    }

    private fun attachLockUi(root: OverlayRootView, requestedAt: Long) {
        if (rootView !== root || root.isNotEmpty()) return
        val composeView = ComposeView(context).apply {
            setContent {
                CompositionLocalProvider(
                    LocalOnBackPressedDispatcherOwner provides this@LockScreenOverlayManager
                ) {
                    AppLockTheme { OverlayContent() }
                }
            }
        }
        composeView.doOnPreDraw {
            Log.d(TAG, "Lock UI first frame after ${SystemClock.uptimeMillis() - requestedAt} ms")
        }
        root.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    @androidx.compose.runtime.Composable
    private fun OverlayContent() {
        val current = session ?: return
        key(current.token) {
            LockScreen(
                spec = LockScreenSpec(
                    packageName = current.packageName,
                    title = appName,
                    showBiometric = biometricAvailable,
                    onBiometric = { startBiometric() },
                    onClose = { exitToHome() },
                    sessionKey = current.token,
                    onSuccess = { unlock(current.token) }
                )
            )
        }
    }

    private fun newSession(packageName: String, onUnlock: () -> Unit, onExit: () -> Unit): Session {
        val token = ++tokenCounter
        appName = ""
        thread(name = "overlay-label") {
            val label = context.loadAppLabel(packageName)
            mainHandler.post { if (session?.token == token) appName = label }
        }
        return Session(packageName, token, onUnlock, onExit)
    }

    private fun createLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SECURE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                (if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS else 0),
        PixelFormat.TRANSLUCENT
    ).apply {
        title = "AppLockOverlay"
        @Suppress("DEPRECATION")
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Cover the status/navigation bar area too so no app content shows around the edges.
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (repository.shouldUseMaxBrightness()) {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
    }

    private fun maybeAutoPromptBiometric() {
        val token = session?.token ?: return
        if (!biometricAvailable || !repository.isBiometricAutoPromptEnabled()) return
        mainHandler.postDelayed({
            if (session?.token == token) startBiometric()
        }, AUTO_PROMPT_DELAY_MS)
    }

    private fun startBiometric() {
        val current = session ?: return
        if (biometricToken != 0L || leaving || rootView == null) return
        biometricToken = current.token
        activeManager = WeakReference(this)
        AppLockManager.reportBiometricAuthStarted()
        val intent = Intent(context, TransparentBiometricActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(TransparentBiometricActivity.EXTRA_LOCKED_PACKAGE, current.packageName)
            putExtra(TransparentBiometricActivity.EXTRA_APP_NAME, appName)
            putExtra(TransparentBiometricActivity.EXTRA_TOKEN, current.token)
        }
        try {
            context.startActivity(intent)
            mainHandler.postDelayed(biometricWatchdog, BIOMETRIC_START_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start biometric prompt", e)
            endBiometric()
        }
    }

    /** The biometric activity is drawn and about to show the prompt: get out of its way. */
    private fun onBiometricHostReady(token: Long): Boolean {
        if (token == 0L || token != biometricToken || session?.token != token) return false
        mainHandler.removeCallbacks(biometricWatchdog)
        hideContent()
        return true
    }

    private fun onBiometricResult(token: Long, success: Boolean): Boolean {
        if (token == 0L || token != biometricToken) return false
        // Restore the overlay first; on success it is removed in the same frame.
        endBiometric()
        val current = session ?: return false
        if (current.token != token) return false
        if (success) {
            val pkg = current.packageName
            thread(name = "unlock-attempt") {
                repository.recordUnlockAttempt(pkg, true, UnlockLogRepository.Method.BIOMETRIC)
            }
            unlock(token)
        }
        return true
    }

    private fun endBiometric() {
        mainHandler.removeCallbacks(biometricWatchdog)
        if (biometricToken != 0L) {
            biometricToken = 0L
            AppLockManager.reportBiometricAuthFinished()
        }
        showContent()
    }

    private fun hideContent() {
        val root = rootView ?: return
        val params = layoutParams ?: return
        if (hiddenForBiometric) return
        hiddenForBiometric = true
        root.visibility = View.INVISIBLE
        params.flags = params.flags or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        updateLayout(root, params)
    }

    private fun showContent() {
        val root = rootView ?: return
        val params = layoutParams ?: return
        if (!hiddenForBiometric) return
        hiddenForBiometric = false
        root.visibility = View.VISIBLE
        params.flags = params.flags and
                (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE).inv()
        updateLayout(root, params)
        root.requestFocus()
    }

    private fun updateLayout(root: View, params: WindowManager.LayoutParams) {
        try {
            windowManager.updateViewLayout(root, params)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to update overlay", e)
        }
    }

    private fun unlock(token: Long) {
        val current = session ?: return
        if (current.token != token || leaving) return
        leaving = true
        current.onUnlock()
        removeOverlay()
    }

    /** Close / Back: go home, and only then take the overlay down so the app is never revealed. */
    private fun exitToHome() {
        val current = session
        if (current == null) {
            removeOverlay()
            return
        }
        if (leaving) return
        leaving = true
        endBiometric()
        current.onExit()
        val token = current.token
        mainHandler.postDelayed({
            if (session?.token == token) removeOverlay()
        }, EXIT_REMOVE_DELAY_MS)
    }

    fun removeOverlay() {
        mainHandler.removeCallbacks(biometricWatchdog)
        if (biometricToken != 0L) {
            biometricToken = 0L
            AppLockManager.reportBiometricAuthFinished()
        }
        hiddenForBiometric = false
        session = null
        leaving = false
        val root = rootView ?: return
        rootView = null
        layoutParams = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) unregisterBackCallback(root)
        try {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            windowManager.removeViewImmediate(root)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to remove lock overlay", e)
        }
        AppLockManager.isLockScreenShown.set(false)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun registerBackCallback(root: View) {
        val callback = OnBackInvokedCallback { onBackPressedDispatcher.onBackPressed() }
        root.findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback
        )
        backInvokedCallback = callback
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun unregisterBackCallback(root: View) {
        val callback = backInvokedCallback as? OnBackInvokedCallback ?: return
        backInvokedCallback = null
        try {
            root.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(callback)
        } catch (_: Exception) {
        }
    }

    /** Swallows Back on older releases (and when no OnBackInvokedCallback is used). */
    private class OverlayRootView(context: Context, private val onBack: () -> Unit) :
        FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    companion object {
        private const val TAG = "LockScreenOverlay"
        private const val AUTO_PROMPT_DELAY_MS = 120L
        private const val BIOMETRIC_START_TIMEOUT_MS = 3_000L
        private const val EXIT_REMOVE_DELAY_MS = 350L

        private var tokenCounter = 0L
        private var activeManager: WeakReference<LockScreenOverlayManager>? = null

        /** Called by [TransparentBiometricActivity] on the main thread. */
        internal fun dispatchBiometricReady(token: Long): Boolean =
            activeManager?.get()?.onBiometricHostReady(token) == true

        /** Returns false when no overlay session owns this prompt any more. */
        internal fun dispatchBiometricResult(token: Long, success: Boolean): Boolean =
            activeManager?.get()?.onBiometricResult(token, success) == true
    }
}
