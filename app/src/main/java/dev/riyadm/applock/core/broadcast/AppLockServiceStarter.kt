package dev.riyadm.applock.core.broadcast

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.BackendImplementation
import dev.riyadm.applock.services.ShizukuAppLockService
import dev.riyadm.applock.services.UsageLockService
import dev.riyadm.applock.services.isServiceRunning

object AppLockServiceStarter {
    private const val TAG = "AppLockServiceStarter"

    /**
     * Starts the foreground service for the selected polling backend.
     *
     * The accessibility service is never started here: the system binds it whenever it is
     * enabled, and startService() on it only creates a disconnected instance (or throws when
     * called from the background).
     */
    fun startAppropriateServices(context: Context, repository: AppLockRepository) {
        when (repository.getBackendImplementation()) {
            BackendImplementation.SHIZUKU -> startForegroundService(context, ShizukuAppLockService::class.java)
            BackendImplementation.USAGE_STATS -> startForegroundService(context, UsageLockService::class.java)
            BackendImplementation.ACCESSIBILITY -> Unit
        }
    }

    private fun startForegroundService(context: Context, serviceClass: Class<*>) {
        try {
            if (context.isServiceRunning(serviceClass)) {
                Log.d(TAG, "Service already running: ${serviceClass.simpleName}")
                return
            }
            ContextCompat.startForegroundService(context, Intent(context, serviceClass))
            Log.d(TAG, "Started service: ${serviceClass.simpleName}")
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException when not called from an exempt context.
            Log.e(TAG, "Failed to start service: ${serviceClass.simpleName}", e)
        }
    }
}
