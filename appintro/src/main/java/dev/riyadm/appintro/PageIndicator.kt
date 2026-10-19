package dev.riyadm.appintro

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Displays indicators for the current page position with enhanced animations
 */
@Composable
fun PageIndicators(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(8.dp)
    ) {
        repeat(pageCount) { index ->
            PageIndicator(
                isSelected = index == currentPage,
                activeColor = activeColor,
                inactiveColor = inactiveColor
            )
        }
    }
}

/**
 * Single indicator dot for the pager with enhanced animations
 */
@Composable
fun PageIndicator(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
) {
    val width by animateDpAsState(
        targetValue = if (isSelected) 28.dp else 10.dp,
        animationSpec = tween(durationMillis = 300),
        label = "indicator width"
    )

    val height by animateDpAsState(
        targetValue = if (isSelected) 10.dp else 10.dp,
        animationSpec = tween(durationMillis = 300),
        label = "indicator height"
    )

    val color by animateColorAsState(
        targetValue = if (isSelected) activeColor else inactiveColor,
        animationSpec = tween(durationMillis = 300),
        label = "indicator color"
    )

    val shape = if (isSelected) RoundedCornerShape(5.dp) else CircleShape

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(color)
    )
}
