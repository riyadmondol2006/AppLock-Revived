package dev.riyadm.applock.features.lockscreen.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dev.riyadm.applock.R
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.UnlockLogRepository
import dev.riyadm.applock.features.lockscreen.domain.PolicyUnlockGate
import dev.riyadm.applock.features.lockscreen.domain.resolvedLockPolicy
import dev.riyadm.applock.services.AppLockManager
import dev.riyadm.applock.ui.components.PinDots
import dev.riyadm.applock.ui.components.PinKeypad
import dev.riyadm.applock.ui.components.keypadDigits
import dev.riyadm.applock.ui.theme.AppLockTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lock screen activity used by the Shizuku and Usage Stats backends.
 * It can only be left by authenticating or by going to the home screen.
 */
class PasswordOverlayActivity : FragmentActivity() {
    private lateinit var appLockRepository: AppLockRepository
    private lateinit var biometricPrompt: BiometricPrompt

    private var lockedPackage by mutableStateOf("")
    private var appName by mutableStateOf("")
    private var session by mutableIntStateOf(0)
    private var biometricUsable by mutableStateOf(false)
    private var gate by mutableStateOf<PolicyUnlockGate?>(null)

    private var autoPromptPending = false
    private var isBiometricShowing = false
    private var isUnlocking = false
    private var lockUiShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appLockRepository = applicationContext.appLockRepository()

        if (!readIntent(intent)) {
            Log.e(TAG, "No locked_package provided. Finishing.")
            finish()
            return
        }

