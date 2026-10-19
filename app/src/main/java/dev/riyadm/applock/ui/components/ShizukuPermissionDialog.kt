package dev.riyadm.applock.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.riyadm.applock.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShizukuPermissionDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    ExpressivePermissionDialog(
        icon = Icons.Rounded.AutoAwesome,
        iconShape = MaterialShapes.SoftBurst,
        title = stringResource(R.string.shizuku_permission_dialog_title),
        text = stringResource(R.string.shizuku_permission_dialog_text_1),
        supportingText = stringResource(R.string.dialog_text_press_grant_permission_to_open_settings),
        confirmText = stringResource(R.string.grant_permission_button),
        onConfirm = onOpenSettings,
        onDismiss = onDismiss
    )
}
