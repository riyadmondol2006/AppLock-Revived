package dev.riyadm.applock.core.trusted

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Network profile settings: which lock profile each Wi-Fi network activates, and the profile for
 * every other network. Kept in the shared "app_lock_settings" file. None of these keys are in
 * the backup allow-list, so a restored backup can never switch profiles on its own, the same as
 * trusted Wi-Fi.
 */
class NetworkProfileSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) = prefs.edit(commit = true) {
        putBoolean(KEY_ENABLED, enabled)
        // Start fresh so the current network is applied when this is turned on.
        remove(KEY_LAST)
    }

    /** The profile for networks without a rule, or null to leave the active profile alone. */
    fun getDefaultProfileId(): String? = prefs.getString(KEY_DEFAULT, null)?.takeIf { it.isNotEmpty() }

    fun setDefaultProfileId(id: String?) = prefs.edit(commit = true) {
        if (id.isNullOrEmpty()) remove(KEY_DEFAULT) else putString(KEY_DEFAULT, id)
        remove(KEY_LAST)
    }

    fun getRules(): List<NetworkProfileRule> {
        val raw = prefs.getString(KEY_RULES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val ssid = item.optString(FIELD_SSID).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val profileId = item.optString(FIELD_PROFILE).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                NetworkProfileRule(
                    network = TrustedNetwork(
                        ssid = ssid,
                        bssid = item.optString(FIELD_BSSID).takeIf { it.isNotEmpty() },
                        securityType = item.optInt(FIELD_SECURITY, TrustedNetwork.SECURITY_UNKNOWN),
                        addedAt = item.optLong(FIELD_ADDED, 0L)
                    ),
                    profileId = profileId
                )
            }
        } catch (_: Exception) {
            // Corrupt data maps nothing.
            emptyList()
        }
    }

    /** Adds the rule, replacing any rule for the same network. */
    fun setRule(rule: NetworkProfileRule) {
        save(getRules().filterNot { it.network.sameNetwork(rule.network) } + rule)
    }

    fun removeRule(network: TrustedNetwork) {
        save(getRules().filterNot { it.network.sameNetwork(network) })
    }

    /** The connection a profile was last applied for, so a manual switch isn't undone. */
    fun getLastKey(): String? = prefs.getString(KEY_LAST, null)

    fun setLastKey(key: String?) = prefs.edit(commit = true) {
        if (key == null) remove(KEY_LAST) else putString(KEY_LAST, key)
    }

    private fun save(rules: List<NetworkProfileRule>) {
        val array = JSONArray()
        rules.forEach {
            array.put(
                JSONObject()
                    .put(FIELD_SSID, it.network.ssid)
                    .put(FIELD_BSSID, it.network.bssid ?: "")
                    .put(FIELD_SECURITY, it.network.securityType)
                    .put(FIELD_ADDED, it.network.addedAt)
                    .put(FIELD_PROFILE, it.profileId)
            )
        }
        prefs.edit(commit = true) {
            putString(KEY_RULES, array.toString())
            remove(KEY_LAST)
        }
    }

    companion object {
        private const val PREFS_NAME = "app_lock_settings"
        const val KEY_ENABLED = "network_profiles_enabled"
        const val KEY_RULES = "network_profiles_rules"
        const val KEY_DEFAULT = "network_profiles_default"
        private const val KEY_LAST = "network_profiles_last"

        /** Changes to these keys make the monitor look at the connection again. */
        val WATCHED_KEYS = setOf(KEY_ENABLED, KEY_RULES, KEY_DEFAULT)

        private const val FIELD_SSID = "ssid"
        private const val FIELD_BSSID = "bssid"
        private const val FIELD_SECURITY = "security"
        private const val FIELD_ADDED = "added"
        private const val FIELD_PROFILE = "profile"
    }
}
