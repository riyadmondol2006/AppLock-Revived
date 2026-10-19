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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
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
import dev.riyadm.applock.services.AppLockManager
import dev.riyadm.applock.ui.motion.rememberAppHaptics
import dev.riyadm.applock.ui.theme.AppLockTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

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

    private fun isBiometricUsable(): Boolean =
        appLockRepository.isBiometricAuthEnabled() && canUseBiometrics()

    private fun shouldAutoPrompt(): Boolean =
        appLockRepository.isBiometricAutoPromptEnabled() && isBiometricUsable()

    private val authenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            // Cancel, "Use PIN", lockout, dismissal: stay on the credential screen.
            isBiometricShowing = false
            AppLockManager.reportBiometricAuthFinished()
            Log.d(TAG, "Biometric error $errorCode: $errString")
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            isBiometricShowing = false
            val pkg = lockedPackage
            thread(name = "unlock-attempt") {
                appLockRepository.recordUnlockAttempt(pkg, true, UnlockLogRepository.Method.BIOMETRIC)
            }
            AppLockManager.temporarilyUnlockAppWithBiometrics(pkg)
            unlockAndFinish()
        }
    }

    private fun triggerBiometricPrompt() {
        if (isBiometricShowing || isUnlocking || isFinishing || !biometricUsable) return
        isBiometricShowing = true
        AppLockManager.reportBiometricAuthStarted()
        try {
            biometricPrompt.authenticate(buildBiometricPromptInfo(this, appName))
        } catch (e: Exception) {
            Log.e(TAG, "Unable to show biometric prompt", e)
            isBiometricShowing = false
            AppLockManager.reportBiometricAuthFinished()
        }
    }

    private fun onCredentialVerified() {
        if (isUnlocking) return
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
    var pin by remember(spec.sessionKey) { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var rejecting by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(controller.failureCount) {
        if (controller.failureCount == 0) return@LaunchedEffect
        showError = true
        rejecting = true
        shake.shake()
        pin = ""
        rejecting = false
    }
    if (autoUnlock) {
        LaunchedEffect(pin) {
            if (pin.length >= MIN_PIN_LENGTH) {
                delay(AUTO_UNLOCK_DEBOUNCE_MS)
                controller.trySilently(pin)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val inputEnabled = controller.canInput && !rejecting

    fun updatePin(value: String) {
        pin = value
        showError = false
        controller.onInputChanged(value)
    }

    val onDigit: (Int) -> Unit = { digit ->
        if (inputEnabled && pin.length < MAX_PIN_LENGTH) {
            haptics.press()
            updatePin(pin + digit)
        }
    }
    val onBackspace: () -> Unit = {
        if (inputEnabled && pin.isNotEmpty()) {
            haptics.press()
            updatePin(pin.dropLast(1))
        }
    }
    val onClear: () -> Unit = {
        if (inputEnabled && pin.isNotEmpty()) {
            haptics.longPress()
            updatePin("")
        }
    }
    val onSubmit: () -> Unit = submit@{
        if (!inputEnabled) return@submit
        if (pin.length < MIN_PIN_LENGTH) {
            haptics.reject()
            scope.launch { shake.shake() }
        } else {
            controller.submit(pin)
        }
    }

    val hint = spec.subtitle ?: stringResource(R.string.lock_enter_pin)
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
                Spacer(Modifier.height(if (compact) 8.dp else 20.dp))
                PinDots(
                    length = pin.length,
                    isError = showError || rejecting,
                    modifier = Modifier
                        .shakeOffset(shake)
                        .padding(horizontal = 32.dp)
                )
            }
            if (spec.showBiometric) {
                Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
                BiometricButton(onClick = spec.onBiometric, compact = compact)
            }
        },
        input = {
            PinKeypad(
                digits = digits,
                enabled = inputEnabled,
                isVerifying = controller.isVerifying,
                onDigit = onDigit,
                onBackspace = onBackspace,
                onClear = onClear,
                onSubmit = onSubmit,
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}

/** PIN progress dots, also used by the set-PIN screens. */
@Composable
fun PasswordIndicators(passwordLength: Int) {
    PinDots(length = passwordLength, isError = false, modifier = Modifier.padding(horizontal = 32.dp))
}

/** One row of morphing keypad keys, also used by the set-PIN screens. */
@Composable
fun KeypadRow(
    disableHaptics: Boolean = false,
    keys: List<String>,
    icons: List<ImageVector?> = emptyList(),
    onKeyClick: (String) -> Unit,
    buttonSize: Dp,
    buttonSpacing: Dp
) {
    val haptics = rememberAppHaptics()
    val colors = MaterialTheme.colorScheme
    val backspace = stringResource(R.string.lock_backspace_cd)
    val confirm = stringResource(R.string.lock_confirm_cd)
    val digitStyle = if (buttonSize >= 76.dp) MaterialTheme.typography.displaySmall
    else MaterialTheme.typography.headlineLarge

    Row(
        horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEachIndexed { index, key ->
            val icon = icons.getOrNull(index)
            val isPrimary = key == "proceed"
            KeypadKey(
                onClick = {
                    if (!disableHaptics) haptics.press()
                    onKeyClick(key)
                },
                size = buttonSize,
                containerColor = when {
                    isPrimary -> colors.primary
                    icon != null -> colors.secondaryContainer
                    else -> colors.surfaceContainerHigh
                },
                pressedContainerColor = if (isPrimary) colors.inversePrimary else colors.primaryContainer,
                contentColor = when {
                    isPrimary -> colors.onPrimary
                    icon != null -> colors.onSecondaryContainer
                    else -> colors.onSurface
                },
                contentDescription = when (key) {
                    "backspace" -> backspace
                    "proceed" -> confirm
                    else -> key
                }
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(buttonSize * 0.38f))
                } else {
                    Text(text = key, style = digitStyle)
                }
            }
        }
    }
}

private const val AUTO_UNLOCK_DEBOUNCE_MS = 160L
