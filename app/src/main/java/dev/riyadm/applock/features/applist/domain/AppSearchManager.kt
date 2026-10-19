package dev.riyadm.applock.features.applist.domain

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppSearchManager(private val context: Context) {

    private var allApps: List<ApplicationInfo> = emptyList()
    private var appNameCache: HashMap<ApplicationInfo, String> = HashMap()
    private var prefixIndexCache: HashMap<String, List<ApplicationInfo>> = HashMap()

    suspend fun loadApps(includeSystemApps: Boolean = false): Set<ApplicationInfo> {
        return withContext(Dispatchers.IO) {
            val launcherApps =
                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

            val apps = if (includeSystemApps) {
                // Load all apps including system apps
                val pm = context.packageManager
                pm.getInstalledApplications(0)
                    .filter { it.packageName != context.packageName }
            } else {
                // Load only user-installed apps with launcher activities
                launcherApps.getActivityList(null, Process.myUserHandle())
                    .mapNotNull { it.applicationInfo }
                    .filter { it.enabled && it.packageName != context.packageName }
            }

            val nameCache =
                apps.associateWithTo(HashMap()) { app ->
                    app.loadLabel(context.packageManager).toString().lowercase()
                }

            val prefixCache = HashMap<String, MutableList<ApplicationInfo>>()

            nameCache.forEach { (app, appName) ->
                if (appName.isNotEmpty()) {
                    val firstChar = appName.take(1)
                    prefixCache.getOrPut(firstChar) { mutableListOf() }.add(app)

                    if (appName.length >= 2) {
                        val firstTwoChars = appName.take(2)
                        prefixCache.getOrPut(firstTwoChars) { mutableListOf() }.add(app)

                        if (appName.length >= 3) {
                            val firstThreeChars = appName.take(3)
                            prefixCache.getOrPut(firstThreeChars) { mutableListOf() }.add(app)
                        }
                    }
                }
            }

            val sortedApps = apps.sortedBy { nameCache[it] }

            allApps = sortedApps
            appNameCache = nameCache
            val finalPrefixCache = HashMap<String, List<ApplicationInfo>>(prefixCache.size)
            prefixCache.forEach { (prefix, appList) ->
                finalPrefixCache[prefix] = appList
            }
            prefixIndexCache = finalPrefixCache

            sortedApps.distinctBy { it.packageName }.toSet()
        }
    }
}
