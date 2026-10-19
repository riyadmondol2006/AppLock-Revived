package dev.riyadm.applock.core.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.riyadm.applock.core.utils.LogUtils
import dev.riyadm.applock.services.AppLockManager

class DeviceUnlockReceiver(
    private val onDeviceUnlocked: () -> Unit,
    private val onScreenOff: () -> Unit = {}
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_USER_PRESENT -> {
                LogUtils.d("DeviceUnlockReceiver", "Device unlocked (ACTION_USER_PRESENT)")
                onDeviceUnlocked()
            }

            Intent.ACTION_SCREEN_OFF -> {
                AppLockManager.onScreenOff()
                onScreenOff()
                LogUtils.d("DeviceUnlockReceiver", "Screen turned OFF (locked)")
            }
        }
    }
}
