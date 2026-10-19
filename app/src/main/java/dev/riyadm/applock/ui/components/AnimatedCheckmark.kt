package dev.riyadm.applock.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A status mark that pops a filled circle in with a bouncy spring and then draws the tick.
 * When unchecked it shows an outlined ring. Animations are read only in the draw phase.
 */
@Composable
fun AnimatedCheckmark(
    checked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    checkColor: Color = MaterialTheme.colorScheme.onPrimary,
    outlineColor: Color = MaterialTheme.colorScheme.outline
) {
    val circle = remember { Animatable(if (checked) 1f else 0f) }
    val stroke = remember { Animatable(if (checked) 1f else 0f) }

    LaunchedEffect(checked) {
        if (checked) {
            launch { circle.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f)) }
            delay(90)
            stroke.animateTo(1f, spring(dampingRatio = 1f, stiffness = 260f))
        } else {
            stroke.snapTo(0f)
            circle.animateTo(0f, spring(dampingRatio = 1f, stiffness = 900f))
        }
    }

    val tick = remember { Path() }
    val segment = remember { Path() }
    val measure = remember { PathMeasure() }

    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val c = circle.value
        val ringWidth = 2.dp.toPx()
        if (c < 1f) {
            drawCircle(
                color = outlineColor.copy(alpha = outlineColor.alpha * (1f - c).coerceIn(0f, 1f)),
                radius = radius - ringWidth / 2f,
                style = Stroke(ringWidth)
            )
        }
        if (c > 0f) drawCircle(color = color, radius = radius * c.coerceAtMost(1.15f))

        val s = stroke.value
        if (s > 0f) {
            val w = this.size.width
            val h = this.size.height
            tick.rewind()
            tick.moveTo(w * 0.28f, h * 0.52f)
            tick.lineTo(w * 0.44f, h * 0.67f)
            tick.lineTo(w * 0.73f, h * 0.36f)
            measure.setPath(tick, false)
            segment.rewind()
            measure.getSegment(0f, measure.length * s.coerceIn(0f, 1f), segment, true)
            drawPath(
                path = segment,
                color = checkColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
