package dev.riyadm.applock.features.applist.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RemoveModerator
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.riyadm.applock.R
import dev.riyadm.applock.core.ui.MorphingIconBadge
import dev.riyadm.applock.features.applist.ui.ProtectionStatus.State
import dev.riyadm.applock.ui.motion.pressScale

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
 * The badge morphs between expressive shapes as the state changes; the container colour is
 * animated in the draw phase only.
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
    val cardShape = MaterialTheme.shapes.extraLarge
    val interactionSource = remember { MutableInteractionSource() }
    val needsAction = state != State.ACTIVE

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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (needsAction) Modifier.pressScale(interactionSource, 0.97f) else Modifier)
            .clip(cardShape)
            .drawBehind { drawRect(container) }
            .then(
                if (needsAction) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClick = onFix
                    )
                } else Modifier
            )
            .padding(20.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides style.content) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MorphingIconBadge(
                    polygon = style.polygon,
                    icon = style.icon,
                    containerColor = style.badgeContainer,
                    contentColor = style.badgeContent,
                    size = 64.dp,
                    rotate = false
                )
                Spacer(Modifier.width(16.dp))
                AnimatedContent(
                    targetState = title to description,
                    transitionSpec = {
                        fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
                    },
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.weight(1f),
                    label = "statusText"
                ) { (titleText, descriptionText) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleLargeEmphasized
                        )
                        Text(
                            text = descriptionText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = style.content.copy(alpha = 0.85f)
                        )
                    }
                }
                if (state != State.NEEDS_SETUP) {
                    Spacer(Modifier.width(12.dp))
                    val switchDescription = stringResource(R.string.applist_status_switch_cd)
                    Switch(
                        checked = status.protectEnabled,
                        onCheckedChange = onProtectionToggle,
                        thumbContent = if (status.protectEnabled) {
                            {
                                Icon(
                                    Icons.Rounded.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        } else null,
                        modifier = Modifier.semantics { contentDescription = switchDescription }
                    )
                }
            }

            AnimatedVisibility(
                visible = status.antiUninstallPermission != null && state != State.NEEDS_SETUP,
                enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.defaultSpatialSpec())
            ) {
                val permission = status.antiUninstallPermission
                if (permission != null) {
                    AntiUninstallIssueRow(permission, onFixAntiUninstall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AntiUninstallIssueRow(permission: MissingPermission, onFix: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f),
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
            FilledTonalButton(onClick = onFix, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.applist_status_fix))
            }
        }
    }
}
