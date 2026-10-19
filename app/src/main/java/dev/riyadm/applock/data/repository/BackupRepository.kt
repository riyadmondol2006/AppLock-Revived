package dev.riyadm.applock.data.repository

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports and imports the list of locked apps and non-sensitive settings as JSON.
 * The PIN / pattern / password is never written to the backup file.
 */
class BackupRepository(
    private val context: Context,
    private val repository: AppLockRepository
) {

    data class ImportResult(val lockedApps: Int, val settings: Int)

    fun export(uri: Uri) {
        val settings = JSONObject()
        repository.exportSettings().forEach { (key, value) -> settings.put(key, value) }

        val root = JSONObject()
            .put(FIELD_FORMAT, FORMAT_ID)
            .put(FIELD_VERSION, FORMAT_VERSION)
            .put(FIELD_CREATED, System.currentTimeMillis())
            .put(FIELD_LOCKED, JSONArray(repository.getLockedApps().sorted()))
            .put(FIELD_EXCLUDED, JSONArray(repository.getTriggerExcludedApps().sorted()))
            .put(FIELD_ANTI_UNINSTALL, JSONArray(repository.getAntiUninstallApps().sorted()))
            .put(FIELD_SETTINGS, settings)

        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("Cannot open $uri for writing")
        stream.bufferedWriter().use { it.write(root.toString(2)) }
    }

    /** Throws [IllegalArgumentException] if the file isn't an App Lock backup. */
    fun import(uri: Uri): ImportResult {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open $uri for reading")
        val text = stream.bufferedReader().use { it.readText() }
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a valid backup file", e)
        }
        require(root.optString(FIELD_FORMAT) == FORMAT_ID) { "Not an App Lock backup file" }
        require(root.optInt(FIELD_VERSION) <= FORMAT_VERSION) { "Backup is from a newer version" }

        val locked = root.optJSONArray(FIELD_LOCKED).toStringSet()
        repository.replaceAppLists(
            locked = locked,
            excluded = root.optJSONArray(FIELD_EXCLUDED).toStringSet(),
            antiUninstall = root.optJSONArray(FIELD_ANTI_UNINSTALL).toStringSet()
        )

        val settingsJson = root.optJSONObject(FIELD_SETTINGS) ?: JSONObject()
        val settings = settingsJson.keys().asSequence().associateWith { settingsJson.get(it) }
        repository.importSettings(settings)

        return ImportResult(lockedApps = locked.size, settings = settings.size)
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }.toSet()
    }

    companion object {
        const val MIME_TYPE = "application/json"
        const val SUGGESTED_FILE_NAME = "applock-revived-backup.json"

        private const val FORMAT_ID = "dev.riyadm.applock.backup"
        private const val FORMAT_VERSION = 1
        private const val FIELD_FORMAT = "format"
        private const val FIELD_VERSION = "version"
        private const val FIELD_CREATED = "created"
        private const val FIELD_LOCKED = "lockedApps"
        private const val FIELD_EXCLUDED = "triggerExcludedApps"
        private const val FIELD_ANTI_UNINSTALL = "antiUninstallApps"
        private const val FIELD_SETTINGS = "settings"
    }
}
