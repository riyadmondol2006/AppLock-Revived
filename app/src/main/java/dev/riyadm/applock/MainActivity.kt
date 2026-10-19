package dev.riyadm.applock

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.compose.rememberNavController
import dev.riyadm.applock.core.navigation.AppNavHost
import dev.riyadm.applock.core.navigation.NavigationManager
import dev.riyadm.applock.core.navigation.Screen
import dev.riyadm.applock.ui.theme.AppLockTheme

class MainActivity : FragmentActivity() {

    private lateinit var navigationManager: NavigationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
            }
        }
    }

    private fun handleOnResume(navController: androidx.navigation.NavHostController) {
        val currentRoute = navController.currentDestination?.route

        if (navigationManager.shouldSkipPasswordCheck(currentRoute)) {
            return
        }

        if (currentRoute != Screen.PasswordOverlay.route) {
            navController.navigate(Screen.PasswordOverlay.route)
        }
    }
}


