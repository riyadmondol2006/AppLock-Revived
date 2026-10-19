package dev.riyadm.applock.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val shapes = mutableListOf(
    MaterialShapes.Triangle,
    MaterialShapes.Pentagon,
    MaterialShapes.Circle,
    MaterialShapes.Arrow,
    MaterialShapes.Pill,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Heart,
    MaterialShapes.PixelTriangle,
    MaterialShapes.PixelCircle,
    MaterialShapes.Gem
).apply { shuffle() }

/** A [Shape] for one of the [MaterialShapes] polygons, remembered across compositions. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberPolygonShape(polygon: RoundedPolygon): Shape = polygon.toShape()

/**
 * An icon sitting on an expressive (cookie / burst / clover…) shaped container,
 * used for hero cards and empty states.
 */
@Composable
fun ExpressiveIconBadge(
    polygon: RoundedPolygon,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    size: Dp = 56.dp,
    iconSize: Dp = size * 0.46f,
    contentDescription: String? = null,
) {
    ExpressiveIconBadge(
        shape = rememberPolygonShape(polygon),
        icon = icon,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        size = size,
        iconSize = iconSize,
        contentDescription = contentDescription
    )
}

@Composable
fun ExpressiveIconBadge(
    shape: Shape,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    size: Dp = 56.dp,
    iconSize: Dp = size * 0.46f,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Badge whose container morphs between [MaterialShapes] polygons whenever [polygon] changes,
 * optionally with a slow idle rotation. Shape, rotation and colour are all read in the draw
 * phase, so the animation never recomposes or re-lays out the badge.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingIconBadge(
    polygon: RoundedPolygon,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    size: Dp = 64.dp,
    iconSize: Dp = size * 0.44f,
    rotate: Boolean = false,
    contentDescription: String? = null,
) {
    val motion = MaterialTheme.motionScheme
    var from by remember { mutableStateOf(polygon) }
    var to by remember { mutableStateOf(polygon) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(polygon) {
        if (polygon != to) {
            from = if (progress.value >= 0.5f) to else from
            to = polygon
            progress.snapTo(0f)
            progress.animateTo(1f, motion.slowSpatialSpec())
        }
    }
    val morph = remember(from, to) { Morph(from, to) }
    val color by animateColorAsState(containerColor, motion.defaultEffectsSpec(), label = "badge")
    val rotation = if (rotate) {
        val transition = rememberInfiniteTransition(label = "badgeRotation")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
            label = "rotation"
        )
    } else null
    val path = remember { Path() }
    val matrix = remember { Matrix() }

    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                morph.toPath(progress.value, path)
                matrix.reset()
                matrix.scale(this.size.width, this.size.height)
                path.transform(matrix)
                rotate(rotation?.value ?: 0f) { drawPath(path, color) }
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (scaleIn(motion.fastSpatialSpec(), initialScale = 0.6f) + fadeIn(motion.fastEffectsSpec()))
                    .togetherWith(scaleOut(motion.fastSpatialSpec(), targetScale = 0.6f) + fadeOut(motion.fastEffectsSpec()))
            },
            label = "badgeIcon"
        ) { target ->
            Icon(
                imageVector = target,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/** Small label shown above a group of segmented list items. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 8.dp)
    )
}

/** Centered empty / no-results state with an expressive shaped icon. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ExpressiveIconBadge(
            polygon = polygon,
            icon = icon,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            size = 88.dp
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Expressive search: a collapsed pill [SearchBar] that expands into an
 * [ExpandedFullScreenSearchBar] showing [results]. The query is cleared when search closes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveSearch(
    textFieldState: TextFieldState,
    searchBarState: SearchBarState,
    placeholder: String,
    backContentDescription: String,
    clearContentDescription: String,
    modifier: Modifier = Modifier,
    results: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(searchBarState.currentValue) {
        if (searchBarState.currentValue == SearchBarValue.Collapsed) textFieldState.clearText()
    }

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = { focusManager.clearFocus() },
            placeholder = { Text(placeholder, maxLines = 1) },
            leadingIcon = {
                if (searchBarState.currentValue == SearchBarValue.Expanded) {
                    IconButton(
                        onClick = { scope.launch { searchBarState.animateToCollapsed() } },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = backContentDescription)
                    }
                } else {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(
                        onClick = { textFieldState.clearText() },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = clearContentDescription)
                    }
                }
            },
        )
    }

    SearchBar(
        state = searchBarState,
        inputField = inputField,
        modifier = modifier.fillMaxWidth(),
        colors = SearchBarDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
    )
    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = inputField,
        colors = SearchBarDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        content = results,
    )
}
