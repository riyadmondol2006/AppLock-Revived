package dev.riyadm.applock.core.trusted

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.riyadm.applock.core.utils.LogUtils
import dev.riyadm.applock.core.utils.appLockRepository

/**
 * Switches the active lock profile when the connection changes, if the user turned on network
 * profiles. [TrustedNetworkMonitor] reports every status change; the switch itself waits
 * [DEBOUNCE_MS] and looks at the latest status then, so a Wi-Fi drop and reconnect or a roam
 * between access points doesn't flap profiles.
 *
 * A profile is applied once per connection: the last handled connection is remembered, so a
 * profile the user picks by hand stays until the network changes (or the rules are edited).
 * An unreadable network name never changes anything.
 */
internal object NetworkProfileSwitcher {
    private const val TAG = "NetworkProfileSwitcher"
    const val DEBOUNCE_MS = 4_000L

    private val handler = Handler(Looper.getMainLooper())

    private var latest: TrustedNetworkMonitor.Status? = null
    private var appContext: Context? = null
    private var pending = false

    private val evaluate = Runnable {
        pending = false
        val context = appContext ?: return@Runnable
        val status = latest ?: return@Runnable
        try {
            apply(context, situationOf(status))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply a network profile", e)
        }
    }

    /** Main thread, like every monitor callback. Called with every status the monitor publishes while it is listening. */
    fun onStatus(
        context: Context,
        status: TrustedNetworkMonitor.Status,
        listening: Boolean,
        settings: NetworkProfileSettings,
    ) {
        // Not listening means the status says nothing about the connection.
        if (!listening || !settings.isEnabled()) {
            handler.removeCallbacks(evaluate)
            pending = false
            latest = null
            return
        }
        appContext = context.applicationContext
        latest = status
        if (!pending) {
            pending = true
            handler.postDelayed(evaluate, DEBOUNCE_MS)
        }
    }

    internal fun situationOf(status: TrustedNetworkMonitor.Status): NetworkSituation = when {
        !status.onWifi -> NetworkSituation.Offline
        status.wifiUnreadable || status.problem != null || status.connections.isEmpty() ->
            NetworkSituation.Unreadable

        else -> NetworkSituation.Wifi(status.connections)
    }

    private fun apply(context: Context, situation: NetworkSituation) {
        val settings = NetworkProfileSettings(context)
        // Re-check: the user may have turned the feature off while the debounce ran.
        if (!settings.isEnabled()) return
        val repository = context.appLockRepository()
        val state = repository.profiles.state.value
        val decision = NetworkProfiles.decide(
            situation = situation,
            rules = settings.getRules(),
            defaultProfileId = settings.getDefaultProfileId(),
            existingProfileIds = state.profiles.mapTo(HashSet()) { it.id },
            sdkInt = Build.VERSION.SDK_INT
        )
        when (NetworkProfiles.plan(decision, settings.getLastKey(), state.activeId)) {
            NetworkProfileAction.NONE -> Unit
            NetworkProfileAction.REMEMBER -> settings.setLastKey(decision.situationKey)
            NetworkProfileAction.SWITCH -> {
                val target = decision.targetProfileId ?: return
                if (repository.switchProfile(target)) {
                    LogUtils.d(TAG, "Switched profile for the current network")
                    settings.setLastKey(decision.situationKey)
                }
            }
        }
    }
}
