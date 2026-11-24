package dev.riyadm.applock.features.settings.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import dev.riyadm.applock.R

/**
 * Top-level groups on the settings hub. Each one opens its own page; what a page contains is
 * decided in settingsCategoryItems (SettingsScreen.kt), where adding a section is one line.
 */
enum class SettingsCategory(
    val id: String,
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val icon: ImageVector,
    val accent: SettingsAccent = SettingsAccent.Primary
) {
    Appearance("appearance", R.string.settings_cat_appearance_title, R.string.settings_cat_appearance_summary, Icons.Rounded.Palette),
    LockScreen("lock", R.string.settings_cat_lock_title, R.string.settings_cat_lock_summary, Icons.Rounded.Lock),
    Protection("protection", R.string.settings_cat_protection_title, R.string.settings_cat_protection_summary, Icons.Rounded.Shield, SettingsAccent.Secondary),
    Automation("automation", R.string.settings_cat_automation_title, R.string.settings_cat_automation_summary, Icons.Rounded.Wifi),
    Privacy("privacy", R.string.settings_cat_privacy_title, R.string.settings_cat_privacy_summary, Icons.Rounded.VisibilityOff, SettingsAccent.Tertiary),
    Backup("backup", R.string.settings_cat_backup_title, R.string.settings_cat_backup_summary, Icons.Rounded.Backup, SettingsAccent.Secondary),
    System("system", R.string.settings_cat_system_title, R.string.settings_cat_system_summary, Icons.Rounded.Tune, SettingsAccent.Secondary),
    About("about", R.string.settings_cat_about_title, R.string.settings_cat_about_summary, Icons.Rounded.Info, SettingsAccent.Tertiary);

    companion object {
        fun fromId(id: String?): SettingsCategory? = entries.firstOrNull { it.id == id }
    }
}
