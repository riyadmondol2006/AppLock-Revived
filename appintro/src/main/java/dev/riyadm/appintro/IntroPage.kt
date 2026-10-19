package dev.riyadm.appintro

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.graphics.shapes.RoundedPolygon

/**
 * Represents a single page in the app intro
 *
 * @param title The title of the intro page
 * @param description The description text for the page
 * @param icon Optional icon shown inside the hero shape
 * @param backgroundColor Optional container color of the hero shape (defaults to a tonal theme color)
 * @param contentColor Optional color of the hero icon (defaults to the matching on-container color)
 * @param illustration Optional composable replacing the hero shape
 * @param customContent Optional composable replacing the title/description block of the page
 * @param onNext Optional callback when user moves to the next page from this page (returns true to proceed, false to prevent navigation)
 * @param heroShape Optional Material shape for the hero; the hero morphs between the shapes of consecutive pages
 * @param nextButtonText Optional label for the next button while this page is shown
 * @param content Optional extra content shown below the description
 */
data class IntroPage(
    val title: String,
    val description: String,
    val icon: ImageVector? = null,
    val backgroundColor: Color? = null,
    val contentColor: Color? = null,
    val illustration: (@Composable () -> Unit)? = null,
    val customContent: (@Composable () -> Unit)? = null,
    val onNext: (() -> Boolean)? = null,
    val heroShape: RoundedPolygon? = null,
    val nextButtonText: String? = null,
    val content: (@Composable ColumnScope.() -> Unit)? = null
)