        val createdAt = SystemClock.uptimeMillis()
        disableOpenTransition()
        enableEdgeToEdge()
        setupWindow()
        // Opaque shield in the lock screen colour; the theme gives the starting window the same.
        window.setBackgroundDrawable(LockShield.surfaceColor(this).toDrawable())

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            authenticationCallback
        )
        biometricUsable = isBiometricUsable()
        autoPromptPending = savedInstanceState == null && shouldAutoPrompt()

        // First frame: just the shield, so the window covers the app as early as possible.
        // The Compose lock screen is composed in the frame after that.
        LockShield.afterFirstFrame(window.decorView, ACTIVITY_ATTACH_FALLBACK_MS) { committed ->
            Log.d(
                TAG, "Shield frame ${if (committed) "committed" else "timed out"} after " +
                        "${SystemClock.uptimeMillis() - createdAt} ms"
            )
            if (!isFinishing && !isDestroyed) showLockUi(createdAt)
        }
    }

    private fun showLockUi(createdAt: Long) {
        if (lockUiShown) return
        lockUiShown = true
        window.decorView.doOnPreDraw {
            Log.d(TAG, "Lock UI first frame after ${SystemClock.uptimeMillis() - createdAt} ms")
        }
        setContent {
            AppLockTheme {
                key(session) {
                    LockScreen(
                        spec = LockScreenSpec(
                            packageName = lockedPackage,
                            title = appName,
                            showBiometric = biometricUsable,
                            onBiometric = ::triggerBiometricPrompt,
                            onClose = ::goHome,
                            sessionKey = session,
                            gate = gate,
                            onSuccess = ::onCredentialVerified
                        )
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (readIntent(intent)) autoPromptPending = shouldAutoPrompt()
    }

    private fun readIntent(intent: Intent?): Boolean {
        val pkg = intent?.getStringExtra(EXTRA_LOCKED_PACKAGE) ?: return false
        lockedPackage = pkg
        appName = ""
        isUnlocking = false
        // A new session starts with no factor passed; the policy is resolved once per session.
        gate = PolicyUnlockGate(resolvedLockPolicy(pkg))
        biometricUsable = isBiometricUsable()
        session++
        lifecycleScope.launch {
            val label = withContext(Dispatchers.IO) { loadAppLabel(pkg) }
            if (lockedPackage == pkg) appName = label
        }
        return true
    }

    /** An open animation would scale or fade the lock screen in over the locked app. */
    private fun disableOpenTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun setupWindow() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SECURE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window.setHideOverlayWindows(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        applyBrightness()
    }

    private fun applyBrightness() {
        if (!appLockRepository.shouldUseMaxBrightness()) return
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
    }

    private fun isBiometricUsable(): Boolean = gate?.policy?.allowBiometric == true

    private fun shouldAutoPrompt(): Boolean = gate?.shouldAutoPromptBiometric == true

    private val authenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            // Cancel, "Use PIN", lockout, dismissal: stay on the credential screen.
            isBiometricShowing = false
            AppLockManager.reportBiometricAuthFinished()
            gate?.onBiometricError(errorCode)
            Log.d(TAG, "Biometric error $errorCode: $errString")
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            isBiometricShowing = false
            val pkg = lockedPackage
            if (gate?.onBiometricSucceeded(appLockRepository, pkg) != true) {
                // Two-step: the credential is still required.
                AppLockManager.reportBiometricAuthFinished()
                return
            }
            AppLockManager.temporarilyUnlockAppWithBiometrics(pkg)
            unlockAndFinish()
        }
    }

    private fun triggerBiometricPrompt() {
        if (isBiometricShowing || isUnlocking || isFinishing || !biometricUsable) return
        if (gate?.isBiometricBlocked(appLockRepository.getRemainingLockoutMs()) == true) return
        isBiometricShowing = true
        AppLockManager.reportBiometricAuthStarted()
        try {
            biometricPrompt.authenticate(
                buildBiometricPromptInfo(this, appName, credentialFallback = gate?.policy?.biometricFirst != true)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unable to show biometric prompt", e)
            isBiometricShowing = false
            AppLockManager.reportBiometricAuthFinished()
        }
    }

    /** Called by the lock screen only once the app's policy is satisfied. */
    private fun onCredentialVerified() {
        if (isUnlocking || gate?.isSatisfied == false) return
        AppLockManager.unlockApp(lockedPackage)
        unlockAndFinish()
    }

    private fun unlockAndFinish() {
        isUnlocking = true
        finish()
    }

    /** Leaving without authenticating always goes home; finishing here would reveal the app. */
    private fun goHome() {
        if (isUnlocking) return
        try {
            startActivity(homeIntent())
        } catch (e: Exception) {
            Log.e(TAG, "Unable to launch home", e)
        }
    }

    override fun onResume() {
        super.onResume()
        AppLockManager.isLockScreenShown.set(true)
        biometricUsable = isBiometricUsable()
        if (autoPromptPending) {
            autoPromptPending = false
            window.decorView.post { triggerBiometricPrompt() }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isChangingConfigurations && !isBiometricShowing) {
            AppLockManager.isLockScreenShown.set(false)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        AppLockManager.isLockScreenShown.set(false)
        if (isBiometricShowing) {
            isBiometricShowing = false
            AppLockManager.reportBiometricAuthFinished()
        }
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isChangingConfigurations) return
        AppLockManager.isLockScreenShown.set(false)
        if (isBiometricShowing) AppLockManager.reportBiometricAuthFinished()
    }

    companion object {
        private const val TAG = "PasswordOverlayActivity"
        const val EXTRA_LOCKED_PACKAGE = "locked_package"

        // The activity window is only added after onResume, so allow more time than the overlay.
        private const val ACTIVITY_ATTACH_FALLBACK_MS = 250L
    }
}

@Composable
fun PinPasswordOverlayScreen(spec: LockScreenSpec, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { context.appLockRepository() }
    val controller = rememberUnlockController(spec.packageName, UnlockLogRepository.Method.PIN)
    val haptics = controller.haptics
    val scope = rememberCoroutineScope()

    val digits = remember(spec.sessionKey) { keypadDigits(repository.isScrambleKeypadEnabled()) }
    val autoUnlock = remember(spec.sessionKey) { repository.isAutoUnlockEnabled() }
    // Only the dots read the PIN during composition; the host and keypad don't recompose per digit.
    val pinState = remember(spec.sessionKey) { mutableStateOf("") }
    var pin by pinState
    val ready by remember(pinState) { derivedStateOf { pinState.value.length >= MIN_PIN_LENGTH } }
    var showError by remember { mutableStateOf(false) }
    var rejecting by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(controller.failureCount) {
        if (controller.failureCount == 0) return@LaunchedEffect
        showError = true
        rejecting = true
        try {
            shake.shake()
        } finally {
            // Typing during the shake already started a fresh PIN.
            if (rejecting) {
                rejecting = false
                pin = ""
            }
        }
    }
    if (autoUnlock) {
        LaunchedEffect(controller, pinState) {
            snapshotFlow { pinState.value }.collectLatest { value ->
                if (value.length >= MIN_PIN_LENGTH) {
                    delay(AUTO_UNLOCK_DEBOUNCE_MS)
                    controller.trySilently(value)
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    fun updatePin(value: String) {
        rejecting = false
        pin = value
        showError = false
        controller.onInputChanged(value)
    }

    val credentialVisible = spec.credentialVisible

    // The keypad gives haptics on touch down; hardware keys need none.
    val onDigit: (Int) -> Unit = { digit ->
        if (controller.canInput && credentialVisible) {
            val base = if (rejecting) "" else pin
            if (base.length < MAX_PIN_LENGTH) updatePin(base + digit)
        }
    }
    val onBackspace: () -> Unit = {
        if (controller.canInput && pin.isNotEmpty()) updatePin(if (rejecting) "" else pin.dropLast(1))
    }
    val onClear: () -> Unit = {
        if (controller.canInput && pin.isNotEmpty()) updatePin("")
    }
    val onSubmit: () -> Unit = submit@{
        if (!controller.canInput || rejecting || !credentialVisible) return@submit
        if (pin.length < MIN_PIN_LENGTH) {
            haptics.reject()
            scope.launch { shake.shake() }
        } else {
            controller.submit(pin)
        }
    }

    val hint = policyHint(spec, stringResource(R.string.lock_enter_pin), R.string.policy_lock_step_pin)
    val wrong = stringResource(R.string.lock_wrong_pin)

    LockScreenScaffold(
        spec = spec,
        controller = controller,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val char = event.utf16CodePoint.toChar()
                when {
                    char in '0'..'9' -> onDigit(char - '0')
                    event.key == Key.Backspace -> onBackspace()
                    event.key == Key.Enter || event.key == Key.NumPadEnter -> onSubmit()
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
        status = { compact ->
            if (controller.isLockedOut) {
                LockoutIndicator(controller, compact, Modifier.padding(top = 8.dp))
            } else {
                LockStatusText(text = if (showError) wrong else hint, isError = showError)
                if (credentialVisible) {
                    Spacer(Modifier.height(if (compact) 8.dp else 20.dp))
                    PinDots(
                        length = pin.length,
                        isError = showError,
                        dotSize = if (compact) 14.dp else 16.dp,
                        modifier = Modifier
                            .shakeOffset(shake)
                            .padding(horizontal = 32.dp)
                    )
                }
            }
            if (spec.biometricVisible && credentialVisible) {
                Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
                BiometricButton(onClick = spec.onBiometric, compact = compact)
            }
        },
        input = input@{
            if (!credentialVisible) {
                BiometricFirstPanel(spec, controller)
                return@input
            }
            PinKeypad(
                digits = digits,
                enabled = controller.canInput,
                confirmReady = ready,
                busy = controller.isVerifying,
                onDigit = onDigit,
                onBackspace = onBackspace,
                onClear = onClear,
                onConfirm = onSubmit,
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}

private const val AUTO_UNLOCK_DEBOUNCE_MS = 60L
