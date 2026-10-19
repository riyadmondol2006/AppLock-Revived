package dev.riyadm.applock.features.applist.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.RemoveModerator
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.riyadm.applock.R
import dev.riyadm.applock.core.ui.ExpressiveIconBadge
import dev.riyadm.applock.features.applist.ui.ProtectionStatus.State

private class StatusStyle(
    val container: Color,
    val content: Color,
    val badgeContainer: Color,
    val badgeContent: Color,
    val polygon: RoundedPolygon,
    val icon: ImageVector,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun statusStyle(state: State): StatusStyle {
    val colors = MaterialTheme.colorScheme
    return when (state) {
        State.ACTIVE -> StatusStyle(
            colors.primaryContainer, colors.onPrimaryContainer,
            colors.primary, colors.onPrimary,
            MaterialShapes.Cookie9Sided, Icons.Rounded.Shield
        )

        State.PAUSED -> StatusStyle(
            colors.surfaceContainerHigh, colors.onSurface,
            colors.secondaryContainer, colors.onSecondaryContainer,
            MaterialShapes.Pentagon, Icons.Rounded.RemoveModerator
        )

        State.NEEDS_SETUP -> StatusStyle(
            colors.errorContainer, colors.onErrorContainer,
            colors.error, colors.onError,
            MaterialShapes.SoftBurst, Icons.Rounded.GppMaybe
        )

        State.SERVICE_STOPPED -> StatusStyle(
            colors.tertiaryContainer, colors.onTertiaryContainer,
            colors.tertiary, colors.onTertiary,
            MaterialShapes.Clover4Leaf, Icons.Rounded.RestartAlt
        )
    }
}

/**
 * Hero card on the home screen summarising whether apps are actually being protected.
 * When something is wrong the whole card becomes a button that starts the matching fix.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProtectionStatusCard(
    status: ProtectionStatus,
    lockedCount: Int,
    onProtectionToggle: (Boolean) -> Unit,
    onFix: () -> Unit,
    onFixAntiUninstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = status.state
    val style = statusStyle(state)
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(style.container, motion.defaultEffectsSpec(), label = "container")
    val content by animateColorAsState(style.content, motion.defaultEffectsSpec(), label = "content")
    val badgeScale by animateFloatAsState(
        targetValue = if (state == State.ACTIVE) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f),
        label = "badgeScale"
    )

    val backendLabel = stringResource(status.backend.labelRes())
    val lockedText = pluralStringResource(R.plurals.applist_locked_count, lockedCount, lockedCount)
    val title = when (state) {
        State.ACTIVE -> stringResource(R.string.applist_status_active_title)
        State.PAUSED -> stringResource(R.string.applist_status_paused_title)
        State.NEEDS_SETUP -> stringResource(R.string.applist_status_setup_title)
        State.SERVICE_STOPPED -> stringResource(R.string.applist_status_stopped_title)
    }
    val description = when (state) {
        State.ACTIVE -> stringResource(R.string.applist_status_active_desc, lockedText, backendLabel)
        State.PAUSED -> stringResource(R.string.applist_status_paused_desc)
        State.NEEDS_SETUP -> stringResource(
            R.string.applist_status_setup_desc,
            stringResource(status.blockingPermission!!.titleResId)
        )

        State.SERVICE_STOPPED -> stringResource(R.string.applist_status_stopped_desc, backendLabel)
    }

    val needsAction = state != State.ACTIVE
    val cardContent: @Composable () -> Unit = {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpressiveIconBadge(
                    polygon = style.polygon,
                    icon = style.icon,
                    containerColor = style.badgeContainer,
                    contentColor = style.badgeContent,
                    size = 64.dp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = badgeScale
                        scaleY = badgeScale
                    }
                )
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = content
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = content.copy(alpha = 0.85f)
                    )
                }
                if (state != State.NEEDS_SETUP) {
                    Spacer(Modifier.width(12.dp))
                    val switchDescription = stringResource(R.string.applist_status_switch_cd)
                    Switch(
                        checked = status.protectEnabled,
                        onCheckedChange = onProtectionToggle,
                        modifier = Modifier.semantics { contentDescription = switchDescription }
                    )
                }
            }

            AnimatedVisibility(
                visible = status.antiUninstallPermission != null && state != State.NEEDS_SETUP,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val permission = status.antiUninstallPermission
                if (permission != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(
                                    R.string.applist_status_secondary_issue,
                                    stringResource(permission.titleResId)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(onClick = onFixAntiUninstall) {
                                Text(stringResource(R.string.applist_status_fix))
                            }
                        }
                    }
                }
            }
        }
    }

    if (needsAction) {
        Surface(
            onClick = onFix,
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = container,
            contentColor = content,
            content = cardContent
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = container,
            contentColor = content,
            content = cardContent
        )
    }
}
