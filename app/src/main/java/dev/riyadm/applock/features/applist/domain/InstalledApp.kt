package dev.riyadm.applock.features.applist.domain

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Immutable

/**
 * Snapshot of an installed package with everything the app lists need, resolved off the
 * main thread. [applicationInfo] is only kept for lazy icon loading and is ignored for equality.
 */
@Immutable
class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val hasLauncher: Boolean,
    val isEnabled: Boolean,
    val firstInstallTime: Long,
    val applicationInfo: ApplicationInfo,
) {
    fun matches(query: String): Boolean =
        query.isBlank() ||
                label.contains(query, ignoreCase = true) ||
                packageName.contains(query, ignoreCase = true)

    /** Whether the app belongs in a list by default (without "show system apps"). */
    val isUserFacing: Boolean
        get() = isEnabled && (!isSystem || hasLauncher)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InstalledApp) return false
        return packageName == other.packageName &&
                label == other.label &&
                isSystem == other.isSystem &&
                hasLauncher == other.hasLauncher &&
                isEnabled == other.isEnabled &&
                firstInstallTime == other.firstInstallTime
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + firstInstallTime.hashCode()
        return result
    }
}
