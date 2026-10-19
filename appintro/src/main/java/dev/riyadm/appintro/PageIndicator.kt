package dev.riyadm.appintro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.abs

/**
 * Expressive page indicator. The active page is a pill that stretches and slides continuously
 * with the pager position, visited pages stay tinted and upcoming pages are small dots.
 *
 * [position] is read only while drawing (e.g. `{ pagerState.currentPage + pagerState.currentPageOffsetFraction }`),
 * so page transitions never recompose the indicator.
 */
@Composable
fun PageIndicators(
    pageCount: Int,
    position: () -> Float,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.outlineVariant,
    visitedColor: Color = activeColor.copy(alpha = 0.4f),
    dotSize: Dp = 8.dp,
    activeWidth: Dp = 28.dp,
    activeHeight: Dp = 10.dp,
    spacing: Dp = 8.dp,
    contentDescription: String? = null
) {
    val totalWidth = dotSize * (pageCount - 1) + activeWidth + spacing * (pageCount - 1)
    Canvas(
        modifier = modifier
            .width(totalWidth)
            .height(activeHeight)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
            }
    ) {
        val pos = position()
        val dot = dotSize.toPx()
        val active = activeWidth.toPx()
        val activeH = activeHeight.toPx()
        val gap = spacing.toPx()
        val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
        var x = 0f
        for (i in 0 until pageCount) {
            val emphasis = (1f - abs(pos - i)).coerceIn(0f, 1f)
            val w = lerp(dot, active, emphasis)
            val h = lerp(dot, activeH, emphasis)
            val base = if (i < pos) visitedColor else inactiveColor
            val color = lerp(base, activeColor, emphasis)
            val left = if (rtl) size.width - x - w else x
            drawRoundRect(
                color = color,
                topLeft = Offset(left, (size.height - h) / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(h / 2f)
            )
            x += w + gap
        }
    }
}
