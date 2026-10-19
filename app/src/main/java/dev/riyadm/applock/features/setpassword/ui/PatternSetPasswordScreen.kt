package dev.riyadm.applock.features.setpassword.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavController

@Composable
fun PatternSetPasswordScreen(
    navController: NavController,
    isFirstTimeSetup: Boolean
) {
    CredentialSetupScreen(navController, isFirstTimeSetup, LockKind.PATTERN)
}
