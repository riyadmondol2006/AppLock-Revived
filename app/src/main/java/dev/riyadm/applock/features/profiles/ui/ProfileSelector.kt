package dev.riyadm.applock.features.profiles.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.riyadm.applock.R
import dev.riyadm.applock.data.repository.LockProfile
import dev.riyadm.applock.data.repository.LockProfilesState
import dev.riyadm.applock.data.repository.ProfileKind

/** Profile chips at the top of the home screen. Selecting a chip switches the active profile. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileSelector(
    state: LockProfilesState,
    onSelect: (LockProfile) -> Unit,
    onManage: () -> Unit,
    onLockAllUserApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.profiles_selector_title),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            IconButton(onClick = onManage, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    Icons.Rounded.EditNote,
                    contentDescription = stringResource(R.string.profiles_manage_cd)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.profiles.forEach { profile ->
                val selected = profile.id == state.activeId
                FilterChip(
                    selected = selected,
                    onClick = { if (!selected) onSelect(profile) },
                    modifier = Modifier.animateContentSize(motion.fastSpatialSpec()),
                    label = { Text(profileName(profile), maxLines = 1) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (selected) Icons.Rounded.Check else profile.kind.icon,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                )
            }
            AnimatedVisibility(
                visible = state.active.kind == ProfileKind.GUEST,
                enter = fadeIn(motion.fastEffectsSpec()) + expandHorizontally(motion.fastSpatialSpec()),
                exit = fadeOut(motion.fastEffectsSpec()) + shrinkHorizontally(motion.fastSpatialSpec())
            ) {
                AssistChip(
                    onClick = onLockAllUserApps,
                    label = { Text(stringResource(R.string.profiles_lock_all_user_apps)) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        leadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    border = null
                )
            }
        }
    }
}
