package dev.riyadm.applock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.graphics.shapes.RoundedPolygon
import dev.riyadm.applock.R
import dev.riyadm.applock.core.utils.isRestrictedSettingsGuidanceNeeded
import dev.riyadm.applock.core.utils.openAppInfoSettings

/**
 * An icon sitting on an expressive Material shape.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveShapeIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    shape: RoundedPolygon = MaterialShapes.Cookie9Sided,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 64.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(containerColor, shape.toShape()),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.45f)
        )
    }
}

/**
 * Material 3 Expressive permission dialog with a shaped hero icon.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressivePermissionDialog(
    icon: ImageVector,
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String = stringResource(R.string.cancel_button),
    iconShape: RoundedPolygon = MaterialShapes.Cookie9Sided,
    supportingText: String? = null,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        icon = {
            ExpressiveShapeIcon(icon = icon, shape = iconShape)
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                extraContent?.invoke(this)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, shapes = ButtonDefaults.shapes()) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(dismissText)
            }
        }
    )
}

/**
 * Explains how to allow "restricted settings" on Android 13+ so a sideloaded app can enable
 * its accessibility service. Renders nothing on older Android versions.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RestrictedSettingsHint(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh
) {
    if (!isRestrictedSettingsGuidanceNeeded()) return
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExpressiveShapeIcon(
                    icon = Icons.Rounded.Info,
                    shape = MaterialShapes.Cookie4Sided,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    size = 36.dp
                )
                Text(
                    text = stringResource(R.string.ob_restricted_settings_title),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(R.string.ob_restricted_settings_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.ob_restricted_settings_steps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = { openAppInfoSettings(context) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Text(
                    stringResource(R.string.intro_open_app_info),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing)
                )
            }
        }
    }
}
