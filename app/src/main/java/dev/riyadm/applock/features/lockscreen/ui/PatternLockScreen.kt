package dev.riyadm.applock.features.lockscreen.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.riyadm.applock.R
import dev.riyadm.applock.data.repository.UnlockLogRepository
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ceil

@Composable
fun PatternLockScreen(spec: LockScreenSpec, modifier: Modifier = Modifier) {
    val controller = rememberUnlockController(spec.packageName, UnlockLogRepository.Method.PATTERN)
    val haptics = controller.haptics
    val padState = remember(spec.sessionKey) { PatternPadState() }
    var showError by remember { mutableStateOf(false) }
    var rejecting by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val maxPadSize = rememberPatternMaxSize()

    LaunchedEffect(controller.failureCount) {
        if (controller.failureCount == 0) return@LaunchedEffect
        showError = true
        rejecting = true
        shake.shake()
        delay(PATTERN_ERROR_HOLD_MS)
        padState.clear()
        rejecting = false
    }

    val credentialVisible = spec.credentialVisible
    val hint = policyHint(spec, stringResource(R.string.lock_draw_pattern), R.string.policy_lock_step_pattern)
    val wrong = stringResource(R.string.lock_wrong_pattern)

    LockScreenScaffold(
        spec = spec,
        controller = controller,
        modifier = modifier,
        status = { compact ->
            if (controller.isLockedOut) {
                LockoutIndicator(controller, compact, Modifier.padding(top = 8.dp))
            } else {
                LockStatusText(text = if (showError) wrong else hint, isError = showError)
            }
            if (spec.biometricVisible && credentialVisible) {
                Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
                BiometricButton(onClick = spec.onBiometric, compact = compact)
            }
        },
        input = input@{
            if (!credentialVisible) {
                BiometricFirstPanel(spec)
                return@input
            }
            PatternPad(
                state = padState,
                enabled = controller.canInput && !rejecting,
                isError = rejecting,
                onStart = {
                    showError = false
                    haptics.tick()
                },
                onDotAdded = { haptics.tick() },
                onPatternComplete = { ids ->
                    if (ids.size < MIN_PATTERN_DOTS) {
                        padState.clear()
                    } else {
                        controller.submit(ids.joinToString(""))
                    }
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = maxPadSize)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .shakeOffset(shake)
            )
        }
    )
}

@Stable
internal class PatternPadState {
    val selected = mutableStateListOf<Int>()
    var pointer by mutableStateOf(Offset.Unspecified)

    fun clear() {
        selected.clear()
        pointer = Offset.Unspecified
    }
}

/**
 * 3x3 pattern input. Dot ids are column-major (id = col * 3 + row + 1) to stay
 * compatible with patterns saved by the previous pattern library.
 *
 * Hit targets scale with the pad (not fixed pixels), the pointer path between two
 * events is sampled so fast swipes don't skip dots, and callbacks are always the
 * latest ones (the old library captured stale state).
 */
@Composable
internal fun PatternPad(
    state: PatternPadState,
    enabled: Boolean,
    isError: Boolean,
    onStart: () -> Unit,
    onDotAdded: () -> Unit,
    onPatternComplete: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val accent = animateColorAsState(
        targetValue = if (isError) colors.error else colors.primary,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "patternAccent"
    )
    val idleDot = colors.onSurfaceVariant.copy(alpha = 0.55f)
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnDot by rememberUpdatedState(onDotAdded)
    val currentOnComplete by rememberUpdatedState(onPatternComplete)

    val dotProgress: List<State<Float>> = List(DOTS) { index ->
        animateFloatAsState(
            targetValue = if ((index + 1) in state.selected) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.45f, stiffness = 600f),
            label = "dot$index"
        )
    }
    val description = stringResource(R.string.lock_pattern_pad_cd)

    Box(
        modifier = modifier
            .semantics { contentDescription = description }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val cell = size.width / GRID.toFloat()
                    val hitRadius = cell * HIT_RADIUS_FRACTION
                    val step = cell * 0.2f

                    fun center(col: Int, row: Int) =
                        Offset(cell * (col + 0.5f), (size.height / GRID.toFloat()) * (row + 0.5f))

                    fun hitTest(position: Offset) {
                        val col = (position.x / cell).toInt()
                        val row = (position.y / (size.height / GRID.toFloat())).toInt()
                        if (col !in 0 until GRID || row !in 0 until GRID) return
                        if ((center(col, row) - position).getDistance() > hitRadius) return
                        val id = col * GRID + row + 1
                        if (id in state.selected) return
                        val last = state.selected.lastOrNull()
                        if (last != null) {
                            val lastCol = (last - 1) / GRID
                            val lastRow = (last - 1) % GRID
                            if (abs(col - lastCol) % 2 == 0 && abs(row - lastRow) % 2 == 0) {
                                val midId = ((col + lastCol) / 2) * GRID + (row + lastRow) / 2 + 1
                                if (midId != id && midId !in state.selected) {
                                    state.selected.add(midId)
                                }
                            }
                        }
                        if (state.selected.isEmpty()) currentOnStart() else currentOnDot()
                        state.selected.add(id)
                    }

                    state.clear()
                    hitTest(down.position)
                    state.pointer = down.position
                    down.consume()
                    var previous = down.position

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        val position = change.position
                        val distance = (position - previous).getDistance()
                        val samples = ceil(distance / step).toInt().coerceAtLeast(1)
                        for (i in 1..samples) {
                            val t = i / samples.toFloat()
                            hitTest(Offset(lerp(previous.x, position.x, t), lerp(previous.y, position.y, t)))
                        }
                        previous = position
                        state.pointer = position
                        change.consume()
                    }

                    state.pointer = Offset.Unspecified
                    if (state.selected.isNotEmpty()) currentOnComplete(state.selected.toList())
                }
            }
            .drawBehind {
                val cellW = size.width / GRID
                val cellH = size.height / GRID
                fun centerOf(id: Int): Offset {
                    val col = (id - 1) / GRID
                    val row = (id - 1) % GRID
                    return Offset(cellW * (col + 0.5f), cellH * (row + 0.5f))
                }

                val lineColor = accent.value.copy(alpha = 0.8f)
                val stroke = 6.dp.toPx()
                val path = state.selected
                for (i in 0 until path.size - 1) {
                    drawLine(lineColor, centerOf(path[i]), centerOf(path[i + 1]), stroke, StrokeCap.Round)
                }
                val pointer = state.pointer
                if (path.isNotEmpty() && pointer != Offset.Unspecified) {
                    drawLine(lineColor, centerOf(path.last()), pointer, stroke, StrokeCap.Round)
                }

                val baseRadius = 6.dp.toPx()
                val selectedRadius = 11.dp.toPx()
                val haloRadius = minOf(cellW, cellH) * 0.3f
                for (index in 0 until DOTS) {
                    val p = dotProgress[index].value
                    val center = centerOf(index + 1)
                    if (p > 0.01f) {
                        drawCircle(accent.value.copy(alpha = 0.16f * p.coerceIn(0f, 1f)), haloRadius * p, center)
                    }
                    val color = if (p > 0.01f) accent.value else idleDot
                    drawCircle(color, baseRadius + (selectedRadius - baseRadius) * p, center)
                }
            }
    )
}

private const val GRID = 3
private const val DOTS = GRID * GRID
private const val HIT_RADIUS_FRACTION = 0.36f
private const val MIN_PATTERN_DOTS = 2
private const val PATTERN_ERROR_HOLD_MS = 350L
