package dev.riyadm.applock.features.settings.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Pattern
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.riyadm.applock.R
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.data.repository.HardeningPreferences
import dev.riyadm.applock.features.hardening.LockedAppCloser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

/** Closing locked apps on screen off, unlock history retention and pattern size. */
@Composable
fun HardeningSection(unlockHistoryEnabled: Boolean) {
    val context = LocalContext.current
    val prefs = remember(context) { HardeningPreferences(context.applicationContext) }
    var closeApps by remember { mutableStateOf(prefs.closeLockedAppsOnScreenOff) }
    var retentionDays by remember { mutableIntStateOf(prefs.unlockLogRetentionDays) }
    var patternSize by remember { mutableStateOf(prefs.patternSize) }

    // Shizuku may be started or granted in another app; re-check when the screen comes back.
    var resumeCount by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeCount++
        onPauseOrDispose { }
    }
    val closeMethod by produceState<LockedAppCloser.Method?>(null, resumeCount) {
        value = withContext(Dispatchers.IO) { LockedAppCloser.currentMethod() }
    }

    val retentionOptions = HardeningPreferences.RETENTION_OPTIONS.map {
        ChoiceOption(it, pluralStringResource(R.plurals.hardening_retention_days, it, it))
    }
    val patternOptions = listOf(
        ChoiceOption(HardeningPreferences.PATTERN_SMALL, stringResource(R.string.hardening_pattern_size_small)),
        ChoiceOption(HardeningPreferences.PATTERN_MEDIUM, stringResource(R.string.hardening_pattern_size_medium)),
        ChoiceOption(HardeningPreferences.PATTERN_LARGE, stringResource(R.string.hardening_pattern_size_large)),
    )

    SettingsGroup(title = stringResource(R.string.hardening_section_title)) {
        item { shapes ->
            SwitchSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.CleaningServices,
                title = stringResource(R.string.hardening_close_apps_title),
                summary = stringResource(
                    when (closeMethod) {
                        LockedAppCloser.Method.FORCE_STOP -> R.string.hardening_close_apps_desc_shizuku
                        LockedAppCloser.Method.UNAVAILABLE -> R.string.hardening_close_apps_desc_unavailable
                        else -> R.string.hardening_close_apps_desc_background
                    }
                ),
                checked = closeApps,
                onCheckedChange = {
                    closeApps = it
                    prefs.closeLockedAppsOnScreenOff = it
                },
                accent = SettingsAccent.Secondary
            )
        }
        item { shapes ->
            ChoiceSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.AutoDelete,
                title = stringResource(R.string.hardening_history_retention_title),
                summary = stringResource(
                    if (unlockHistoryEnabled) R.string.hardening_history_retention_desc
                    else R.string.hardening_history_retention_desc_disabled
                ),
                options = retentionOptions,
                selected = retentionDays,
                onSelect = { days ->
                    retentionDays = days
                    prefs.unlockLogRetentionDays = days
                    val app = context.applicationContext
                    thread(name = "unlock-log-prune") { app.appLockRepository().unlockLog.prune() }
                },
                enabled = unlockHistoryEnabled,
                accent = SettingsAccent.Tertiary
            )
        }
        item { shapes ->
            ChoiceSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.Pattern,
                title = stringResource(R.string.hardening_pattern_size_title),
                summary = stringResource(R.string.hardening_pattern_size_desc),
                options = patternOptions,
                selected = patternSize,
                onSelect = {
                    patternSize = it
                    prefs.patternSize = it
                }
            )
        }
    }
}
