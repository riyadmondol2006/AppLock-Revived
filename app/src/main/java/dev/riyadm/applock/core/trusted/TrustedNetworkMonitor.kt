package dev.riyadm.applock.core.trusted

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dev.riyadm.applock.core.utils.LogUtils
import dev.riyadm.applock.services.AppLockManager
import dev.riyadm.applock.services.isDeviceLocked
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Tracks the connected Wi-Fi network and decides whether locked apps may open without
 * authentication because the device is on a network the user trusts.
 *
 * Everything fails secure: no Wi-Fi, a redacted SSID/BSSID, a missing location permission,
 * location turned off, an open network, a keyguard that is showing or any error means
 * "not trusted". Losing trust clears every temporary unlock so apps re-lock at once.
 *
 * The lock services register while they run; screens that show the current network
 * [watch] it while visible. All state changes happen on the main thread.
 */
object TrustedNetworkMonitor {
    private const val TAG = "TrustedNetworkMonitor"
    private const val REVERIFY_MS = 10_000L
    private const val UNKNOWN_SSID = "<unknown ssid>"
    private val REDACTED_BSSIDS = setOf("02:00:00:00:00:00", "00:00:00:00:00:00")

    enum class Problem {
        LOCATION_PERMISSION,
        PRECISE_LOCATION,
        BACKGROUND_LOCATION,
        LOCATION_OFF,
        SSID_UNAVAILABLE
    }

    @Immutable
    data class CurrentWifi(val ssid: String, val bssid: String?, val securityType: Int) {
        val displayName: String get() = displaySsid(ssid)

        /** False for open, enhanced-open (OWE) and WEP networks, which anyone can impersonate. */
        val canBeTrusted: Boolean
            get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || securityClass(securityType) != null
    }

