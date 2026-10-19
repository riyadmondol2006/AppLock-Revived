package dev.riyadm.applock.features.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiLock
import androidx.compose.material.icons.rounded.WifiPassword
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.riyadm.applock.R
import dev.riyadm.applock.core.trusted.TrustedNetworkMonitor
import dev.riyadm.applock.core.trusted.TrustedNetworkMonitor.Problem
import dev.riyadm.applock.core.trusted.TrustedNetworkSettings
import dev.riyadm.applock.ui.components.ExpressivePermissionDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings rows for trusted Wi-Fi and automation. Opening the trusted networks screen is left to
 * the caller so the section doesn't depend on navigation.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrustedNetworksSection(onOpenTrustedNetworks: () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) { TrustedNetworkSettings(context) }
    val status by TrustedNetworkMonitor.status.collectAsStateWithLifecycle()

    var enabled by remember { mutableStateOf(settings.isEnabled()) }
    var networkCount by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf<Problem?>(null) }
    var resumeCount by remember { mutableIntStateOf(0) }
    var showEnableDialog by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        enabled = settings.isEnabled()
        resumeCount++
        onPauseOrDispose { }
    }

    LaunchedEffect(resumeCount, status) {
        val (count, currentProblem) = withContext(Dispatchers.Default) {
            settings.getNetworks().size to TrustedNetworkMonitor.locationProblem(context)
        }
        networkCount = count
        problem = currentProblem ?: status.problem
    }

    if (showEnableDialog) {
        TrustedWifiEnableDialog(
            onConfirm = {
                showEnableDialog = false
                settings.setEnabled(true)
                enabled = true
                if (networkCount == 0 || problem != null) onOpenTrustedNetworks()
            },
            onDismiss = { showEnableDialog = false }
        )
    }

    Column {
        SettingsGroup(
            title = stringResource(R.string.trusted_section_title),
            footer = stringResource(R.string.trusted_section_footer) + "\n\n" +
                stringResource(R.string.followups_trusted_two_step_note)
        ) {
            item { shapes ->
                val currentProblem = problem
                SwitchSettingItem(
                    shapes = shapes,
                    icon = Icons.Rounded.WifiLock,
                    title = stringResource(R.string.trusted_wifi_title),
                    summary = when {
                        !enabled -> stringResource(R.string.trusted_wifi_desc_off)
                        currentProblem != null -> stringResource(
                            R.string.trusted_wifi_desc_problem,
                            stringResource(currentProblem.shortLabelRes())
                        )

                        else -> stringResource(R.string.trusted_wifi_desc_on)
                    },
                    checked = enabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            showEnableDialog = true
                        } else {
                            // Turning trust off only makes locking stricter: no confirmation.
                            settings.setEnabled(false)
                            enabled = false
                        }
                    },
                    accent = SettingsAccent.Tertiary
                )
            }
            item { shapes ->
                val count = if (networkCount == 0) {
                    stringResource(R.string.trusted_networks_summary_none)
                } else {
                    pluralStringResource(R.plurals.trusted_networks_summary_count, networkCount, networkCount)
                }
                val relaxedSsid = status.relaxedSsid
                ActionSettingItem(
                    shapes = shapes,
                    icon = Icons.Rounded.Wifi,
                    title = stringResource(R.string.trusted_networks_title),
                    summary = if (relaxedSsid != null) {
                        stringResource(R.string.trusted_networks_summary_relaxed, count, relaxedSsid)
                    } else count,
                    onClick = onOpenTrustedNetworks,
                    accent = SettingsAccent.Tertiary
                )
            }
        }
        AutomationSection()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TrustedWifiEnableDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ExpressivePermissionDialog(
        icon = Icons.Rounded.WifiPassword,
        title = stringResource(R.string.trusted_enable_dialog_title),
        text = stringResource(R.string.trusted_enable_dialog_text),
        supportingText = stringResource(R.string.trusted_enable_dialog_supporting),
        confirmText = stringResource(R.string.trusted_enable_dialog_confirm),
        iconShape = MaterialShapes.Cookie7Sided,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

internal fun Problem.shortLabelRes(): Int = when (this) {
    Problem.LOCATION_PERMISSION, Problem.PRECISE_LOCATION -> R.string.trusted_problem_short_location
    Problem.BACKGROUND_LOCATION -> R.string.trusted_problem_short_background
    Problem.LOCATION_OFF -> R.string.trusted_problem_short_location_off
    Problem.SSID_UNAVAILABLE -> R.string.trusted_problem_short_ssid
}
