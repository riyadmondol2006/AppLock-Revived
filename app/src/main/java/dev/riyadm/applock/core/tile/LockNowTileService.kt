package dev.riyadm.applock.core.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import dev.riyadm.applock.MainActivity
import dev.riyadm.applock.R
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.services.AppLockManager

/**
 * "Lock all now" Quick Settings tile. Forgets every temporary unlock so each locked app asks for
 * authentication again. It can only ever make things stricter: it never turns protection off.
 * When protection is off, tapping it opens the app (which asks for the credential).
 */
class LockNowTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val repository = applicationContext.appLockRepository()
        if (!repository.isProtectEnabled()) {
            if (isLocked) {
                unlockAndRun { openApp() }
            } else {
                openApp()
            }
            return
        }

        AppLockManager.lockAllNow()
        try {
            Toast.makeText(this, R.string.tile_lock_now_done, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val protectEnabled = applicationContext.appLockRepository().isProtectEnabled()
        tile.state = if (protectEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_lock_now_label)
        tile.icon = Icon.createWithResource(this, R.drawable.baseline_shield_24)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                if (protectEnabled) R.string.tile_lock_now_subtitle_active
                else R.string.tile_lock_now_subtitle_inactive
            )
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app from tile", e)
        }
    }

    private companion object {
        const val TAG = "LockNowTileService"
    }
}
