package dev.riyadm.applock.core.navigation

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.riyadm.applock.AppLockApplication
import dev.riyadm.applock.R
import dev.riyadm.applock.core.utils.LogUtils
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.PreferencesRepository
import dev.riyadm.applock.data.repository.UnlockLogRepository
import dev.riyadm.applock.features.antiuninstall.ui.AntiUninstallScreen
import dev.riyadm.applock.features.appintro.ui.AppIntroScreen
import dev.riyadm.applock.features.applist.ui.MainScreen
import dev.riyadm.applock.features.history.ui.UnlockHistoryScreen
import dev.riyadm.applock.features.intruder.ui.IntruderPhotosScreen
import dev.riyadm.applock.features.lockscreen.ui.LockScreen
import dev.riyadm.applock.features.lockscreen.ui.LockScreenSpec
import dev.riyadm.applock.features.lockscreen.ui.buildBiometricPromptInfo
import dev.riyadm.applock.features.lockscreen.ui.canUseBiometrics
import dev.riyadm.applock.features.setpassword.ui.AlphanumericSetPasswordScreen
import dev.riyadm.applock.features.setpassword.ui.PatternSetPasswordScreen
import dev.riyadm.applock.features.setpassword.ui.SetPasswordScreen
import dev.riyadm.applock.features.settings.ui.SettingsScreen
import dev.riyadm.applock.features.settings.ui.TrustedNetworksScreen
import dev.riyadm.applock.features.triggerexclusions.ui.TriggerExclusionsScreen
import dev.riyadm.applock.ui.components.SecureWindowEffect
import dev.riyadm.applock.ui.motion.ExpressiveMotion
import kotlin.concurrent.thread

@Composable
fun AppNavHost(navController: NavHostController, startDestination: String) {
    val application = LocalContext.current.applicationContext as AppLockApplication

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = ExpressiveMotion.enter,
        exitTransition = ExpressiveMotion.exit,
        popEnterTransition = ExpressiveMotion.popEnter,
        popExitTransition = ExpressiveMotion.popExit,
    ) {
        composable(Screen.AppIntro.route) {
            AppIntroScreen(navController)
        }

        composable(Screen.SetPassword.route) {
            SetPasswordScreen(navController, isFirstTimeSetup = true)
        }

        composable(Screen.ChangePassword.route) {
            when (application.appLockRepository.getLockType()) {
                PreferencesRepository.LOCK_TYPE_PATTERN -> {
                    PatternSetPasswordScreen(navController, false)
                }
                PreferencesRepository.LOCK_TYPE_PASSWORD -> {
                    AlphanumericSetPasswordScreen(navController, false)
                }
                else -> {
                    SetPasswordScreen(navController, isFirstTimeSetup = false)
                }
            }
        }

        composable(Screen.SetPasswordPattern.route) {
            PatternSetPasswordScreen(navController, isFirstTimeSetup = true)
        }

        composable(Screen.SetPasswordAlphanumeric.route) {
            AlphanumericSetPasswordScreen(navController, isFirstTimeSetup = true)
        }

        composable(Screen.Main.route) {
            MainScreen(navController)
        }

        composable(Screen.PasswordOverlay.route) {
            InAppLockScreen(navController)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(navController)
        }

        composable(Screen.TriggerExclusions.route) {
            TriggerExclusionsScreen(navController)
        }

        composable(Screen.AntiUninstall.route) {
            AntiUninstallScreen(navController)
        }

        composable(Screen.UnlockHistory.route) {
            UnlockHistoryScreen(navController)
        }

        composable(Screen.TrustedNetworks.route) {
            TrustedNetworksScreen(navController)
        }

        composable(Screen.IntruderPhotos.route) {
            IntruderPhotosScreen(navController)
        }
    }
}

fun NavController.finishPasswordSetup(isFirstTimeSetup: Boolean) {
    if (isFirstTimeSetup) {
        navigate(Screen.Main.route) {
            popUpTo(Screen.AppIntro.route) {
                inclusive = true
            }
            launchSingleTop = true
        }
    } else {
        navigate(Screen.Main.route) {
            popUpTo(Screen.Main.route) {
                inclusive = false
            }
            launchSingleTop = true
        }
    }
}

@Composable
private fun InAppLockScreen(navController: NavHostController) {
    val activity = LocalActivity.current as FragmentActivity
    val repository = remember { activity.appLockRepository() }
    val biometricUsable = remember {
        repository.isBiometricAuthEnabled() && activity.canUseBiometrics()
    }
    var autoPrompted by rememberSaveable { mutableStateOf(false) }
    val onSuccess = remember(navController) { { handleAuthenticationSuccess(navController) } }
    val onBiometric = remember(activity) {
        { showInAppBiometricPrompt(activity, repository, onSuccess) }
    }

    // Back on the lock screen leaves the app; it must never pop to the protected screens.
    BackHandler { activity.finish() }

    SecureWindowEffect()

    LaunchedEffect(Unit) {
        if (!autoPrompted && biometricUsable && repository.isBiometricAutoPromptEnabled()) {
            autoPrompted = true
            onBiometric()
        }
    }

    LockScreen(
        spec = LockScreenSpec(
            packageName = activity.packageName,
            title = stringResource(R.string.app_name),
            showBiometric = biometricUsable,
            onBiometric = onBiometric,
            onSuccess = onSuccess
        )
    )
}

private fun showInAppBiometricPrompt(
    activity: FragmentActivity,
    repository: AppLockRepository,
    onSuccess: () -> Unit
) {
    try {
        val biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Log.w(TAG, "Biometric authentication error: $errString ($errorCode)")
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    LogUtils.d(TAG, "Biometric authentication succeeded")
                    val pkg = activity.packageName
                    thread(name = "unlock-attempt") {
                        repository.recordUnlockAttempt(pkg, true, UnlockLogRepository.Method.BIOMETRIC)
                    }
                    onSuccess()
                }
            }
        )
        biometricPrompt.authenticate(
            buildBiometricPromptInfo(activity, activity.getString(R.string.app_name))
        )
    } catch (e: Exception) {
        Log.e(TAG, "Error during biometric authentication", e)
    }
}

private fun handleAuthenticationSuccess(navController: NavHostController) {
    // Biometric and credential success can race; only leave the lock screen once.
    if (navController.currentDestination?.route != Screen.PasswordOverlay.route) return
    if (navController.previousBackStackEntry != null) {
        navController.popBackStack()
    } else {
        navigateToMain(navController)
    }
}

private fun navigateToMain(navController: NavHostController) {
    navController.navigate(Screen.Main.route) {
        popUpTo(Screen.PasswordOverlay.route) { inclusive = true }
    }
}

private const val TAG = "AppNavHost"
