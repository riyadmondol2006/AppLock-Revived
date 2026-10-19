package dev.riyadm.applock.features.applist.ui

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.riyadm.applock.R
import dev.riyadm.applock.core.broadcast.AppLockServiceStarter
import dev.riyadm.applock.core.broadcast.DeviceAdmin
import dev.riyadm.applock.core.navigation.Screen
import dev.riyadm.applock.core.ui.ExpressiveEmptyState
import dev.riyadm.applock.core.ui.ExpressiveSearch
import dev.riyadm.applock.core.ui.SectionHeader
import dev.riyadm.applock.ui.motion.ExpressiveMotion
import dev.riyadm.applock.ui.motion.rememberAppHaptics
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.core.utils.openAccessibilitySettings
import dev.riyadm.applock.data.repository.BackendImplementation
import dev.riyadm.applock.features.applist.domain.InstalledApp
import dev.riyadm.applock.services.AppLockManager
import dev.riyadm.applock.features.applist.ui.ProtectionStatus.State
import dev.riyadm.applock.ui.components.AccessibilityServiceGuideDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

private const val SHIZUKU_PERMISSION_REQUEST_CODE = 423

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    mainViewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val repository = remember(context) { context.appLockRepository() }
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val searchResults by mainViewModel.searchResults.collectAsStateWithLifecycle()
    val lockedPackages by mainViewModel.lockedPackages.collectAsStateWithLifecycle()
    val textFieldState = rememberTextFieldState()
    val searchBarState = rememberSearchBarState()
    val selection by mainViewModel.selection.collectAsStateWithLifecycle()
    val selectionMode = selection.isNotEmpty()

    var status by remember { mutableStateOf(context.readProtectionStatus()) }
    var statusCheck by remember { mutableIntStateOf(0) }
    var showAccessibilityGuide by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        mainViewModel.refresh()
        statusCheck++
    }

    LaunchedEffect(statusCheck) {
        status = withContext(Dispatchers.IO) { context.readProtectionStatus() }
        // Services connect asynchronously right after a permission is granted
        if (status.state == State.SERVICE_STOPPED) {
            delay(1_500)
            status = withContext(Dispatchers.IO) { context.readProtectionStatus() }
        }
    }

    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, result ->
            if (result == PackageManager.PERMISSION_GRANTED) {
                AppLockServiceStarter.startAppropriateServices(context, repository)
            }
            statusCheck++
        }
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
        onDispose { runCatching { Shizuku.removeRequestPermissionResultListener(listener) } }
    }

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect(mainViewModel::onSearchQueryChange)
    }

    BackHandler(enabled = selectionMode) { mainViewModel.clearSelection() }

    // Stable callbacks so rows can skip recomposition when other rows change
    val onToggleLock = remember(mainViewModel, haptics) {
        { app: InstalledApp, locked: Boolean ->
            haptics.toggle(locked)
            mainViewModel.setLocked(app.packageName, locked)
        }
    }
    val onSelect = remember(mainViewModel, haptics) {
        { packageName: String, selected: Boolean ->
            haptics.tick()
            mainViewModel.setSelected(packageName, selected)
        }
    }
    val onStartSelection = remember(mainViewModel, haptics) {
        { app: InstalledApp ->
            haptics.longPress()
            mainViewModel.startSelection(app.packageName)
        }
    }

    fun handlePermission(permission: MissingPermission) {
        when (permission) {
            MissingPermission.ACCESSIBILITY -> showAccessibilityGuide = true
            else -> context.requestPermission(permission)
        }
    }

    fun fixProtection() {
        when (status.state) {
            State.NEEDS_SETUP -> status.blockingPermission?.let(::handlePermission)
            State.PAUSED -> {
                repository.setProtectEnabled(true)
                haptics.toggle(true)
                statusCheck++
            }

            State.SERVICE_STOPPED -> {
                if (status.backend == BackendImplementation.ACCESSIBILITY) {
                    showAccessibilityGuide = true
                } else {
                    AppLockServiceStarter.startAppropriateServices(context, repository)
                    statusCheck++
                }
            }

            State.ACTIVE -> Unit
        }
    }

    fun showBulkResult(changed: Set<String>, locked: Boolean) {
        if (changed.isEmpty()) return
        haptics.confirm()
        val message = context.resources.getQuantityString(
            if (locked) R.plurals.applist_bulk_locked else R.plurals.applist_bulk_unlocked,
            changed.size,
            changed.size
        )
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = context.getString(R.string.applist_undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                mainViewModel.setLocked(changed, !locked)
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MainTopBar(
                scrollBehavior = scrollBehavior,
                subtitle = pluralStringResource(
                    R.plurals.applist_locked_count,
                    uiState.lockedCount,
                    uiState.lockedCount
                ),
                onOpenSettings = {
                    navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                },
                onOpenTriggerExclusions = {
                    navController.navigate(Screen.TriggerExclusions.route) { launchSingleTop = true }
                },
                onOpenAntiUninstall = {
                    navController.navigate(Screen.AntiUninstall.route) { launchSingleTop = true }
                },
                onLockAllNow = {
                    AppLockManager.lockAllNow()
                    haptics.confirm()
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(context.getString(R.string.applist_locked_all_now))
                    }
                }
            )
        }
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection)
                )
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
                    .imePadding(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + if (selectionMode) 112.dp else 24.dp
                )
            ) {
                item(key = "status", contentType = "status") {
                    ProtectionStatusCard(
                        status = status,
                        lockedCount = uiState.lockedCount,
                        onProtectionToggle = { enabled ->
                            repository.setProtectEnabled(enabled)
                            haptics.toggle(enabled)
                            statusCheck++
                        },
                        onFix = ::fixProtection,
                        onFixAntiUninstall = {
                            status.antiUninstallPermission?.let(::handlePermission)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                stickyHeader(key = "controls", contentType = "controls") {
                    ListControls(
                        search = {
                            ExpressiveSearch(
                                textFieldState = textFieldState,
                                searchBarState = searchBarState,
                                placeholder = stringResource(R.string.applist_search_placeholder),
                                backContentDescription = stringResource(R.string.applist_navigate_back_cd),
                                clearContentDescription = stringResource(R.string.applist_search_clear_cd),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                SearchResults(
                                    query = textFieldState.text.toString(),
                                    results = searchResults,
                                    lockedPackages = lockedPackages,
                                    onToggle = onToggleLock
                                )
                            }
                        },
                        filter = uiState.filter,
                        onFilterChange = {
                            haptics.tick()
                            mainViewModel.setFilter(it)
                            scope.launch { if (listState.firstVisibleItemIndex > 1) listState.scrollToItem(1) }
                        },
                        sort = uiState.sort,
                        onSortChange = mainViewModel::setSort,
                        showSystemFilter = uiState.showSystemFilter
                    )
                }

                when {
                    uiState.isLoading -> item(key = "loading", contentType = "loading") {
                        LoadingState(Modifier.animateItem())
                    }

                    uiState.isEmpty -> item(key = "empty", contentType = "empty") {
                        EmptyListState(
                            filter = uiState.filter,
                            modifier = Modifier.animateItem()
                        )
                    }

                    else -> {
                        appSection(
                            key = "header_locked",
                            title = context.getString(
                                R.string.applist_section_count,
                                context.getString(R.string.applist_section_locked),
                                uiState.lockedApps.size
                            ),
                            apps = uiState.lockedApps,
                            locked = true,
                            selection = selection,
                            selectionMode = selectionMode,
                            onToggle = onToggleLock,
                            onSelect = onSelect,
                            onStartSelection = onStartSelection
                        )
                        appSection(
                            key = "header_other",
                            title = context.getString(
                                R.string.applist_section_count,
                                context.getString(R.string.applist_section_other),
                                uiState.otherApps.size
                            ),
                            apps = uiState.otherApps,
                            locked = false,
                            selection = selection,
                            selectionMode = selectionMode,
                            onToggle = onToggleLock,
                            onSelect = onSelect,
                            onStartSelection = onStartSelection
                        )
                    }
                }
            }

            val motion = MaterialTheme.motionScheme
            AnimatedVisibility(
                visible = selectionMode,
                enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
                exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp)
            ) {
                SelectionToolbar(
                    count = selection.size,
                    onClose = mainViewModel::clearSelection,
                    onSelectAll = {
                        haptics.tick()
                        mainViewModel.selectAllVisible()
                    },
                    onLock = { showBulkResult(mainViewModel.lockSelected(true), true) },
                    onUnlock = { showBulkResult(mainViewModel.lockSelected(false), false) }
                )
            }
        }
    }

    if (showAccessibilityGuide) {
        AccessibilityServiceGuideDialog(
            onOpenSettings = {
                showAccessibilityGuide = false
                openAccessibilitySettings(context)
            },
            onDismiss = { showAccessibilityGuide = false }
        )
    }
}

