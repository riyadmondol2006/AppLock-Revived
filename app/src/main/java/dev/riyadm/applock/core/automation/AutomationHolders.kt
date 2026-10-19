package dev.riyadm.applock.core.automation

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Immutable

/**
 * Lists the apps that currently hold [AutomationReceiver.PERMISSION].
 *
 * A runtime permission is granted without asking to apps built for Android 5.1 or older
 * (target SDK below 23): at install on Android 8-9, and pre-granted pending a review screen on
 * Android 10-13. Such an app could control App Lock Revived without the user ever having agreed,
 * so while one holds the permission every automation command is refused.
 */
object AutomationHolders {
    private const val TAG = "AutomationHolders"

    @Immutable
    data class Holder(val packageName: String, val targetSdk: Int) {
        /** May have been granted the permission without the user's consent. */
        val legacy: Boolean get() = targetSdk < Build.VERSION_CODES.M
    }

    /** The holders, or null when they couldn't be listed (callers must then fail secure). */
    fun find(context: Context): List<Holder>? = try {
        val pm = context.packageManager
        val permissions = arrayOf(AutomationReceiver.PERMISSION)
        val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackagesHoldingPermissions(permissions, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackagesHoldingPermissions(permissions, 0)
        }
        fromPackages(packages, context.packageName)
    } catch (e: Exception) {
        Log.e(TAG, "Unable to list automation permission holders", e)
        null
    }

    internal fun fromPackages(packages: List<PackageInfo>, ownPackage: String): List<Holder> =
        packages
            .filter { it.packageName != null && it.packageName != ownPackage }
            // An unknown target SDK is treated as the oldest possible one.
            .map { Holder(it.packageName, it.applicationInfo?.targetSdkVersion ?: 0) }
            .distinctBy { it.packageName }
            .sortedBy { it.packageName }

    /** True when commands must be refused: the holders are unknown or include a legacy app. */
    fun blocksCommands(holders: List<Holder>?): Boolean = holders == null || holders.any { it.legacy }
}
