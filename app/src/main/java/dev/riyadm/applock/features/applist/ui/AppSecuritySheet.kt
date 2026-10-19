package dev.riyadm.applock.features.applist.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.riyadm.applock.R
import dev.riyadm.applock.core.ui.MorphingIconBadge
import dev.riyadm.applock.data.repository.LockProfilesState
import dev.riyadm.applock.data.repository.SecurityLevel
import dev.riyadm.applock.features.applist.domain.InstalledApp
import dev.riyadm.applock.features.profiles.ui.descriptionRes
import dev.riyadm.applock.features.profiles.ui.icon
import dev.riyadm.applock.features.profiles.ui.profileName
import dev.riyadm.applock.features.profiles.ui.titleRes
import dev.riyadm.applock.features.settings.ui.SettingsIcon
import dev.riyadm.applock.features.settings.ui.SettingsSectionHeader
import dev.riyadm.applock.features.settings.ui.settingsItemColors
import dev.riyadm.applock.ui.motion.rememberAppHaptics

/** Per-app security level and profile membership for one app. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppSecuritySheet(
    app: InstalledApp,
    level: SecurityLevel,
    profiles: LockProfilesState,
    biometricAvailable: Boolean,
    onLevelChange: (SecurityLevel) -> Unit,
    onMembershipChange: (profileId: String, member: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        SheetValue.Hidden,
        setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded)
    )
    val haptics = rememberAppHaptics()
    val motion = MaterialTheme.motionScheme
    val levels = SecurityLevel.entries

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                AppIcon(app, size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                MorphingIconBadge(
                    polygon = when (level) {
                        SecurityLevel.DEFAULT -> MaterialShapes.Circle
                        SecurityLevel.BIOMETRIC_ONLY -> MaterialShapes.Cookie9Sided
                        SecurityLevel.CREDENTIAL_ONLY -> MaterialShapes.Square
                        SecurityLevel.TWO_STEP -> MaterialShapes.SoftBurst
                    },
                    icon = level.icon,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 48.dp
                )
            }

            SettingsSectionHeader(stringResource(R.string.policy_sheet_section_method))
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                levels.forEachIndexed { index, option ->
                    val selected = option == level
                    SegmentedListItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                haptics.tick()
                                onLevelChange(option)
                            }
                        },
                        shapes = ListItemDefaults.segmentedShapes(index, levels.size),
                        modifier = Modifier.semantics { role = Role.RadioButton },
                        leadingContent = { SettingsIcon(option.icon) },
                        supportingContent = { Text(stringResource(option.descriptionRes)) },
                        trailingContent = { RadioButton(selected = selected, onClick = null) },
                        colors = settingsItemColors(highlightSelected = true),
                        content = { Text(stringResource(option.titleRes)) }
                    )
                }
            }

            AnimatedVisibility(
                visible = !biometricAvailable &&
                        (level == SecurityLevel.BIOMETRIC_ONLY || level == SecurityLevel.TWO_STEP),
                enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec())
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null)
                        Text(
                            text = stringResource(R.string.policy_biometric_unavailable),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            SettingsSectionHeader(stringResource(R.string.policy_sheet_section_profiles))
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                profiles.profiles.forEachIndexed { index, profile ->
                    val member = app.packageName in profile.lockedApps
                    SegmentedListItem(
                        checked = member,
                        onCheckedChange = {
                            haptics.toggle(it)
                            onMembershipChange(profile.id, it)
                        },
                        shapes = ListItemDefaults.segmentedShapes(index, profiles.profiles.size),
                        leadingContent = { SettingsIcon(profile.kind.icon) },
                        supportingContent = if (profile.id == profiles.activeId) {
                            { Text(stringResource(R.string.profiles_active_badge)) }
                        } else null,
                        trailingContent = { Checkbox(checked = member, onCheckedChange = null) },
                        colors = settingsItemColors(),
                        content = { Text(profileName(profile), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }

            Text(
                text = stringResource(R.string.policy_sheet_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Trailing button on a locked app row; filled when the app has its own security level. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PolicyTuneButton(level: SecurityLevel, appLabel: String, onClick: () -> Unit) {
    val description = stringResource(R.string.policy_tune_cd, appLabel)
    if (level == SecurityLevel.DEFAULT) {
        IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
            Icon(level.icon, contentDescription = description)
        }
    } else {
        FilledTonalIconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
            Icon(level.icon, contentDescription = description)
        }
    }
}
