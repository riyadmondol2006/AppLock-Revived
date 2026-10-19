package dev.riyadm.applock.features.applist.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import dev.riyadm.applock.R
import dev.riyadm.applock.core.broadcast.DeviceAdmin
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.core.utils.hasUsagePermission
import dev.riyadm.applock.core.utils.isAccessibilityServiceEnabled
import dev.riyadm.applock.data.repository.BackendImplementation
import dev.riyadm.applock.services.AppLockAccessibilityService
import dev.riyadm.applock.services.ShizukuAppLockService
import dev.riyadm.applock.services.UsageLockService
import rikka.shizuku.Shizuku

enum class MissingPermission(@StringRes val titleResId: Int, @StringRes val descriptionResId: Int) {
    OVERLAY(R.string.permission_warning_overlay_title, R.string.permission_warning_overlay_desc),
    ACCESSIBILITY(
        R.string.permission_warning_accessibility_title,
        R.string.permission_warning_accessibility_desc
    ),
    USAGE_STATS(
        R.string.permission_warning_usage_stats_title,
        R.string.permission_warning_usage_stats_desc
    ),
    SHIZUKU(R.string.permission_warning_shizuku_title, R.string.permission_warning_shizuku_desc),
    DEVICE_ADMIN(
        R.string.permission_warning_device_admin_title,
        R.string.permission_warning_device_admin_desc
    )
}

@Immutable
data class ProtectionStatus(
    val protectEnabled: Boolean,
    val backend: BackendImplementation,
    /** A permission without which apps can't be locked at all. */
    val blockingPermission: MissingPermission?,
    /** A permission only needed by anti-uninstall; locking itself still works. */
    val antiUninstallPermission: MissingPermission?,
    val serviceRunning: Boolean,
) {
    val state: State
        get() = when {
            blockingPermission != null -> State.NEEDS_SETUP
            !protectEnabled -> State.PAUSED
            !serviceRunning -> State.SERVICE_STOPPED
            else -> State.ACTIVE
        }

    enum class State { ACTIVE, PAUSED, NEEDS_SETUP, SERVICE_STOPPED }

    companion object {
        val Initial = ProtectionStatus(
            protectEnabled = true,
            backend = BackendImplementation.ACCESSIBILITY,
            blockingPermission = null,
            antiUninstallPermission = null,
            serviceRunning = true
        )
    }
}

@StringRes
fun BackendImplementation.labelRes(): Int = when (this) {
    BackendImplementation.ACCESSIBILITY -> R.string.applist_backend_accessibility
    BackendImplementation.USAGE_STATS -> R.string.applist_backend_usage_stats
    BackendImplementation.SHIZUKU -> R.string.applist_backend_shizuku
}

fun Context.readProtectionStatus(): ProtectionStatus {
    val repository = appLockRepository()
    val backend = repository.getBackendImplementation()
    val accessibilityEnabled = isAccessibilityServiceEnabled()

    val blocking = when {
        !Settings.canDrawOverlays(this) -> MissingPermission.OVERLAY
        backend == BackendImplementation.ACCESSIBILITY && !accessibilityEnabled ->
            MissingPermission.ACCESSIBILITY

        backend == BackendImplementation.USAGE_STATS && !hasUsagePermission() ->
            MissingPermission.USAGE_STATS

        backend == BackendImplementation.SHIZUKU && !isShizukuGranted() -> MissingPermission.SHIZUKU
        else -> null
    }

    val antiUninstall = if (repository.isAntiUninstallEnabled()) {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(this, DeviceAdmin::class.java)
        when {
            !accessibilityEnabled -> MissingPermission.ACCESSIBILITY
            !dpm.isAdminActive(component) -> MissingPermission.DEVICE_ADMIN
            else -> null
        }
    } else null

    val running = when (backend) {
        BackendImplementation.ACCESSIBILITY -> AppLockAccessibilityService.isServiceRunning
        BackendImplementation.USAGE_STATS -> UsageLockService.isServiceRunning
        BackendImplementation.SHIZUKU -> ShizukuAppLockService.isServiceRunning
    }

    return ProtectionStatus(
        protectEnabled = repository.isProtectEnabled(),
        backend = backend,
        blockingPermission = blocking,
        antiUninstallPermission = antiUninstall.takeIf { it != blocking },
        serviceRunning = running
    )
}

private fun isShizukuGranted(): Boolean = runCatching {
    Shizuku.pingBinder() && !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
}.getOrDefault(false)
