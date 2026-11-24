package dev.riyadm.applock.core.trusted

import androidx.compose.runtime.Immutable
import dev.riyadm.applock.core.trusted.TrustedNetworkMonitor.CurrentWifi

/** A Wi-Fi network that activates [profileId] while the phone is connected to it. */
@Immutable
data class NetworkProfileRule(val network: TrustedNetwork, val profileId: String)

/** What the monitor can currently tell about the connection. */
sealed interface NetworkSituation {
    /** On Wi-Fi, but the network's name can't be read (permission, location off, redaction). */
    data object Unreadable : NetworkSituation

    /** Not on Wi-Fi: mobile data only, or no connection at all. */
    data object Offline : NetworkSituation

    /** Connected to these readable Wi-Fi networks. */
    data class Wifi(val connections: List<CurrentWifi>) : NetworkSituation
}

/**
 * [situationKey] names the connection that was evaluated (null when it couldn't be read, so
 * nothing is remembered). [targetProfileId] is the profile that should be active, or null to
 * leave the active profile alone.
 */
data class NetworkProfileDecision(val situationKey: String?, val targetProfileId: String?)

enum class NetworkProfileAction { NONE, REMEMBER, SWITCH }

/**
 * Pure rules that pick a lock profile for a network situation, kept apart from the monitor so
 * they can be tested.
 *
 * Networks are matched with [WifiTrust.matches], so open, enhanced-open and WEP networks (which
 * anyone can impersonate) never activate a profile, and a rule saved with a router address only
 * applies to that router. Anything uncertain leaves the active profile alone.
 */
internal object NetworkProfiles {

    fun decide(
        situation: NetworkSituation,
        rules: List<NetworkProfileRule>,
        defaultProfileId: String?,
        existingProfileIds: Set<String>,
        sdkInt: Int,
    ): NetworkProfileDecision {
        // A deleted profile is treated as if the rule or default didn't exist.
        val liveRules = rules.filter { it.profileId in existingProfileIds }
        val default = defaultProfileId?.takeIf { it in existingProfileIds }
        return when (situation) {
            NetworkSituation.Unreadable -> NetworkProfileDecision(null, null)
            NetworkSituation.Offline -> NetworkProfileDecision(KEY_OFFLINE, default)
            is NetworkSituation.Wifi -> {
                if (situation.connections.isEmpty()) return NetworkProfileDecision(null, null)
                val key = situation.connections
                    .map { "${it.ssid}|${WifiTrust.normalizeBssid(it.bssid).orEmpty()}" }
                    .sorted()
                    .joinToString(separator = "\n", prefix = KEY_WIFI)
                val mapped = situation.connections.map { wifi ->
                    liveRules.firstOrNull { WifiTrust.matches(it.network, wifi, sdkInt) }?.profileId
                }.toSet()
                val target = when {
                    // Two connections that disagree: don't guess.
                    mapped.filterNotNull().size > 1 -> return NetworkProfileDecision(key, null)
                    mapped.filterNotNull().size == 1 && null !in mapped -> mapped.first()
                    else -> default
                }
                NetworkProfileDecision(key, target)
            }
        }
    }

    /**
     * Acts only when the connection differs from the one last handled, so a profile the user
     * picked by hand stays until the network changes. Already being on the target profile just
     * remembers the connection.
     */
    fun plan(decision: NetworkProfileDecision, lastKey: String?, activeProfileId: String): NetworkProfileAction {
        val key = decision.situationKey ?: return NetworkProfileAction.NONE
        if (key == lastKey) return NetworkProfileAction.NONE
        val target = decision.targetProfileId ?: return NetworkProfileAction.REMEMBER
        return if (target == activeProfileId) NetworkProfileAction.REMEMBER else NetworkProfileAction.SWITCH
    }

    private const val KEY_OFFLINE = "offline"
    private const val KEY_WIFI = "wifi:"
}
