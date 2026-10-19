package dev.riyadm.applock.core.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import dev.riyadm.applock.MainActivity
import dev.riyadm.applock.R
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.features.profiles.ui.displayName

/**
 * "Lock profile" Quick Settings tile. Shows the active profile; tapping it opens the profile
 * switcher inside the app, which always asks for the credential first. The tile itself never
 * changes the profile.
 */
class ProfileTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { openProfiles() } else openProfiles()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val repository = applicationContext.appLockRepository()
        val profileName = repository.getActiveProfile().displayName(this)
        tile.state = if (repository.isProtectEnabled()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_profile)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = getString(R.string.tile_profile_label)
            tile.subtitle = profileName
        } else {
            tile.label = profileName
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openProfiles() {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_PROFILES, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    REQUEST_OPEN_PROFILES,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open profiles from tile", e)
        }
    }

    companion object {
        private const val TAG = "ProfileTileService"
        private const val REQUEST_OPEN_PROFILES = 1

        /** Asks the system to refresh the tile, e.g. after the active profile changed. */
        fun requestUpdate(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, ProfileTileService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't refresh the profile tile", e)
            }
        }
    }
}
