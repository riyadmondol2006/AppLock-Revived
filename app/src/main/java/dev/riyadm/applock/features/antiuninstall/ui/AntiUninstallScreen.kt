package dev.riyadm.applock.features.antiuninstall.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.animation.animateColorAsState
import dev.riyadm.applock.R
import dev.riyadm.applock.core.ui.MorphingIconBadge
import dev.riyadm.applock.features.antiuninstall.domain.ShizukuState
import dev.riyadm.applock.features.applist.ui.PackageToggleScreen
import dev.riyadm.applock.ui.motion.rememberAppHaptics
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

private const val SHIZUKU_REQUEST_CODE = 1001

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AntiUninstallScreen(
    navController: NavController,
    viewModel: AntiUninstallViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val checked by viewModel.checked.collectAsStateWithLifecycle()
    val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val isWorking by viewModel.isWorking.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmRemoveAll by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshShizukuState() }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            val message = when (event) {
                AntiUninstallEvent.NeedsShizuku -> {
                    haptics.reject()
                    context.getString(R.string.anti_uninstall_needs_shizuku)
                }

                is AntiUninstallEvent.ToggleFailed -> {
                    haptics.reject()
                    context.getString(R.string.anti_uninstall_toggle_failed, event.packageName)
                }

                is AntiUninstallEvent.RemovedAll -> {
                    haptics.confirm()
                    context.resources.getQuantityString(
                        R.plurals.anti_uninstall_removed_count, event.count, event.count
                    )
                }
            }
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }


    PackageToggleScreen(
        title = stringResource(R.string.anti_uninstall_title),
        subtitle = pluralStringResource(R.plurals.anti_uninstall_count, checked.size, checked.size),
        viewModel = viewModel,
        checkedSectionTitle = stringResource(R.string.anti_uninstall_section_protected),
        checkedThumbIcon = Icons.Rounded.Shield,
        addPackageDescription = stringResource(R.string.anti_uninstall_add_desc),
        onBack = { navController.navigateUp() },
        onToggle = viewModel::setProtected,
        onAddPackage = viewModel::addManualPackage,
        pendingPackages = pending,
        snackbarHostState = snackbarHostState,
        extraActions = {
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.applist_more_options_cd)
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.anti_uninstall_remove_all)) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
                        enabled = shizukuState == ShizukuState.READY && !isWorking,
                        onClick = {
                            menuExpanded = false
                            confirmRemoveAll = true
                        }
                    )
                }
            }
        }
    ) {
        ShizukuStatusCard(
            state = shizukuState,
            isWorking = isWorking,
            onAction = {
                haptics.press()
                handleShizukuAction(context, shizukuState)
                viewModel.refreshShizukuState()
            },
            onRetry = {
                haptics.tick()
                viewModel.refreshShizukuState()
            }
        )
    }

    if (confirmRemoveAll) {
        AlertDialog(
            onDismissRequest = { confirmRemoveAll = false },
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
            title = { Text(stringResource(R.string.anti_uninstall_remove_all_title)) },
            text = { Text(stringResource(R.string.anti_uninstall_remove_all_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRemoveAll = false
                        viewModel.removeAllProtection()
                    },
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.anti_uninstall_remove_all_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveAll = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.applist_cancel))
                }
            }
        )
    }
}

private class ShizukuStyle(
    val title: Int,
    val description: Int,
    val action: Int?,
    val actionIcon: ImageVector?,
    val polygon: RoundedPolygon,
    val icon: ImageVector,
    val ready: Boolean,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun shizukuStyle(state: ShizukuState): ShizukuStyle = when (state) {
    ShizukuState.READY -> ShizukuStyle(
        R.string.anti_uninstall_ready_title, R.string.anti_uninstall_ready_desc,
        null, null, MaterialShapes.Cookie9Sided, Icons.Rounded.VerifiedUser, true
    )

    ShizukuState.NOT_INSTALLED -> ShizukuStyle(
        R.string.anti_uninstall_not_installed_title, R.string.anti_uninstall_not_installed_desc,
        R.string.anti_uninstall_action_install, Icons.Rounded.Download,
        MaterialShapes.SoftBurst, Icons.Rounded.GppMaybe, false
    )

    ShizukuState.NOT_RUNNING -> ShizukuStyle(
        R.string.anti_uninstall_not_running_title, R.string.anti_uninstall_not_running_desc,
        R.string.anti_uninstall_action_open, Icons.AutoMirrored.Rounded.OpenInNew,
        MaterialShapes.Clover4Leaf, Icons.Rounded.GppMaybe, false
    )

    ShizukuState.OUTDATED -> ShizukuStyle(
        R.string.anti_uninstall_outdated_title, R.string.anti_uninstall_shizuku_old,
        R.string.anti_uninstall_action_open, Icons.AutoMirrored.Rounded.OpenInNew,
        MaterialShapes.Clover4Leaf, Icons.Rounded.GppMaybe, false
    )

    ShizukuState.PERMISSION_DENIED -> ShizukuStyle(
        R.string.anti_uninstall_permission_title, R.string.anti_uninstall_permission_desc,
        R.string.anti_uninstall_action_grant, Icons.Rounded.Shield,
        MaterialShapes.Pentagon, Icons.Rounded.GppMaybe, false
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShizukuStatusCard(
    state: ShizukuState,
    isWorking: Boolean,
    onAction: () -> Unit,
    onRetry: () -> Unit,
) {
    val style = shizukuStyle(state)
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        if (style.ready) colors.primaryContainer else colors.tertiaryContainer,
        motion.defaultEffectsSpec(),
        label = "shizukuContainer"
    )
    val content = if (style.ready) colors.onPrimaryContainer else colors.onTertiaryContainer

    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .drawBehind { drawRect(container) }
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorphingIconBadge(
                polygon = style.polygon,
                icon = style.icon,
                containerColor = if (style.ready) colors.primary else colors.tertiary,
                contentColor = if (style.ready) colors.onPrimary else colors.onTertiary,
                size = 56.dp
            )
            Spacer(Modifier.width(16.dp))
            AnimatedContent(
                targetState = style,
                transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                label = "shizukuText"
            ) { target ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(target.title),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = content
                    )
                    Text(
                        text = stringResource(target.description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = content
                    )
                }
            }
        }
        if (isWorking) {
            Spacer(Modifier.height(16.dp))
            LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        }
        if (style.action != null) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAction, shapes = ButtonDefaults.shapes()) {
                    style.actionIcon?.let {
                        Icon(it, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    }
                    Text(stringResource(style.action))
                }
                if (state == ShizukuState.NOT_RUNNING) {
                    FilledTonalButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.anti_uninstall_action_retry))
                    }
                }
            }
        }
    }
}

private fun handleShizukuAction(context: Context, state: ShizukuState) {
    try {
        when (state) {
            ShizukuState.NOT_INSTALLED -> context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=${ShizukuProvider.MANAGER_APPLICATION_ID}".toUri()
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )

            ShizukuState.NOT_RUNNING, ShizukuState.OUTDATED -> context.packageManager
                .getLaunchIntentForPackage(ShizukuProvider.MANAGER_APPLICATION_ID)
                ?.let(context::startActivity)

            ShizukuState.PERMISSION_DENIED -> Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)

            ShizukuState.READY -> Unit
        }
    } catch (e: Exception) {
        Log.w("AntiUninstallScreen", "Shizuku action failed for $state", e)
    }
}
