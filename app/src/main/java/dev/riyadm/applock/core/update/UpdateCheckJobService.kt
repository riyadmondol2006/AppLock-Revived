package dev.riyadm.applock.core.update

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import dev.riyadm.applock.AppLockApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Checks GitHub for a new release in the background, about twice a day, and posts a
 * notification when one is found. Uses the platform JobScheduler, so the check only runs
 * with a network connection and survives reboots.
 */
class UpdateCheckJobService : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val repository = (applicationContext as? AppLockApplication)?.appLockRepository
        if (repository == null || !repository.isUpdateCheckEnabled()) {
            cancel(this)
            return false
        }
        work = scope.launch {
            try {
                val info = UpdateChecker.check(applicationContext)
                if (info != null && repository.getLastNotifiedUpdateVersion() != info.latestVersion) {
                    if (UpdateNotifier.notify(applicationContext, info)) {
                        repository.setLastNotifiedUpdateVersion(info.latestVersion)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background update check failed", e)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "UpdateCheckJob"
        private const val JOB_ID = 0x5550D
        private const val INTERVAL_MS = 12 * 60 * 60 * 1000L
        private const val FLEX_MS = 2 * 60 * 60 * 1000L

        /** Schedules the periodic check if it isn't already scheduled. */
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            // Jobs scheduled before the battery constraint existed are replaced once.
            if (scheduler.getPendingJob(JOB_ID)?.isRequireBatteryNotLow == true) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateCheckJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresBatteryNotLow(true)
                .setPeriodic(INTERVAL_MS, FLEX_MS)
                .setPersisted(true)
                .build()
            try {
                scheduler.schedule(job)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't schedule update checks", e)
            }
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        }

        fun sync(context: Context, enabled: Boolean) {
            if (enabled) schedule(context) else cancel(context)
        }
    }
}
