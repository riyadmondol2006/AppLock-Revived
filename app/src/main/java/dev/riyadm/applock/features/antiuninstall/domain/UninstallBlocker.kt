package dev.riyadm.applock.features.antiuninstall.domain

import android.content.Context
import android.content.pm.IPackageManager
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.ShizukuProvider
import rikka.shizuku.SystemServiceHelper

enum class ShizukuState {
    NOT_INSTALLED,
    NOT_RUNNING, // Binder hasn't been received
    OUTDATED,
    PERMISSION_DENIED,
    READY
}

fun isShizukuInstalled(context: Context): Boolean = try {
    context.packageManager.getPackageInfo(ShizukuProvider.MANAGER_APPLICATION_ID, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

/**
 * Never throws. A live binder counts even without the manager app (Sui on rooted devices).
 */
fun checkShizukuState(context: Context): ShizukuState {
    val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    if (!alive) {
        return if (isShizukuInstalled(context)) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
    }
    return runCatching {
        when {
            Shizuku.isPreV11() -> ShizukuState.OUTDATED
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.READY
            else -> ShizukuState.PERMISSION_DENIED
        }
    }.getOrDefault(ShizukuState.NOT_RUNNING)
}

/**
 * Blocks / unblocks uninstalling packages through Shizuku. All calls are binder IPC and must
 * run off the main thread; they return null / false instead of throwing.
 */
object UninstallBlocker {
    private const val TAG = "UninstallBlocker"
    private const val PER_USER_RANGE = 100_000

    private val userId: Int get() = Process.myUid() / PER_USER_RANGE

    @Volatile
    private var packageManager: IPackageManager? = null

    private fun pm(): IPackageManager {
        packageManager?.let { return it }
        val binder = SystemServiceHelper.getSystemService("package")
            ?: error("package service unavailable")
        return IPackageManager.Stub.asInterface(ShizukuBinderWrapper(binder)).also {
            packageManager = it
        }
    }

    fun setBlocked(packageName: String, blocked: Boolean): Boolean = try {
        pm().setBlockUninstallForUser(packageName, blocked, userId)
        isBlocked(packageName) == blocked
    } catch (e: Exception) {
        Log.e(TAG, "setBlockUninstallForUser($packageName, $blocked) failed", e)
        packageManager = null
        false
    }

    fun isBlocked(packageName: String): Boolean? = try {
        pm().getBlockUninstallForUser(packageName, userId)
    } catch (e: Exception) {
        Log.w(TAG, "getBlockUninstallForUser($packageName) failed", e)
        packageManager = null
        null
    }
}
