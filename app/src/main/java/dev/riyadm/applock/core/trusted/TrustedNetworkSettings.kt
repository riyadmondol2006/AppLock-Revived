package dev.riyadm.applock.core.trusted

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * A Wi-Fi network the user trusts. [ssid] is stored exactly as WifiInfo.getSSID() returns it
 * (quoted for UTF-8 names, bare hex otherwise) so matching is byte-exact. When [bssid] is set,
 * only that access point counts. [securityType] is a WifiInfo.SECURITY_TYPE_* value, or
 * [SECURITY_UNKNOWN] when the Android version can't report it.
 */
@Immutable
data class TrustedNetwork(
    val ssid: String,
    val bssid: String?,
    val securityType: Int,
    val addedAt: Long,
) {
    val displayName: String get() = displaySsid(ssid)

    fun sameNetwork(other: TrustedNetwork): Boolean = ssid == other.ssid && bssid == other.bssid

    companion object {
        const val SECURITY_UNKNOWN = -1
    }
}

fun displaySsid(raw: String): String =
    if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) raw.substring(1, raw.length - 1) else raw

/**
 * Trusted Wi-Fi settings, kept in the shared "app_lock_settings" file. None of these keys are in
 * the backup allow-list, so a restored backup can never enable trust on its own.
 */
class TrustedNetworkSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) = prefs.edit(commit = true) { putBoolean(KEY_ENABLED, enabled) }

    fun getNetworks(): List<TrustedNetwork> {
        val raw = prefs.getString(KEY_NETWORKS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val ssid = item.optString(FIELD_SSID).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                TrustedNetwork(
                    ssid = ssid,
                    bssid = item.optString(FIELD_BSSID).takeIf { it.isNotEmpty() },
                    securityType = item.optInt(FIELD_SECURITY, TrustedNetwork.SECURITY_UNKNOWN),
                    addedAt = item.optLong(FIELD_ADDED, 0L)
                )
            }
        } catch (_: Exception) {
            // Corrupt data trusts nothing.
            emptyList()
        }
    }

    fun addNetwork(network: TrustedNetwork) {
        val updated = getNetworks().filterNot { it.sameNetwork(network) } + network
        save(updated)
    }

    fun removeNetwork(network: TrustedNetwork) {
        save(getNetworks().filterNot { it.sameNetwork(network) })
    }

    private fun save(networks: List<TrustedNetwork>) {
        val array = JSONArray()
        networks.forEach {
            array.put(
                JSONObject()
                    .put(FIELD_SSID, it.ssid)
                    .put(FIELD_BSSID, it.bssid ?: "")
                    .put(FIELD_SECURITY, it.securityType)
                    .put(FIELD_ADDED, it.addedAt)
            )
        }
        prefs.edit(commit = true) { putString(KEY_NETWORKS, array.toString()) }
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        private const val PREFS_NAME = "app_lock_settings"
        const val KEY_ENABLED = "trusted_wifi_enabled"
        const val KEY_NETWORKS = "trusted_wifi_networks"

        private const val FIELD_SSID = "ssid"
        private const val FIELD_BSSID = "bssid"
        private const val FIELD_SECURITY = "security"
        private const val FIELD_ADDED = "added"
    }
}
