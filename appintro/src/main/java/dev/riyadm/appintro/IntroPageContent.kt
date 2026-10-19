package dev.riyadm.appintro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Displays the content of a single intro page with enhanced Material 3 design and animations
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IntroPageContent(
    page: IntroPage,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    val backgroundColor = page.backgroundColor ?: MaterialTheme.colorScheme.primaryContainer
    val contentColor = page.contentColor ?: MaterialTheme.colorScheme.onPrimaryContainer

    // Optimization: Use a single transition state instead of multiple
    val contentVisible = remember { MutableTransitionState(false) }

    // Trigger animations when page becomes visible - simplified animation trigger
    LaunchedEffect(isVisible) {
        contentVisible.targetState = isVisible
    }

    Box(
        modifier = modifier
            .background(backgroundColor)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // If custom content is provided, use it instead of the default layout
        if (page.customContent != null) {
            AnimatedVisibility(
                visibleState = contentVisible,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(200))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    page.customContent.invoke()
                }
            }
        } else {
            // Default layout
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp) // Extra padding for bottom navigation
            ) {
                // Icon with optimized animation - use a more performant animation spec
                AnimatedVisibility(
                    visibleState = contentVisible,
                    enter = fadeIn(animationSpec = tween(300)) +
                            scaleIn(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow // Lower stiffness for smoother animation
                                ),
                                initialScale = 0.8f // Start closer to final size
                            ),
                    exit = fadeOut(animationSpec = tween(200))
                ) {
                    if (page.illustration != null) {
                        Box(
                            modifier = Modifier.padding(bottom = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            page.illustration.invoke()
                        }
                    } else if (page.icon != null) {
                        Surface(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .clickable(true) {},
                            shape = CircleShape,
                            color = contentColor.copy(alpha = 0.15f),

                            ) {
                            Icon(
                                imageVector = page.icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier
                                    .padding(32.dp)
                                    .size(76.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Title with sequential animation - slightly delayed
                AnimatedVisibility(
                    visibleState = contentVisible,
                    enter = fadeIn(animationSpec = tween(300, delayMillis = 100)) +
                            slideInVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                initialOffsetY = { it / 3 } // Smaller initial offset for smoother animation
                            ),
                    exit = fadeOut(animationSpec = tween(200))
                ) {
                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Description with the most delayed animation
                AnimatedVisibility(
                    visibleState = contentVisible,
                    enter = fadeIn(animationSpec = tween(300, delayMillis = 200)) +
                            slideInVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                initialOffsetY = { it / 4 } // Even smaller initial offset
                            ),
                    exit = fadeOut(animationSpec = tween(150))
                ) {
                    Text(
                        text = page.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )
                }
            }
        }
    }
}
