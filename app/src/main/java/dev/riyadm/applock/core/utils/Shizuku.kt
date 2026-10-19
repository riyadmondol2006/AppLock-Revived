package dev.riyadm.applock.core.utils

import android.content.pm.IPackageManager
import android.os.Process
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

private const val PER_USER_RANGE = 100_000

/** Resolved on each use: the Shizuku binder may not exist yet (or may have been restarted). */
private val pm: IPackageManager
    get() = SystemServiceHelper.getSystemService("package")
        .let(::ShizukuBinderWrapper)
        .let(IPackageManager.Stub::asInterface)

/** Android user id of this process (UserHandle.getIdentifier() is hidden). */
private fun currentUserId(): Int = Process.myUid() / PER_USER_RANGE

fun blockUninstallForUser(packageName: String) {
    pm.setBlockUninstallForUser(packageName, true, currentUserId())
}

fun unblockUninstallForUser(packageName: String) {
    pm.setBlockUninstallForUser(packageName, false, currentUserId())
}
