package dev.riyadm.applock.features.applist.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.riyadm.applock.R
import dev.riyadm.applock.features.applist.domain.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
private val iconDispatcher = Dispatchers.IO.limitedParallelism(4)

/** App icon loaded lazily off the main thread through [AppIconCache]. */
@Composable
fun AppIcon(
    app: InstalledApp,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val context = LocalContext.current
    val bitmap by produceState(AppIconCache.peekIcon(app.packageName), app.packageName) {
        if (value == null) {
            value = withContext(iconDispatcher) {
                AppIconCache.getIcon(context.applicationContext, app.applicationInfo)
            }
        }
    }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        val icon = bitmap
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
        }
    }
}

@Composable
fun appListItemColors() = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
)

/**
 * An app row inside a segmented group with a switch on the trailing side. In selection mode
 * the switch turns into a checkbox and clicks toggle the selection instead.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppSwitchListItem(
    app: InstalledApp,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedThumbIcon: ImageVector = Icons.Rounded.Lock,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val colors = appListItemColors()
    val supporting: @Composable () -> Unit = {
        Text(
            text = if (app.isSystem) {
                stringResource(R.string.applist_system_supporting, app.packageName)
            } else app.packageName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val headline: @Composable () -> Unit = {
        Text(text = app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val leading: @Composable () -> Unit = { AppIcon(app) }

    if (selectionMode) {
        SegmentedListItem(
            checked = selected,
            onCheckedChange = onSelectedChange,
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            leadingContent = leading,
            supportingContent = supporting,
            trailingContent = { Checkbox(checked = selected, onCheckedChange = null) },
            colors = colors,
            content = headline,
        )
    } else {
        SegmentedListItem(
            onClick = { onCheckedChange(!checked) },
            shapes = shapes,
            modifier = modifier.semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            },
            enabled = enabled,
            leadingContent = leading,
            supportingContent = supporting,
            trailingContent = {
                Switch(
                    checked = checked,
                    onCheckedChange = null,
                    enabled = enabled,
                    thumbContent = if (checked) {
                        {
                            Icon(
                                imageVector = checkedThumbIcon,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        }
                    } else null
                )
            },
            onLongClick = onLongClick,
            onLongClickLabel = if (onLongClick != null) {
                stringResource(R.string.applist_select_action_label)
            } else null,
            colors = colors,
            content = headline,
        )
    }
}

/** Row for a package that was added by name and isn't installed. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ManualPackageListItem(
    packageName: String,
    onRemove: () -> Unit,
    index: Int,
    count: Int,
    removeContentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = modifier,
        enabled = enabled,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        supportingContent = {
            Text(stringResource(R.string.applist_manual_package_desc), maxLines = 1)
        },
        trailingContent = {
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(Icons.Rounded.Delete, contentDescription = removeContentDescription)
            }
        },
        colors = appListItemColors(),
    ) {
        Text(packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
