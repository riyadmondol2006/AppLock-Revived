package dev.riyadm.applock.features.profiles.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavController
import dev.riyadm.applock.R
import dev.riyadm.applock.core.ui.MorphingIconBadge
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.data.repository.LockProfile
import dev.riyadm.applock.data.repository.LockProfilesRepository
import dev.riyadm.applock.data.repository.ProfileKind
import dev.riyadm.applock.features.settings.ui.SettingsSectionHeader
import dev.riyadm.applock.features.settings.ui.UNLOCK_DURATION_OPTIONS
import dev.riyadm.applock.features.settings.ui.settingsItemColors
import dev.riyadm.applock.features.settings.ui.unlockDurationLabel
import dev.riyadm.applock.ui.motion.rememberAppHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Create, rename, delete and activate lock profiles. Lives behind the in-app lock screen. */
@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfilesScreen(navController: NavController) {
    val context = LocalContext.current
    val repository = remember(context) { context.appLockRepository() }
    val state by repository.profiles.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val onBack = dropUnlessResumed { navController.popBackStack() }

    var showCreate by rememberSaveable { mutableStateOf(false) }
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var durationId by rememberSaveable { mutableStateOf<String?>(null) }

    val activeName = profileName(state.active)

    fun activate(profile: LockProfile) {
        if (profile.id == state.activeId) return
        val name = profile.displayName(context)
        scope.launch {
            val switched = withContext(Dispatchers.IO) { repository.switchProfile(profile.id) }
            if (!switched) return@launch
            haptics.confirm()
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(context.getString(R.string.profiles_switched, name))
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.profiles_title)) },
                subtitle = { Text(stringResource(R.string.profiles_subtitle, activeName), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.settings_screen_back_cd)
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
        floatingActionButton = {
            MediumExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.profiles_new)) },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                onClick = {
                    haptics.press()
                    showCreate = true
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 112.dp
            )
        ) {
            item(key = "intro", contentType = "text") {
                Text(
                    text = stringResource(R.string.profiles_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item(key = "header", contentType = "header") {
                SettingsSectionHeader(stringResource(R.string.profiles_section_profiles))
            }
            itemsIndexed(
                items = state.profiles,
                key = { _, profile -> profile.id },
                contentType = { _, _ -> "profile" }
            ) { index, profile ->
                ProfileRow(
                    profile = profile,
                    active = profile.id == state.activeId,
                    index = index,
                    count = state.profiles.size,
                    onActivate = { activate(profile) },
                    onRename = { renameId = profile.id },
                    onUnlockDuration = { durationId = profile.id },
                    onDelete = { deleteId = profile.id },
                    modifier = Modifier
                        .animateItem()
                        .padding(bottom = ListItemDefaults.SegmentedGap)
                )
            }
            item(key = "hint", contentType = "hint") {
                GuestHint(Modifier.padding(top = 20.dp))
            }
        }
    }

    if (showCreate) {
        ProfileNameDialog(
            title = stringResource(R.string.profiles_create_title),
            description = stringResource(R.string.profiles_create_desc, activeName),
            initialName = stringResource(R.string.profiles_name_custom),
            confirmLabel = stringResource(R.string.profiles_create_confirm),
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                scope.launch {
                    withContext(Dispatchers.IO) { repository.profiles.create(name) }
                    haptics.confirm()
                }
            }
        )
    }

    state.profiles.firstOrNull { it.id == renameId }?.let { profile ->
        ProfileNameDialog(
            title = stringResource(R.string.profiles_rename_title),
            description = null,
            initialName = profileName(profile),
            confirmLabel = stringResource(R.string.profiles_save),
            onDismiss = { renameId = null },
            onConfirm = { name ->
                renameId = null
                scope.launch { withContext(Dispatchers.IO) { repository.profiles.rename(profile.id, name) } }
            }
        )
    }

    state.profiles.firstOrNull { it.id == deleteId }?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.profiles_delete_title, profileName(profile))) },
            text = { Text(stringResource(R.string.profiles_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteId = null
                        scope.launch {
                            val deleted = withContext(Dispatchers.IO) { repository.profiles.delete(profile.id) }
                            if (deleted) haptics.confirm() else haptics.reject()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.profiles_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel_button)) }
            }
        )
    }

    state.profiles.firstOrNull { it.id == durationId }?.let { profile ->
        ProfileUnlockDurationSheet(
            current = profile.unlockDurationMinutes,
            globalMinutes = remember { repository.getGlobalUnlockTimeDuration() },
            onDismiss = { durationId = null },
            onSelect = { minutes ->
                scope.launch {
                    withContext(Dispatchers.IO) { repository.profiles.setUnlockDuration(profile.id, minutes) }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileRow(
    profile: LockProfile,
    active: Boolean,
    index: Int,
    count: Int,
    onActivate: () -> Unit,
    onRename: () -> Unit,
    onUnlockDuration: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = profileName(profile)
    val apps = pluralStringResource(R.plurals.profiles_app_count, profile.lockedApps.size, profile.lockedApps.size)
    val duration = profile.unlockDurationMinutes?.let {
        stringResource(R.string.profiles_unlock_override, unlockDurationLabel(it))
    } ?: stringResource(R.string.profiles_unlock_follow_global)
    var menuExpanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    SegmentedListItem(
        selected = active,
        onClick = onActivate,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = modifier,
        leadingContent = {
            MorphingIconBadge(
                polygon = if (active) MaterialShapes.Cookie9Sided else MaterialShapes.Circle,
                icon = profile.kind.icon,
                containerColor = if (active) colors.primary else colors.surfaceContainerHighest,
                contentColor = if (active) colors.onPrimary else colors.onSurfaceVariant,
                size = 44.dp
            )
        },
        supportingContent = {
            Text(
                text = stringResource(R.string.profiles_summary, apps, duration),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (active) ActiveBadge()
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.profiles_options_cd, name)
                        )
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (!active) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.profiles_activate)) },
                                leadingIcon = { Icon(Icons.Rounded.Check, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onActivate()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profiles_rename)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profiles_unlock_duration)) },
                            leadingIcon = { Icon(Icons.Rounded.Timer, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onUnlockDuration()
                            }
                        )
                        if (profile.kind == ProfileKind.CUSTOM && !active) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.profiles_delete)) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        },
        colors = settingsItemColors(highlightSelected = true),
        content = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = CircleShape
    ) {
        Text(
            text = stringResource(R.string.profiles_active_badge),
            style = MaterialTheme.typography.labelMediumEmphasized,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun GuestHint(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Rounded.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = stringResource(R.string.profiles_guest_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProfileNameDialog(
    title: String,
    description: String?,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val valid by remember { derivedStateOf { name.isNotBlank() } }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (description != null) {
                    Text(description)
                    Spacer(Modifier.height(16.dp))
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(LockProfilesRepository.MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.profiles_name_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(name) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = valid) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileUnlockDurationSheet(
    current: Int?,
    globalMinutes: Int,
    onDismiss: () -> Unit,
    onSelect: (Int?) -> Unit,
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val options: List<Int?> = remember { listOf<Int?>(null) + UNLOCK_DURATION_OPTIONS }
    val selected = current?.let { minutes ->
        UNLOCK_DURATION_OPTIONS.minBy { kotlin.math.abs(it.toLong() - minutes) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.profiles_unlock_duration),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.profiles_unlock_duration_sheet_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                options.forEachIndexed { index, minutes ->
                    val isSelected = minutes == selected
                    SegmentedListItem(
                        selected = isSelected,
                        onClick = {
                            onSelect(minutes)
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                        },
                        shapes = ListItemDefaults.segmentedShapes(index, options.size),
                        leadingContent = { RadioButton(selected = isSelected, onClick = null) },
                        colors = settingsItemColors(highlightSelected = true),
                        content = {
                            Text(
                                if (minutes == null) {
                                    stringResource(
                                        R.string.profiles_unlock_duration_global,
                                        unlockDurationLabel(globalMinutes)
                                    )
                                } else unlockDurationLabel(minutes)
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
