package dev.riyadm.applock.core.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.riyadm.applock.core.utils.LogUtils
import dev.riyadm.applock.core.utils.appLockRepository

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                LogUtils.clearAllLogs()
                startServices(context)
            }

            Intent.ACTION_BOOT_COMPLETED -> startServices(context)

            else -> Log.w(TAG, "Unexpected intent action: ${intent.action}")
        }
    }

    private fun startServices(context: Context) {
        try {
            AppLockServiceStarter.startAppropriateServices(context, context.appLockRepository())
        } catch (e: Exception) {
            Log.e(TAG, "Error starting services", e)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
