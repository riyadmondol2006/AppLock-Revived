package dev.riyadm.applock.ui.motion

import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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

/** Kinds of feedback the app asks for; mapped to the best effect the device supports. */
private enum class Feel { Tick, Press, Confirm, Reject, ToggleOn, ToggleOff, LongPress }

/**
 * Haptics that respect the "Disable haptics" setting, read live on every call so flipping the
 * toggle takes effect immediately (also in the lock-screen overlay).
 *
 * The view haptic path (Compose's performHapticFeedback) silently does nothing on many devices:
 * older APIs, Samsung with touch feedback off, overlay windows. So the vibrator service is the
 * primary path, with predefined effects on API 29+ and one-shot/waveform effects below. The view
 * haptic is only a fallback when the vibrator is missing or throws, so nothing double-vibrates.
 */
@Stable
class AppHaptics(
    context: Context,
    private val viewFallback: (HapticFeedbackType) -> Unit,
    private val isEnabled: () -> Boolean
) {
    private val vibrator: Vibrator? = runCatching { obtainVibrator(context.applicationContext) }.getOrNull()

    fun tick() = fire(Feel.Tick, HapticFeedbackType.SegmentTick)
    fun press() = fire(Feel.Press, HapticFeedbackType.VirtualKey)
    fun confirm() = fire(Feel.Confirm, HapticFeedbackType.Confirm)
    fun reject() = fire(Feel.Reject, HapticFeedbackType.Reject)
    fun toggle(on: Boolean) = if (on) {
        fire(Feel.ToggleOn, HapticFeedbackType.ToggleOn)
    } else {
        fire(Feel.ToggleOff, HapticFeedbackType.ToggleOff)
    }
    fun longPress() = fire(Feel.LongPress, HapticFeedbackType.LongPress)

    private fun fire(feel: Feel, fallback: HapticFeedbackType) {
        if (!isEnabled()) return
        if (vibrate(feel)) return
        runCatching { viewFallback(fallback) }
    }

    private fun vibrate(feel: Feel): Boolean {
        val v = vibrator ?: return false
        return try {
            if (!v.hasVibrator()) return false
            val effect = effectFor(feel, v)
            if (Build.VERSION.SDK_INT >= 29) {
                @Suppress("DEPRECATION")
                v.vibrate(effect, TOUCH_ATTRIBUTES)
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun effectFor(feel: Feel, v: Vibrator): VibrationEffect {
        if (Build.VERSION.SDK_INT >= 29) {
            val id = when (feel) {
                Feel.Tick, Feel.ToggleOff -> VibrationEffect.EFFECT_TICK
                Feel.Press, Feel.ToggleOn -> VibrationEffect.EFFECT_CLICK
                Feel.Confirm -> VibrationEffect.EFFECT_DOUBLE_CLICK
                Feel.LongPress -> VibrationEffect.EFFECT_HEAVY_CLICK
                Feel.Reject -> null // no predefined "error"; use the waveform below
            }
            if (id != null) {
                val supported = Build.VERSION.SDK_INT < 30 || v.areAllEffectsSupported(id) == Vibrator.VIBRATION_EFFECT_SUPPORT_YES
                if (supported) return VibrationEffect.createPredefined(id)
            }
        }
        return when (feel) {
            Feel.Tick, Feel.ToggleOff -> oneShot(10, 70)
            Feel.Press, Feel.ToggleOn -> oneShot(18, 130)
            Feel.LongPress -> oneShot(35, 220)
            Feel.Confirm -> waveform(longArrayOf(0, 18, 60, 18), intArrayOf(0, 160, 0, 160))
            Feel.Reject -> waveform(longArrayOf(0, 45, 55, 45, 55, 45), intArrayOf(0, 255, 0, 255, 0, 255))
        }
    }

    private fun oneShot(ms: Long, amplitude: Int): VibrationEffect =
        VibrationEffect.createOneShot(ms, if (supportsAmplitude()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE)

    private fun waveform(timings: LongArray, amplitudes: IntArray): VibrationEffect =
        if (supportsAmplitude()) VibrationEffect.createWaveform(timings, amplitudes, -1)
        else VibrationEffect.createWaveform(timings, -1)

    private fun supportsAmplitude(): Boolean = vibrator?.hasAmplitudeControl() == true

    private companion object {
        val TOUCH_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        fun obtainVibrator(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
    }
}

/** Live "haptics allowed" check: the user's master switch, read from the repository each call. */
fun hapticsAllowed(context: Context): Boolean {
    val app = context.applicationContext as? AppLockApplication
    return app?.appLockRepository?.shouldDisableHaptics() != true
}

@Composable
fun rememberAppHaptics(): AppHaptics {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    return remember(haptic, context) {
        AppHaptics(context, haptic::performHapticFeedback) { hapticsAllowed(context) }
    }
}
