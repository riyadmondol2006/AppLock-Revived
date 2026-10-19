package dev.riyadm.applock.features.applist.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * Memory-bounded cache for app icons and labels. Icons are rasterised once at a fixed size so
 * a list of hundreds of apps neither re-decodes drawables nor holds full-resolution bitmaps.
 * All loading methods do I/O and must be called off the main thread.
 */
object AppIconCache {
    private const val TAG = "AppIconCache"
    private const val ICON_SIZE_PX = 144
    private const val MAX_LABELS = 1000

    private val iconCache = object : LruCache<String, ImageBitmap>(maxIconBytes()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * 4
    }
    private val labelCache = LruCache<String, String>(MAX_LABELS)

    private fun maxIconBytes(): Int {
        val eighthOfHeap = Runtime.getRuntime().maxMemory() / 8
        return eighthOfHeap.coerceAtMost(32L * 1024 * 1024).toInt()
    }

    fun peekIcon(packageName: String): ImageBitmap? = iconCache.get(packageName)

    fun getIcon(context: Context, appInfo: ApplicationInfo): ImageBitmap? {
        iconCache.get(appInfo.packageName)?.let { return it }
        val pm = context.packageManager
        val icon = try {
            appInfo.loadIcon(pm)
                .toBitmap(ICON_SIZE_PX, ICON_SIZE_PX)
                .asImageBitmap()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load icon for ${appInfo.packageName}", e)
            null
        }
        icon?.let { iconCache.put(appInfo.packageName, it) }
        return icon
    }

    /** Loads the icon of a package by name, returning null if it isn't installed. */
    fun getIcon(context: Context, packageName: String): ImageBitmap? {
        iconCache.get(packageName)?.let { return it }
        val info = try {
            context.packageManager.getApplicationInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        return getIcon(context, info)
    }

    fun getLabel(context: Context, appInfo: ApplicationInfo): String {
        labelCache.get(appInfo.packageName)?.let { return it }
        val label = runCatching { appInfo.loadLabel(context.packageManager).toString() }
            .getOrDefault(appInfo.packageName)
        labelCache.put(appInfo.packageName, label)
        return label
    }

    fun putLabel(packageName: String, label: String) {
        labelCache.put(packageName, label)
    }

    fun invalidate(packageName: String) {
        iconCache.remove(packageName)
        labelCache.remove(packageName)
    }

    fun clear() {
        iconCache.evictAll()
        labelCache.evictAll()
    }
}
