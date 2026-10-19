package dev.riyadm.applock.features.hardening

import android.content.Context
import android.util.Log
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.data.repository.HardeningPreferences
import kotlin.concurrent.thread

/** App-start hook for the hardening features. */
object Hardening {
    private const val TAG = "Hardening"

    fun onAppCreate(context: Context) {
        val app = context.applicationContext
        try {
            HardeningPreferences(app).migrateUnlockLogDefault()
        } catch (e: Exception) {
            Log.e(TAG, "Settings migration failed", e)
        }
        LockedAppCloser.register(app)
        thread(name = "unlock-log-prune") {
            try {
                app.appLockRepository().unlockLog.prune()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to prune unlock history", e)
            }
        }
    }
}