    @Immutable
    data class Status(
        val enabled: Boolean = false,
        val onWifi: Boolean = false,
        /** The connected network, when its name can be read. */
        val wifi: CurrentWifi? = null,
        val problem: Problem? = null,
        /** The saved network the connection matches, if any. */
        val matchedNetwork: TrustedNetwork? = null,
        /** True while locking is actually relaxed (feature on, lock service running, match). */
        val relaxed: Boolean = false,
    ) {
        val relaxedSsid: String? get() = if (relaxed) matchedNetwork?.displayName else null
    }

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceOwners: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())
    private val viewers: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var settings: TrustedNetworkSettings? = null

    private var callback: ConnectivityManager.NetworkCallback? = null
    private var locationReceiver: BroadcastReceiver? = null
    private val wifiNetworks = LinkedHashMap<Network, WifiInfo?>()

    /** Read from any thread by the lock backends. */
    @Volatile
    private var trusted = false

    @Volatile
    private var lastVerifiedAt = 0L

    /** Main thread only: the last value [update] published, used to detect loss of trust. */
    private var reportedTrusted = false

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == TrustedNetworkSettings.KEY_ENABLED || key == TrustedNetworkSettings.KEY_NETWORKS || key == null) {
            runOnMain { update() }
        }
    }

    /** Called by a running lock service. */
    fun register(context: Context, owner: Any) = runOnMain {
        init(context)
        serviceOwners.add(owner)
        update()
    }

    fun unregister(owner: Any) = runOnMain {
        if (serviceOwners.remove(owner)) update()
    }

    /** Called by UI that shows the current network, for as long as it is visible. */
    fun watch(context: Context, viewer: Any) = runOnMain {
        init(context)
        viewers.add(viewer)
        update()
    }

    fun unwatch(viewer: Any) = runOnMain {
        if (viewers.remove(viewer)) update()
    }

    /**
     * Re-reads permissions and the connection, e.g. after the user granted location access.
     * On Android 12+ location info only arrives with fresh callbacks, so the callback is
     * registered again; known networks are kept so trust doesn't flicker meanwhile.
     */
    fun refresh() = runOnMain {
        val context = appContext ?: return@runOnMain
        if (callback != null) {
            unregisterCallback(context, clearNetworks = false)
            registerCallback(context)
        }
        update()
    }

    /**
     * True only when locked apps may open without authentication right now. Cheap enough to be
     * called on every lock decision; permissions and location are re-verified periodically.
     */
    fun isTrusted(): Boolean {
        if (!trusted) return false
        return try {
            val context = appContext ?: return false
            val currentSettings = settings ?: return false
            if (!currentSettings.isEnabled()) {
                invalidate()
                return false
            }
            if (context.isDeviceLocked()) return false
            val now = SystemClock.elapsedRealtime()
            if (now - lastVerifiedAt >= REVERIFY_MS) {
                if (locationProblem(context) != null) {
                    invalidate()
                    return false
                }
                lastVerifiedAt = now
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Trust check failed", e)
            false
        }
    }

    private fun invalidate() {
        trusted = false
        runOnMain { update() }
    }

    private fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        settings = TrustedNetworkSettings(app).also { it.registerListener(prefsListener) }
    }

    private fun update() {
        val context = appContext ?: return
        val currentSettings = settings ?: return
        val newStatus = try {
            val enabled = currentSettings.isEnabled()
            val active = viewers.isNotEmpty() || (enabled && serviceOwners.isNotEmpty())
            if (active) startListening(context) else stopListening(context)
            if (active) computeStatus(context, enabled, currentSettings) else Status(enabled = enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to evaluate trusted network", e)
            Status(enabled = false)
        }

        val nowTrusted = newStatus.relaxed
        val wasTrusted = reportedTrusted
        reportedTrusted = nowTrusted
        lastVerifiedAt = SystemClock.elapsedRealtime()
        trusted = nowTrusted
        _status.value = newStatus

        if (wasTrusted != nowTrusted) {
            LogUtils.d(TAG, if (nowTrusted) "On a trusted network" else "Left trusted network")
        }
        if (wasTrusted && !nowTrusted) {
            // Re-lock everything right away: apps opened while trusted were never unlocked.
            AppLockManager.lockAllNow()
        }
    }

    private fun computeStatus(context: Context, enabled: Boolean, settings: TrustedNetworkSettings): Status {
        val permissionProblem = locationProblem(context)
        val onWifi = wifiNetworks.isNotEmpty()
        val connections = currentConnections(context)
        val readable = connections.mapNotNull { it }
        val unreadable = onWifi && (connections.isEmpty() || readable.size != connections.size)

        val problem = permissionProblem ?: if (unreadable) Problem.SSID_UNAVAILABLE else null
        val saved = settings.getNetworks()
        val matched = if (onWifi && !unreadable && readable.isNotEmpty()) {
            val matches = readable.map { wifi -> saved.firstOrNull { it.matches(wifi) } }
            if (matches.all { it != null }) matches.first() else null
        } else null

        val relaxed = enabled && serviceOwners.isNotEmpty() && problem == null && matched != null
        return Status(
            enabled = enabled,
            onWifi = onWifi,
            wifi = readable.firstOrNull(),
            problem = problem,
            matchedNetwork = if (problem == null || problem == Problem.BACKGROUND_LOCATION) matched else null,
            relaxed = relaxed
        )
    }

    /** One entry per connected Wi-Fi network; null where the name couldn't be read. */
    @Suppress("DEPRECATION")
    private fun currentConnections(context: Context): List<CurrentWifi?> {
        if (wifiNetworks.isEmpty()) return emptyList()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            wifiNetworks.values.map { it?.toCurrentWifi() }
        } else {
            // WifiInfo is only attached to NetworkCapabilities from Android 12.
            val wifiManager = context.getSystemService(WifiManager::class.java)
            listOf(wifiManager?.connectionInfo?.toCurrentWifi())
        }
    }

    @SuppressLint("HardwareIds")
    private fun WifiInfo.toCurrentWifi(): CurrentWifi? {
        val rawSsid = ssid
        if (rawSsid.isNullOrEmpty() || rawSsid == UNKNOWN_SSID || rawSsid == "\"\"") return null
        val rawBssid = bssid?.lowercase()?.takeUnless { it in REDACTED_BSSIDS }
        val security = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            currentSecurityType
        } else {
            TrustedNetwork.SECURITY_UNKNOWN
        }
        return CurrentWifi(rawSsid, rawBssid, security)
    }

    private fun TrustedNetwork.matches(wifi: CurrentWifi): Boolean {
        if (ssid != wifi.ssid) return false
        if (bssid != null && !bssid.equals(wifi.bssid, ignoreCase = true)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val currentClass = securityClass(wifi.securityType) ?: return false
            if (securityType != TrustedNetwork.SECURITY_UNKNOWN && securityClass(securityType) != currentClass) {
                return false
            }
        }
        return true
    }

    /** What the app still needs before it can read the network name from a background service. */
    fun locationProblem(context: Context): Problem? {
        val fine = context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = context.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!fine && !coarse) return Problem.LOCATION_PERMISSION
        // Android 10+ only returns Wi-Fi details with precise location.
        if (!fine && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return Problem.PRECISE_LOCATION
        // Android 10+: without "Allow all the time" the name is redacted while another app is in front.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !context.isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            return Problem.BACKGROUND_LOCATION
        }
        val locationManager = context.getSystemService(LocationManager::class.java)
        if (locationManager == null || !LocationManagerCompat.isLocationEnabled(locationManager)) {
            return Problem.LOCATION_OFF
        }
        return null
    }

    private fun Context.isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun startListening(context: Context) {
        if (callback == null) registerCallback(context)
        if (locationReceiver == null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    runOnMain { update() }
                }
            }
            val filter = IntentFilter().apply {
                addAction(LocationManager.MODE_CHANGED_ACTION)
                addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            }
            try {
                ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                locationReceiver = receiver
            } catch (e: Exception) {
                Log.e(TAG, "Failed to watch location mode", e)
            }
        }
    }

    private fun stopListening(context: Context) {
        if (callback != null) unregisterCallback(context, clearNetworks = true)
        wifiNetworks.clear()
        locationReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        locationReceiver = null
    }

    private fun registerCallback(context: Context) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val newCallback = createCallback()
        try {
            connectivityManager.registerNetworkCallback(request, newCallback, mainHandler)
            callback = newCallback
        } catch (e: Exception) {
            // Without network callbacks we never know the network, so nothing is trusted.
            Log.e(TAG, "Failed to register network callback", e)
            callback = null
        }
    }

    private fun unregisterCallback(context: Context, clearNetworks: Boolean) {
        val current = callback ?: return
        callback = null
        try {
            context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(current)
        } catch (_: Exception) {
        }
        if (clearNetworks) wifiNetworks.clear()
    }

    private fun createCallback(): ConnectivityManager.NetworkCallback =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Without FLAG_INCLUDE_LOCATION_INFO Android 12+ always redacts the SSID and BSSID.
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                    onNetworkChanged(this, network, capabilities)

                override fun onLost(network: Network) = onNetworkLost(this, network)
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                    onNetworkChanged(this, network, capabilities)

                override fun onLost(network: Network) = onNetworkLost(this, network)
            }
        }

    private fun onNetworkChanged(
        source: ConnectivityManager.NetworkCallback,
        network: Network,
        capabilities: NetworkCapabilities
    ) {
        if (source !== callback) return
        wifiNetworks[network] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            capabilities.transportInfo as? WifiInfo
        } else null
        update()
    }

    private fun onNetworkLost(source: ConnectivityManager.NetworkCallback, network: Network) {
        if (source !== callback) return
        wifiNetworks.remove(network)
        update()
    }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }
}

/**
 * Groups Wi-Fi security types that need the same secret, so a WPA2/WPA3 transition network
 * still matches. Returns null for types that can't be trusted: open, enhanced open (OWE, no
 * authentication), WEP (key is recoverable) and unknown.
 */
@SuppressLint("InlinedApi")
internal fun securityClass(type: Int): Int? = when (type) {
    WifiInfo.SECURITY_TYPE_PSK, WifiInfo.SECURITY_TYPE_SAE, WifiInfo.SECURITY_TYPE_WAPI_PSK -> 1
    WifiInfo.SECURITY_TYPE_EAP,
    WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE,
    WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT,
    WifiInfo.SECURITY_TYPE_WAPI_CERT,
    WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2,
    WifiInfo.SECURITY_TYPE_PASSPOINT_R3 -> 2

    WifiInfo.SECURITY_TYPE_DPP -> 3
    else -> null
}
