package dev.riyadm.applock.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores a bounded, on-device history of unlock attempts on locked apps: at most
 * [MAX_ENTRIES] entries, none older than the retention period chosen in settings.
 * Nothing here ever leaves the device.
 */
class UnlockLogRepository(context: Context) {

    private val hardening = HardeningPreferences(context)

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
        val now = System.currentTimeMillis()
        val entries = readArray()
        entries.put(
            JSONObject()
                .put(FIELD_TIME, now)
                .put(FIELD_PACKAGE, packageName)
                .put(FIELD_SUCCESS, success)
                .put(FIELD_METHOD, method.name)
        )
        preferences.edit { putString(KEY_ENTRIES, entries.trimmed(now).toString()) }
    }

    /** Deletes entries past the retention period. Safe to call often; only writes on change. */
    @Synchronized
    fun prune() {
        val entries = readArray()
        if (entries.length() == 0) return
        val trimmed = entries.trimmed(System.currentTimeMillis())
        if (trimmed.length() != entries.length()) {
            preferences.edit { putString(KEY_ENTRIES, trimmed.toString()) }
        }
    }

    /** Newest first. */
    @Synchronized
    fun getEntries(): List<Entry> {
        val array = readArray()
        val cutoff = retentionCutoff(System.currentTimeMillis())
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            if (o.optLong(FIELD_TIME) < cutoff) return@mapNotNull null
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

    private fun retentionCutoff(now: Long): Long =
        now - hardening.unlockLogRetentionDays * DAY_MS

    /** Drops entries older than the retention period, then keeps the newest [MAX_ENTRIES]. */
    private fun JSONArray.trimmed(now: Long): JSONArray {
        val cutoff = retentionCutoff(now)
        val kept = (0 until length()).mapNotNull { i ->
            optJSONObject(i)?.takeIf { it.optLong(FIELD_TIME) >= cutoff }
        }
        return JSONArray().apply { kept.takeLast(MAX_ENTRIES).forEach { put(it) } }
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
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val FIELD_TIME = "t"
        private const val FIELD_PACKAGE = "p"
        private const val FIELD_SUCCESS = "s"
        private const val FIELD_METHOD = "m"
    }
}
