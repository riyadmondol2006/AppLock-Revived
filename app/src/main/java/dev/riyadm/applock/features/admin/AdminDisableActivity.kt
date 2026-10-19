package dev.riyadm.applock.features.admin

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.ui.res.stringResource
import dev.riyadm.applock.R
import dev.riyadm.applock.core.broadcast.DeviceAdmin
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.features.lockscreen.ui.LockScreen
import dev.riyadm.applock.features.lockscreen.ui.LockScreenSpec
import dev.riyadm.applock.ui.theme.AppLockTheme

/**
 * Asks for the App Lock credential before device admin (anti-uninstall) can be turned off.
 * Shares the lock screen UI, including brute-force lockout. Biometrics are not accepted here.
 */
class AdminDisableActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = cancel()
        })

        setContent {
            AppLockTheme {
                LockScreen(
                    spec = LockScreenSpec(
                        packageName = packageName,
                        title = stringResource(R.string.lock_admin_title),
                        subtitle = stringResource(R.string.unlock_to_disable_admin),
                        fallbackIcon = Icons.Rounded.Shield,
                        onClose = ::cancel,
                        closeIcon = Icons.Rounded.Close,
                        closeContentDescription = stringResource(R.string.lock_admin_cancel_cd),
                        onSuccess = ::onVerified
                    )
                )
            }
        }
    }

    private fun onVerified() {
        DeviceAdmin().setPasswordVerified(this, true)
        appLockRepository().setAntiUninstallEnabled(false)
        Toast.makeText(this, R.string.password_verified_admin, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun cancel() {
        DeviceAdmin().setPasswordVerified(this, false)
        finish()
    }
}
