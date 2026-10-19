package dev.riyadm.applock.features.settings.ui

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.riyadm.applock.R
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.PreferencesRepository
import dev.riyadm.applock.ui.theme.AppearanceSettings
import dev.riyadm.applock.ui.theme.isAppInDarkTheme

@Composable
fun AppearanceSection(repository: AppLockRepository) {
    val themeMode = AppearanceSettings.themeMode
    val isDark = isAppInDarkTheme()
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val themeOptions = listOf(
        ChoiceOption(PreferencesRepository.THEME_SYSTEM, stringResource(R.string.settings_theme_system)),
        ChoiceOption(PreferencesRepository.THEME_LIGHT, stringResource(R.string.settings_theme_light)),
        ChoiceOption(PreferencesRepository.THEME_DARK, stringResource(R.string.settings_theme_dark))
    )

    SettingsGroup(title = stringResource(R.string.settings_section_appearance)) {
        item { shapes ->
            ChoiceSettingItem(
                shapes = shapes,
                icon = when (themeMode) {
                    PreferencesRepository.THEME_LIGHT -> Icons.Rounded.LightMode
                    PreferencesRepository.THEME_DARK -> Icons.Rounded.DarkMode
                    else -> Icons.Rounded.BrightnessAuto
                },
                title = stringResource(R.string.settings_theme_title),
                summary = null,
                options = themeOptions,
                selected = themeMode,
                onSelect = { AppearanceSettings.update(repository, themeMode = it) },
                accent = SettingsAccent.Tertiary
            )
        }
        if (dynamicColorSupported) {
            item { shapes ->
                SwitchSettingItem(
                    shapes = shapes,
                    icon = Icons.Rounded.Palette,
                    title = stringResource(R.string.settings_dynamic_color_title),
                    summary = stringResource(R.string.settings_dynamic_color_desc),
                    checked = AppearanceSettings.dynamicColor,
                    onCheckedChange = { AppearanceSettings.update(repository, dynamicColor = it) },
                    accent = SettingsAccent.Tertiary
                )
            }
        }
        item { shapes ->
            SwitchSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.Contrast,
                title = stringResource(R.string.settings_amoled_title),
                summary = stringResource(
                    if (isDark) R.string.settings_amoled_desc
                    else R.string.settings_amoled_desc_needs_dark
                ),
                checked = AppearanceSettings.amoledBlack && isDark,
                onCheckedChange = { AppearanceSettings.update(repository, amoledBlack = it) },
                enabled = isDark,
                accent = SettingsAccent.Tertiary
            )
        }
    }
}
