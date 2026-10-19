package dev.riyadm.applock.features.setpassword.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Pattern
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.graphics.shapes.RoundedPolygon
import dev.riyadm.applock.R
import dev.riyadm.applock.data.repository.PreferencesRepository
import dev.riyadm.applock.ui.motion.AppHaptics
import dev.riyadm.applock.ui.motion.pressScale

enum class LockKind(val prefValue: String, val icon: ImageVector) {
    PIN(PreferencesRepository.LOCK_TYPE_PIN, Icons.Rounded.Pin),
    PATTERN(PreferencesRepository.LOCK_TYPE_PATTERN, Icons.Rounded.Pattern),
    PASSWORD(PreferencesRepository.LOCK_TYPE_PASSWORD, Icons.Rounded.Password);

    companion object {
        fun fromPref(value: String): LockKind = entries.firstOrNull { it.prefValue == value } ?: PIN
    }
}

/**
 * Plays a decaying horizontal spring wobble; read the value in a layout/draw lambda.
 */
suspend fun Animatable<Float, *>.shake() {
    snapTo(0f)
    animateTo(
        targetValue = 0f,
        animationSpec = spring(dampingRatio = 0.22f, stiffness = 1200f),
        initialVelocity = 700f
    )
}

/**
 * Connected button group to pick PIN / Pattern / Password.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LockTypeSwitcher(
    selected: LockKind,
    onSelect: (LockKind) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = LockKind.entries
    val interactions = remember { options.map { MutableInteractionSource() } }
    val labels = options.map {
        stringResource(
            when (it) {
                LockKind.PIN -> R.string.setup_type_pin
                LockKind.PATTERN -> R.string.setup_type_pattern
                LockKind.PASSWORD -> R.string.setup_type_password
            }
        )
    }
    ButtonGroup(
        overflowIndicator = { state -> ButtonGroupDefaults.OverflowIndicator(menuState = state) },
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, kind ->
            customItem(
                buttonGroupContent = {
                    ToggleButton(
                        checked = kind == selected,
                        onCheckedChange = { onSelect(kind) },
                        interactionSource = interactions[index],
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .animateWidth(interactions[index])
                            .height(48.dp)
                            .semantics { role = Role.RadioButton }
                    ) {
                        Icon(
                            kind.icon,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Text(
                            labels[index],
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing)
                        )
                    }
                },
                menuContent = { state ->
                    DropdownMenuItem(
                        text = { Text(labels[index]) },
                        onClick = {
                            state.dismiss()
                            onSelect(kind)
                        }
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val DotShapes: List<RoundedPolygon> by lazy {
    listOf(
        MaterialShapes.Cookie4Sided,
        MaterialShapes.Pentagon,
        MaterialShapes.Sunny,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Gem,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Diamond,
        MaterialShapes.Flower,
        MaterialShapes.Puffy,
        MaterialShapes.SoftBurst
    )
}

/**
 * PIN entry indicator. Every digit pops in as a different expressive shape with a bouncy spring.
 * Hollow placeholders show the minimum length while creating a PIN.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PinDots(
    length: Int,
    isError: Boolean,
    modifier: Modifier = Modifier,
    placeholders: Int = 0
) {
    val color by animateColorAsState(
        if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "dot color"
    )
    val outline = MaterialTheme.colorScheme.outlineVariant
    val description = stringResource(R.string.setup_digits_entered, length)
    val dotSize = if (length > 10) 12.dp else 18.dp
    val total = maxOf(length, placeholders)

    Row(
        modifier = modifier
            .height(32.dp)
            .semantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(if (length > 10) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until total) {
            key(i) {
                if (i < length) {
                    FilledPinDot(DotShapes[i % DotShapes.size], dotSize) { color }
                } else {
                    Box(
                        Modifier
                            .size(dotSize * 0.6f)
                            .drawBehind {
                                drawCircle(outline, style = Stroke(1.5.dp.toPx()))
                            }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilledPinDot(polygon: RoundedPolygon, size: Dp, color: () -> Color) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 700f))
    }
    val shape = polygon.toShape()
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                val p = progress.value
                scaleX = p
                scaleY = p
                rotationZ = (1f - p) * -90f
                this.shape = shape
                clip = true
            }
            .drawBehind { drawRect(color()) }
    )
}

/**
 * Expressive number pad. Keys are rounded squares that spring into circles while pressed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressivePinKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
    isFinalStep: Boolean,
    haptics: AppHaptics,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val gap = 12.dp
        val byWidth = (maxWidth - gap * 2) / 3
        val byHeight = if (maxHeight == Dp.Infinity) byWidth else (maxHeight - gap * 3) / 4
        val keySize = min(min(byWidth, byHeight), 92.dp)
        val keyColors = MaterialTheme.colorScheme

        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            listOf("123", "456", "789").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { digit ->
                        KeypadKey(
                            size = keySize,
                            containerColor = { keyColors.surfaceContainerHighest },
                            contentColor = keyColors.onSurface,
                            contentDescription = digit.toString(),
                            enabled = enabled,
                            onClick = {
                                haptics.press()
                                onDigit(digit)
                            }
                        ) {
                            Text(digit.toString(), style = MaterialTheme.typography.headlineLargeEmphasized)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                KeypadKey(
                    size = keySize,
                    containerColor = { keyColors.secondaryContainer },
                    contentColor = keyColors.onSecondaryContainer,
                    contentDescription = stringResource(R.string.setup_delete),
                    enabled = enabled,
                    onClick = {
                        haptics.press()
                        onBackspace()
                    },
                    onLongClick = {
                        haptics.longPress()
                        onClear()
                    }
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = null)
                }
                KeypadKey(
                    size = keySize,
                    containerColor = { keyColors.surfaceContainerHighest },
                    contentColor = keyColors.onSurface,
                    contentDescription = "0",
                    enabled = enabled,
                    onClick = {
                        haptics.press()
                        onDigit('0')
                    }
                ) {
                    Text("0", style = MaterialTheme.typography.headlineLargeEmphasized)
                }
                val confirmContainer = animateColorAsState(
                    if (confirmEnabled) keyColors.primary else keyColors.surfaceContainerHigh,
                    MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "confirm key"
                )
                KeypadKey(
                    size = keySize,
                    containerColor = { confirmContainer.value },
                    contentColor = if (confirmEnabled) keyColors.onPrimary else keyColors.onSurfaceVariant,
                    contentDescription = stringResource(
                        if (isFinalStep) R.string.setup_confirm else R.string.setup_continue
                    ),
                    enabled = enabled,
                    onClick = onConfirm
                ) {
                    Icon(
                        if (isFinalStep) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeypadKey(
    size: Dp,
    containerColor: () -> Color,
    contentColor: Color,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner = animateFloatAsState(
        targetValue = if (pressed) 50f else 30f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "key corner"
    )
    Box(
        modifier = modifier
            .size(size)
            .pressScale(interaction, pressedScale = 0.94f)
            .graphicsLayer {
                shape = RoundedCornerShape(CornerSize(corner.value))
                clip = true
            }
            .drawBehind { drawRect(containerColor()) }
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(color = contentColor),
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}
