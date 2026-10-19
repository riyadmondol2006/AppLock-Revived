package dev.riyadm.appintro

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Expressive page indicator: the active page stretches into a pill with a bouncy spring,
 * visited pages stay tinted and upcoming pages are small dots.
 */
@Composable
fun PageIndicators(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.outlineVariant,
    visitedColor: Color = activeColor.copy(alpha = 0.45f),
    contentDescription: String? = null
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(8.dp)
            .semantics {
                this.contentDescription = contentDescription ?: "${currentPage + 1} / $pageCount"
            }
    ) {
        repeat(pageCount) { index ->
            PageIndicator(
                isSelected = index == currentPage,
                isVisited = index < currentPage,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                visitedColor = visitedColor
            )
        }
    }
}

/**
 * Single indicator for the pager.
 */
@Composable
fun PageIndicator(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    isVisited: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.outlineVariant,
    visitedColor: Color = activeColor.copy(alpha = 0.45f)
) {
    val width by animateDpAsState(
        targetValue = if (isSelected) 28.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "indicator width"
    )

    val height by animateDpAsState(
        targetValue = if (isSelected) 10.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "indicator height"
    )

    val color by animateColorAsState(
        targetValue = when {
            isSelected -> activeColor
            isVisited -> visitedColor
            else -> inactiveColor
        },
        label = "indicator color"
    )

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(CircleShape)
            .background(color)
    )
}
