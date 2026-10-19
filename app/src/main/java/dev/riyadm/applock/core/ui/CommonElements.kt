package dev.riyadm.applock.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.riyadm.applock.core.utils.appLockRepository

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val shapes = mutableListOf(
    MaterialShapes.Triangle,
    MaterialShapes.Pentagon,
    MaterialShapes.Circle,
    MaterialShapes.Arrow,
    MaterialShapes.Pill,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Heart,
    MaterialShapes.PixelTriangle,
    MaterialShapes.PixelCircle,
    MaterialShapes.Gem
).apply { shuffle() }

/** Remembers a [Shape] for one of the [MaterialShapes] polygons. */
@Composable
fun rememberPolygonShape(polygon: RoundedPolygon): Shape =
    remember(polygon) { polygon.toShape() }

/**
 * An icon sitting on an expressive (e.g. cookie / burst) shaped container,
 * used for hero cards and empty states.
 */
@Composable
fun ExpressiveIconBadge(
    polygon: RoundedPolygon,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    size: Dp = 56.dp,
    iconSize: Dp = size * 0.46f,
    contentDescription: String? = null,
) {
    ExpressiveIconBadge(
        shape = rememberPolygonShape(polygon),
        icon = icon,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        size = size,
        iconSize = iconSize,
        contentDescription = contentDescription
    )
}

@Composable
fun ExpressiveIconBadge(
    shape: Shape,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    size: Dp = 56.dp,
    iconSize: Dp = size * 0.46f,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

/** Small label shown above a group of segmented list items. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 8.dp)
    )
}

/** Centered empty / no-results state with an expressive shaped icon. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ExpressiveIconBadge(
            polygon = polygon,
            icon = icon,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            size = 88.dp
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Inline (non-expanding) pill search field used to filter lists in place. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InlineSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    clearContentDescription: String,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { focusManager.clearFocus() },
                expanded = false,
                onExpandedChange = {},
                placeholder = {
                    Text(
                        text = placeholder,
                        maxLines = 1
                    )
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = clearContentDescription)
                        }
                    }
                } else null,
            )
        },
        expanded = false,
        onExpandedChange = {},
        modifier = modifier.fillMaxWidth(),
        colors = SearchBarDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
        content = {},
    )
}

/** Haptic feedback that honours the "disable haptics" setting. */
class AppHaptics internal constructor(
    private val enabled: Boolean,
    private val perform: (HapticFeedbackType) -> Unit,
) {
    fun toggle(on: Boolean) {
        if (enabled) perform(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
    }

    fun longPress() {
        if (enabled) perform(HapticFeedbackType.LongPress)
    }

    fun confirm() {
        if (enabled) perform(HapticFeedbackType.Confirm)
    }
}

@Composable
fun rememberAppHaptics(): AppHaptics {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    return remember(haptic) {
        val enabled = !context.appLockRepository().shouldDisableHaptics()
        AppHaptics(enabled) { haptic.performHapticFeedback(it) }
    }
}
