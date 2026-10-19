package dev.riyadm.applock.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import dev.riyadm.applock.R
import dev.riyadm.applock.ui.motion.AppHaptics
import dev.riyadm.applock.ui.motion.rememberAppHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import kotlin.math.roundToInt

/** Digits in keypad order (1-9 then 0), shuffled with a CSPRNG when scrambling is on. */
fun keypadDigits(scramble: Boolean = false): List<Int> {
    val digits = mutableListOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
    if (scramble) java.util.Collections.shuffle(digits, SecureRandom())
    return digits
}

private val DefaultDigits = keypadDigits()

/**
 * Number pad shared by the lock screens and credential setup.
 *
 * Digits and backspace act on pointer down, like a phone dialer, with the haptic in the same
 * frame; confirm acts on release. Press feedback is drawn from plain float states, so pressing
 * or typing never recomposes the keys. Long-pressing backspace clears the input.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PinKeypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    digits: List<Int> = DefaultDigits,
    enabled: Boolean = true,
    confirmReady: Boolean = true,
    busy: Boolean = false,
    confirmIcon: ImageVector = Icons.AutoMirrored.Rounded.ArrowForward,
    confirmContentDescription: String = stringResource(R.string.lock_confirm_cd),
) {
    val haptics = rememberAppHaptics()
    val colors = MaterialTheme.colorScheme
    val contentDirection = LocalLayoutDirection.current

    val currentOnDigit = rememberUpdatedState(onDigit)
    val currentOnBackspace = rememberUpdatedState(onBackspace)
    val currentOnClear = rememberUpdatedState(onClear)
    val currentOnConfirm = rememberUpdatedState(onConfirm)
    val digitAction: (Int) -> Unit = remember { { currentOnDigit.value(it) } }
    val backspaceAction: () -> Unit = remember { { currentOnBackspace.value() } }
    val clearAction: () -> Unit = remember { { currentOnClear.value() } }
    val confirmAction: () -> Unit = remember { { currentOnConfirm.value() } }

    val backspaceLabel = stringResource(R.string.lock_backspace_cd)
    val clearLabel = stringResource(R.string.pin_keypad_clear_action)

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val byWidth = if (constraints.hasBoundedWidth) maxWidth / (3 + 2 * KEY_GAP_RATIO) else MaxKeySize
        val byHeight = if (constraints.hasBoundedHeight) maxHeight / (4 + 3 * KEY_GAP_RATIO) else byWidth
        val keySize = minOf(byWidth, byHeight).coerceIn(MinKeySize, MaxKeySize)
        val gap = keySize * KEY_GAP_RATIO
        val digitStyle = if (keySize >= 76.dp) MaterialTheme.typography.displaySmall
        else MaterialTheme.typography.headlineLarge
        val iconSize = keySize * 0.36f

        @Composable
        fun DigitKey(digit: Int) {
            KeypadKey(
                keySize = keySize,
                container = { colors.surfaceContainerHigh },
                pressedContainer = colors.primaryContainer,
                contentColor = colors.onSurface,
                label = digit.toString(),
                enabled = enabled,
                actOnDown = true,
                haptics = haptics,
                onActivate = { digitAction(digit) },
                contentDirection = contentDirection
            ) {
                Text(text = digit.toString(), style = digitStyle)
            }
        }

        // Phone keypads keep 1-2-3 left to right in every locale; key content stays localized.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                verticalArrangement = Arrangement.spacedBy(gap),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (row in 0 until 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (col in 0 until 3) DigitKey(digits[row * 3 + col])
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    KeypadKey(
                        keySize = keySize,
                        container = { colors.secondaryContainer },
                        pressedContainer = colors.tertiaryContainer,
                        contentColor = colors.onSecondaryContainer,
                        label = backspaceLabel,
                        enabled = enabled,
                        actOnDown = true,
                        haptics = haptics,
                        onActivate = backspaceAction,
                        onLongPress = clearAction,
                        longPressLabel = clearLabel,
                        contentDirection = contentDirection
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Backspace,
                            contentDescription = null,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                    DigitKey(digits[9])
                    val confirmContainer = animateColorAsState(
                        targetValue = if (confirmReady || busy) colors.primary else colors.surfaceContainerHigh,
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        label = "confirmKey"
                    )
                    val ready = confirmReady || busy
                    KeypadKey(
                        keySize = keySize,
                        container = { confirmContainer.value },
                        pressedContainer = if (ready) colors.onPrimary.copy(alpha = 0.2f).compositeOver(colors.primary)
                        else colors.primaryContainer,
                        contentColor = if (ready) colors.onPrimary else colors.onSurfaceVariant,
                        label = confirmContentDescription,
                        enabled = enabled || busy,
                        actOnDown = false,
                        haptics = haptics,
                        onActivate = confirmAction,
                        contentDirection = contentDirection
                    ) {
                        if (busy) {
                            LoadingIndicator(
                                color = LocalContentColor.current,
                                modifier = Modifier.size(keySize * 0.6f)
                            )
                        } else {
                            Icon(confirmIcon, contentDescription = null, modifier = Modifier.size(iconSize))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Press animation state for one key. Values are plain snapshot floats written from the
 * pointer handler and read only in graphicsLayer/drawBehind, so feedback costs one draw.
 */
