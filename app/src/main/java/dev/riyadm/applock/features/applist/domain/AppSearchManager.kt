package dev.riyadm.applock.features.applist.domain

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import dev.riyadm.applock.features.applist.ui.AppIconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loads every installed package (except this app) together with its label and launcher
 * visibility. Results are shared between screens for a short time so that opening the
 * exclusion / anti-uninstall lists right after the home screen is instant.
 */
class AppSearchManager(context: Context) {

    private val appContext = context.applicationContext

    suspend fun loadInstalledApps(useCache: Boolean = true): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val cached = cache
                if (useCache && cached != null &&
                    SystemClock.elapsedRealtime() - cachedAt < CACHE_TTL_MS
                ) {
                    return@withLock cached
                }
                val apps = queryApps()
                cache = apps
                cachedAt = SystemClock.elapsedRealtime()
                apps
            }
        }

    private fun queryApps(): List<InstalledApp> {
        val pm = appContext.packageManager
        val launcherPackages = launcherPackages(pm)

        @Suppress("DEPRECATION")
        val packages: List<PackageInfo> = runCatching { pm.getInstalledPackages(0) }
            .getOrElse {
                Log.e(TAG, "Failed to query installed packages", it)
                emptyList()
            }

        return packages.asSequence()
            .filter { it.packageName != appContext.packageName }
            .mapNotNull { pkg ->
                val info = pkg.applicationInfo ?: return@mapNotNull null
                val label = runCatching { info.loadLabel(pm).toString() }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: pkg.packageName
                AppIconCache.putLabel(pkg.packageName, label)
                InstalledApp(
                    packageName = pkg.packageName,
                    label = label,
                    isSystem = info.flags and
                            (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                    hasLauncher = pkg.packageName in launcherPackages,
                    isEnabled = info.enabled,
                    firstInstallTime = pkg.firstInstallTime,
                    applicationInfo = info
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    private fun launcherPackages(pm: PackageManager): Set<String> {
        val fromLauncherApps = runCatching {
            appContext.getSystemService(LauncherApps::class.java)
                ?.getActivityList(null, Process.myUserHandle())
                ?.mapTo(HashSet()) { it.applicationInfo.packageName }
        }.getOrNull()
        if (!fromLauncherApps.isNullOrEmpty()) return fromLauncherApps

        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            pm.queryIntentActivities(intent, 0).mapTo(HashSet()) { it.activityInfo.packageName }
        }.getOrDefault(emptySet())
    }

    companion object {
        private const val TAG = "AppSearchManager"
        private const val CACHE_TTL_MS = 60_000L

        private val mutex = Mutex()

        @Volatile
        private var cache: List<InstalledApp>? = null

        @Volatile
        private var cachedAt = 0L

        /** Drops the shared app list, e.g. after a package was installed or removed. */
        fun invalidate() {
            cache = null
        }
    }
}
