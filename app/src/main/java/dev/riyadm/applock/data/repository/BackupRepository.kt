package dev.riyadm.applock.data.repository

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports and imports the list of locked apps and non-sensitive settings as JSON.
 * The PIN / pattern / password is never written to the backup file.
 *
 * Plain backups are format version 1. Password-protected backups (version 2) wrap the same
 * JSON in an envelope encrypted with [BackupCrypto].
 */
class BackupRepository(
    private val context: Context,
    private val repository: AppLockRepository
) {

    data class ImportResult(val lockedApps: Int, val settings: Int)

    /** The file is encrypted and [import] was called without a password. */
    class PasswordRequiredException : Exception("This backup is protected with a password")

    /** Writes a backup; encrypted when [password] is given. */
    fun export(uri: Uri, password: CharArray? = null) {
        val plain = buildPlainBackup()
        val output = if (password == null) {
            plain.toString(2)
        } else {
            val sealed = BackupCrypto.encrypt(plain.toString().toByteArray(Charsets.UTF_8), password)
            JSONObject()
                .put(FIELD_FORMAT, FORMAT_ID)
                .put(FIELD_VERSION, FORMAT_VERSION_ENCRYPTED)
                .put(FIELD_KDF, BackupCrypto.KDF)
                .put(FIELD_ITERATIONS, sealed.iterations)
                .put(FIELD_CIPHER, BackupCrypto.CIPHER)
                .put(FIELD_SALT, BackupCrypto.encode(sealed.salt))
                .put(FIELD_IV, BackupCrypto.encode(sealed.iv))
                .put(FIELD_CIPHERTEXT, BackupCrypto.encode(sealed.ciphertext))
                .toString(2)
        }

        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("Cannot open $uri for writing")
        stream.bufferedWriter().use { it.write(output) }
    }

    /**
     * Reads a backup and applies it. Nothing is changed unless the whole file was read,
     * decrypted and validated.
     *
     * @throws PasswordRequiredException if the file is encrypted and [password] is null
     * @throws BackupCrypto.WrongPasswordException if the password is wrong or the file was changed
     * @throws IllegalArgumentException if the file isn't an App Lock backup
     */
    fun import(uri: Uri, password: CharArray? = null): ImportResult {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open $uri for reading")
        val text = stream.bufferedReader().use { it.readText() }
        var root = parse(text)
        require(root.optString(FIELD_FORMAT) == FORMAT_ID) { "Not an App Lock backup file" }

        if (root.has(FIELD_CIPHERTEXT)) {
            require(root.optInt(FIELD_VERSION) <= FORMAT_VERSION_ENCRYPTED) { "Backup is from a newer version" }
            require(root.optString(FIELD_KDF) == BackupCrypto.KDF &&
                    root.optString(FIELD_CIPHER, BackupCrypto.CIPHER) == BackupCrypto.CIPHER) {
                "Backup is from a newer version"
            }
            if (password == null) throw PasswordRequiredException()
            val sealed = BackupCrypto.Sealed(
                iterations = root.optInt(FIELD_ITERATIONS),
                salt = BackupCrypto.decode(root.optString(FIELD_SALT)),
                iv = BackupCrypto.decode(root.optString(FIELD_IV)),
                ciphertext = BackupCrypto.decode(root.optString(FIELD_CIPHERTEXT))
            )
            root = parse(String(BackupCrypto.decrypt(sealed, password), Charsets.UTF_8))
            require(root.optString(FIELD_FORMAT) == FORMAT_ID) { "Not an App Lock backup file" }
        }
        require(root.optInt(FIELD_VERSION) <= FORMAT_VERSION) { "Backup is from a newer version" }

        val locked = root.optJSONArray(FIELD_LOCKED).toStringSet()
        val excluded = root.optJSONArray(FIELD_EXCLUDED).toStringSet()
        val antiUninstall = root.optJSONArray(FIELD_ANTI_UNINSTALL).toStringSet()
        val settings = root.optJSONObject(FIELD_SETTINGS).toMap()
        val extra = root.optJSONObject(FIELD_EXTRA_SETTINGS).toMap()

        repository.replaceAppLists(locked = locked, excluded = excluded, antiUninstall = antiUninstall)
        val applied = repository.importSettings(settings) + HardeningPreferences(context).importValues(extra)

        return ImportResult(lockedApps = locked.size, settings = applied)
    }

    private fun buildPlainBackup(): JSONObject {
        val settings = JSONObject()
        repository.exportSettings().forEach { (key, value) -> settings.put(key, value) }
        val extra = JSONObject()
        HardeningPreferences(context).exportValues().forEach { (key, value) -> extra.put(key, value) }

        return JSONObject()
            .put(FIELD_FORMAT, FORMAT_ID)
            .put(FIELD_VERSION, FORMAT_VERSION)
            .put(FIELD_CREATED, System.currentTimeMillis())
            .put(FIELD_LOCKED, JSONArray(repository.getLockedApps().sorted()))
            .put(FIELD_EXCLUDED, JSONArray(repository.getTriggerExcludedApps().sorted()))
            .put(FIELD_ANTI_UNINSTALL, JSONArray(repository.getAntiUninstallApps().sorted()))
            .put(FIELD_SETTINGS, settings)
            .put(FIELD_EXTRA_SETTINGS, extra)
    }

    private fun parse(text: String): JSONObject = try {
        JSONObject(text)
    } catch (e: Exception) {
        throw IllegalArgumentException("Not a valid backup file", e)
    }

    private fun JSONObject?.toMap(): Map<String, Any?> {
        if (this == null) return emptyMap()
        return keys().asSequence().associateWith { get(it) }
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }.toSet()
    }

    companion object {
        const val MIME_TYPE = "application/json"
        const val SUGGESTED_FILE_NAME = "applock-revived-backup.json"
        const val SUGGESTED_ENCRYPTED_FILE_NAME = "applock-revived-backup-encrypted.json"

        private const val FORMAT_ID = "dev.riyadm.applock.backup"
        private const val FORMAT_VERSION = 1
        private const val FORMAT_VERSION_ENCRYPTED = 2
        private const val FIELD_FORMAT = "format"
        private const val FIELD_VERSION = "version"
        private const val FIELD_CREATED = "created"
        private const val FIELD_LOCKED = "lockedApps"
        private const val FIELD_EXCLUDED = "triggerExcludedApps"
        private const val FIELD_ANTI_UNINSTALL = "antiUninstallApps"
        private const val FIELD_SETTINGS = "settings"
        private const val FIELD_EXTRA_SETTINGS = "hardeningSettings"
        private const val FIELD_KDF = "kdf"
        private const val FIELD_ITERATIONS = "iterations"
        private const val FIELD_CIPHER = "cipher"
        private const val FIELD_SALT = "salt"
        private const val FIELD_IV = "iv"
        private const val FIELD_CIPHERTEXT = "ciphertext"
    }
}
