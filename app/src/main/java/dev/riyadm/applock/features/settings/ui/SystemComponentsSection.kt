package dev.riyadm.applock.features.settings.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.riyadm.applock.R
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.BackendImplementation

/**
 * "Lock system components": opt-in locks for parts of Android that aren't apps in the app list.
 * The photo / file pickers work with every detection method; the quick settings guard needs the
 * accessibility one, the only one that can see the panel.
 */
@Composable
fun SystemComponentsSection(repository: AppLockRepository) {
    var lockPickers by remember { mutableStateOf(repository.isLockSystemPickersEnabled()) }
    var lockQuickSettings by remember { mutableStateOf(repository.isLockQuickSettingsEnabled()) }
    var backend by remember { mutableStateOf(repository.getBackendImplementation()) }

    // The detection method is changed further down this screen; re-read it when coming back.
    LifecycleResumeEffect(Unit) {
        backend = repository.getBackendImplementation()
        onPauseOrDispose { }
    }

    val quickSettingsAvailable = backend == BackendImplementation.ACCESSIBILITY

    SettingsGroup(
        title = stringResource(R.string.system_lock_section_title),
        footer = stringResource(R.string.system_lock_footer)
    ) {
        item { shapes ->
            SwitchSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.PermMedia,
                title = stringResource(R.string.system_lock_pickers_title),
                summary = stringResource(R.string.system_lock_pickers_desc),
                checked = lockPickers,
                onCheckedChange = {
                    lockPickers = it
                    repository.setLockSystemPickersEnabled(it)
                },
                accent = SettingsAccent.Tertiary
            )
        }
        item { shapes ->
            SwitchSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.SettingsSuggest,
                title = stringResource(R.string.system_lock_quick_settings_title),
                summary = stringResource(
                    if (quickSettingsAvailable) R.string.system_lock_quick_settings_desc
                    else R.string.system_lock_quick_settings_desc_unavailable
                ),
                checked = lockQuickSettings,
                onCheckedChange = {
                    lockQuickSettings = it
                    repository.setLockQuickSettingsEnabled(it)
                },
                // Turning it off is always possible; turning it on only makes sense where it works.
                enabled = quickSettingsAvailable || lockQuickSettings,
                accent = SettingsAccent.Tertiary
            )
        }
    }
}
