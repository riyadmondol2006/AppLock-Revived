package dev.riyadm.applock.features.history.ui

import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HistoryToggleOff
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavController
import dev.riyadm.applock.AppLockApplication
import dev.riyadm.applock.R
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.UnlockLogRepository
import dev.riyadm.applock.features.applist.ui.AppIconCache
import dev.riyadm.applock.features.settings.ui.SettingsSectionHeader
import dev.riyadm.applock.features.settings.ui.settingsItemColors
import dev.riyadm.applock.ui.motion.rememberSettleProgress
import dev.riyadm.applock.ui.motion.rememberAppHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

@Immutable
private data class AppDisplay(val label: String, val icon: ImageBitmap?)

@Immutable
private data class DayGroup(val dayStart: Long, val entries: List<UnlockLogRepository.Entry>)

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val EMPTY_ENTRY_SPIN = 90f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UnlockHistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val repository = remember {
        (context.applicationContext as? AppLockApplication)?.appLockRepository
            ?: AppLockRepository(context.applicationContext)
    }
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    val timeFormat = remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    var entries by remember { mutableStateOf<List<UnlockLogRepository.Entry>?>(null) }
    var apps by remember { mutableStateOf<Map<String, AppDisplay>>(emptyMap()) }
    var loggingEnabled by remember { mutableStateOf(repository.isUnlockLogEnabled()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showClearDialog by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        refreshKey++
        onPauseOrDispose { }
    }

    LaunchedEffect(refreshKey) {
        loggingEnabled = repository.isUnlockLogEnabled()
        val loaded = withContext(Dispatchers.IO) { repository.unlockLog.getEntries() }
        val known = apps
        val resolved = withContext(Dispatchers.IO) {
            loaded.asSequence().map { it.packageName }.distinct()
                .filterNot { it in known }
                .associateWith { resolveApp(context, it) }
        }
        apps = known + resolved
        entries = loaded
    }

    val groups = remember(entries) { entries.orEmpty().groupByDay() }
    val now = remember(entries) { System.currentTimeMillis() }
    val failedLastDay = remember(entries) {
        entries.orEmpty().count { !it.success && it.timestamp >= now - DAY_MS }
    }
    val successLastDay = remember(entries) {
        entries.orEmpty().count { it.success && it.timestamp >= now - DAY_MS }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
            title = { Text(stringResource(R.string.history_clear_dialog_title)) },
            text = { Text(stringResource(R.string.history_clear_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        scope.launch {
                            withContext(Dispatchers.IO) { repository.unlockLog.clear() }
                            entries = emptyList()
                            haptics.confirm()
                            snackbarHostState.showSnackbar(context.getString(R.string.history_cleared))
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.history_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val onBack = dropUnlessResumed { navController.popBackStack() }
    val total = entries?.size ?: 0

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                subtitle = if (total > 0) {
                    { Text(pluralStringResource(R.plurals.history_entry_count, total, total)) }
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
                        onClick = { showClearDialog = true },
                        enabled = total > 0,
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.history_clear_cd)
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        val currentEntries = entries
        if (currentEntries == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { ContainedLoadingIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 32.dp
            )
        ) {
            if (!loggingEnabled) {
                item(key = "disabled", contentType = "banner") {
                    HistoryDisabledBanner(
                        onEnable = {
                            repository.setUnlockLogEnabled(true)
                            loggingEnabled = true
                            haptics.toggle(true)
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (currentEntries.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    EmptyHistory(
                        modifier = Modifier
                            .fillParentMaxHeight(0.75f)
                            .fillMaxWidth()
                    )
                }
                return@LazyColumn
            }

            item(key = "summary", contentType = "summary") {
                SummaryCard(
                    failed = failedLastDay,
                    successful = successLastDay,
                    modifier = Modifier.animateItem()
                )
            }

            groups.forEach { group ->
                item(key = "day_${group.dayStart}", contentType = "header") {
                    SettingsSectionHeader(
                        text = dayLabel(context, group.dayStart, now),
                        modifier = Modifier.animateItem()
                    )
                }
                itemsIndexed(
                    items = group.entries,
                    key = { index, entry -> "${group.dayStart}_${index}_${entry.timestamp}" },
                    contentType = { _, _ -> "entry" }
                ) { index, entry ->
                    HistoryEntryItem(
                        entry = entry,
                        app = apps[entry.packageName],
                        index = index,
                        count = group.entries.size,
                        timeFormat = timeFormat,
                        modifier = Modifier.animateItem().padding(
                            top = if (index == 0) 0.dp else ListItemDefaults.SegmentedGap
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SummaryCard(failed: Int, successful: Int, modifier: Modifier = Modifier) {
    val hasFailures = failed > 0
    val container = if (hasFailures) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.secondaryContainer
    val content = if (hasFailures) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSecondaryContainer
    val accent = if (hasFailures) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.secondary
    val onAccent = if (hasFailures) MaterialTheme.colorScheme.onError
    else MaterialTheme.colorScheme.onSecondary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(
                        if (hasFailures) MaterialShapes.SoftBurst.toShape()
                        else MaterialShapes.Cookie9Sided.toShape()
                    )
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasFailures) Icons.Rounded.GppMaybe else Icons.Rounded.VerifiedUser,
                    contentDescription = null,
                    tint = onAccent,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = failed.toString(),
                    style = MaterialTheme.typography.displaySmallEmphasized
                )
                Text(
                    text = pluralStringResource(R.plurals.history_summary_failed, failed),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = pluralStringResource(R.plurals.history_summary_success, successful, successful),
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryEntryItem(
    entry: UnlockLogRepository.Entry,
    app: AppDisplay?,
    index: Int,
    count: Int,
    timeFormat: java.text.DateFormat,
    modifier: Modifier = Modifier
) {
    val time = remember(entry.timestamp, timeFormat) { timeFormat.format(entry.timestamp) }
    val method = stringResource(methodLabel(entry.method))
    val status = if (entry.success) stringResource(R.string.history_entry_success, method, time)
    else stringResource(R.string.history_entry_failed, method, time)

    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier,
        leadingContent = { AppIcon(app) },
        trailingContent = { StatusBadge(entry.success) },
        supportingContent = {
            Text(
                text = status,
                color = if (entry.success) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error
            )
        },
        colors = settingsItemColors(),
        content = {
            Text(
                text = app?.label ?: entry.packageName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

@Composable
private fun AppIcon(app: AppDisplay?) {
    val icon = app?.icon
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusBadge(success: Boolean) {
    val container = if (success) MaterialTheme.colorScheme.tertiaryContainer
    else MaterialTheme.colorScheme.errorContainer
    val content = if (success) MaterialTheme.colorScheme.onTertiaryContainer
    else MaterialTheme.colorScheme.onErrorContainer
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (success) Icons.Rounded.Check else Icons.Rounded.Close,
            contentDescription = stringResource(
                if (success) R.string.history_status_success_cd else R.string.history_status_failed_cd
            ),
            tint = content,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun HistoryDisabledBanner(onEnable: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.HistoryToggleOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.history_disabled_banner),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onEnable, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.history_turn_on))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    // Turns into place once; an idle empty screen renders no frames.
    val settle = rememberSettleProgress(MaterialTheme.motionScheme.slowSpatialSpec())
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
                    .graphicsLayer { rotationZ = (1f - settle.value) * -EMPTY_ENTRY_SPIN }
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(72.dp)
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.history_empty_title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.history_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun methodLabel(method: UnlockLogRepository.Method): Int = when (method) {
    UnlockLogRepository.Method.PIN -> R.string.history_method_pin
    UnlockLogRepository.Method.PATTERN -> R.string.history_method_pattern
    UnlockLogRepository.Method.PASSWORD -> R.string.history_method_password
    UnlockLogRepository.Method.BIOMETRIC -> R.string.history_method_biometric
}

private fun resolveApp(context: Context, packageName: String): AppDisplay = try {
    val info = context.packageManager.getApplicationInfo(packageName, 0)
    AppDisplay(AppIconCache.getLabel(context, info), AppIconCache.getIcon(context, info))
} catch (_: PackageManager.NameNotFoundException) {
    AppDisplay(packageName, null)
} catch (_: Exception) {
    AppDisplay(packageName, null)
}

private fun startOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun List<UnlockLogRepository.Entry>.groupByDay(): List<DayGroup> =
    groupBy { startOfDay(it.timestamp) }.map { (day, items) -> DayGroup(day, items) }

private fun dayLabel(context: Context, dayStart: Long, now: Long): String {
    val today = startOfDay(now)
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = today
        add(Calendar.DAY_OF_YEAR, -1)
    }.timeInMillis
    return when (dayStart) {
        today -> context.getString(R.string.history_today)
        yesterday -> context.getString(R.string.history_yesterday)
        else -> {
            val sameYear = Calendar.getInstance().apply { timeInMillis = dayStart }
                .get(Calendar.YEAR) == Calendar.getInstance().apply { timeInMillis = now }
                .get(Calendar.YEAR)
            var flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY
            flags = flags or if (sameYear) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_SHOW_YEAR
            DateUtils.formatDateTime(context, dayStart, flags)
        }
    }
}
