package dev.riyadm.applock.features.intruder.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.util.Log
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavController
import dev.riyadm.applock.R
import dev.riyadm.applock.features.applist.ui.AppIconCache
import dev.riyadm.applock.features.intruder.data.IntruderPhoto
import dev.riyadm.applock.features.intruder.data.IntruderPhotoStore
import dev.riyadm.applock.features.intruder.data.IntruderSettings
import dev.riyadm.applock.ui.components.SecureWindowEffect
import dev.riyadm.applock.ui.motion.pressScale
import dev.riyadm.applock.ui.motion.rememberAppHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "IntruderPhotos"
private const val THUMBNAIL_EDGE_PX = 480
private const val FULL_EDGE_PX = 2048

@Immutable
private data class AppDisplay(val label: String, val icon: ImageBitmap?)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IntruderPhotosScreen(navController: NavController) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    val snackbarHostState = remember { SnackbarHostState() }
    val featureEnabled = remember { IntruderSettings(context).isEnabled }

    val changes by IntruderPhotoStore.changes.collectAsStateWithLifecycle()
    var photos by remember { mutableStateOf<List<IntruderPhoto>?>(null) }
    var apps by remember { mutableStateOf<Map<String, AppDisplay>>(emptyMap()) }
    var viewerPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteAll by remember { mutableStateOf(false) }

    LaunchedEffect(changes) {
        val loaded = withContext(Dispatchers.IO) {
            IntruderPhotoStore.applyRetention(context)
            IntruderPhotoStore.list(context)
        }
        val known = apps
        val resolved = withContext(Dispatchers.IO) {
            loaded.asSequence().map { it.packageName }.distinct()
                .filterNot { it in known }
                .associateWith { resolveApp(context, it) }
                .toMap()
        }
        apps = known + resolved
        photos = loaded
    }

    SecureWindowEffect()

    fun delete(photo: IntruderPhoto) {
        scope.launch {
            val deleted = withContext(Dispatchers.IO) { IntruderPhotoStore.delete(photo) }
            if (deleted) {
                IntruderThumbnails.evict(photo.file)
                haptics.confirm()
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(resources.getString(R.string.intruder_photo_deleted))
            } else {
                haptics.reject()
            }
        }
    }

    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
            title = { Text(stringResource(R.string.intruder_delete_all_title)) },
            text = { Text(stringResource(R.string.intruder_delete_all_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAll = false
                        scope.launch {
                            withContext(Dispatchers.IO) { IntruderPhotoStore.deleteAll(context) }
                            IntruderThumbnails.clear()
                            haptics.confirm()
                            snackbarHostState.showSnackbar(resources.getString(R.string.intruder_all_deleted))
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.intruder_delete_all_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAll = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val onBack = dropUnlessResumed { navController.popBackStack() }
    val total = photos?.size ?: 0

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.intruder_photos_title)) },
                    subtitle = if (total > 0) {
                        { Text(pluralStringResource(R.plurals.intruder_photo_count, total, total)) }
                    } else null,
                    navigationIcon = {
                        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.settings_screen_back_cd)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showDeleteAll = true },
                            enabled = total > 0,
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(R.string.intruder_delete_all_cd)
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { innerPadding ->
            val current = photos
            if (current == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) { ContainedLoadingIndicator() }
                return@Scaffold
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 32.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!featureEnabled) {
                    item(key = "disabled", span = { GridItemSpan(maxLineSpan) }, contentType = "banner") {
                        DisabledBanner()
                    }
                }
                if (current.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                        EmptyState(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp)
                        )
                    }
                    return@LazyVerticalGrid
                }
                items(current, key = { it.id }, contentType = { "photo" }) { photo ->
                    PhotoTile(
                        photo = photo,
                        app = apps[photo.packageName],
                        onClick = {
                            haptics.press()
                            viewerPhotoId = photo.id
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        val current = photos.orEmpty()
        val initialIndex = current.indexOfFirst { it.id == viewerPhotoId }
        LaunchedEffect(photos, viewerPhotoId) {
            if (photos != null && viewerPhotoId != null && initialIndex < 0) viewerPhotoId = null
        }
        AnimatedVisibility(
            visible = viewerPhotoId != null && initialIndex >= 0,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.9f),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.94f)
        ) {
            PhotoViewer(
                photos = current,
                initialIndex = initialIndex.coerceAtLeast(0),
                apps = apps,
                onPageChanged = { viewerPhotoId = it.id },
                onClose = { viewerPhotoId = null },
                onShare = { sharePhoto(context, it) },
                onDelete = { photo ->
                    val index = current.indexOf(photo)
                    val next = current.getOrNull(index + 1) ?: current.getOrNull(index - 1)
                    viewerPhotoId = next?.id
                    delete(photo)
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (viewerPhotoId != null) 96.dp else 0.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PhotoTile(
    photo: IntruderPhoto,
    app: AppDisplay?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val time = remember(photo.timestamp) { formatTime(context, photo.timestamp) }
    val label = app?.label ?: photo.packageName

    Surface(
        onClick = onClick,
        modifier = modifier.pressScale(interactionSource, pressedScale = 0.96f),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        interactionSource = interactionSource
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .padding(4.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                PhotoImage(
                    file = photo.file,
                    maxEdge = THUMBNAIL_EDGE_PX,
                    contentDescription = stringResource(R.string.intruder_photo_cd, label, time),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                AttemptsBadge(
                    attempts = photo.attempts,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(app, size = 28)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AttemptsBadge(attempts: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Text(
            text = pluralStringResource(R.plurals.intruder_attempts_short, attempts, attempts),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PhotoViewer(
    photos: List<IntruderPhoto>,
    initialIndex: Int,
    apps: Map<String, AppDisplay>,
    onPageChanged: (IntruderPhoto) -> Unit,
    onClose: () -> Unit,
    onShare: (IntruderPhoto) -> Unit,
    onDelete: (IntruderPhoto) -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }

    BackHandler(onBack = onClose)
    LightSystemBarIconsEffect()

    LaunchedEffect(pagerState, photos) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            photos.getOrNull(page)?.let(onPageChanged)
        }
    }

    val page = pagerState.currentPage.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
    val photo = photos.getOrNull(page)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            key = { photos[it].id },
            modifier = Modifier.fillMaxSize()
        ) { index ->
            val item = photos[index]
            val label = apps[item.packageName]?.label ?: item.packageName
            PhotoImage(
                file = item.file,
                maxEdge = FULL_EDGE_PX,
                contentDescription = stringResource(
                    R.string.intruder_photo_cd,
                    label,
                    formatTime(context, item.timestamp)
                ),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                loadingColor = Color.White
            )
        }

        if (photo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onClose,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.16f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.intruder_viewer_close_cd))
                }
                Spacer(Modifier.width(12.dp))
                AppIcon(apps[photo.packageName], size = 32)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = apps[photo.packageName]?.label ?: photo.packageName,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.intruder_viewer_details,
                            remember(photo.timestamp) { formatTime(context, photo.timestamp) },
                            pluralStringResource(R.plurals.intruder_attempts_long, photo.attempts, photo.attempts)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalFloatingToolbar(
                expanded = true,
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                IconButton(
                    onClick = {
                        haptics.press()
                        onShare(photo)
                    },
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.intruder_share))
                }
                IconButton(
                    onClick = { onDelete(photo) },
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.intruder_delete))
                }
            }
        }
    }
}

