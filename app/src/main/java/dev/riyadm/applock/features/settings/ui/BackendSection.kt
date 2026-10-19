package dev.riyadm.applock.features.settings.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.riyadm.applock.R
import dev.riyadm.applock.core.utils.hasUsagePermission
import dev.riyadm.applock.core.utils.isAccessibilityServiceEnabled
import dev.riyadm.applock.core.utils.openAccessibilitySettings
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.BackendImplementation
import dev.riyadm.applock.services.AppLockAccessibilityService
import dev.riyadm.applock.services.AppLockManager
import dev.riyadm.applock.services.ShizukuAppLockService
import dev.riyadm.applock.services.UsageLockService
import dev.riyadm.applock.ui.icons.Accessibility
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

private const val TAG = "BackendSection"
private const val SHIZUKU_PERMISSION_REQUEST_CODE = 423

@Composable
fun BackendSection(
    repository: AppLockRepository,
    selected: BackendImplementation,
    onBackendChanged: (BackendImplementation) -> Unit
) {
    val context = LocalContext.current
    val currentOnChanged = rememberUpdatedState(onBackendChanged)

    fun apply(backend: BackendImplementation) {
        applyBackend(context, repository, backend)
        currentOnChanged.value(backend)
    }

    val shizukuPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(
                context,
                context.getString(R.string.settings_screen_shizuku_permission_granted),
                Toast.LENGTH_SHORT
            ).show()
            apply(BackendImplementation.SHIZUKU)
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.settings_screen_shizuku_permission_required_desc),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Select Shizuku as soon as the user grants the permission in the Shizuku dialog
    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE &&
                grantResult == PackageManager.PERMISSION_GRANTED
            ) {
                applyBackend(context, repository, BackendImplementation.SHIZUKU)
                currentOnChanged.value(BackendImplementation.SHIZUKU)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        onDispose { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    fun onSelect(backend: BackendImplementation) {
        if (backend == selected) return
        when (backend) {
            BackendImplementation.SHIZUKU -> {
                val binderAlive = Shizuku.pingBinder()
                val granted = binderAlive && runCatching {
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                }.getOrDefault(false)
                when {
                    granted -> apply(backend)
                    Shizuku.isPreV11() -> shizukuPermissionLauncher.launch(ShizukuProvider.PERMISSION)

                    binderAlive -> Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                    else -> Toast.makeText(
                        context,
                        context.getString(R.string.settings_screen_shizuku_not_running_toast),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            BackendImplementation.USAGE_STATS -> {
                if (context.hasUsagePermission()) {
                    apply(backend)
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_screen_usage_permission_toast),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            BackendImplementation.ACCESSIBILITY -> {
                if (context.isAccessibilityServiceEnabled()) apply(backend)
                else openAccessibilitySettings(context)
            }
        }
    }

    SettingsGroup(
        title = stringResource(R.string.settings_screen_backend_implementation_title),
        footer = stringResource(R.string.settings_screen_backend_implementation_desc)
    ) {
        BackendImplementation.entries.forEach { backend ->
            item { shapes ->
                BackendItem(
                    shapes = shapes,
                    backend = backend,
                    isSelected = selected == backend,
                    onClick = { onSelect(backend) }
                )
            }
        }
    }
}

/** Persists the backend and makes sure only the matching lock service runs. */
internal fun applyBackend(context: Context, repository: AppLockRepository, backend: BackendImplementation) {
    repository.setBackendImplementation(backend)
    try {
        when (backend) {
            BackendImplementation.SHIZUKU -> {
                AppLockManager.stopAllOtherServices(context, ShizukuAppLockService::class.java)
                context.startService(Intent(context, ShizukuAppLockService::class.java))
            }

            BackendImplementation.USAGE_STATS -> {
                AppLockManager.stopAllOtherServices(context, UsageLockService::class.java)
                context.startService(Intent(context, UsageLockService::class.java))
            }

            BackendImplementation.ACCESSIBILITY -> {
                AppLockManager.stopAllOtherServices(context, AppLockAccessibilityService::class.java)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to switch backend to $backend", e)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BackendItem(
    shapes: androidx.compose.material3.ListItemShapes,
    backend: BackendImplementation,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    SegmentedListItem(
        selected = isSelected,
        onClick = onClick,
        shapes = shapes,
        leadingContent = {
            SettingsIcon(
                icon = backendIcon(backend),
                accent = if (isSelected) SettingsAccent.Primary else SettingsAccent.Secondary
            )
        },
        trailingContent = { RadioButton(selected = isSelected, onClick = null) },
        supportingContent = { Text(backendDescription(backend)) },
        colors = settingsItemColors(highlightSelected = true),
        content = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(backendTitle(backend))
                if (backend == BackendImplementation.SHIZUKU) {
                    Spacer(Modifier.width(8.dp))
                    Badge(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ) {
                        Text(stringResource(R.string.settings_screen_backend_implementation_shizuku_advanced))
                    }
                }
            }
        }
    )
}

@Composable
private fun backendTitle(backend: BackendImplementation): String = stringResource(
    when (backend) {
        BackendImplementation.ACCESSIBILITY -> R.string.settings_backend_accessibility_title
        BackendImplementation.USAGE_STATS -> R.string.settings_backend_usage_stats_title
        BackendImplementation.SHIZUKU -> R.string.settings_backend_shizuku_title
    }
)

@Composable
private fun backendDescription(backend: BackendImplementation): String = stringResource(
    when (backend) {
        BackendImplementation.ACCESSIBILITY -> R.string.settings_backend_accessibility_desc
        BackendImplementation.USAGE_STATS -> R.string.settings_backend_usage_stats_desc
        BackendImplementation.SHIZUKU -> R.string.settings_backend_shizuku_desc
    }
)

private fun backendIcon(backend: BackendImplementation): ImageVector = when (backend) {
    BackendImplementation.ACCESSIBILITY -> Accessibility
    BackendImplementation.USAGE_STATS -> Icons.Rounded.QueryStats
    BackendImplementation.SHIZUKU -> Icons.Rounded.AutoAwesome
}