private fun LazyListScope.appSection(
    key: String,
    title: String,
    apps: List<InstalledApp>,
    locked: Boolean,
    selection: Set<String>,
    selectionMode: Boolean,
    onToggle: (InstalledApp, Boolean) -> Unit,
    onSelect: (String, Boolean) -> Unit,
    onStartSelection: (InstalledApp) -> Unit,
) {
    if (apps.isEmpty()) return
    item(key = key, contentType = "header") {
        SectionHeader(text = title, modifier = Modifier.animateItem())
    }
    itemsIndexed(apps, key = { _, app -> app.packageName }, contentType = { _, _ -> "app" }) { index, app ->
        AppSwitchListItem(
            app = app,
            checked = locked,
            onCheckedChange = { onToggle(app, it) },
            index = index,
            count = apps.size,
            selectionMode = selectionMode,
            selected = app.packageName in selection,
            onSelectedChange = { onSelect(app.packageName, it) },
            onLongClick = { onStartSelection(app) },
            modifier = Modifier
                .animateItem()
                .padding(horizontal = 16.dp)
                .padding(bottom = ListItemDefaults.SegmentedGap)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    subtitle: String,
    onOpenSettings: () -> Unit,
    onOpenTriggerExclusions: () -> Unit,
    onOpenAntiUninstall: () -> Unit,
    onLockAllNow: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    LargeFlexibleTopAppBar(
        title = {
            Text(
                text = stringResource(R.string.app_name),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        subtitle = { Text(text = subtitle, maxLines = 1) },
        actions = {
            IconButton(onClick = onOpenSettings, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.main_screen_settings_cd)
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.applist_more_options_cd)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.applist_menu_lock_all_now)) },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onLockAllNow()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.applist_menu_trigger_exclusions)) },
                        leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onOpenTriggerExclusions()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.applist_menu_prevent_uninstall)) },
                        leadingIcon = { Icon(Icons.Rounded.Shield, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onOpenAntiUninstall()
                        }
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun ListControls(
    search: @Composable () -> Unit,
    filter: AppFilter,
    onFilterChange: (AppFilter) -> Unit,
    sort: AppSort,
    onSortChange: (AppSort) -> Unit,
    showSystemFilter: Boolean,
) {
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntSize>()
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        search()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val filters = buildList {
                add(AppFilter.ALL to R.string.applist_filter_all)
                add(AppFilter.LOCKED to R.string.applist_filter_locked)
                add(AppFilter.UNLOCKED to R.string.applist_filter_unlocked)
                if (showSystemFilter) add(AppFilter.SYSTEM to R.string.applist_filter_system)
            }
            filters.forEach { (value, label) ->
                val selected = filter == value
                FilterChip(
                    selected = selected,
                    onClick = { if (!selected) onFilterChange(value) },
                    modifier = Modifier.animateContentSize(spatial),
                    label = { Text(stringResource(label)) },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null
                )
            }
            SortChip(sort = sort, onSortChange = onSortChange)
        }
    }
}

