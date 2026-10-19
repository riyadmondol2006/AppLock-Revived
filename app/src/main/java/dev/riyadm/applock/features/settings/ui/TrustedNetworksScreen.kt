package dev.riyadm.applock.features.settings.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.NotListedLocation
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SignalWifiBad
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiLock
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.WrongLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavController
import dev.riyadm.applock.AppLockApplication
import dev.riyadm.applock.R
import dev.riyadm.applock.core.trusted.TrustedNetwork
import dev.riyadm.applock.core.trusted.TrustedNetworkMonitor
import dev.riyadm.applock.core.trusted.TrustedNetworkMonitor.Problem
import dev.riyadm.applock.core.trusted.TrustedNetworkSettings
import dev.riyadm.applock.core.trusted.WifiTrust
import dev.riyadm.applock.core.utils.openAppInfoSettings
import dev.riyadm.applock.ui.components.ExpressivePermissionDialog
import dev.riyadm.applock.ui.components.ExpressiveShapeIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrustedNetworksScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val settings = remember(context) { TrustedNetworkSettings(context) }
    val hapticsEnabled = remember(context) {
        (context.applicationContext as? AppLockApplication)?.appLockRepository?.shouldDisableHaptics() != true
    }
    val status by TrustedNetworkMonitor.status.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(settings.isEnabled()) }
    var networks by remember { mutableStateOf(settings.getNetworks()) }
    var showEnableDialog by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showBackgroundDialog by remember { mutableStateOf(false) }
    val viewer = remember { Any() }
    val deniedText = stringResource(R.string.trusted_permission_denied)
    val openSettingsText = stringResource(R.string.trusted_action_open_settings)
    val undoText = stringResource(R.string.trusted_undo)
    val resources = LocalResources.current

    LifecycleResumeEffect(viewer) {
        enabled = settings.isEnabled()
        networks = settings.getNetworks()
        TrustedNetworkMonitor.watch(context, viewer)
        // Permissions may have changed in system settings meanwhile.
        TrustedNetworkMonitor.refresh()
        onPauseOrDispose { TrustedNetworkMonitor.unwatch(viewer) }
    }

    fun showDeniedMessage() {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = deniedText,
                actionLabel = openSettingsText,
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) openAppInfoSettings(context)
        }
    }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        TrustedNetworkMonitor.refresh()
        val fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!fineGranted && !activity.canAskAgain(Manifest.permission.ACCESS_FINE_LOCATION)) {
            showDeniedMessage()
        }
    }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        TrustedNetworkMonitor.refresh()
        if (!granted && !activity.canAskAgain(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            showDeniedMessage()
        }
    }

    val backgroundLabel = remember(context) { context.backgroundPermissionLabel() }

    if (showEnableDialog) {
        TrustedWifiEnableDialog(
            onConfirm = {
                showEnableDialog = false
                settings.setEnabled(true)
                enabled = true
            },
            onDismiss = { showEnableDialog = false }
        )
    }

    if (showLocationDialog) {
        ExpressivePermissionDialog(
            icon = Icons.Rounded.WifiLock,
            title = stringResource(R.string.trusted_location_dialog_title),
            text = stringResource(R.string.trusted_location_dialog_text),
            confirmText = stringResource(R.string.trusted_dialog_continue),
            iconShape = MaterialShapes.Cookie7Sided,
            onConfirm = {
                showLocationDialog = false
                foregroundLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            },
            onDismiss = { showLocationDialog = false }
        )
    }

    if (showBackgroundDialog) {
        ExpressivePermissionDialog(
            icon = Icons.Rounded.NotListedLocation,
            title = stringResource(R.string.trusted_background_dialog_title),
            text = stringResource(R.string.trusted_background_dialog_text, backgroundLabel),
            supportingText = stringResource(R.string.trusted_background_dialog_supporting, backgroundLabel),
            confirmText = stringResource(R.string.trusted_dialog_continue),
            iconShape = MaterialShapes.Clover4Leaf,
            onConfirm = {
                showBackgroundDialog = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            },
            onDismiss = { showBackgroundDialog = false },
            extraContent = {
                TextButton(
                    onClick = {
                        showBackgroundDialog = false
                        openAppInfoSettings(context)
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Text(
                        stringResource(R.string.trusted_action_open_settings),
                        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing)
                    )
                }
            }
        )
    }

    fun fixProblem(problem: Problem) {
        when (problem) {
            Problem.LOCATION_PERMISSION, Problem.PRECISE_LOCATION -> showLocationDialog = true
            Problem.BACKGROUND_LOCATION -> showBackgroundDialog = true
            Problem.LOCATION_OFF -> openLocationSettings(context)
            Problem.SSID_UNAVAILABLE -> openAppInfoSettings(context)
        }
    }

    fun addCurrent(matchRouter: Boolean) {
        val wifi = status.wifi ?: return
        if (!wifi.canBeTrusted) return
        val pinRouter = matchRouter || WifiTrust.routerRequired(Build.VERSION.SDK_INT)
        if (!WifiTrust.canSave(wifi, pinRouter, Build.VERSION.SDK_INT)) {
            // Never fall back to trusting by name alone when the router's address is redacted.
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(resources.getString(R.string.followups_trusted_router_unavailable))
            }
            return
        }
        val network = TrustedNetwork(
            ssid = wifi.ssid,
            bssid = if (pinRouter) WifiTrust.normalizeBssid(wifi.bssid) else null,
            securityType = wifi.securityType,
            addedAt = System.currentTimeMillis()
        )
        settings.addNetwork(network)
        networks = settings.getNetworks()
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(resources.getString(R.string.trusted_added, network.displayName))
        }
    }

    fun remove(network: TrustedNetwork) {
        settings.removeNetwork(network)
        networks = settings.getNetworks()
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = resources.getString(R.string.trusted_removed, network.displayName),
                actionLabel = undoText,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                settings.addNetwork(network)
                networks = settings.getNetworks()
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val onBack = dropUnlessResumed { navController.popBackStack() }
    val relaxedSsid = status.relaxedSsid

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.trusted_networks_title)) },
                subtitle = {
                    Text(
                        when {
                            !enabled -> stringResource(R.string.trusted_screen_subtitle_off)
                            relaxedSsid != null -> stringResource(R.string.trusted_screen_subtitle_relaxed, relaxedSsid)
                            else -> stringResource(R.string.trusted_screen_subtitle_on)
                        }
                    )
                },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        CompositionLocalProvider(LocalSettingsHapticsEnabled provides hapticsEnabled) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 32.dp
                )
            ) {
                item(key = "master", contentType = "master") {
                    SettingsGroup(title = null, modifier = Modifier.animateItem()) {
                        item { shapes ->
                            SwitchSettingItem(
                                shapes = shapes,
                                icon = Icons.Rounded.WifiLock,
                                title = stringResource(R.string.trusted_master_switch),
                                summary = stringResource(
                                    if (enabled) R.string.trusted_wifi_desc_on else R.string.trusted_wifi_desc_off
                                ),
                                checked = enabled,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        showEnableDialog = true
                                    } else {
                                        settings.setEnabled(false)
                                        enabled = false
                                    }
                                },
                                accent = SettingsAccent.Tertiary
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                val problem = status.problem
                if (problem != null) {
                    item(key = "problem", contentType = "problem") {
                        ProblemCard(
                            problem = problem,
                            backgroundLabel = backgroundLabel,
                            onFix = { fixProblem(problem) },
                            onOpenSettings = { openAppInfoSettings(context) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }

                item(key = "current_header", contentType = "header") {
                    SettingsSectionHeader(
                        stringResource(R.string.trusted_current_header),
                        modifier = Modifier.animateItem()
                    )
                }
                item(key = "current", contentType = "current") {
                    CurrentNetworkCard(
                        status = status,
                        enabled = enabled,
                        networks = networks,
                        onTrust = ::addCurrent,
                        modifier = Modifier.animateItem()
                    )
                }

                item(key = "saved_header", contentType = "header") {
                    SettingsSectionHeader(
                        stringResource(R.string.trusted_saved_header),
                        modifier = Modifier.animateItem()
                    )
                }
                if (networks.isEmpty()) {
                    item(key = "saved_empty", contentType = "empty") {
                        Text(
                            text = stringResource(R.string.trusted_saved_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    itemsIndexed(
                        items = networks,
                        key = { _, network -> "net_${network.ssid}_${network.bssid}" },
                        contentType = { _, _ -> "network" }
                    ) { index, network ->
                        SavedNetworkItem(
                            network = network,
                            index = index,
                            count = networks.size,
                            connected = status.matchedNetwork?.sameNetwork(network) == true,
                            onRemove = { remove(network) },
                            modifier = Modifier
                                .animateItem()
                                .padding(top = if (index == 0) 0.dp else ListItemDefaults.SegmentedGap)
                        )
                    }
                }

                item(key = "footer", contentType = "footer") {
                    Text(
                        text = stringResource(R.string.trusted_screen_footer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .animateItem()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProblemCard(
    problem: Problem,
    backgroundLabel: String,
    onFix: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, title, description) = when (problem) {
        Problem.LOCATION_PERMISSION -> Triple(
            Icons.Rounded.WrongLocation,
            stringResource(R.string.trusted_problem_location_title),
            stringResource(R.string.trusted_problem_location_desc)
        )

        Problem.PRECISE_LOCATION -> Triple(
            Icons.Rounded.WrongLocation,
            stringResource(R.string.trusted_problem_precise_title),
            stringResource(R.string.trusted_problem_precise_desc)
        )

        Problem.BACKGROUND_LOCATION -> Triple(
            Icons.Rounded.NotListedLocation,
            stringResource(R.string.trusted_problem_background_title),
            stringResource(R.string.trusted_problem_background_desc, backgroundLabel)
        )

        Problem.LOCATION_OFF -> Triple(
            Icons.Rounded.LocationOff,
            stringResource(R.string.trusted_problem_location_off_title),
            stringResource(R.string.trusted_problem_location_off_desc)
        )

        Problem.SSID_UNAVAILABLE -> Triple(
            Icons.Rounded.SignalWifiBad,
            stringResource(R.string.trusted_problem_ssid_title),
            stringResource(R.string.trusted_problem_ssid_desc)
        )
    }
    val fixLabel = when (problem) {
        Problem.LOCATION_OFF -> stringResource(R.string.trusted_action_turn_on_location)
        Problem.SSID_UNAVAILABLE -> stringResource(R.string.trusted_action_open_settings)
        else -> stringResource(R.string.trusted_action_allow)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpressiveShapeIcon(
                    icon = icon,
                    shape = MaterialShapes.SoftBurst,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    size = 56.dp
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = title, style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(text = description, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.trusted_problem_footer),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (problem != Problem.SSID_UNAVAILABLE) {
                    TextButton(onClick = onOpenSettings, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.trusted_action_open_settings))
                    }
                }
                Button(
                    onClick = onFix,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(fixLabel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CurrentNetworkCard(
    status: TrustedNetworkMonitor.Status,
    enabled: Boolean,
    networks: List<TrustedNetwork>,
    onTrust: (matchRouter: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val wifi = status.wifi
    val saved = remember(wifi, networks) {
        wifi?.let { current ->
            networks.firstOrNull { WifiTrust.matches(it, current, Build.VERSION.SDK_INT) }
        }
    }
    val routerRequired = WifiTrust.routerRequired(Build.VERSION.SDK_INT)
    var matchRouter by remember(wifi?.ssid) { mutableStateOf(true) }
    var showNameOnlyWarning by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    if (showNameOnlyWarning) {
        ExpressivePermissionDialog(
            icon = Icons.Rounded.SignalWifiBad,
            title = stringResource(R.string.followups_trusted_name_only_title),
            text = stringResource(R.string.followups_trusted_name_only_text),
            confirmText = stringResource(R.string.followups_trusted_name_only_confirm),
            iconShape = MaterialShapes.SoftBurst,
            onConfirm = {
                showNameOnlyWarning = false
                matchRouter = false
            },
            onDismiss = { showNameOnlyWarning = false }
        )
    }
    val isTrustedHere = saved != null && wifi?.canBeTrusted == true

    val (icon, title) = when {
        wifi != null -> Icons.Rounded.Wifi to wifi.displayName
        status.onWifi -> Icons.Rounded.Wifi to stringResource(R.string.trusted_current_unknown)
        else -> Icons.Rounded.WifiOff to stringResource(R.string.trusted_current_not_connected)
    }
    val description = when {
        wifi == null && status.onWifi -> stringResource(R.string.trusted_current_unknown_desc)
        wifi == null -> null
        !wifi.canBeTrusted && !wifi.securityKnown -> stringResource(R.string.followups_trusted_security_unknown)
        !wifi.canBeTrusted -> stringResource(R.string.trusted_current_open)
        saved != null && !enabled -> stringResource(R.string.trusted_current_trusted_off)
        saved != null && status.relaxed -> stringResource(R.string.trusted_current_trusted)
        saved != null -> stringResource(R.string.trusted_current_trusted_paused)
        else -> stringResource(R.string.trusted_current_not_trusted)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (isTrustedHere) colors.tertiaryContainer else colors.surfaceContainer,
        contentColor = if (isTrustedHere) colors.onTertiaryContainer else colors.onSurface
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpressiveShapeIcon(
                    icon = if (isTrustedHere) Icons.Rounded.WifiLock else icon,
                    shape = if (isTrustedHere) MaterialShapes.Cookie9Sided else MaterialShapes.Circle,
                    containerColor = if (isTrustedHere) colors.tertiary else colors.secondaryContainer,
                    contentColor = if (isTrustedHere) colors.onTertiary else colors.onSecondaryContainer,
                    size = 56.dp
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (wifi?.canBeTrusted == false) colors.error
                            else LocalContentColor.current.copy(alpha = 0.85f)
                        )
                    }
                    wifi?.bssid?.let { bssid ->
                        Text(
                            text = stringResource(R.string.trusted_current_router, bssid),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalContentColor.current.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            if (wifi != null && saved == null && wifi.canBeTrusted) {
                Spacer(Modifier.height(16.dp))
                val pinRouter = matchRouter || routerRequired
                val canSave = WifiTrust.canSave(wifi, pinRouter, Build.VERSION.SDK_INT)
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    SwitchSettingItem(
                        shapes = ListItemDefaults.segmentedShapes(0, 1),
                        icon = Icons.Rounded.Router,
                        title = stringResource(R.string.trusted_match_router_title),
                        summary = stringResource(
                            when {
                                routerRequired -> R.string.followups_trusted_match_router_required
                                !pinRouter -> R.string.followups_trusted_match_router_off_warning
                                else -> R.string.trusted_match_router_desc
                            }
                        ),
                        checked = pinRouter,
                        enabled = !routerRequired,
                        onCheckedChange = { checked ->
                            if (checked) matchRouter = true else showNameOnlyWarning = true
                        },
                        accent = if (pinRouter) SettingsAccent.Secondary else SettingsAccent.Error
                    )
                }
                if (!canSave) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.followups_trusted_router_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.error
                    )
                }
                Spacer(Modifier.height(16.dp))
                val haptics = rememberSettingsHaptics()
                Button(
                    enabled = canSave,
                    onClick = {
                        haptics.confirm()
                        onTrust(pinRouter)
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ButtonDefaults.MediumContainerHeight)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(
                        stringResource(R.string.trusted_add_current),
                        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SavedNetworkItem(
    network: TrustedNetwork,
    index: Int,
    count: Int,
    connected: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = network.displayName
    val detail = network.bssid?.let { stringResource(R.string.trusted_saved_exact_router, it) }
        ?: if (WifiTrust.routerRequired(Build.VERSION.SDK_INT)) {
            stringResource(R.string.followups_trusted_saved_needs_router)
        } else {
            stringResource(R.string.trusted_saved_any_router)
        }
    val supporting = if (connected) "${stringResource(R.string.trusted_saved_connected)} · $detail" else detail
    val icon: ImageVector = if (network.bssid != null) Icons.Rounded.Router else Icons.Rounded.Wifi
    val haptics = rememberSettingsHaptics()

    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier,
        leadingContent = {
            SettingsIcon(icon, if (connected) SettingsAccent.Tertiary else SettingsAccent.Secondary)
        },
        trailingContent = {
            IconButton(
                onClick = {
                    haptics.press()
                    onRemove()
                },
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.trusted_remove_cd, name)
                )
            }
        },
        supportingContent = { Text(supporting) },
        colors = settingsItemColors(),
        content = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    )
}

private fun Activity?.canAskAgain(permission: String): Boolean =
    this != null && ActivityCompat.shouldShowRequestPermissionRationale(this, permission)

private fun Context.backgroundPermissionLabel(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching { packageManager.backgroundPermissionOptionLabel.toString() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.trusted_background_default_label)
    } else {
        getString(R.string.trusted_background_default_label)
    }

private fun openLocationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        openAppInfoSettings(context)
    }
}
