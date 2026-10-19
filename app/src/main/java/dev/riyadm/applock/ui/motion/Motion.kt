package dev.riyadm.applock.ui.motion

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import dev.riyadm.applock.AppLockApplication

/**
 * Material 3 Expressive motion tokens as plain specs, for places that can't read
 * MaterialTheme.motionScheme (e.g. NavHost transition lambdas). Inside composables
 * prefer MaterialTheme.motionScheme.*Spec().
 */
object ExpressiveMotion {
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(0.6f, 800f)
    fun <T> defaultSpatial(): FiniteAnimationSpec<T> = spring(0.8f, 380f)
    fun <T> slowSpatial(): FiniteAnimationSpec<T> = spring(0.8f, 200f)
    fun <T> fastEffects(): FiniteAnimationSpec<T> = spring(1f, 3800f)
    fun <T> defaultEffects(): FiniteAnimationSpec<T> = spring(1f, 1600f)
    fun <T> slowEffects(): FiniteAnimationSpec<T> = spring(1f, 800f)

    private val offsetSpec = spring<IntOffset>(0.9f, 500f, IntOffset(1, 1))

    // Shared-axis X navigation; the pop variants drive predictive back.
    val enter: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
        slideInHorizontally(offsetSpec) { it / 6 } + fadeIn(defaultEffects())
    }
    val exit: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
        slideOutHorizontally(offsetSpec) { -it / 10 } + fadeOut(fastEffects())
    }
    val popEnter: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
        slideInHorizontally(offsetSpec) { -it / 10 } + fadeIn(defaultEffects())
    }
    val popExit: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
        slideOutHorizontally(offsetSpec) { it / 6 } + fadeOut(fastEffects()) +
                scaleOut(defaultEffects(), targetScale = 0.94f)
    }

    // For full-screen surfaces like the lock screen.
    val fadeScaleIn: EnterTransition =
        fadeIn(defaultEffects()) + scaleIn(defaultSpatial(), initialScale = 0.92f)
    val fadeScaleOut: ExitTransition =
        fadeOut(fastEffects()) + scaleOut(fastSpatial(), targetScale = 1.04f)
}

/** False when the system "Remove animations" setting (animator duration scale 0) is on. */
@Composable
fun rememberAnimationsEnabled(): Boolean = remember { ValueAnimator.areAnimatorsEnabled() }

/**
 * One-shot 0 → 1 entry progress for hero decorations. It plays once when first composed and then
 * rests, so an idle screen renders no frames. Starts at rest when animations are off.
 */
@Composable
fun rememberSettleProgress(spec: AnimationSpec<Float>): Animatable<Float, AnimationVector1D> {
    val enabled = rememberAnimationsEnabled()
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(progress) { progress.animateTo(1f, spec) }
    return progress
}

/**
 * Springy press feedback. The animated value is only read in the draw phase,
 * so pressing doesn't recompose the content.
 */
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.92f) =
    composed {
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) pressedScale else 1f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
            label = "pressScale"
        )
        graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }

/** Haptics that respect the "Disable haptics" setting. */
@Stable
class AppHaptics(private val perform: (HapticFeedbackType) -> Unit, private val enabled: Boolean) {
    fun tick() = fire(HapticFeedbackType.SegmentTick)
    fun press() = fire(HapticFeedbackType.VirtualKey)
    fun confirm() = fire(HapticFeedbackType.Confirm)
    fun reject() = fire(HapticFeedbackType.Reject)
    fun toggle(on: Boolean) = fire(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
    fun longPress() = fire(HapticFeedbackType.LongPress)

    private fun fire(type: HapticFeedbackType) {
        if (enabled) perform(type)
    }
}

@Composable
fun rememberAppHaptics(): AppHaptics {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val enabled = remember(context) {
        val app = context.applicationContext as? AppLockApplication
        app?.appLockRepository?.shouldDisableHaptics() != true
    }
    return remember(haptic, enabled) { AppHaptics(haptic::performHapticFeedback, enabled) }
}
