package dev.riyadm.applock.features.intruder.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

@Immutable
data class IntruderPhoto(
    val file: File,
    val timestamp: Long,
    val packageName: String,
    val attempts: Int
) {
    val id: String get() = file.name
}

/**
 * Intruder photos in app-private storage (filesDir/intruders). Nothing here is ever written to
 * shared storage, uploaded, or included in backups. Metadata is encoded in the file name so a
 * photo and its details can never get out of sync. All methods do disk I/O.
 */
object IntruderPhotoStore {
    private const val TAG = "IntruderPhotoStore"
    private const val DIR = "intruders"
    private const val PREFIX = "intruder"
    private const val EXTENSION = ".jpg"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    private val _changes = MutableStateFlow(0)

    /** Bumped whenever photos are added or removed. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    fun directory(context: Context): File = File(context.filesDir, DIR)

    @Synchronized
    fun save(context: Context, jpeg: ByteArray, packageName: String, attempts: Int, timestamp: Long): IntruderPhoto? {
        val dir = directory(context)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val name = "${PREFIX}_${timestamp}_${attempts}_$packageName$EXTENSION"
        val target = File(dir, name)
        val temp = File(dir, "$name.tmp")
        return try {
            temp.outputStream().use { it.write(jpeg) }
            if (!temp.renameTo(target)) {
                temp.delete()
                return null
            }
            _changes.update { it + 1 }
            IntruderPhoto(target, timestamp, packageName, attempts)
        } catch (e: Exception) {
            Log.w(TAG, "Could not save photo", e)
            temp.delete()
            null
        }
    }

    /** Newest first. */
    fun list(context: Context): List<IntruderPhoto> {
        val files = directory(context).listFiles() ?: return emptyList()
        return files.mapNotNull(::parse).sortedByDescending { it.timestamp }
    }

    fun count(context: Context): Int =
        directory(context).listFiles()?.count { parse(it) != null } ?: 0

    fun delete(photo: IntruderPhoto): Boolean {
        val deleted = photo.file.delete()
        if (deleted) _changes.update { it + 1 }
        return deleted
    }

    @Synchronized
    fun deleteAll(context: Context) {
        directory(context).listFiles()?.forEach { it.delete() }
        _changes.update { it + 1 }
    }

    /** Deletes photos older than the retention setting, plus any half-written leftovers. */
    @Synchronized
    fun applyRetention(context: Context, retentionDays: Int = IntruderSettings(context).retentionDays) {
        val files = directory(context).listFiles() ?: return
        val cutoff = if (retentionDays == IntruderSettings.KEEP_FOREVER) Long.MIN_VALUE
        else System.currentTimeMillis() - retentionDays * DAY_MS
        var removed = 0
        files.forEach { file ->
            val photo = parse(file)
            val expired = photo == null || photo.timestamp < cutoff
            if (expired && file.delete()) removed++
        }
        if (removed > 0) _changes.update { it + 1 }
    }

    private fun parse(file: File): IntruderPhoto? {
        val name = file.name
        if (!file.isFile || !name.startsWith("${PREFIX}_") || !name.endsWith(EXTENSION)) return null
        val parts = name.removeSuffix(EXTENSION).split('_', limit = 4)
        if (parts.size != 4) return null
        val timestamp = parts[1].toLongOrNull() ?: return null
        val attempts = parts[2].toIntOrNull() ?: return null
        val packageName = parts[3].takeIf { it.isNotBlank() } ?: return null
        return IntruderPhoto(file, timestamp, packageName, attempts)
    }
}
