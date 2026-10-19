package dev.riyadm.appintro

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * A [Shape] drawing a [Morph] at a given progress, scaled from the unit square to the layout size.
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
 * Returns a shape that smoothly morphs to [target] whenever it changes.
 */
@Composable
fun rememberMorphingShape(
    target: RoundedPolygon,
    animationSpec: AnimationSpec<Float> = spring(
        dampingRatio = 0.6f,
        stiffness = Spring.StiffnessMediumLow
    )
): Shape {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (target != to) {
            from = to
            to = target
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec)
        }
    }

    val morph = remember(from, to) { Morph(from, to) }
    return MorphShape(morph, progress.value)
}

/**
 * Large expressive hero: an icon sitting on a slowly rotating Material shape which morphs
 * whenever [shape] changes.
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
    backdropShape: RoundedPolygon? = null,
    rotate: Boolean = true
) {
    val morphShape = rememberMorphingShape(shape)
    val backdropMorphShape = backdropShape?.let { rememberMorphingShape(it) }

    val rotation = if (rotate) {
        val transition = rememberInfiniteTransition(label = "hero rotation")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart),
            label = "hero rotation value"
        ).value
    } else 0f

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (backdropMorphShape != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = -rotation * 0.6f
                        scaleX = 1.18f
                        scaleY = 1.18f
                    }
                    .background(containerColor.copy(alpha = 0.32f), backdropMorphShape)
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation }
                .background(containerColor, morphShape)
        )
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 60)) + scaleIn(
                    spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
                    initialScale = 0.4f
                )).togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.6f))
            },
            label = "hero icon"
        ) { target ->
            if (target != null) {
                Icon(
                    imageVector = target,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(size * 0.38f)
                )
            }
        }
    }
}
