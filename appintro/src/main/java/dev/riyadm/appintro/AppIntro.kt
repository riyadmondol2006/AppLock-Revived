package dev.riyadm.appintro

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A Material 3 Expressive app intro.
 *
 * A large hero shape morphs between pages while the text block slides underneath it.
 *
 * @param pages List of [IntroPage] to display in the intro
 * @param onSkip Called when the user skips the intro (optional)
 * @param onFinish Called when the user completes the intro
 * @param showSkipButton Whether to show the skip button
 * @param useAnimatedPager Whether to use enhanced animations between pages
 * @param nextButtonText Default text for the next button
 * @param skipButtonText Text for the skip button
 * @param finishButtonText Text for the finish button
 * @param backButtonText Content description for the back button
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

    val tonalPairs = remember(colorScheme) {
        listOf(
            colorScheme.primaryContainer to colorScheme.onPrimaryContainer,
            colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer,
            colorScheme.secondaryContainer to colorScheme.onSecondaryContainer
        )
    }
    val defaultPair = tonalPairs[targetIndex % tonalPairs.size]
    val heroContainer by animateColorAsState(
        targetPage?.backgroundColor ?: defaultPair.first,
        animationSpec = motionScheme.slowEffectsSpec(),
        label = "hero container"
    )
    val heroContent by animateColorAsState(
        targetPage?.contentColor ?: defaultPair.second,
        animationSpec = motionScheme.slowEffectsSpec(),
        label = "hero content"
    )

    val heroShape = targetPage?.heroShape ?: DefaultHeroShapes[targetIndex % DefaultHeroShapes.size]
    val backdropShape = DefaultHeroShapes[(targetIndex + 3) % DefaultHeroShapes.size]
    val compactHero = targetPage?.content != null || targetPage?.customContent != null

    fun goNext() {
        val page = pages[pagerState.currentPage]
        val canProceed = page.onNext?.invoke() ?: true
        if (!canProceed) return
        if (pagerState.currentPage >= pages.size - 1) {
            onFinish()
        } else {
            coroutineScope.launch {
                pagerState.animateScrollToPage(
                    pagerState.currentPage + 1,
                    animationSpec = motionScheme.defaultSpatialSpec()
                )
            }
        }
    }

    BackHandler(enabled = pagerState.currentPage > 0) {
        navigateToPreviousPage(pagerState, coroutineScope)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
    ) {
        val fullHero = min(240.dp, min(maxWidth * 0.6f, maxHeight * 0.32f))
        val smallHero = min(112.dp, maxHeight * 0.16f)
        val heroSize by animateDpAsState(
            targetValue = if (compactHero) smallHero else fullHero,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "hero size"
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                AnimatedVisibility(
                    visible = showSkipButton && !isLastPage,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TextButton(onClick = onSkip) {
                        Text(skipButtonText, style = MaterialTheme.typography.labelLargeEmphasized)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (compactHero) 8.dp else 24.dp),
                contentAlignment = Alignment.Center
            ) {
                val illustration = targetPage?.illustration
                if (illustration != null) {
                    illustration()
                } else {
                    MorphingHero(
                        shape = heroShape,
                        icon = targetPage?.icon,
                        containerColor = heroContainer,
                        contentColor = heroContent,
                        size = heroSize,
                        backdropShape = backdropShape
                    )
                }
            }

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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = targetIndex > 0,
                        enter = fadeIn() + scaleIn(spring(dampingRatio = 0.5f)),
                        exit = fadeOut() + scaleOut()
                    ) {
                        FilledTonalIconButton(
                            onClick = { navigateToPreviousPage(pagerState, coroutineScope) },
                            modifier = Modifier.size(56.dp),
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = backButtonText
                            )
                        }
                    }
                }

                PageIndicators(
                    pageCount = pages.size,
                    currentPage = targetIndex,
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentWidth(Alignment.CenterHorizontally),
                    activeColor = colorScheme.primary,
                    inactiveColor = colorScheme.outlineVariant,
                    contentDescription = pageIndicatorDescription?.invoke(
                        targetIndex + 1,
                        pages.size
                    )
                )

                val label = when {
                    isLastPage -> finishButtonText
                    else -> targetPage?.nextButtonText ?: nextButtonText
                }
                Button(
                    onClick = { goNext() },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.height(56.dp),
                    contentPadding = ButtonDefaults.MediumContentPadding
                ) {
                    AnimatedContent(
                        targetState = label to isLastPage,
                        transitionSpec = {
                            (slideInVertically(spring(dampingRatio = 0.7f)) { it } + fadeIn())
                                .togetherWith(slideOutVertically { -it } + fadeOut())
                                .using(SizeTransform(clip = false) { _, _ ->
                                    spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                })
                        },
                        label = "next label"
                    ) { (text, last) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = text, style = MaterialTheme.typography.titleMediumEmphasized)
                            Icon(
                                imageVector = if (last) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

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
