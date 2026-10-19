package dev.riyadm.applock.core.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

@SuppressLint("StaticFieldLeak")
object LogUtils {
    private val logScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
    private const val TAG = "LogUtils"
    private const val FILE_NAME = "app_logs.txt"
    private const val SECURITY_LOGS = "audit_log.txt"
    private const val RETENTION_DAYS = 7L
    private const val OLDEST_ENTRY_SCAN_LINES = 64
    private lateinit var context: Context
    @Volatile
    private var loggingEnabled = false

    fun initialize(application: Context) {
        context = application
    }

    fun setLoggingEnabled(enabled: Boolean) {
        loggingEnabled = enabled
    }

    fun d(tag: String, message: String) {
        if (!loggingEnabled) return

        val line = "${Instant.now()} D $tag: $message\n"
        Log.d(tag, message)
        writeAuditLogLine(line)
    }

    fun e(tag: String, message: String, e: Throwable? = null) {
        if (!loggingEnabled) return

        val line = "${Instant.now()} E $tag: $message\n${Log.getStackTraceString(e)}\n"
        Log.e(tag, message)
        writeAuditLogLine(line)
    }

    private fun writeAuditLogLine(line: String) {
        logScope.launch {
            try {
                val file = File(context.filesDir, SECURITY_LOGS)
                if (!file.exists()) {
                    file.createNewFile()
                }
                file.appendText(line)
            } catch (e: Exception) {
                Log.e(TAG, "Error writing audit log", e)
            }
        }
    }

    fun exportAuditLogs(): Uri? {
        val file = File(context.filesDir, SECURITY_LOGS)
        return if (file.exists()) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } else {
            null
        }
    }

    fun exportLogs(): Uri? {
        val file = File(context.cacheDir, FILE_NAME)
        try {
            if (file.exists()) {
                file.delete()
            }
            file.createNewFile()

            val process = Runtime.getRuntime().exec("logcat -d")

            process.inputStream.bufferedReader().use { reader ->
                file.writer().use { writer ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        reader.transferTo(writer)
                    } else {
                        reader.forEachLine { line ->
                            writer.write(line + "\n")
                        }
                    }
                }
            }

            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error exporting logs", e)
            return null
        }
    }

    /**
     * Clear all security and audit logs.
     * Called when the app is updated.
     */
    fun clearAllLogs() {
        logScope.launch {
            try {
                val securityLogFile = File(context.filesDir, SECURITY_LOGS)
                if (securityLogFile.exists()) {
                    securityLogFile.delete()
                    Log.d(TAG, "Cleared security logs")
                }

                val appLogFile = File(context.cacheDir, FILE_NAME)
                if (appLogFile.exists()) {
                    appLogFile.delete()
                    Log.d(TAG, "Cleared app logs")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing logs", e)
            }
        }
    }

    /**
     * Purge log entries older than [RETENTION_DAYS] days from both audit and app log files.
     * This prevents logs from growing indefinitely.
     * Runs asynchronously to avoid blocking the main thread.
     */
    fun purgeOldLogs() {
        logScope.launch {
            purgeOldLogsFromFile(File(context.filesDir, SECURITY_LOGS), "audit")
            purgeOldLogsFromFile(File(context.cacheDir, FILE_NAME), "app")
        }
    }

    private fun oldestEntry(logFile: File): Instant? = try {
        logFile.bufferedReader().use { reader ->
            reader.lineSequence()
                .take(OLDEST_ENTRY_SCAN_LINES)
                .firstNotNullOfOrNull { line -> runCatching { Instant.parse(line.substringBefore(" ")) }.getOrNull() }
        }
    } catch (_: Exception) {
        null
    }

    private fun purgeOldLogsFromFile(logFile: File, logType: String) {
        try {
            if (!logFile.exists()) {
                return
            }
            val cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS)
            // Lines are appended in order, so the first one is the oldest. Runs on every app
            // start: don't copy and rewrite the file when nothing is old enough to go.
            if (oldestEntry(logFile)?.isAfter(cutoff) == true) return

            val tempDir = context.cacheDir
            val tempLogFile = File(tempDir, logFile.name + ".processing")
            val backupFile = File(tempDir, logFile.name + ".backup")

            try {
                logFile.copyTo(backupFile, overwrite = true)

                var purgedCount = 0
                var keptCount = 0

                backupFile.bufferedReader().use { reader ->
                    tempLogFile.bufferedWriter().use { writer ->
                        reader.forEachLine { line ->
                            try {
                                val timestampStr = line.substringBefore(" ")
                                val timestamp = Instant.parse(timestampStr)

                                if (timestamp.isAfter(cutoff)) {
                                    writer.write(line)
                                    writer.newLine()
                                    keptCount++
                                } else {
                                    purgedCount++
                                }
                            } catch (_: Exception) {
                                writer.write(line)
                                writer.newLine()
                                keptCount++
                            }
                        }
                    }
                }

                if (keptCount == 0) {
                    logFile.delete()
                    tempLogFile.delete()
                    Log.d(TAG, "Deleted $logType log file - all entries were older than $RETENTION_DAYS days")
                } else if (purgedCount > 0) {
                    tempLogFile.copyTo(logFile, overwrite = true)
                    tempLogFile.delete()
                    Log.d(TAG, "Purged $purgedCount old $logType log entries")
                } else {
                    tempLogFile.delete()
                }
            } finally {
                backupFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error purging old $logType logs", e)
        }
    }
}
