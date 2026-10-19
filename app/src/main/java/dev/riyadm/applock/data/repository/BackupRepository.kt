package dev.riyadm.applock.data.repository

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports and imports the list of locked apps, lock profiles, per-app security levels and
 * non-sensitive settings as JSON. The PIN / pattern / password is never written to the backup
 * file, and neither are trusted networks, automation or intruder settings.
 *
 * Plain backups are format version 1. Password-protected backups (version 2) wrap the same
 * JSON in an envelope encrypted with [BackupCrypto]. Profiles and security levels are optional
 * fields, so older app versions can still read new backups and older backups still import here:
 * without profiles, the backup's locked apps go into the active profile and the other profiles
 * are kept; without security levels, the current levels are kept.
 */
class BackupRepository(
    private val context: Context,
    private val repository: AppLockRepository
) {

    data class ImportResult(
        val lockedApps: Int,
        val settings: Int,
        val profiles: Int = 0,
        val securityLevels: Int = 0,
    )

    /** The file is encrypted and [import] was called without a password. */
    class PasswordRequiredException : Exception("This backup is protected with a password")

    /** Writes a backup; encrypted when [password] is given. */
    fun export(uri: Uri, password: CharArray? = null) {
        val output = exportToString(password)
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("Cannot open $uri for writing")
        stream.bufferedWriter().use { it.write(output) }
    }

    internal fun exportToString(password: CharArray? = null): String {
        val plain = buildPlainBackup()
        return if (password == null) {
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
        return importFromString(text, password)
    }

    /** Same as [import], for the file's text. */
    internal fun importFromString(text: String, password: CharArray? = null): ImportResult {
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
        val profiles = parseProfiles(root.opt(FIELD_PROFILES))
        val levels = parseSecurityLevels(root.opt(FIELD_SECURITY_LEVELS))

        repository.replaceAppLists(locked = locked, excluded = excluded, antiUninstall = antiUninstall)
        // Without profiles in the file, the locked-apps set written above is the active profile.
        val restoredProfiles = profiles
            ?.takeIf { repository.restoreProfiles(it.profiles, it.activeId, locked) }
            ?.profiles?.size ?: 0
        levels?.let(repository.appPolicies::replaceAll)
        val applied = repository.importSettings(settings) + HardeningPreferences(context).importValues(extra)

        return ImportResult(
            lockedApps = locked.size,
            settings = applied,
            profiles = restoredProfiles,
            securityLevels = levels?.size ?: 0
        )
    }

    private class ParsedProfiles(val profiles: List<LockProfile>, val activeId: String)

    /**
     * Valid profiles from the backup, or null if it has none (older backup or unusable field).
     * Entries without a usable id or a known kind are skipped; a bad name, app entry or unlock
     * duration only drops that value. The active profile's apps are the backup's locked apps.
     */
    private fun parseProfiles(value: Any?): ParsedProfiles? {
        val json = value as? JSONObject ?: return null
        val items = json.opt(FIELD_PROFILE_ITEMS) as? JSONArray ?: return null
        val profiles = (0 until items.length()).asSequence()
            .mapNotNull { parseProfile(items.opt(it)) }
            .distinctBy { it.id }
            .take(MAX_PROFILES)
            .toList()
        if (profiles.isEmpty()) return null
        val activeId = (json.opt(FIELD_PROFILE_ACTIVE) as? String)
            ?.takeIf { id -> profiles.any { it.id == id } }
            ?: profiles.first().id
        return ParsedProfiles(profiles, activeId)
    }

    private fun parseProfile(value: Any?): LockProfile? {
        val json = value as? JSONObject ?: return null
        val id = (json.opt(FIELD_PROFILE_ID) as? String)
            ?.takeIf { it.isNotBlank() && it.length <= MAX_ID_LENGTH }
            ?: return null
        val kind = (json.opt(FIELD_PROFILE_KIND) as? String)
            ?.let { name -> ProfileKind.entries.firstOrNull { it.name == name } }
            ?: return null
        val apps = when (val raw = json.opt(FIELD_PROFILE_APPS)) {
            null -> emptySet()
            is JSONArray -> raw.toStringSet()
            else -> return null
        }
        return LockProfile(
            id = id,
            kind = kind,
            customName = (json.opt(FIELD_PROFILE_NAME) as? String)
                ?.trim()?.take(LockProfilesRepository.MAX_NAME_LENGTH)?.ifBlank { null },
            lockedApps = apps,
            unlockDurationMinutes = json.opt(FIELD_PROFILE_UNLOCK).toNonNegativeInt()
        )
    }

    /** Valid levels from the backup, or null if the field is missing or not an object. */
    private fun parseSecurityLevels(value: Any?): Map<String, SecurityLevel>? {
        val json = value as? JSONObject ?: return null
        return json.keys().asSequence()
            .filter { it.isNotBlank() && it.length <= MAX_PACKAGE_LENGTH }
            .mapNotNull { pkg ->
                val name = json.opt(pkg) as? String ?: return@mapNotNull null
                SecurityLevel.entries.firstOrNull { it.name == name }
                    ?.takeIf { it != SecurityLevel.DEFAULT }
                    ?.let { pkg to it }
            }
            .toMap()
    }

    private fun Any?.toNonNegativeInt(): Int? {
        val number = this as? Number ?: return null
        val long = number.toLong()
        if (number.toDouble() != long.toDouble()) return null
        return long.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
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
            .put(FIELD_PROFILES, buildProfiles())
            .put(FIELD_SECURITY_LEVELS, buildSecurityLevels())
    }

    private fun buildProfiles(): JSONObject {
        val state = repository.profiles.snapshot()
        val items = JSONArray()
        state.profiles.forEach { profile ->
            val json = JSONObject()
                .put(FIELD_PROFILE_ID, profile.id)
                .put(FIELD_PROFILE_KIND, profile.kind.name)
                .put(FIELD_PROFILE_APPS, JSONArray(profile.lockedApps.sorted()))
            profile.customName?.let { json.put(FIELD_PROFILE_NAME, it) }
            profile.unlockDurationMinutes?.let { json.put(FIELD_PROFILE_UNLOCK, it) }
            items.put(json)
        }
        return JSONObject()
            .put(FIELD_PROFILE_ACTIVE, state.activeId)
            .put(FIELD_PROFILE_ITEMS, items)
    }

    private fun buildSecurityLevels(): JSONObject {
        val json = JSONObject()
        repository.appPolicies.levels.value.toSortedMap().forEach { (pkg, level) ->
            if (level != SecurityLevel.DEFAULT) json.put(pkg, level.name)
        }
        return json
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
        return (0 until length()).mapNotNull { (opt(it) as? String)?.takeIf(String::isNotBlank) }.toSet()
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
        private const val FIELD_PROFILES = "profiles"
        private const val FIELD_PROFILE_ACTIVE = "active"
        private const val FIELD_PROFILE_ITEMS = "items"
        private const val FIELD_PROFILE_ID = "id"
        private const val FIELD_PROFILE_KIND = "kind"
        private const val FIELD_PROFILE_NAME = "name"
        private const val FIELD_PROFILE_APPS = "apps"
        private const val FIELD_PROFILE_UNLOCK = "unlockMinutes"
        private const val FIELD_SECURITY_LEVELS = "appSecurityLevels"
        private const val MAX_PROFILES = 64
        private const val MAX_ID_LENGTH = 64
        private const val MAX_PACKAGE_LENGTH = 255
        private const val FIELD_KDF = "kdf"
        private const val FIELD_ITERATIONS = "iterations"
        private const val FIELD_CIPHER = "cipher"
        private const val FIELD_SALT = "salt"
        private const val FIELD_IV = "iv"
        private const val FIELD_CIPHERTEXT = "ciphertext"
    }
}
