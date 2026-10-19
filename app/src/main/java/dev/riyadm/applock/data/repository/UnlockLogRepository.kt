package dev.riyadm.applock.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores a bounded, on-device history of unlock attempts on locked apps.
 * Nothing here ever leaves the device.
 */
class UnlockLogRepository(context: Context) {

    enum class Method { PIN, PATTERN, PASSWORD, BIOMETRIC }

    data class Entry(
        val timestamp: Long,
        val packageName: String,
        val success: Boolean,
        val method: Method
    )

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun add(packageName: String, success: Boolean, method: Method) {
        val entries = readArray()
        entries.put(
            JSONObject()
                .put(FIELD_TIME, System.currentTimeMillis())
                .put(FIELD_PACKAGE, packageName)
                .put(FIELD_SUCCESS, success)
                .put(FIELD_METHOD, method.name)
        )
        val trimmed = if (entries.length() > MAX_ENTRIES) {
            JSONArray().apply {
                for (i in entries.length() - MAX_ENTRIES until entries.length()) put(entries.get(i))
            }
        } else entries
        preferences.edit { putString(KEY_ENTRIES, trimmed.toString()) }
    }

    /** Newest first. */
    @Synchronized
    fun getEntries(): List<Entry> {
        val array = readArray()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            Entry(
                timestamp = o.optLong(FIELD_TIME),
                packageName = o.optString(FIELD_PACKAGE),
                success = o.optBoolean(FIELD_SUCCESS),
                method = runCatching { Method.valueOf(o.optString(FIELD_METHOD)) }
                    .getOrDefault(Method.PIN)
            )
        }.reversed()
    }

    fun getFailedCountSince(timestamp: Long): Int =
        getEntries().count { !it.success && it.timestamp >= timestamp }

    @Synchronized
    fun clear() {
        preferences.edit { remove(KEY_ENTRIES) }
    }

    private fun readArray(): JSONArray = try {
        JSONArray(preferences.getString(KEY_ENTRIES, "[]"))
    } catch (_: Exception) {
        JSONArray()
    }

    companion object {
        private const val PREFS_NAME = "unlock_log"
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ENTRIES = 200
        private const val FIELD_TIME = "t"
        private const val FIELD_PACKAGE = "p"
        private const val FIELD_SUCCESS = "s"
        private const val FIELD_METHOD = "m"
    }
}
