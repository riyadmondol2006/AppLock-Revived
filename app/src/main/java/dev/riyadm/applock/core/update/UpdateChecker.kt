package dev.riyadm.applock.core.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.saveable.Saver
import dev.riyadm.applock.AppLockApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val releaseName: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val apkUrl: String?,
    val apkSize: Long
) {
    val downloadUrl: String get() = apkUrl ?: releaseUrl

    companion object {
        val Saver: Saver<UpdateInfo?, ArrayList<String>> = Saver(
            save = { info ->
                info?.let {
                    arrayListOf(
                        it.currentVersion, it.latestVersion, it.releaseName, it.releaseNotes,
                        it.releaseUrl, it.apkUrl.orEmpty(), it.apkSize.toString()
                    )
                }
            },
            restore = { values ->
                UpdateInfo(
                    currentVersion = values[0],
                    latestVersion = values[1],
                    releaseName = values[2],
                    releaseNotes = values[3],
                    releaseUrl = values[4],
                    apkUrl = values[5].ifEmpty { null },
                    apkSize = values[6].toLongOrNull() ?: 0L
                )
            }
        )
    }
}

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val RELEASES_API =
        "https://api.github.com/repos/riyadmondol2006/AppLock-Revived/releases/latest"
    const val RELEASES_PAGE = "https://github.com/riyadmondol2006/AppLock-Revived/releases"
    private const val TIMEOUT_MS = 10_000
    private const val AUTO_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L

    /**
     * Returns info about a newer release, or null when up to date, skipped, throttled, disabled or
     * on any error. [force] bypasses the enabled flag, the 12h throttle and the skipped version.
     */
    suspend fun check(context: Context, force: Boolean = false): UpdateInfo? {
        val appContext = context.applicationContext
        val repository = (appContext as? AppLockApplication)?.appLockRepository ?: return null

        if (!force) {
            if (!repository.isUpdateCheckEnabled()) return null
            val elapsed = System.currentTimeMillis() - repository.getLastUpdateCheck()
            if (elapsed in 0 until AUTO_CHECK_INTERVAL_MS) return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val release = fetchLatestRelease() ?: return@withContext null
                repository.setLastUpdateCheck(System.currentTimeMillis())

                val current = currentVersionName(appContext)
                val latest = normalizeVersion(release.optString("tag_name"))
                if (latest.isEmpty() || compareVersions(latest, current) <= 0) {
                    return@withContext null
                }
                if (!force && repository.getSkippedUpdateVersion() == latest) {
                    return@withContext null
                }

                var apkUrl: String? = null
                var apkSize = 0L
                val assets = release.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url").ifBlank { null }
                            apkSize = asset.optLong("size")
                            break
                        }
                    }
                }

                UpdateInfo(
                    currentVersion = current,
                    latestVersion = latest,
                    releaseName = release.optString("name").ifBlank { "v$latest" },
                    releaseNotes = release.optString("body"),
                    releaseUrl = release.optString("html_url").ifBlank { RELEASES_PAGE },
                    apkUrl = apkUrl,
                    apkSize = apkSize
                )
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
                null
            }
        }
    }

    fun skipVersion(context: Context, version: String) {
        (context.applicationContext as? AppLockApplication)
            ?.appLockRepository
            ?.setSkippedUpdateVersion(version)
    }

    private fun fetchLatestRelease(): JSONObject? {
        val connection = URL(RELEASES_API).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "AppLock-Revived-Android")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Unexpected response: ${connection.responseCode}")
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    fun currentVersionName(context: Context): String {
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            normalizeVersion(info.versionName.orEmpty())
        } catch (_: Exception) {
            "0"
        }
    }

    internal fun normalizeVersion(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V").trim()

    /**
     * Compares semantic-ish versions numerically ("3.1" == "3.1.0", "3.10" > "3.9").
     * A pre-release ("3.1.0-beta1") sorts lower than its release ("3.1.0").
     */
    internal fun compareVersions(a: String, b: String): Int {
        val (coreA, preA) = splitVersion(normalizeVersion(a))
        val (coreB, preB) = splitVersion(normalizeVersion(b))

        val length = maxOf(coreA.size, coreB.size)
        for (i in 0 until length) {
            val diff = coreA.getOrElse(i) { 0 }.compareTo(coreB.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }

        return when {
            preA == null && preB == null -> 0
            preA == null -> 1
            preB == null -> -1
            else -> comparePreRelease(preA, preB)
        }
    }

    private fun splitVersion(version: String): Pair<List<Int>, String?> {
        val withoutBuild = version.substringBefore('+')
        val dash = withoutBuild.indexOf('-')
        val core = if (dash >= 0) withoutBuild.substring(0, dash) else withoutBuild
        val pre = if (dash >= 0) withoutBuild.substring(dash + 1).ifBlank { null } else null
        val parts = core.split('.').map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
        return parts to pre
    }

    private fun comparePreRelease(a: String, b: String): Int {
        val partsA = tokenize(a)
        val partsB = tokenize(b)
        for (i in 0 until maxOf(partsA.size, partsB.size)) {
            val x = partsA.getOrNull(i) ?: return -1
            val y = partsB.getOrNull(i) ?: return 1
            val nx = x.toIntOrNull()
            val ny = y.toIntOrNull()
            val diff = when {
                nx != null && ny != null -> nx.compareTo(ny)
                nx != null -> -1
                ny != null -> 1
                else -> x.compareTo(y, ignoreCase = true)
            }
            if (diff != 0) return diff
        }
        return 0
    }

    private fun tokenize(value: String): List<String> =
        Regex("\\d+|[A-Za-z]+").findAll(value).map { it.value }.toList()
}
