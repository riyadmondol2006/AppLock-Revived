package dev.riyadm.applock.core.navigation

sealed class Screen(val route: String) {
    object AppIntro : Screen("app_intro")
    object SetPassword : Screen("set_password")
    object SetPasswordPattern : Screen("set_password_pattern")
    object SetPasswordAlphanumeric : Screen("set_password_alphanumeric")
    object ChangePassword : Screen("change_password")
    object Main : Screen("main")
    object PasswordOverlay : Screen("password_overlay")
    object Settings : Screen("settings")
    object TriggerExclusions : Screen("trigger_exclusions")
    object AntiUninstall: Screen("anti_uninstall")
    object UnlockHistory : Screen("unlock_history")
    object Profiles : Screen("profiles")
    object TrustedNetworks : Screen("trusted_networks")
    object IntruderPhotos : Screen("intruder_photos")
}
