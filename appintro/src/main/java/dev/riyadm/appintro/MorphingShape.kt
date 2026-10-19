package dev.riyadm.appintro

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.launch

/**
 * A [Shape] drawing a [Morph] at a fixed progress, scaled from the unit square to the layout size.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    private val matrix = Matrix()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = morph.toPath(progress = progress, path = Path())
        matrix.reset()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * Draws [morph] at [progress] filling the draw area. Material shapes live in the unit square,
 * so the canvas is scaled instead of transforming the path. Meant to be called from draw-phase
 * lambdas so animations never recompose.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun DrawScope.drawMorph(
    morph: Morph,
    progress: Float,
    color: Color,
    path: Path,
    rotation: Float = 0f,
    scale: Float = 1f
) {
    path.rewind()
    morph.toPath(progress = progress, path = path)
    rotate(rotation, pivot = center) {
        scale(scale, pivot = center) {
            scale(size.width, size.height, pivot = Offset.Zero) {
                drawPath(path, color)
            }
        }
    }
}

private val HeroSettleSpec: AnimationSpec<Float> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow)

/** Starts [sweepDegrees] off and turns into place once; at rest from the start when animations are off. */
@Composable
private fun rememberEntrySpin(sweepDegrees: Float): Animatable<Float, AnimationVector1D> {
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    val spin = remember { Animatable(if (animate) -sweepDegrees else 0f) }
    LaunchedEffect(spin) { spin.animateTo(0f, HeroSettleSpec) }
    return spin
}

/**
 * Entry rotation for hero shapes: turns into place once and then rests, so an idle screen
 * renders no frames. The returned state should only be read in draw.
 */
@Composable
fun rememberHeroRotation(sweepDegrees: Float = HERO_ENTRY_SWEEP): State<Float> =
    rememberEntrySpin(sweepDegrees).asState()

/**
 * Icon sitting on a Material shape that springs into a new shape whenever [shape] changes,
 * with a turn on entry and on every change (finite, so the idle hero draws nothing).
 * All per-frame work happens in the draw phase.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingHero(
    shape: RoundedPolygon,
    icon: ImageVector?,
    containerColor: Color,
    contentColor: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    rotate: Boolean = true,
    morphSpec: AnimationSpec<Float> = spring(
        dampingRatio = 0.6f,
        stiffness = Spring.StiffnessMediumLow
    )
) {
    var from by remember { mutableStateOf(shape) }
    var to by remember { mutableStateOf(shape) }
    val progress = remember { Animatable(1f) }

    val spin = rememberEntrySpin(if (rotate) HERO_ENTRY_SWEEP else 0f)

    LaunchedEffect(shape) {
        if (shape != to) {
            from = to
            to = shape
            if (rotate) launch { spin.animateTo(spin.targetValue + HERO_CHANGE_TURN, morphSpec) }
            progress.snapTo(0f)
            progress.animateTo(1f, morphSpec)
        }
    }

    val morph = remember(from, to) { Morph(from, to) }
    val path = remember { Path() }
    val motion = MaterialTheme.motionScheme
    val color by animateColorAsState(
        containerColor,
        motion.defaultEffectsSpec(),
        label = "hero color"
    )
    val iconTint by animateColorAsState(
        contentColor,
        motion.defaultEffectsSpec(),
        label = "hero tint"
    )

    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                drawMorph(
                    morph = morph,
                    progress = progress.value,
                    color = color,
                    path = path,
                    rotation = spin.value
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(
                    spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
                    initialScale = 0.4f
                )).togetherWith(
                    fadeOut(motion.fastEffectsSpec()) +
                            scaleOut(motion.fastSpatialSpec(), targetScale = 0.6f)
                )
            },
            label = "hero icon"
        ) { target ->
            if (target != null) {
                Icon(
                    imageVector = target,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(size * 0.4f)
                )
            }
        }
    }
}

private const val HERO_ENTRY_SWEEP = 60f
private const val HERO_CHANGE_TURN = 45f
