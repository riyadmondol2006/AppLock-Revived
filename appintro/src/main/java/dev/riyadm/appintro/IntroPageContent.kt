package dev.riyadm.appintro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Displays the text block of a single intro page (title, description and optional extra content).
 * The hero illustration is drawn by [AppIntro] so that it can morph between pages.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IntroPageContent(
    page: IntroPage,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    val contentVisible = remember { MutableTransitionState(false) }

    LaunchedEffect(isVisible) {
        contentVisible.targetState = isVisible
    }

    if (page.customContent != null) {
        Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
            AnimatedVisibility(
                visibleState = contentVisible,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(200))
            ) {
                page.customContent.invoke()
            }
        }
        return
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        AnimatedVisibility(
            visibleState = contentVisible,
            enter = fadeIn(tween(300, delayMillis = 60)) + slideInVertically(
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { it / 2 },
            exit = fadeOut(tween(150))
        ) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() }
            )
        }

        AnimatedVisibility(
            visibleState = contentVisible,
            enter = fadeIn(tween(300, delayMillis = 140)) + slideInVertically(
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) { it / 3 },
            exit = fadeOut(tween(150))
        ) {
            Text(
                text = page.description.trim(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
            )
        }

        if (page.content != null) {
            AnimatedVisibility(
                visibleState = contentVisible,
                enter = fadeIn(tween(300, delayMillis = 220)) + slideInVertically(
                    spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) { it / 4 },
                exit = fadeOut(tween(150))
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .widthIn(max = 520.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    page.content.invoke(this)
                }
            }
        }
    }
}