@Stable
private class KeyPressState {
    val press = mutableFloatStateOf(0f)
    val highlight = mutableFloatStateOf(0f)
    private var pressVelocity = 0f
    private var pressJob: Job? = null
    private var highlightJob: Job? = null

    fun pressIn(scope: CoroutineScope) {
        highlightJob?.cancel()
        highlight.floatValue = 1f
        animatePress(scope, 1f, KeyPressSpec)
    }

    fun release(scope: CoroutineScope) {
        animatePress(scope, 0f, KeyReleaseSpec)
        highlightJob?.cancel()
        highlightJob = scope.launch {
            animate(highlight.floatValue, 0f, animationSpec = KeyFadeSpec) { value, _ ->
                highlight.floatValue = value
            }
        }
    }

    private fun animatePress(scope: CoroutineScope, target: Float, spec: AnimationSpec<Float>) {
        pressJob?.cancel()
        pressJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animate(press.floatValue, target, pressVelocity, spec) { value, velocity ->
                press.floatValue = value
                pressVelocity = velocity
            }
            pressVelocity = 0f
        }
    }
}

@Composable
private fun KeypadKey(
    keySize: Dp,
    container: () -> Color,
    pressedContainer: Color,
    contentColor: Color,
    label: String,
    enabled: Boolean,
    actOnDown: Boolean,
    haptics: AppHaptics,
    onActivate: () -> Unit,
    contentDirection: LayoutDirection,
    onLongPress: (() -> Unit)? = null,
    longPressLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state = remember { KeyPressState() }
    val currentEnabled by rememberUpdatedState(enabled)
    val activate by rememberUpdatedState(onActivate)
    val longPress by rememberUpdatedState(onLongPress)
    val containerColor by rememberUpdatedState(container)
    val pressedColor by rememberUpdatedState(pressedContainer)
    val hasLongPress = onLongPress != null

    Box(
        modifier = Modifier
            .size(keySize)
            .graphicsLayer {
                val scale = 1f - KEY_PRESSED_SCALE_DELTA * state.press.floatValue
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else DISABLED_ALPHA
            }
            .drawBehind {
                val p = state.press.floatValue.coerceIn(0f, 1f)
                val radius = size.minDimension * (KEY_IDLE_CORNER + (0.5f - KEY_IDLE_CORNER) * p)
                drawRoundRect(
                    color = lerp(containerColor(), pressedColor, state.highlight.floatValue),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }
            .pointerInput(actOnDown, hasLongPress) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (!currentEnabled) return@awaitEachGesture
                    // Consuming at once means no ancestor can delay or steal the press.
                    down.consume()
                    state.pressIn(scope)
                    haptics.press()
                    if (actOnDown) activate()
                    try {
                        val lifted: Boolean = if (hasLongPress) {
                            val result = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                waitForUpOrCancellation()?.also { it.consume() } != null
                            }
                            if (result == null) {
                                if (currentEnabled) {
                                    haptics.longPress()
                                    longPress?.invoke()
                                }
                                waitForUpOrCancellation()?.consume()
                                false
                            } else {
                                result
                            }
                        } else {
                            waitForUpOrCancellation()?.also { it.consume() } != null
                        }
                        if (lifted && !actOnDown && currentEnabled) activate()
                    } finally {
                        state.release(scope)
                    }
                }
            }
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                if (!enabled) disabled()
                onClick {
                    if (currentEnabled) activate()
                    currentEnabled
                }
                if (hasLongPress) {
                    onLongClick(label = longPressLabel) {
                        if (currentEnabled) longPress?.invoke()
                        currentEnabled
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalLayoutDirection provides contentDirection,
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val DotMorphs: List<Morph> by lazy {
    listOf(
        MaterialShapes.Cookie4Sided,
        MaterialShapes.Sunny,
        MaterialShapes.Gem,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Pentagon,
        MaterialShapes.SoftBurst,
        MaterialShapes.Diamond,
        MaterialShapes.Cookie6Sided,
    ).map { Morph(it, MaterialShapes.Circle) }
}

/**
 * PIN progress. Each dot pops in on a fast spring while morphing from an expressive shape
 * into a circle; hollow placeholders mark the minimum length. Dots shrink to fit when the
 * row is too long. Animations are read in layout/draw only.
 */
@Composable
fun PinDots(
    length: Int,
    isError: Boolean,
    modifier: Modifier = Modifier,
    placeholders: Int = 0,
    dotSize: Dp = 16.dp,
) {
    val colors = MaterialTheme.colorScheme
    val color = animateColorAsState(
        targetValue = if (isError) colors.error else colors.primary,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "dotColor"
    )
    val ring = colors.outline
    var shown by remember { mutableIntStateOf(length) }
    LaunchedEffect(length) {
        if (length < shown) delay(DOT_EXIT_MS)
        shown = length
    }
    // Build the morphs off the main thread before the first digit needs them.
    LaunchedEffect(Unit) { withContext(Dispatchers.Default) { DotMorphs.size } }

    val description = stringResource(R.string.lock_pin_progress_cd, length)
    val spacing = dotSize * 0.75f
    val count = maxOf(length, shown, placeholders)

    Layout(
        content = {
            for (index in 0 until count) {
                key(index) {
                    PinDot(
                        filled = index < length,
                        placeholder = index < placeholders,
                        index = index,
                        dotSize = dotSize,
                        spacing = spacing,
                        color = { color.value },
                        ringColor = ring
                    )
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(dotSize * 2f)
            .semantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            }
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val total = placeables.sumOf { it.width }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else total
        val height = constraints.constrainHeight(placeables.maxOfOrNull { it.height } ?: 0)
        val fit = if (total > width && total > 0) width.toFloat() / total else 1f
        layout(width, height) {
            var x = (width - total * fit) / 2f
            placeables.forEach { placeable ->
                val center = x + placeable.width * fit / 2f
                val left = (center - placeable.width / 2f).roundToInt()
                val top = (height - placeable.height) / 2
                if (fit < 1f) {
                    placeable.placeRelativeWithLayer(left, top) {
                        scaleX = fit
                        scaleY = fit
                    }
                } else {
                    placeable.placeRelative(left, top)
                }
                x += placeable.width * fit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PinDot(
    filled: Boolean,
    placeholder: Boolean,
    index: Int,
    dotSize: Dp,
    spacing: Dp,
    color: () -> Color,
    ringColor: Color,
) {
    val presence = remember { Animatable(0f) }
    val morphProgress = remember { Animatable(0f) }
    LaunchedEffect(filled) {
        if (filled) {
            launch {
                morphProgress.snapTo(0f)
                morphProgress.animateTo(1f, DotMorphSpec)
            }
            presence.animateTo(1f, DotInSpec)
        } else {
            presence.animateTo(0f, DotOutSpec)
        }
    }
    val morph = DotMorphs[index % DotMorphs.size]
    val path = remember { Path() }
    val matrix = remember { Matrix() }
    val currentRing by rememberUpdatedState(ringColor)

    Box(
        Modifier
            .layout { measurable, _ ->
                val dotPx = dotSize.roundToPx()
                val slot = (dotSize + spacing).toPx()
                val p = if (placeholder) 1f else presence.value.coerceIn(0f, 1f)
                val width = (slot * p).roundToInt()
                val placeable = measurable.measure(Constraints.fixed(dotPx, dotPx))
                layout(width, dotPx) { placeable.place((width - dotPx) / 2, 0) }
            }
            .drawBehind {
                val p = presence.value
                if (placeholder && p < 1f) {
                    drawCircle(
                        color = currentRing,
                        radius = size.minDimension * 0.3f,
                        alpha = (1f - p).coerceIn(0f, 1f),
                        style = Stroke(1.5.dp.toPx())
                    )
                }
                if (p > 0f) {
                    path.rewind()
                    morph.toPath(morphProgress.value.coerceIn(0f, 1f), path)
                    matrix.reset()
                    matrix.scale(size.width, size.height)
                    path.transform(matrix)
                    scale(p) { drawPath(path, color()) }
                }
            }
    )
}

// Crisp press, quick settle (~150 ms) and a short colour fade so even fast taps read clearly.
private val KeyPressSpec = spring<Float>(dampingRatio = 0.65f, stiffness = 1800f)
private val KeyReleaseSpec = spring<Float>(dampingRatio = 0.75f, stiffness = 1500f)
private val KeyFadeSpec = spring<Float>(dampingRatio = 1f, stiffness = 900f)
private val DotInSpec = spring<Float>(dampingRatio = 0.55f, stiffness = 1400f)
private val DotMorphSpec = spring<Float>(dampingRatio = 0.9f, stiffness = 1000f)
private val DotOutSpec = spring<Float>(dampingRatio = 1f, stiffness = 1800f)

private val MinKeySize = 48.dp
private val MaxKeySize = 96.dp
private const val KEY_GAP_RATIO = 0.2f
private const val KEY_IDLE_CORNER = 0.3f
private const val KEY_PRESSED_SCALE_DELTA = 0.06f
private const val DISABLED_ALPHA = 0.38f
private const val DOT_EXIT_MS = 160L
