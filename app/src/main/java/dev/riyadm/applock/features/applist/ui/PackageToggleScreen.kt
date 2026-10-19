package dev.riyadm.applock.features.applist.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.consumeWindowInsets
import dev.riyadm.applock.R
import dev.riyadm.applock.core.ui.ExpressiveEmptyState
import dev.riyadm.applock.core.ui.ExpressiveSearch
import dev.riyadm.applock.core.ui.SectionHeader
import dev.riyadm.applock.features.applist.domain.InstalledApp
import dev.riyadm.applock.ui.motion.rememberAppHaptics

/**
 * Scaffold shared by the trigger-exclusions and prevent-uninstall screens: flexible top bar,
 * expressive search, a hero slot, and segmented groups for checked / manual / other apps.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PackageToggleScreen(
    title: String,
    subtitle: String,
    viewModel: PackageToggleViewModel,
    checkedSectionTitle: String,
    checkedThumbIcon: ImageVector,
    addPackageDescription: String,
    onBack: () -> Unit,
    onToggle: (packageName: String, checked: Boolean) -> Unit,
    onAddPackage: (String) -> Unit,
    modifier: Modifier = Modifier,
    togglesEnabled: Boolean = true,
    pendingPackages: Set<String> = emptySet(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    extraActions: @Composable () -> Unit = {},
    hero: @Composable ColumnScope.() -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val checked by viewModel.checked.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val haptics = rememberAppHaptics()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val textFieldState = rememberTextFieldState()
    val searchBarState = rememberSearchBarState()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    val installedTitle = stringResource(R.string.applist_section_installed)
    val manualTitle = stringResource(R.string.applist_section_manual)

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect(viewModel::onSearchQueryChange)
    }

    val toggle = remember(onToggle, haptics) {
        { packageName: String, value: Boolean ->
            haptics.toggle(value)
            onToggle(packageName, value)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(title, maxLines = 1) },
                subtitle = { Text(subtitle, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.applist_navigate_back_cd)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.applist_add_package_cd)
                        )
                    }
                    extraActions()
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .imePadding(),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            )
        ) {
            item(key = "hero", contentType = "hero") {
                Column(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                ) { hero() }
            }

            stickyHeader(key = "controls", contentType = "controls") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(top = 8.dp, bottom = 4.dp)
                ) {
                    ExpressiveSearch(
                        textFieldState = textFieldState,
                        searchBarState = searchBarState,
                        placeholder = stringResource(R.string.applist_search_placeholder),
                        backContentDescription = stringResource(R.string.applist_navigate_back_cd),
                        clearContentDescription = stringResource(R.string.applist_search_clear_cd),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        PackageSearchResults(
                            query = textFieldState.text.toString(),
                            results = searchResults,
                            checked = checked,
                            enabled = togglesEnabled,
                            pendingPackages = pendingPackages,
                            checkedThumbIcon = checkedThumbIcon,
                            onToggle = toggle
                        )
                    }
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.showSystemApps,
                            onClick = {
                                haptics.tick()
                                viewModel.setShowSystemApps(!uiState.showSystemApps)
                            },
                            modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec()),
                            label = { Text(stringResource(R.string.applist_show_system_apps)) },
                            leadingIcon = if (uiState.showSystemApps) {
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
                }
            }

            if (uiState.isLoading) {
                item(key = "loading", contentType = "loading") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp)
                            .animateItem(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ContainedLoadingIndicator(Modifier.size(72.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.applist_loading),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                if (uiState.manualPackages.isNotEmpty()) {
                    item(key = "header_manual", contentType = "header") {
                        SectionHeader(manualTitle, Modifier.animateItem())
                    }
                    itemsIndexed(
                        uiState.manualPackages,
                        key = { _, pkg -> "manual:$pkg" },
                        contentType = { _, _ -> "manual" }
                    ) { index, pkg ->
                        ManualPackageListItem(
                            packageName = pkg,
                            onRemove = { toggle(pkg, false) },
                            index = index,
                            count = uiState.manualPackages.size,
                            removeContentDescription = stringResource(R.string.applist_remove_package_cd, pkg),
                            enabled = togglesEnabled && pkg !in pendingPackages,
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = ListItemDefaults.SegmentedGap)
                        )
                    }
                }
                toggleSection(
                    key = "header_checked",
                    title = checkedSectionTitle,
                    apps = uiState.checkedApps,
                    checked = true,
                    enabled = togglesEnabled,
                    pendingPackages = pendingPackages,
                    checkedThumbIcon = checkedThumbIcon,
                    onToggle = toggle
                )
                toggleSection(
                    key = "header_other",
                    title = installedTitle,
                    apps = uiState.otherApps,
                    checked = false,
                    enabled = togglesEnabled,
                    pendingPackages = pendingPackages,
                    checkedThumbIcon = checkedThumbIcon,
                    onToggle = toggle
                )
            }
        }
    }

    if (showAddDialog) {
        AddPackageDialog(
            description = addPackageDescription,
            onAdd = {
                showAddDialog = false
                onAddPackage(it)
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

private fun LazyListScope.toggleSection(
    key: String,
    title: String,
    apps: List<InstalledApp>,
    checked: Boolean,
    enabled: Boolean,
    pendingPackages: Set<String>,
    checkedThumbIcon: ImageVector,
    onToggle: (String, Boolean) -> Unit,
) {
    if (apps.isEmpty()) return
    item(key = key, contentType = "header") {
        SectionHeader(title, Modifier.animateItem())
    }
    itemsIndexed(apps, key = { _, app -> app.packageName }, contentType = { _, _ -> "app" }) { index, app ->
        AppSwitchListItem(
            app = app,
            checked = checked,
            onCheckedChange = { onToggle(app.packageName, it) },
            index = index,
            count = apps.size,
            enabled = enabled && app.packageName !in pendingPackages,
            checkedThumbIcon = checkedThumbIcon,
            modifier = Modifier
                .animateItem()
                .padding(horizontal = 16.dp)
                .padding(bottom = ListItemDefaults.SegmentedGap)
        )
    }
}

@Composable
private fun PackageSearchResults(
    query: String,
    results: List<InstalledApp>,
    checked: Set<String>,
    enabled: Boolean,
    pendingPackages: Set<String>,
    checkedThumbIcon: ImageVector,
    onToggle: (String, Boolean) -> Unit,
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
        itemsIndexed(results, key = { _, app -> app.packageName }, contentType = { _, _ -> "app" }) { index, app ->
            AppSwitchListItem(
                app = app,
                checked = app.packageName in checked,
                onCheckedChange = { onToggle(app.packageName, it) },
                index = index,
                count = results.size,
                enabled = enabled && app.packageName !in pendingPackages,
                checkedThumbIcon = checkedThumbIcon,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = ListItemDefaults.SegmentedGap)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AddPackageDialog(
    description: String,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    val submit = {
        if (PackageToggleViewModel.isValidPackageName(value)) onAdd(value.trim()) else showError = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.applist_add_package_title)) },
        text = {
            Column {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        showError = false
                    },
                    label = { Text(stringResource(R.string.applist_package_name_label)) },
                    placeholder = { Text(stringResource(R.string.applist_package_name_placeholder)) },
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(stringResource(R.string.applist_package_name_invalid)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = submit,
                enabled = value.isNotBlank(),
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(R.string.applist_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.applist_cancel))
            }
        }
    )
}
