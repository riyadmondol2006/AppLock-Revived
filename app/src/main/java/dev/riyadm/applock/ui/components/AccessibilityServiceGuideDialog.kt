package dev.riyadm.applock.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.riyadm.applock.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccessibilityServiceGuideDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    ExpressivePermissionDialog(
        icon = Icons.Rounded.AccessibilityNew,
        iconShape = MaterialShapes.Cookie9Sided,
        title = stringResource(R.string.accessibility_guide_dialog_title),
        text = stringResource(R.string.accessibility_guide_dialog_text_1),
        supportingText = stringResource(R.string.ob_accessibility_guide_steps),
        confirmText = stringResource(R.string.accessibility_guide_open_settings_button),
        onConfirm = onOpenSettings,
        onDismiss = onDismiss,
        extraContent = { RestrictedSettingsHint() }
    )
}
