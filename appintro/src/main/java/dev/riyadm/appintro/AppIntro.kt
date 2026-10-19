package dev.riyadm.appintro

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A modern Material 3 App Intro component for Android applications.
 *
 * @param pages List of [IntroPage] to display in the intro
 * @param onSkip Called when the user skips the intro (optional)
 * @param onFinish Called when the user completes the intro
 * @param showSkipButton Whether to show the skip button (defaults to true)
 * @param useAnimatedPager Whether to use enhanced animations between pages (defaults to true)
 * @param nextButtonText Text for the next button (defaults to "Next")
 * @param skipButtonText Text for the skip button (defaults to "Skip")
 * @param finishButtonText Text for the finish button (defaults to "Get Started")
 * @param backButtonText Text for the back button (defaults to "Back")
 */
@OptIn(
    ExperimentalAnimationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
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
    backButtonText: String = "Back"
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    val currentPageColor by remember(pagerState.currentPage) {
        derivedStateOf {
            pages.getOrNull(pagerState.currentPage)?.backgroundColor
                ?: colorScheme.primary
        }
    }

    val currentPageTextColor by remember(pagerState.currentPage) {
        derivedStateOf {
            pages.getOrNull(pagerState.currentPage)?.contentColor
                ?: colorScheme.onPrimary
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (useAnimatedPager) {
            AnimatedIntroPager(
                pages = pages,
                pagerState = pagerState,
                modifier = Modifier
                    .fillMaxSize()
            )
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
            ) { page ->
                IntroPageContent(
                    page = pages[page],
                    modifier = Modifier.fillMaxSize(),
                    isVisible = page == pagerState.currentPage
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Indicators
                PageIndicators(
                    pageCount = pages.size,
                    currentPage = pagerState.currentPage,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    activeColor = currentPageTextColor,
                    inactiveColor = currentPageTextColor.copy(alpha = 0.3f)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Skip button with animation
                    AnimatedVisibility(
                        visible = showSkipButton && pagerState.currentPage < pages.size - 1,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                    ) {
                        TextButton(
                            onClick = onSkip,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = currentPageTextColor
                            )
                        ) {
                            Text(
                                text = skipButtonText,
                                style = MaterialTheme.typography.bodyLargeEmphasized,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }


                    // Back button with animation - only visible when not on first page
                    AnimatedVisibility(
                        visible = pagerState.currentPage > 0,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                    ) {
                        TextButton(
                            onClick = {
                                navigateToPreviousPage(pagerState, coroutineScope)
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = currentPageTextColor
                            )
                        ) {
                            Text(
                                text = backButtonText,
                                style = MaterialTheme.typography.bodyLargeEmphasized,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (!showSkipButton || pagerState.currentPage >= pages.size - 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // Next/Finish button
                    if (pagerState.currentPage < pages.size - 1) {
                        FilledTonalButton(
                            onClick = {
                                // Get current page
                                val currentPage = pages[pagerState.currentPage]

                                // Execute the page's onNext callback if it exists
                                val canProceed = currentPage.onNext?.invoke() ?: true

                                // Only proceed to next page if callback returns true or is null
                                if (canProceed) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = currentPageTextColor.copy(alpha = 0.15f),
                                contentColor = currentPageTextColor
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .height(48.dp)
                        ) {
                            // Animated text content that transitions when text changes
                            AnimatedContent(
                                targetState = nextButtonText,
                                transitionSpec = {
                                    (slideInVertically { height -> height } + fadeIn() + scaleIn(
                                        initialScale = 0.9f
                                    ))
                                        .togetherWith(slideOutVertically { height -> -height } + fadeOut() + scaleOut(
                                            targetScale = 1.1f
                                        ))
                                        .using(SizeTransform(clip = false))
                                },
                                label = "Next Button Text Animation"
                            ) { targetText ->
                                Text(
                                    text = targetText,
                                    style = MaterialTheme.typography.bodyLargeEmphasized,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                // Execute the last page's onNext callback if it exists
                                val currentPage = pages[pagerState.currentPage]
                                val canFinish = currentPage.onNext?.invoke() ?: true

                                // Only finish if callback returns true or is null
                                if (canFinish) {
                                    onFinish()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = currentPageTextColor,
                                contentColor = currentPageColor
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .height(52.dp)
                        ) {
                            // Animated text content for the finish button
                            AnimatedContent(
                                targetState = finishButtonText,
                                transitionSpec = {
                                    (slideInVertically { height -> height } + fadeIn() + scaleIn(
                                        initialScale = 0.9f
                                    ))
                                        .togetherWith(slideOutVertically { height -> -height } + fadeOut() + scaleOut(
                                            targetScale = 1.1f
                                        ))
                                        .using(SizeTransform(clip = false))
                                },
                                label = "Finish Button Text Animation"
                            ) { targetText ->
                                Text(
                                    text = targetText,
                                    style = MaterialTheme.typography.bodyLargeEmphasized,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Navigates to the next page in the intro
 */
fun navigateToNextPage(pagerState: PagerState, scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch {
        if (pagerState.currentPage < pagerState.pageCount - 1) {
            pagerState.animateScrollToPage(pagerState.currentPage + 1)
        }
    }
}

/**
 * Navigates to the previous page in the intro
 */
fun navigateToPreviousPage(pagerState: PagerState, scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch {
        if (pagerState.currentPage > 0) {
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
        }
    }
}
