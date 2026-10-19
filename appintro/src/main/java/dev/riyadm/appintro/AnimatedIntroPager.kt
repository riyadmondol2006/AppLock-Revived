package dev.riyadm.appintro

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/**
 * Pager for the intro text blocks with a subtle fade/scale transition between pages.
 * User swiping is disabled because pages may gate navigation on permissions.
 */
@Composable
fun AnimatedIntroPager(
    pages: List<IntroPage>,
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        pageSpacing = 16.dp,
        userScrollEnabled = false,
        verticalAlignment = Alignment.Top,
        key = { it }
    ) { page ->
        IntroPageContent(
            page = pages[page],
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val offset = ((pagerState.currentPage - page) +
                            pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    val scale = 1f - 0.08f * offset
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - offset
                },
            isVisible = page == pagerState.targetPage
        )
    }
}
