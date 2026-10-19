package dev.riyadm.applock.features.settings.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.IntegrationInstructions
import androidx.compose.material.icons.rounded.Webhook
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.riyadm.applock.R
import dev.riyadm.applock.core.automation.AutomationHolders
import dev.riyadm.applock.core.automation.AutomationReceiver
import dev.riyadm.applock.core.automation.AutomationSettings
import dev.riyadm.applock.core.automation.AutomationSettings.Outcome
import dev.riyadm.applock.ui.components.ExpressivePermissionDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val APP_PACKAGE = "dev.riyadm.applock"

@Immutable
private data class HolderUi(val label: String, val legacy: Boolean)

/** [failed] when the holders couldn't be listed; commands are then refused. */
@Immutable
private data class HoldersState(val holders: List<HolderUi>, val failed: Boolean) {
    val legacy: List<HolderUi> get() = holders.filter { it.legacy }
}

@Suppress("DEPRECATION")
private fun loadHolders(context: Context): HoldersState {
    val holders = AutomationHolders.find(context) ?: return HoldersState(emptyList(), failed = true)
    val pm = context.packageManager
    return HoldersState(
        holders = holders.map { holder ->
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(holder.packageName, 0)).toString()
            } catch (_: Exception) {
                holder.packageName
            }
            HolderUi(label.ifBlank { holder.packageName }, holder.legacy)
        },
        failed = false
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AutomationSection() {
    val context = LocalContext.current
    val settings = remember(context) { AutomationSettings(context) }
    var enabled by remember { mutableStateOf(settings.isEnabled()) }
    var allowDisable by remember { mutableStateOf(settings.isDisableAllowed()) }
    var lastCommand by remember { mutableStateOf(settings.getLastCommand()) }
    var showDisableWarning by remember { mutableStateOf(false) }
    var showHowTo by remember { mutableStateOf(false) }
    val haptics = rememberSettingsHaptics()
    var holdersState by remember { mutableStateOf<HoldersState?>(null) }
    var holdersReload by remember { mutableIntStateOf(0) }

    // Permissions can change in system settings while the app is in the background.
    LifecycleResumeEffect(Unit) {
        holdersReload++
        onPauseOrDispose { }
    }
    LaunchedEffect(holdersReload) {
        holdersState = withContext(Dispatchers.IO) { loadHolders(context) }
    }

    DisposableEffect(settings) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in AutomationSettings.LAST_COMMAND_KEYS) {
                lastCommand = settings.getLastCommand()
            }
        }
        settings.registerListener(listener)
        onDispose { settings.unregisterListener(listener) }
    }

    // Keep the receiver component in step with the setting (e.g. after an app update).
    LaunchedEffect(settings) {
        withContext(Dispatchers.IO) { settings.syncReceiverState() }
    }

    if (showDisableWarning) {
        ExpressivePermissionDialog(
            icon = Icons.Rounded.GppMaybe,
            title = stringResource(R.string.automation_disable_dialog_title),
            text = stringResource(R.string.automation_disable_dialog_text),
            confirmText = stringResource(R.string.automation_disable_dialog_confirm),
            iconShape = MaterialShapes.SoftBurst,
            onConfirm = {
                showDisableWarning = false
                settings.setDisableAllowed(true)
                allowDisable = settings.isDisableAllowed()
                haptics.confirm()
            },
            onDismiss = { showDisableWarning = false }
        )
    }

    if (showHowTo) {
        AutomationHowToSheet(onDismiss = { showHowTo = false })
    }

    SettingsGroup(title = stringResource(R.string.automation_section_title)) {
        item { shapes ->
            SwitchSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.Webhook,
                title = stringResource(R.string.automation_enable_title),
                summary = stringResource(R.string.automation_enable_desc),
                checked = enabled,
                onCheckedChange = { checked ->
                    settings.setEnabled(checked)
                    enabled = settings.isEnabled()
                    allowDisable = settings.isDisableAllowed()
                },
                accent = SettingsAccent.Secondary
            )
        }
        item { shapes ->
            SwitchSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.GppMaybe,
                title = stringResource(R.string.automation_allow_disable_title),
                summary = stringResource(
                    if (enabled) R.string.automation_allow_disable_desc
                    else R.string.automation_allow_disable_needs_master
                ),
                checked = allowDisable,
                enabled = enabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        showDisableWarning = true
                    } else {
                        settings.setDisableAllowed(false)
                        allowDisable = false
                    }
                },
                accent = SettingsAccent.Error
            )
        }
        val holders = holdersState
        if (holders != null && (holders.failed || holders.legacy.isNotEmpty())) {
            item { shapes ->
                InfoSettingItem(
                    shapes = shapes,
                    icon = Icons.Rounded.Dangerous,
                    title = stringResource(R.string.followups_automation_blocked_title),
                    summary = if (holders.failed) {
                        stringResource(R.string.followups_automation_holders_error)
                    } else {
                        stringResource(
                            R.string.followups_automation_legacy_warning,
                            holders.legacy.joinToString(", ") { it.label }
                        )
                    },
                    accent = SettingsAccent.Error
                )
            }
        }
        if (holders != null && !holders.failed) {
            item { shapes ->
                val resources = LocalResources.current
                InfoSettingItem(
                    shapes = shapes,
                    icon = Icons.Rounded.Apps,
                    title = stringResource(R.string.followups_automation_holders_title),
                    summary = if (holders.holders.isEmpty()) {
                        stringResource(R.string.followups_automation_holders_none)
                    } else {
                        holders.holders.joinToString(", ") {
                            if (it.legacy) {
                                resources.getString(R.string.followups_automation_holder_legacy, it.label)
                            } else {
                                it.label
                            }
                        }
                    }
                )
            }
        }
        item { shapes ->
            InfoSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.History,
                title = stringResource(R.string.automation_last_command_title),
                summary = lastCommand?.let { formatLastCommand(context, it) }
                    ?: stringResource(R.string.automation_last_command_none)
            )
        }
        item { shapes ->
            ActionSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.IntegrationInstructions,
                title = stringResource(R.string.automation_howto_title),
                summary = stringResource(R.string.automation_howto_desc),
                onClick = { showHowTo = true },
                accent = SettingsAccent.Secondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InfoSettingItem(
    shapes: ListItemShapes,
    icon: ImageVector,
    title: String,
    summary: String,
    accent: SettingsAccent = SettingsAccent.Secondary
) {
    SegmentedListItem(
        shapes = shapes,
        leadingContent = { SettingsIcon(icon, accent) },
        supportingContent = { Text(summary) },
        colors = settingsItemColors(),
        content = { Text(title) }
    )
}

@Composable
private fun formatLastCommand(context: Context, command: AutomationSettings.LastCommand): String {
    val action = stringResource(
        when (command.action) {
            AutomationReceiver.ACTION_LOCK_ALL_NOW -> R.string.automation_action_lock_all
            AutomationReceiver.ACTION_ENABLE_PROTECTION -> R.string.automation_action_enable
            else -> R.string.automation_action_disable
        }
    )
    val outcome = stringResource(
        when (command.outcome) {
            Outcome.APPLIED -> R.string.automation_outcome_applied
            Outcome.IGNORED_DISABLED -> R.string.automation_outcome_disabled
            Outcome.IGNORED_NOT_ALLOWED -> R.string.automation_outcome_not_allowed
            Outcome.IGNORED_INVALID -> R.string.automation_outcome_invalid
            Outcome.IGNORED_LEGACY_HOLDER -> R.string.followups_automation_outcome_legacy
        }
    )
    val time = remember(command.timestamp) {
        DateUtils.formatDateTime(
            context,
            command.timestamp,
            DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
        )
    }
    val sender = command.sender
    return if (sender != null) {
        stringResource(R.string.automation_last_command_from, action, outcome, time, sender)
    } else {
        stringResource(R.string.automation_last_command_value, action, outcome, time)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AutomationHowToSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberSettingsHaptics()
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    val permissionLabel = stringResource(R.string.automation_howto_permission)
    val packageLabel = stringResource(R.string.automation_howto_package)
    val actionsLabel = stringResource(R.string.automation_howto_actions)
    val entries = remember(permissionLabel, packageLabel) {
        listOf(
            permissionLabel to AutomationReceiver.PERMISSION,
            packageLabel to APP_PACKAGE
        )
    }
    val actions = remember {
        listOf(
            R.string.automation_action_lock_all to AutomationReceiver.ACTION_LOCK_ALL_NOW,
            R.string.automation_action_enable to AutomationReceiver.ACTION_ENABLE_PROTECTION,
            R.string.automation_action_disable to AutomationReceiver.ACTION_DISABLE_PROTECTION
        )
    }
    val allText = remember(permissionLabel, packageLabel, actionsLabel) {
        buildString {
            appendLine("$permissionLabel: ${AutomationReceiver.PERMISSION}")
            appendLine("$packageLabel: $APP_PACKAGE")
            appendLine("$actionsLabel:")
            AutomationReceiver.ACTIONS.forEach { appendLine("  $it") }
        }
    }
    val copy: (String) -> Unit = { text ->
        copyToClipboard(context, text)
        haptics.confirm()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.automation_howto_sheet_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.automation_howto_sheet_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                entries.forEachIndexed { index, (label, value) ->
                    CopyRow(
                        shapes = ListItemDefaults.segmentedShapes(index, entries.size),
                        label = label,
                        value = value,
                        onCopy = copy
                    )
                }
            }
            SettingsSectionHeader(actionsLabel)
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                actions.forEachIndexed { index, (labelRes, value) ->
                    CopyRow(
                        shapes = ListItemDefaults.segmentedShapes(index, actions.size),
                        label = stringResource(labelRes),
                        value = value,
                        onCopy = copy
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.automation_howto_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { copy(allText) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonDefaults.MediumContainerHeight)
            ) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Text(
                    stringResource(R.string.automation_howto_copy_all),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing)
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CopyRow(shapes: ListItemShapes, label: String, value: String, onCopy: (String) -> Unit) {
    SegmentedListItem(
        onClick = { onCopy(value) },
        shapes = shapes,
        overlineContent = { Text(label) },
        trailingContent = {
            IconButton(onClick = { onCopy(value) }, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(R.string.automation_howto_copy_cd, label)
                )
            }
        },
        colors = settingsItemColors(),
        content = { Text(value, style = MaterialTheme.typography.bodyMedium) }
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(context.getString(R.string.automation_howto_clipboard_label), text)
    clipboard.setPrimaryClip(clip)
    // Android 13+ shows its own confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.automation_howto_copied, Toast.LENGTH_SHORT).show()
    }
}