@Composable
private fun SortChip(sort: AppSort, onSortChange: (AppSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val sortDescription = stringResource(R.string.applist_sort_cd)
    Box {
        FilterChip(
            selected = sort != AppSort.NAME,
            onClick = { expanded = true },
            modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec()),
            label = {
                Text(
                    stringResource(
                        when (sort) {
                            AppSort.NAME -> R.string.applist_sort_name
                            AppSort.RECENT -> R.string.applist_sort_recent
                        }
                    )
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Rounded.SwapVert,
                    contentDescription = sortDescription,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                AppSort.NAME to R.string.applist_sort_name,
                AppSort.RECENT to R.string.applist_sort_recent
            ).forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    trailingIcon = if (value == sort) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else null,
                    onClick = {
                        expanded = false
                        onSortChange(value)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectionToolbar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        leadingContent = {
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.applist_action_close_selection)
                )
            }
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    val up = targetState > initialState
                    val motion = ExpressiveMotion
                    (slideInVertically(motion.fastSpatial()) { if (up) it else -it } + fadeIn(motion.fastEffects()))
                        .togetherWith(slideOutVertically(motion.fastSpatial()) { if (up) -it else it } + fadeOut(motion.fastEffects()))
                },
                label = "selectedCount"
            ) { value ->
                Text(
                    text = pluralStringResource(R.plurals.applist_selected_count, value, value),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    ) {
        IconButton(onClick = onSelectAll, shapes = IconButtonDefaults.shapes()) {
            Icon(
                Icons.Rounded.SelectAll,
                contentDescription = stringResource(R.string.applist_action_select_all)
            )
        }
        IconButton(onClick = onUnlock, shapes = IconButtonDefaults.shapes()) {
            Icon(
                Icons.Rounded.LockOpen,
                contentDescription = stringResource(R.string.applist_action_unlock)
            )
        }
        FilledIconButton(onClick = onLock, shapes = IconButtonDefaults.shapes()) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.applist_action_lock)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ContainedLoadingIndicator(modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.applist_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyListState(filter: AppFilter, modifier: Modifier = Modifier) {
    if (filter == AppFilter.LOCKED) {
        ExpressiveEmptyState(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.applist_empty_locked_title),
            description = stringResource(R.string.applist_empty_locked_desc),
            polygon = MaterialShapes.Cookie6Sided,
            modifier = modifier
        )
    } else {
        ExpressiveEmptyState(
            icon = Icons.Rounded.SearchOff,
            title = stringResource(R.string.applist_empty_generic_title),
            description = stringResource(R.string.applist_empty_generic_desc),
            modifier = modifier
        )
    }
}

/** Results shown inside the expanded full-screen search. */
@Composable
private fun SearchResults(
    query: String,
    results: List<InstalledApp>,
    lockedPackages: Set<String>,
    onToggle: (InstalledApp, Boolean) -> Unit,
) {
    if (query.isBlank()) return
    if (results.isEmpty()) {
        ExpressiveEmptyState(
            icon = Icons.Rounded.SearchOff,
            title = stringResource(R.string.applist_empty_search_title, query.trim()),
            description = stringResource(R.string.applist_empty_search_desc)
        )
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        itemsIndexed(
            results,
            key = { _, app -> app.packageName },
            contentType = { _, _ -> "app" }
        ) { index, app ->
            AppSwitchListItem(
                app = app,
                checked = app.packageName in lockedPackages,
                onCheckedChange = { onToggle(app, it) },
                index = index,
                count = results.size,
                modifier = Modifier
                    .animateItem()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = ListItemDefaults.SegmentedGap)
            )
        }
    }
}

private fun Context.requestPermission(permission: MissingPermission) {
    try {
        when (permission) {
            MissingPermission.OVERLAY -> startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = "package:$packageName".toUri()
                }
            )

            MissingPermission.ACCESSIBILITY -> openAccessibilitySettings(this)

            MissingPermission.USAGE_STATS -> startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

            MissingPermission.SHIZUKU -> requestShizukuPermission()

            MissingPermission.DEVICE_ADMIN -> {
                val component = ComponentName(this, DeviceAdmin::class.java)
                startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        getString(R.string.main_screen_device_admin_explanation)
                    )
                })
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("MainScreen", "Couldn't open settings for $permission", e)
    }
}

private fun Context.requestShizukuPermission() {
    try {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(
                this,
                getString(R.string.main_screen_shizuku_not_available_toast),
                Toast.LENGTH_LONG
            ).show()
        } else if (Shizuku.isPreV11()) {
            Toast.makeText(
                this,
                getString(R.string.main_screen_shizuku_manual_permission_toast),
                Toast.LENGTH_LONG
            ).show()
        } else {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        }
    } catch (_: Exception) {
        Toast.makeText(
            this,
            getString(R.string.main_screen_shizuku_not_available_toast),
            Toast.LENGTH_LONG
        ).show()
    }
}