/** Keeps status and navigation bar icons light while the black photo viewer is showing. */
@Composable
private fun LightSystemBarIconsEffect() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNavigation
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PhotoImage(
    file: File,
    maxEdge: Int,
    contentDescription: String,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    loadingColor: Color = MaterialTheme.colorScheme.primary
) {
    val bitmap by produceState(IntruderThumbnails.peek(file, maxEdge), file, maxEdge) {
        if (value == null) value = withContext(Dispatchers.IO) { IntruderThumbnails.load(file, maxEdge) }
    }
    val alpha by animateFloatAsState(
        targetValue = if (bitmap != null) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "photoAlpha"
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.alpha = alpha }
            )
        } else {
            LoadingIndicator(color = loadingColor, modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun AppIcon(app: AppDisplay?, size: Int) {
    val icon = app?.icon
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(size.dp))
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size((size * 0.6f).dp)
            )
        }
    }
}

@Composable
private fun DisabledBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.NoPhotography,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.intruder_disabled_banner),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "intruderEmpty")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )
    val shape = MaterialShapes.Cookie12Sided.toShape()

    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .graphicsLayer { rotationZ = rotation }
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
            )
            Icon(
                imageVector = Icons.Rounded.SentimentSatisfied,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(72.dp)
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.intruder_empty_title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.intruder_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Decoded photos, bounded by memory. Loading does disk I/O; call off the main thread. */
private object IntruderThumbnails {
    private val cache = object : LruCache<String, ImageBitmap>(maxBytes()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private fun maxBytes(): Int =
        (Runtime.getRuntime().maxMemory() / 10).coerceAtMost(48L * 1024 * 1024).toInt()

    private fun key(file: File, maxEdge: Int) = "${file.name}@$maxEdge"

    fun peek(file: File, maxEdge: Int): ImageBitmap? = cache.get(key(file, maxEdge))

    fun load(file: File, maxEdge: Int): ImageBitmap? {
        cache.get(key(file, maxEdge))?.let { return it }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
            val bitmap = BitmapFactory.decodeFile(
                file.path,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return null
            bitmap.asImageBitmap().also { cache.put(key(file, maxEdge), it) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not decode ${file.name}", e)
            null
        }
    }

    fun evict(file: File) {
        cache.remove(key(file, THUMBNAIL_EDGE_PX))
        cache.remove(key(file, FULL_EDGE_PX))
    }

    fun clear() = cache.evictAll()
}

private fun sharePhoto(context: Context, photo: IntruderPhoto) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photo.file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.intruder_share_title))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    } catch (e: Exception) {
        Log.w(TAG, "Share failed", e)
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.intruder_share_failed),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

private fun formatTime(context: Context, timestamp: Long): String =
    DateUtils.formatDateTime(
        context,
        timestamp,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
    )

private fun resolveApp(context: Context, packageName: String): AppDisplay = try {
    val info = context.packageManager.getApplicationInfo(packageName, 0)
    AppDisplay(AppIconCache.getLabel(context, info), AppIconCache.getIcon(context, info))
} catch (_: PackageManager.NameNotFoundException) {
    AppDisplay(packageName, null)
} catch (_: Exception) {
    AppDisplay(packageName, null)
}
