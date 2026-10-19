package dev.riyadm.applock.features.settings.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.CameraFront
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.riyadm.applock.AppLockApplication
import dev.riyadm.applock.R
import dev.riyadm.applock.core.utils.openAppInfoSettings
import dev.riyadm.applock.data.repository.BackendImplementation
import dev.riyadm.applock.features.intruder.data.IntruderPhotoStore
import dev.riyadm.applock.features.intruder.data.IntruderSettings
import dev.riyadm.applock.ui.components.ExpressivePermissionDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class CameraPrompt { None, Rationale, Denied, Blocked }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IntruderSection(onViewPhotos: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val settings = remember(context) { IntruderSettings(context) }
    val haptics = rememberSettingsHaptics()

    var enabled by remember { mutableStateOf(settings.isEnabled) }
    var threshold by remember { mutableIntStateOf(settings.attemptThreshold) }
    var retention by remember { mutableIntStateOf(settings.retentionDays) }
    var hasCamera by remember { mutableStateOf(context.hasCameraPermission()) }
    var backend by remember { mutableStateOf<BackendImplementation?>(null) }
    var prompt by rememberSaveable { mutableStateOf(CameraPrompt.None) }
    var resumeTick by remember { mutableIntStateOf(0) }

    LifecycleResumeEffect(Unit) {
        hasCamera = context.hasCameraPermission()
        backend = (context.applicationContext as? AppLockApplication)
            ?.appLockRepository?.getBackendImplementation()
        resumeTick++
        onPauseOrDispose { }
    }

    val changes by IntruderPhotoStore.changes.collectAsStateWithLifecycle()
    val photoCount by produceState<Int?>(null, changes, resumeTick) {
        value = withContext(Dispatchers.IO) { IntruderPhotoStore.count(context) }
    }

    fun turnOn() {
        settings.isEnabled = true
        enabled = true
        hasCamera = true
        haptics.confirm()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            turnOn()
        } else {
            haptics.reject()
            settings.isEnabled = false
            enabled = false
            val canAskAgain = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true
            prompt = if (canAskAgain) CameraPrompt.Denied else CameraPrompt.Blocked
        }
    }

    fun onToggle(on: Boolean) {
        if (!on) {
            settings.isEnabled = false
            enabled = false
            return
        }
        if (context.hasCameraPermission()) turnOn() else prompt = CameraPrompt.Rationale
    }

    when (prompt) {
        CameraPrompt.None -> Unit
        CameraPrompt.Rationale -> ExpressivePermissionDialog(
            icon = Icons.Rounded.CameraFront,
            iconShape = MaterialShapes.Cookie9Sided,
            title = stringResource(R.string.intruder_permission_title),
            text = stringResource(R.string.intruder_permission_text),
            supportingText = stringResource(R.string.intruder_permission_privacy),
            confirmText = stringResource(R.string.intruder_permission_continue),
            onConfirm = {
                prompt = CameraPrompt.None
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onDismiss = { prompt = CameraPrompt.None }
        )

        CameraPrompt.Denied -> ExpressivePermissionDialog(
            icon = Icons.Rounded.NoPhotography,
            iconShape = MaterialShapes.SoftBurst,
            title = stringResource(R.string.intruder_permission_denied_title),
            text = stringResource(R.string.intruder_permission_denied_text),
            confirmText = stringResource(R.string.intruder_permission_try_again),
            onConfirm = {
                prompt = CameraPrompt.None
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onDismiss = { prompt = CameraPrompt.None },
            dismissText = stringResource(R.string.intruder_not_now)
        )

        CameraPrompt.Blocked -> ExpressivePermissionDialog(
            icon = Icons.Rounded.NoPhotography,
            iconShape = MaterialShapes.SoftBurst,
            title = stringResource(R.string.intruder_permission_denied_title),
            text = stringResource(R.string.intruder_permission_blocked_text),
            confirmText = stringResource(R.string.intruder_open_settings),
            onConfirm = {
                prompt = CameraPrompt.None
                openAppInfoSettings(context)
            },
            onDismiss = { prompt = CameraPrompt.None },
            dismissText = stringResource(R.string.intruder_not_now)
        )
    }

    val active = enabled && hasCamera
    val thresholdOptions = remember {
        IntruderSettings.THRESHOLD_OPTIONS.map { ChoiceOption(it, it.toString()) }
    }
    val retentionOptions = IntruderSettings.RETENTION_OPTIONS.map {
        ChoiceOption(
            it,
            if (it == IntruderSettings.KEEP_FOREVER) stringResource(R.string.intruder_retention_never)
            else pluralStringResource(R.plurals.intruder_retention_days, it, it)
        )
    }
    val footer = buildString {
        append(stringResource(R.string.intruder_footer))
        if (active && backend == BackendImplementation.ACCESSIBILITY) {
            append("\n\n")
            append(stringResource(R.string.intruder_footer_accessibility))
        }
    }

    SettingsGroup(title = stringResource(R.string.intruder_section_title), footer = footer) {
        item { shapes ->
            SwitchSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.CameraFront,
                title = stringResource(R.string.intruder_switch_title),
                summary = stringResource(
                    if (enabled && !hasCamera) R.string.intruder_switch_desc_no_permission
                    else R.string.intruder_switch_desc
                ),
                checked = active,
                onCheckedChange = ::onToggle,
                accent = if (enabled && !hasCamera) SettingsAccent.Error else SettingsAccent.Tertiary
            )
        }
        item { shapes ->
            ChoiceSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.Repeat,
                title = stringResource(R.string.intruder_threshold_title),
                summary = stringResource(R.string.intruder_threshold_desc),
                options = thresholdOptions,
                selected = threshold,
                onSelect = {
                    settings.attemptThreshold = it
                    threshold = it
                },
                enabled = active,
                accent = SettingsAccent.Tertiary
            )
        }
        item { shapes ->
            ChoiceSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.AutoDelete,
                title = stringResource(R.string.intruder_retention_title),
                summary = stringResource(R.string.intruder_retention_desc),
                options = retentionOptions,
                selected = retention,
                onSelect = { days ->
                    settings.retentionDays = days
                    retention = days
                    val appContext = context.applicationContext
                    Thread({ IntruderPhotoStore.applyRetention(appContext, days) }, "IntruderRetention").start()
                },
                accent = SettingsAccent.Tertiary
            )
        }
        item { shapes ->
            val count = photoCount
            ActionSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.PhotoLibrary,
                title = stringResource(R.string.intruder_view_title),
                summary = when {
                    count == null -> null
                    count == 0 -> stringResource(R.string.intruder_view_empty)
                    else -> pluralStringResource(R.plurals.intruder_photo_count, count, count)
                },
                onClick = onViewPhotos,
                accent = SettingsAccent.Tertiary
            )
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
