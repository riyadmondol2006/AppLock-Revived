package dev.riyadm.applock.features.lockscreen.ui

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.withResumed
import dev.riyadm.applock.R
import dev.riyadm.applock.services.AppLockAccessibilityService
import dev.riyadm.applock.services.AppLockManager
import dev.riyadm.applock.ui.theme.AppLockTheme

/**
 * Hosts the system biometric prompt for the accessibility overlay (an accessibility overlay
 * window sits above the prompt, so the prompt needs an activity of its own).
 *
 * It draws an opaque lock backdrop, tells the overlay to hide only once that backdrop is on
 * screen, and always reports back exactly once: success unlocks, anything else brings the
 * overlay back. It lives in its own task (taskAffinity="") so finishing returns to the locked
 * app instead of App Lock's main task.
 */
class TransparentBiometricActivity : FragmentActivity() {
    private var token = 0L
    private var lockedPackage: String? = null
    private var appName: String? = null
    private var credentialFallback = true
    private var authStarted = false
    private var resultDelivered = false
    private lateinit var biometricPrompt: BiometricPrompt

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = intent.getLongExtra(EXTRA_TOKEN, 0L)
        lockedPackage = intent.getStringExtra(EXTRA_LOCKED_PACKAGE)
        appName = intent.getStringExtra(EXTRA_APP_NAME)
        credentialFallback = intent.getBooleanExtra(EXTRA_CREDENTIAL_FALLBACK, true)

        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        enableEdgeToEdge()
        window.setBackgroundDrawable(LockShield.surfaceColor(this).toDrawable())

        if (savedInstanceState != null || token == 0L || lockedPackage == null) {
            deliver(success = false)
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                deliver(success = false)
                finish()
            }
        })

        biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Log.d(TAG, "Biometric error $errorCode: $errString")
                    deliver(success = false, errorCode = errorCode)
                    finish()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    deliver(success = true)
                    finish()
                }
            }
        )

        setContent {
            AppLockTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val name = appName.orEmpty()
                        AppIconBadge(
                            packageName = lockedPackage,
                            fallbackIcon = Icons.Rounded.Lock,
                            size = 104.dp,
                            contentDescription = stringResource(R.string.lock_app_icon_cd, name)
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.headlineMediumEmphasized,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.lock_biometric_waiting),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        // Static: the prompt covers this backdrop, so a spinner would only burn frames.
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    // Two frames: the opaque backdrop is on screen before the overlay hides.
                    withFrameNanos { }
                    withFrameNanos { }
                    lifecycle.withResumed { startAuthentication() }
                }
            }
        }
    }

    private fun startAuthentication() {
        if (authStarted || isFinishing) return
        authStarted = true
        AppLockManager.reportBiometricAuthStarted()
        if (!LockScreenOverlayManager.dispatchBiometricReady(token)) {
            deliver(success = false)
            finish()
            return
        }
        try {
            biometricPrompt.authenticate(buildBiometricPromptInfo(this, appName, credentialFallback))
        } catch (e: Exception) {
            Log.e(TAG, "Biometric failed to start", e)
            deliver(success = false)
            finish()
        }
    }

    private fun deliver(success: Boolean, errorCode: Int = 0) {
        if (resultDelivered) return
        resultDelivered = true
        val handled = LockScreenOverlayManager.dispatchBiometricResult(token, success, errorCode)
        if (!handled) {
            // The overlay that started this prompt is gone; let the service decide what to show.
            AppLockManager.reportBiometricAuthFinished()
            lockedPackage?.let(AppLockAccessibilityService::requestRelock)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !resultDelivered) {
            deliver(success = false)
            if (!isFinishing) finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) deliver(success = false)
    }

    companion object {
        private const val TAG = "TransparentBiometric"
        const val EXTRA_LOCKED_PACKAGE = "locked_package"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_TOKEN = "overlay_token"
        const val EXTRA_CREDENTIAL_FALLBACK = "credential_fallback"
    }
}
