package dev.riyadm.applock.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ProfileKind { HOME, WORK, GUEST, CUSTOM }

/**
 * A named set of locked apps with an optional unlock-duration override (minutes, null = use the
 * global setting). [customName] is null for built-in profiles that keep their localized name.
 */
@Immutable
data class LockProfile(
    val id: String,
    val kind: ProfileKind,
    val customName: String?,
    val lockedApps: Set<String>,
    val unlockDurationMinutes: Int?,
)

@Immutable
data class LockProfilesState(val profiles: List<LockProfile>, val activeId: String) {
    val active: LockProfile get() = profiles.firstOrNull { it.id == activeId } ?: profiles.first()
}

/**
 * Lock profiles. The active profile's apps live in [LockedAppsRepository]'s locked-apps set,
 * which every backend already reads, so the services see the active profile without knowing
 * about profiles. Inactive profiles are stored as JSON in the same preferences file, which lets a
 * switch (save the old set, load the new one, change the active id) happen in a single commit.
 */
class LockProfilesRepository(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(LockedAppsRepository.PREFS_NAME, Context.MODE_PRIVATE)

    private val lock = Any()
    private val _state: MutableStateFlow<LockProfilesState>
    val state: StateFlow<LockProfilesState>

    // Held as a field: SharedPreferences only keeps a weak reference to listeners.
    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in WATCHED_KEYS) reload()
    }

    init {
        migrateIfNeeded()
        _state = MutableStateFlow(read())
        state = _state.asStateFlow()
        preferences.registerOnSharedPreferenceChangeListener(changeListener)
    }

    fun active(): LockProfile = _state.value.active

    /** Unlock duration of the active profile, or null to use the global setting. */
    fun activeUnlockDurationOverride(): Int? = _state.value.active.unlockDurationMinutes

    /**
     * Makes [id] the active profile. Returns the apps that are locked now but weren't before,
     * or null if [id] doesn't exist.
     */
    fun switchTo(id: String): Set<String>? = synchronized(lock) {
        val current = read()
        val target = current.profiles.firstOrNull { it.id == id } ?: return null
        if (target.id == current.activeId) return emptySet()
        val previousApps = current.active.lockedApps
        preferences.edit(commit = true) {
            putString(KEY_PROFILES, encode(current.profiles))
            putString(KEY_ACTIVE, target.id)
            putStringSet(LockedAppsRepository.KEY_LOCKED_APPS, target.lockedApps.toSet())
        }
        reload()
        target.lockedApps - previousApps
    }

    /** Creates a custom profile that starts with a copy of the active profile's apps. */
    fun create(name: String): LockProfile = synchronized(lock) {
        val current = read()
        val profile = LockProfile(
            id = "custom_" + UUID.randomUUID().toString().take(8),
            kind = ProfileKind.CUSTOM,
            customName = name.trim().take(MAX_NAME_LENGTH).ifBlank { null },
            lockedApps = current.active.lockedApps,
            unlockDurationMinutes = current.active.unlockDurationMinutes
        )
        write(current.profiles + profile)
        profile
    }

    fun rename(id: String, name: String) = update(id) {
        it.copy(customName = name.trim().take(MAX_NAME_LENGTH).ifBlank { null })
    }

    fun setUnlockDuration(id: String, minutes: Int?) = update(id) {
        it.copy(unlockDurationMinutes = minutes?.coerceAtLeast(0))
    }

    /** Deletes a custom profile. The active profile and the built-in ones can't be deleted. */
    fun delete(id: String): Boolean = synchronized(lock) {
        val current = read()
        val profile = current.profiles.firstOrNull { it.id == id } ?: return false
        if (profile.kind != ProfileKind.CUSTOM || profile.id == current.activeId) return false
        write(current.profiles - profile)
        true
    }

    /** Adds or removes an app in an inactive profile. Use the locked-apps API for the active one. */
    internal fun setStoredMembership(id: String, packageName: String, member: Boolean) {
        if (packageName.isBlank()) return
        update(id, allowActive = false) {
            it.copy(lockedApps = if (member) it.lockedApps + packageName else it.lockedApps - packageName)
        }
    }

    private fun update(
        id: String,
        allowActive: Boolean = true,
        transform: (LockProfile) -> LockProfile,
    ) = synchronized(lock) {
        val current = read()
        if (!allowActive && id == current.activeId) return@synchronized
        if (current.profiles.none { it.id == id }) return@synchronized
        write(current.profiles.map { if (it.id == id) transform(it) else it })
    }

    /** Writes profile metadata and inactive sets; never touches the active locked-apps set. */
    private fun write(profiles: List<LockProfile>) {
        preferences.edit(commit = true) { putString(KEY_PROFILES, encode(profiles)) }
        reload()
    }

    // Reads the in-memory preferences without the write lock, so the main-thread change listener
    // never waits on a commit. Every write notifies the listener afterwards, so state converges.
    private fun reload() {
        _state.value = read()
    }

    private fun read(): LockProfilesState {
        val mirror = lockedAppsMirror()
        val decoded = decode(preferences.getString(KEY_PROFILES, null))
        val profiles = decoded.ifEmpty { defaultProfiles(mirror) }
        val activeId = preferences.getString(KEY_ACTIVE, null)
            ?.takeIf { id -> profiles.any { it.id == id } }
            ?: profiles.first().id
        // The locked-apps set is the source of truth for the active profile.
        return LockProfilesState(
            profiles = profiles.map { if (it.id == activeId) it.copy(lockedApps = mirror) else it },
            activeId = activeId
        )
    }

    /** First run with profiles: everything locked so far becomes the Home profile. */
    private fun migrateIfNeeded() = synchronized(lock) {
        if (preferences.contains(KEY_PROFILES)) return@synchronized
        val mirror = lockedAppsMirror()
        preferences.edit(commit = true) {
            putString(KEY_PROFILES, encode(defaultProfiles(mirror)))
            putString(KEY_ACTIVE, ID_HOME)
        }
    }

    private fun lockedAppsMirror(): Set<String> =
        preferences.getStringSet(LockedAppsRepository.KEY_LOCKED_APPS, emptySet())?.toSet()
            ?: emptySet()

    private fun defaultProfiles(homeApps: Set<String>) = listOf(
        LockProfile(ID_HOME, ProfileKind.HOME, null, homeApps, null),
        LockProfile(ID_WORK, ProfileKind.WORK, null, emptySet(), null),
        LockProfile(ID_GUEST, ProfileKind.GUEST, null, emptySet(), null),
    )

    private fun encode(profiles: List<LockProfile>): String {
        val array = JSONArray()
        profiles.forEach { profile ->
            val json = JSONObject()
                .put(FIELD_ID, profile.id)
                .put(FIELD_KIND, profile.kind.name)
                .put(FIELD_APPS, JSONArray(profile.lockedApps.sorted()))
            profile.customName?.let { json.put(FIELD_NAME, it) }
            profile.unlockDurationMinutes?.let { json.put(FIELD_UNLOCK, it) }
            array.put(json)
        }
        return JSONObject().put(FIELD_PROFILES, array).toString()
    }

    private fun decode(raw: String?): List<LockProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONObject(raw).optJSONArray(FIELD_PROFILES) ?: return emptyList()
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val id = json.optString(FIELD_ID).takeIf(String::isNotBlank) ?: return@mapNotNull null
                val apps = json.optJSONArray(FIELD_APPS)
                LockProfile(
                    id = id,
                    kind = ProfileKind.entries.firstOrNull { it.name == json.optString(FIELD_KIND) }
                        ?: ProfileKind.CUSTOM,
                    customName = json.optString(FIELD_NAME).takeIf(String::isNotBlank),
                    lockedApps = if (apps == null) emptySet() else {
                        (0 until apps.length()).mapNotNull { apps.optString(it).takeIf(String::isNotBlank) }.toSet()
                    },
                    unlockDurationMinutes = if (json.has(FIELD_UNLOCK)) json.optInt(FIELD_UNLOCK) else null
                )
            }.distinctBy { it.id }
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't read lock profiles", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "LockProfilesRepository"
        const val ID_HOME = "home"
        const val ID_WORK = "work"
        const val ID_GUEST = "guest"
        const val MAX_NAME_LENGTH = 32

        private const val KEY_PROFILES = "lock_profiles"
        private const val KEY_ACTIVE = "lock_profile_active"
        private val WATCHED_KEYS = setOf(KEY_PROFILES, KEY_ACTIVE, LockedAppsRepository.KEY_LOCKED_APPS)

        private const val FIELD_PROFILES = "profiles"
        private const val FIELD_ID = "id"
        private const val FIELD_KIND = "kind"
        private const val FIELD_NAME = "name"
        private const val FIELD_APPS = "apps"
        private const val FIELD_UNLOCK = "unlockMinutes"
    }
}
