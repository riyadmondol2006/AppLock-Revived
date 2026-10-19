package dev.riyadm.applock.features.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.remember
import dev.riyadm.applock.ui.motion.AppHaptics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** False when the user disabled haptic feedback in settings. */
val LocalSettingsHapticsEnabled = compositionLocalOf { true }

enum class SettingsAccent { Primary, Secondary, Tertiary, Error }

data class ChoiceOption<T>(val value: T, val label: String)

class SettingsGroupScope internal constructor() {
    internal val items = mutableListOf<@Composable (ListItemShapes) -> Unit>()

    fun item(content: @Composable (shapes: ListItemShapes) -> Unit) {
        items += content
    }
}

/** A titled group of segmented list items rendered as one rounded card. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsGroup(
    title: String?,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: SettingsGroupScope.() -> Unit
) {
    val scope = SettingsGroupScope().apply(content)
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) SettingsSectionHeader(title)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            scope.items.forEachIndexed { index, item ->
                item(ListItemDefaults.segmentedShapes(index = index, count = scope.items.size))
            }
        }
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp)
            .semantics { heading() }
    )
}

@Composable
fun SettingsIcon(
    icon: ImageVector,
    accent: SettingsAccent = SettingsAccent.Primary,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp)
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (accent) {
        SettingsAccent.Primary -> colors.primaryContainer to colors.onPrimaryContainer
        SettingsAccent.Secondary -> colors.secondaryContainer to colors.onSecondaryContainer
        SettingsAccent.Tertiary -> colors.tertiaryContainer to colors.onTertiaryContainer
        SettingsAccent.Error -> colors.errorContainer to colors.onErrorContainer
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun SettingsSwitch(checked: Boolean, enabled: Boolean) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        enabled = enabled,
        thumbContent = {
            Icon(
                imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize)
            )
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun settingsItemColors(highlightSelected: Boolean = false): ListItemColors {
    val colors = MaterialTheme.colorScheme
    return if (highlightSelected) {
        ListItemDefaults.segmentedColors(containerColor = colors.surfaceContainer)
    } else {
        ListItemDefaults.segmentedColors(
            containerColor = colors.surfaceContainer,
            selectedContainerColor = colors.surfaceContainer,
            selectedContentColor = colors.onSurface,
            selectedLeadingContentColor = colors.onSurfaceVariant,
            selectedTrailingContentColor = colors.onSurfaceVariant,
            selectedOverlineContentColor = colors.onSurfaceVariant,
            selectedSupportingContentColor = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsTitle(text: String) {
    Text(text = text, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** App haptics that follow the live "disable haptics" toggle on the settings screen. */
@Composable
fun rememberSettingsHaptics(): AppHaptics {
    val haptic = LocalHapticFeedback.current
    val enabled = LocalSettingsHapticsEnabled.current
    return remember(haptic, enabled) { AppHaptics(haptic::performHapticFeedback, enabled) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SwitchSettingItem(
    shapes: ListItemShapes,
    icon: ImageVector,
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    accent: SettingsAccent = SettingsAccent.Primary
) {
    val haptics = rememberSettingsHaptics()
    SegmentedListItem(
        checked = checked,
        onCheckedChange = {
            haptics.toggle(it)
            onCheckedChange(it)
        },
        shapes = shapes,
        enabled = enabled,
        leadingContent = { SettingsIcon(icon, accent, enabled) },
        trailingContent = { SettingsSwitch(checked = checked, enabled = enabled) },
        supportingContent = summary?.let { { Text(it) } },
        colors = settingsItemColors(),
        content = { SettingsTitle(title) }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionSettingItem(
    shapes: ListItemShapes,
    icon: ImageVector,
    title: String,
    summary: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    external: Boolean = false,
    accent: SettingsAccent = SettingsAccent.Primary,
    trailing: (@Composable () -> Unit)? = null
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        enabled = enabled,
        leadingContent = { SettingsIcon(icon, accent, enabled) },
        trailingContent = trailing ?: {
            Icon(
                imageVector = if (external) Icons.AutoMirrored.Rounded.OpenInNew
                else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (external) 20.dp else 24.dp)
            )
        },
        supportingContent = summary?.let { { Text(it) } },
        colors = settingsItemColors(),
        content = { SettingsTitle(title) }
    )
}

/** List item with an inline row of connected toggle buttons for picking one value. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ChoiceSettingItem(
    shapes: ListItemShapes,
    icon: ImageVector,
    title: String,
    summary: String?,
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
    accent: SettingsAccent = SettingsAccent.Primary
) {
    SegmentedListItem(
        shapes = shapes,
        enabled = enabled,
        verticalAlignment = Alignment.Top,
        leadingContent = { SettingsIcon(icon, accent, enabled) },
        supportingContent = {
            Column {
                if (summary != null) Text(summary)
                Spacer(Modifier.height(12.dp))
                ConnectedChoiceRow(
                    options = options,
                    selected = selected,
                    onSelect = onSelect,
                    enabled = enabled
                )
            }
        },
        colors = settingsItemColors(),
        content = { SettingsTitle(title) }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedChoiceRow(
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = rememberSettingsHaptics()
    val interactionSources = remember(options.size) {
        List(options.size) { MutableInteractionSource() }
    }
    val select: (T) -> Unit = { value ->
        if (value != selected) {
            haptics.tick()
            onSelect(value)
        }
    }

    ButtonGroup(
        overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, option ->
            val checked = option.value == selected
            val interactionSource = interactionSources[index]
            customItem(
                {
                    ToggleButton(
                        checked = checked,
                        onCheckedChange = { select(option.value) },
                        enabled = enabled,
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .weight(1f)
                            .animateWidth(interactionSource)
                            .semantics { role = Role.RadioButton },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        colors = ToggleButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        AnimatedVisibility(
                            visible = checked,
                            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                                    expandHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()),
                            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                                    shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec())
                        ) {
                            Row {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.size(6.dp))
                            }
                        }
                        Text(
                            text = option.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                { menuState ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        leadingIcon = if (checked) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else null,
                        enabled = enabled,
                        onClick = {
                            select(option.value)
                            menuState.dismiss()
                        }
                    )
                }
            )
        }
    }
}
