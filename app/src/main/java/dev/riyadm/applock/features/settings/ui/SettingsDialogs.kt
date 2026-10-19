package dev.riyadm.applock.features.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.riyadm.applock.R
import kotlinx.coroutines.launch

val UNLOCK_DURATION_OPTIONS = listOf(0, 1, 5, 15, 30, 60, Int.MAX_VALUE)

/** Durations above this many minutes mean "until the screen turns off". */
private const val UNTIL_SCREEN_OFF_THRESHOLD = 10_000

@Composable
fun unlockDurationLabel(minutes: Int): String = when {
    minutes <= 0 -> stringResource(R.string.settings_screen_unlock_duration_summary_immediate)
    minutes > UNTIL_SCREEN_OFF_THRESHOLD -> stringResource(R.string.settings_unlock_duration_until_screen_off)
    minutes == 60 -> stringResource(R.string.settings_screen_unlock_duration_dialog_option_hour)
    else -> pluralStringResource(R.plurals.settings_unlock_duration_minutes, minutes, minutes)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UnlockDurationSheet(
    currentDuration: Int,
    onDismiss: () -> Unit,
    description: String? = null,
    onSelect: (Int) -> Unit
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val selected = if (currentDuration > UNTIL_SCREEN_OFF_THRESHOLD) Int.MAX_VALUE
    else UNLOCK_DURATION_OPTIONS.minBy { kotlin.math.abs(it.toLong() - currentDuration) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.settings_screen_unlock_duration_dialog_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = description ?: stringResource(R.string.settings_unlock_duration_sheet_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                UNLOCK_DURATION_OPTIONS.forEachIndexed { index, duration ->
                    val isSelected = duration == selected
                    SegmentedListItem(
                        selected = isSelected,
                        onClick = {
                            onSelect(duration)
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                        },
                        shapes = ListItemDefaults.segmentedShapes(index, UNLOCK_DURATION_OPTIONS.size),
                        leadingContent = { RadioButton(selected = isSelected, onClick = null) },
                        colors = settingsItemColors(highlightSelected = true),
                        content = { Text(unlockDurationLabel(duration)) }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsInfoDialog(
    icon: ImageVector,
    title: String,
    paragraphs: List<String>,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)?,
    dismissText: String = stringResource(R.string.cancel_button)
) {
    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() ?: onConfirm() },
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                paragraphs.forEach { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = onDismiss?.let { { TextButton(onClick = it) { Text(dismissText) } } }
    )
}

@Composable
fun PermissionRequiredDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    SettingsInfoDialog(
        icon = Icons.Rounded.Shield,
        title = stringResource(R.string.settings_screen_permission_required_dialog_title),
        paragraphs = listOf(
            stringResource(R.string.settings_screen_permission_required_dialog_text_1),
            stringResource(R.string.settings_screen_permission_required_dialog_text_2)
        ),
        confirmText = stringResource(R.string.grant_permission_button),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun DeviceAdminDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    SettingsInfoDialog(
        icon = Icons.Rounded.AdminPanelSettings,
        title = stringResource(R.string.settings_screen_device_admin_dialog_title),
        paragraphs = listOf(
            stringResource(R.string.settings_screen_device_admin_dialog_text_1),
            stringResource(R.string.settings_screen_device_admin_dialog_text_2)
        ),
        confirmText = stringResource(R.string.enable_button),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun AccessibilityDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    SettingsInfoDialog(
        icon = Icons.Rounded.Accessibility,
        title = stringResource(R.string.settings_screen_accessibility_dialog_title),
        paragraphs = listOf(
            stringResource(R.string.settings_screen_accessibility_dialog_text_1),
            stringResource(R.string.settings_screen_accessibility_dialog_text_2),
            stringResource(R.string.settings_screen_accessibility_dialog_text_3)
        ),
        confirmText = stringResource(R.string.enable_button),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun RestoreCompleteDialog(lockedApps: Int, settings: Int, onDismiss: () -> Unit) {
    SettingsInfoDialog(
        icon = Icons.Rounded.Restore,
        title = stringResource(R.string.settings_backup_restore_complete_title),
        paragraphs = listOf(
            pluralStringResource(R.plurals.settings_backup_restored_apps, lockedApps, lockedApps),
            pluralStringResource(R.plurals.settings_backup_restored_settings, settings, settings),
            stringResource(R.string.settings_backup_restore_credentials_note)
        ),
        confirmText = stringResource(R.string.settings_ok_button),
        onConfirm = onDismiss,
        onDismiss = null
    )
}
