package dev.riyadm.applock

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.riyadm.applock.core.navigation.AppNavHost
import dev.riyadm.applock.core.navigation.NavigationManager
import dev.riyadm.applock.core.navigation.Screen
import dev.riyadm.applock.core.update.UpdateChecker
import dev.riyadm.applock.core.update.UpdateInfo
import dev.riyadm.applock.ui.components.UpdateDialog
import dev.riyadm.applock.ui.theme.AppLockTheme

class MainActivity : FragmentActivity() {

    private lateinit var navigationManager: NavigationManager
    private val openProfilesRequested = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) readOpenProfilesExtra(intent)

        navigationManager = NavigationManager(this)

        setContent {
            AppLockTheme {
                val navController = rememberNavController()
                val startDestination = navigationManager.determineStartDestination()

                AppNavHost(
                    navController = navController,
                    startDestination = startDestination
                )

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    handleOnResume(navController)
                }

                UpdateCheckHost(navController)
                OpenProfilesHost(navController)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readOpenProfilesExtra(intent)
    }

    private fun readOpenProfilesExtra(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PROFILES, false) == true) {
            intent.removeExtra(EXTRA_OPEN_PROFILES)
            openProfilesRequested.value = true
        }
    }

    /**
     * Opens the profile switcher requested by the Quick Settings tile once the user is past the
     * in-app lock screen. Every resume puts the lock screen on top, so this never skips it.
     */
    @Composable
    private fun OpenProfilesHost(navController: NavHostController) {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val route = backStackEntry?.destination?.route
        val requested by openProfilesRequested
        LaunchedEffect(requested, route) {
            // Re-read the destination right before navigating: the lock screen may have been
            // pushed since this effect was scheduled, and nothing may ever be put on top of it.
            val current = navController.currentDestination?.route
            if (!requested || current == null) return@LaunchedEffect
            if (current == Screen.PasswordOverlay.route || current in INITIAL_SETUP_ROUTES) return@LaunchedEffect
            openProfilesRequested.value = false
            if (current != Screen.Profiles.route) {
                navController.navigate(Screen.Profiles.route) { launchSingleTop = true }
            }
        }
    }

    @Composable
    private fun UpdateCheckHost(navController: NavHostController) {
        val context = LocalContext.current
        val backStackEntry by navController.currentBackStackEntryAsState()
        val canShowUpdate = backStackEntry?.destination?.route in UPDATE_DIALOG_ROUTES

        var autoCheckDone by rememberSaveable { mutableStateOf(false) }
        var pendingUpdate by rememberSaveable(stateSaver = UpdateInfo.Saver) {
            mutableStateOf<UpdateInfo?>(null)
        }

        LaunchedEffect(canShowUpdate) {
            if (canShowUpdate && !autoCheckDone) {
                autoCheckDone = true
                pendingUpdate = UpdateChecker.check(context)
            }
        }

        val update = pendingUpdate
        if (update != null && canShowUpdate) {
            UpdateDialog(
                updateInfo = update,
                onDismiss = { pendingUpdate = null }
            )
        }
    }

    private fun handleOnResume(navController: NavHostController) {
        val currentRoute = navController.currentDestination?.route ?: return

        if (currentRoute == Screen.PasswordOverlay.route) return

        val isInitialSetup = navigationManager.shouldSkipPasswordCheck(currentRoute) ||
                currentRoute in INITIAL_SETUP_ROUTES
        if (isInitialSetup && !isCredentialSet()) return

        navController.navigate(Screen.PasswordOverlay.route) {
            launchSingleTop = true
        }
    }

    private fun isCredentialSet(): Boolean {
        val repository = (applicationContext as AppLockApplication).appLockRepository
        return repository.getPassword() != null || repository.getPattern() != null
    }

    companion object {
        const val EXTRA_OPEN_PROFILES = "dev.riyadm.applock.extra.OPEN_PROFILES"

        private val INITIAL_SETUP_ROUTES = setOf(
            Screen.AppIntro.route,
            Screen.SetPassword.route,
            Screen.SetPasswordPattern.route,
            Screen.SetPasswordAlphanumeric.route
        )

        private val UPDATE_DIALOG_ROUTES = setOf(
            Screen.Main.route,
            Screen.Settings.route
        )
    }
}
