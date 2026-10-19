package dev.riyadm.appintro

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.util.lerp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A Material 3 Expressive app intro.
 *
 * A large hero shape morphs between the shapes of consecutive pages, driven directly by the
 * pager position in the draw phase, while the text block slides underneath it.
 *
 * @param pages List of [IntroPage] to display in the intro
 * @param onSkip Called when the user skips the intro (optional)
 * @param onFinish Called when the user completes the intro
 * @param showSkipButton Whether to show the skip button
 * @param useAnimatedPager Whether to use enhanced animations between pages
 * @param nextButtonText Default text for the next button
 * @param skipButtonText Text for the skip button
 * @param finishButtonText Text for the finish button
 * @param backButtonText Text for the back button
 * @param pageIndicatorDescription Accessibility description of the page indicator
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppIntro(
    pages: List<IntroPage>,
    onSkip: () -> Unit = {},
    onFinish: () -> Unit,
    showSkipButton: Boolean = true,
    useAnimatedPager: Boolean = true,
    nextButtonText: String = "Next",
    skipButtonText: String = "Skip",
    finishButtonText: String = "Get Started",
    backButtonText: String = "Back",
    pageIndicatorDescription: ((current: Int, total: Int) -> String)? = null
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme

    val targetIndex = pagerState.targetPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val targetPage = pages.getOrNull(targetIndex)
    val isLastPage = targetIndex >= pages.size - 1
    val position: () -> Float = {
        (pagerState.currentPage + pagerState.currentPageOffsetFraction)
            .coerceIn(0f, (pages.size - 1).coerceAtLeast(0).toFloat())
    }
    val pageSpec = remember { spring<Float>(dampingRatio = 0.9f, stiffness = 320f) }

    fun goTo(page: Int) {
        coroutineScope.launch { pagerState.animateScrollToPage(page, animationSpec = pageSpec) }
    }

    fun goNext() {
        val page = pages[pagerState.currentPage]
        val canProceed = page.onNext?.invoke() ?: true
        if (!canProceed) return
        if (pagerState.currentPage >= pages.size - 1) onFinish() else goTo(pagerState.currentPage + 1)
    }

    fun goBack() {
        if (pagerState.currentPage > 0) goTo(pagerState.currentPage - 1)
    }

    BackHandler(enabled = pagerState.currentPage > 0) { goBack() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
    ) {
        val fullHero = min(240.dp, min(maxWidth * 0.6f, maxHeight * 0.3f))
        val compactHero = min(104.dp, maxHeight * 0.14f)

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (showSkipButton && !isLastPage) {
                    TextButton(onClick = onSkip, shapes = ButtonDefaults.shapes()) {
                        Text(skipButtonText, style = MaterialTheme.typography.labelLargeEmphasized)
                    }
                }
            }

            PagerHero(
                pages = pages,
                position = position,
                fullSize = fullHero,
                compactSize = compactHero,
                modifier = Modifier.fillMaxWidth()
            )

            Box(modifier = Modifier.weight(1f)) {
                if (useAnimatedPager) {
                    AnimatedIntroPager(pages = pages, pagerState = pagerState)
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = false,
                        verticalAlignment = Alignment.Top
                    ) { page ->
                        IntroPageContent(
                            page = pages[page],
                            modifier = Modifier.fillMaxSize(),
                            isVisible = page == pagerState.targetPage
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                PageIndicators(
                    pageCount = pages.size,
                    position = position,
                    activeColor = colorScheme.primary,
                    inactiveColor = colorScheme.outlineVariant,
                    contentDescription = pageIndicatorDescription?.invoke(targetIndex + 1, pages.size)
                )

                val nextLabel = if (isLastPage) finishButtonText
                else targetPage?.nextButtonText ?: nextButtonText
                val backInteraction = remember { MutableInteractionSource() }
                val nextInteraction = remember { MutableInteractionSource() }
                val showBack = targetIndex > 0

                ButtonGroup(
                    overflowIndicator = { menuState ->
                        ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (showBack) {
                        customItem(
                            buttonGroupContent = {
                                FilledTonalButton(
                                    onClick = { goBack() },
                                    shapes = ButtonDefaults.shapes(),
                                    interactionSource = backInteraction,
                                    contentPadding = ButtonDefaults.MediumContentPadding,
                                    modifier = Modifier
                                        .weight(1f)
                                        .animateWidth(backInteraction)
                                        .height(ButtonDefaults.MediumContainerHeight)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.MediumIconSize)
                                    )
                                    Text(
                                        backButtonText,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(start = ButtonDefaults.MediumIconSpacing)
                                    )
                                }
                            },
                            menuContent = { state ->
                                DropdownMenuItem(
                                    text = { Text(backButtonText) },
                                    onClick = {
                                        state.dismiss()
                                        goBack()
                                    }
                                )
                            }
                        )
                    }
                    customItem(
                        buttonGroupContent = {
                            Button(
                                onClick = { goNext() },
                                shapes = ButtonDefaults.shapes(),
                                interactionSource = nextInteraction,
                                contentPadding = ButtonDefaults.MediumContentPadding,
                                modifier = Modifier
                                    .weight(1.6f)
                                    .animateWidth(nextInteraction)
                                    .height(ButtonDefaults.MediumContainerHeight)
                            ) {
                                AnimatedContent(
                                    targetState = nextLabel to isLastPage,
                                    transitionSpec = {
                                        (slideInVertically(motionScheme.fastSpatialSpec()) { it } +
                                                fadeIn(motionScheme.defaultEffectsSpec()))
                                            .togetherWith(
                                                slideOutVertically(motionScheme.fastSpatialSpec()) { -it } +
                                                        fadeOut(motionScheme.fastEffectsSpec())
                                            )
                                    },
                                    label = "next label"
                                ) { (text, last) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(ButtonDefaults.MediumIconSpacing)
                                    ) {
                                        Text(text = text, style = MaterialTheme.typography.titleMediumEmphasized)
                                        Icon(
                                            imageVector = if (last) Icons.Filled.Check
                                            else Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            modifier = Modifier.size(ButtonDefaults.MediumIconSize)
                                        )
                                    }
                                }
                            }
                        },
                        menuContent = { state ->
                            DropdownMenuItem(
                                text = { Text(nextLabel) },
                                onClick = {
                                    state.dismiss()
                                    goNext()
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * The hero shape for all pages. The morph progress, size, colour and icon cross-fade are all
 * computed from [position] inside layout/draw lambdas, so a page transition never recomposes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PagerHero(
    pages: List<IntroPage>,
    position: () -> Float,
    fullSize: Dp,
    compactSize: Dp,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val shapes = remember(pages.map { it.heroShape }) {
        pages.mapIndexed { i, page -> page.heroShape ?: DefaultHeroShapes[i % DefaultHeroShapes.size] }
    }
    val morphs = remember(shapes) { consecutiveMorphs(shapes) }
    val backdropMorphs = remember(shapes.size) {
        consecutiveMorphs(List(shapes.size) { i -> DefaultHeroShapes[(i + 3) % DefaultHeroShapes.size] })
    }
    val tonal = listOf(
        colorScheme.primaryContainer to colorScheme.onPrimaryContainer,
        colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer,
        colorScheme.secondaryContainer to colorScheme.onSecondaryContainer
    )
    val containers = pages.mapIndexed { i, p -> p.backgroundColor ?: tonal[i % tonal.size].first }
    val contents = pages.mapIndexed { i, p -> p.contentColor ?: tonal[i % tonal.size].second }
    val compact = pages.map { it.content != null || it.customContent != null }
    val path = remember { Path() }
    val backdropPath = remember { Path() }
    val rotation = rememberHeroRotation()
    val lastIndex = pages.size - 1

    fun segment(pos: Float): Pair<Int, Float> {
        val i = floor(pos).toInt().coerceIn(0, (lastIndex - 1).coerceAtLeast(0))
        return i to (pos - i).coerceIn(0f, 1f)
    }

    Box(
        modifier = modifier.layout { measurable, constraints ->
            val (i, t) = segment(position())
            val next = (i + 1).coerceAtMost(lastIndex)
            fun sizeOf(index: Int) = if (compact[index]) compactSize.toPx() else fullSize.toPx()
            fun padOf(index: Int) = if (compact[index]) 8.dp.toPx() else 28.dp.toPx()
            val side = lerp(sizeOf(i), sizeOf(next), t).roundToInt()
            val pad = lerp(padOf(i), padOf(next), t).roundToInt()
            val placeable = measurable.measure(Constraints.fixed(side, side))
            layout(constraints.maxWidth, side + pad * 2) {
                placeable.place((constraints.maxWidth - side) / 2, pad)
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val (i, t) = segment(position())
                    val next = (i + 1).coerceAtMost(lastIndex)
                    val container = lerp(containers[i], containers[next], t)
                    val r = rotation.value
                    drawMorph(
                        morph = backdropMorphs[i],
                        progress = t,
                        color = container.copy(alpha = 0.3f),
                        path = backdropPath,
                        rotation = -r * 0.6f + t * 45f,
                        scale = 1.2f
                    )
                    drawMorph(
                        morph = morphs[i],
                        progress = t,
                        color = container,
                        path = path,
                        rotation = r + t * 90f
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            pages.forEachIndexed { index, page ->
                val layerModifier = Modifier.graphicsLayer {
                    val offset = position() - index
                    val visibility = (1f - abs(offset) * 1.6f).coerceIn(0f, 1f)
                    alpha = visibility
                    val scale = lerp(0.4f, 1f, visibility)
                    scaleX = scale
                    scaleY = scale
                    rotationZ = offset * -30f
                }
                when {
                    page.illustration != null -> Box(layerModifier) { page.illustration.invoke() }
                    page.icon != null -> Icon(
                        imageVector = page.icon,
                        contentDescription = null,
                        tint = contents[index],
                        modifier = layerModifier.fillMaxSize(0.4f)
                    )
                }
            }
        }
    }
}

private fun consecutiveMorphs(shapes: List<RoundedPolygon>): List<Morph> =
    if (shapes.size < 2) listOf(Morph(shapes.first(), shapes.first()))
    else List(shapes.size - 1) { Morph(shapes[it], shapes[it + 1]) }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val DefaultHeroShapes: List<RoundedPolygon> by lazy {
    listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Sunny,
        MaterialShapes.Pentagon,
        MaterialShapes.Cookie12Sided,
        MaterialShapes.SoftBurst,
        MaterialShapes.Puffy,
        MaterialShapes.Gem,
        MaterialShapes.Flower
    )
}

/**
 * Navigates to the next page in the intro
 */
fun navigateToNextPage(pagerState: PagerState, scope: CoroutineScope) {
    scope.launch {
        if (pagerState.currentPage < pagerState.pageCount - 1) {
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
        }
    }
}

/**
 * Navigates to the previous page in the intro
 */
fun navigateToPreviousPage(pagerState: PagerState, scope: CoroutineScope) {
    scope.launch {
        if (pagerState.currentPage > 0) {
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
        }
    }
}
