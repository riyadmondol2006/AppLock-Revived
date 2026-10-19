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
    private const val CONFIRM_TIMEOUT_MS = 5_000L
    private const val UNKNOWN_SSID = "<unknown ssid>"

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

        /**
         * False for open, enhanced-open (OWE) and WEP networks, which anyone can impersonate,
         * and whenever the security type couldn't be confirmed.
         */
        val canBeTrusted: Boolean get() = WifiTrust.canBeTrusted(securityType)

        val securityKnown: Boolean get() = securityType != TrustedNetwork.SECURITY_UNKNOWN
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

    /** Networks kept across [refresh] that the new callback hasn't reported yet. */
    private val unconfirmedNetworks = HashSet<Network>()
    private val dropUnconfirmed = Runnable {
        if (unconfirmedNetworks.isEmpty()) return@Runnable
        wifiNetworks.keys.removeAll(unconfirmedNetworks)
        unconfirmedNetworks.clear()
        update()
    }

    /** Read from any thread by the lock backends. */
    @Volatile
    private var trusted = false

    @Volatile
    private var lastVerifiedAt = 0L

    /** Main thread only: the last value [update] published, used to detect loss of trust. */
    private var reportedTrusted = false

    /**
     * True once the keyguard was seen unlocked since the screen last turned off, so [isTrusted]
     * doesn't ask the window manager (a binder call) on every lock decision. Cleared on screen
     * off and with every periodic re-verification, so it is never trusted for long.
     */
    @Volatile
    private var keyguardSeenUnlocked = false

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
     * registered again. Known networks are kept so trust doesn't flicker meanwhile, but a lost
     * event queued for the old callback is never delivered, so networks that are already gone
     * are dropped now and any the new callback doesn't confirm shortly are dropped too.
     */
    fun refresh() = runOnMain {
        val context = appContext ?: return@runOnMain
        if (callback != null) {
            unregisterCallback(context, clearNetworks = false)
            registerCallback(context)
            dropVanishedNetworks(context)
            unconfirmedNetworks.clear()
            unconfirmedNetworks.addAll(wifiNetworks.keys)
            mainHandler.removeCallbacks(dropUnconfirmed)
            if (unconfirmedNetworks.isNotEmpty()) mainHandler.postDelayed(dropUnconfirmed, CONFIRM_TIMEOUT_MS)
        }
        update()
    }

    /** Keeps only networks the system still reports as Wi-Fi; on any error, forgets them all. */
    @Suppress("DEPRECATION")
    private fun dropVanishedNetworks(context: Context) {
        if (wifiNetworks.isEmpty()) return
        val live = try {
            val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
            connectivityManager?.allNetworks
                ?.filter {
                    connectivityManager.getNetworkCapabilities(it)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                }
                ?.toSet()
                .orEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list networks", e)
            emptySet()
        }
        wifiNetworks.keys.retainAll(live)
    }

    /**
     * True only when locked apps may open without authentication right now. Cheap enough to be
     * called on every lock decision: network changes, location mode changes and screen off are
     * pushed by callbacks, and permissions, location and the keyguard are re-verified here at
     * most every [REVERIFY_MS] (only while trusted, only when a lock decision asks; no timer).
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
            val now = SystemClock.elapsedRealtime()
            if (now - lastVerifiedAt >= REVERIFY_MS) {
                keyguardSeenUnlocked = false
                if (locationProblem(context) != null) {
                    invalidate()
                    return false
                }
                lastVerifiedAt = now
            }
            if (!keyguardSeenUnlocked) {
                if (context.isDeviceLocked()) return false
                keyguardSeenUnlocked = true
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
            // Apps are not closed: losing Wi-Fi for a moment must not force-stop the app in use.
            AppLockManager.lockAllNow(closeApps = false)
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
            val matches = readable.map { wifi ->
                saved.firstOrNull { WifiTrust.matches(it, wifi, Build.VERSION.SDK_INT) }
            }
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
            wifiNetworks.values.map { it?.toCurrentWifi(null) }
        } else {
            // WifiInfo is only attached to NetworkCapabilities from Android 12.
            val wifiManager = context.getSystemService(WifiManager::class.java)
            listOf(wifiManager?.connectionInfo?.toCurrentWifi(wifiManager))
        }
    }

    @SuppressLint("HardwareIds")
    private fun WifiInfo.toCurrentWifi(wifiManager: WifiManager?): CurrentWifi? {
        val rawSsid = ssid
        if (rawSsid.isNullOrEmpty() || rawSsid == UNKNOWN_SSID || rawSsid == "\"\"") return null
        val rawBssid = WifiTrust.normalizeBssid(bssid)
        val security = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            currentSecurityType
        } else {
            scannedSecurityType(wifiManager, rawBssid)
        }
        return CurrentWifi(rawSsid, rawBssid, security)
    }

    /**
     * Android 8-11 can't report the connected network's security type, so it is read from the
     * scan result of the connected access point. Anything missing means "unknown", which is
     * never trusted.
     */
    @SuppressLint("MissingPermission")
    private fun scannedSecurityType(wifiManager: WifiManager?, bssid: String?): Int {
        if (wifiManager == null || bssid == null) return TrustedNetwork.SECURITY_UNKNOWN
        return try {
            val result = wifiManager.scanResults?.firstOrNull { it.BSSID.equals(bssid, ignoreCase = true) }
            WifiTrust.securityTypeFromCapabilities(result?.capabilities)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read scan results", e)
            TrustedNetwork.SECURITY_UNKNOWN
        }
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
            keyguardSeenUnlocked = false
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                        keyguardSeenUnlocked = false
                        return
                    }
                    runOnMain { update() }
                }
            }
            val filter = IntentFilter().apply {
                addAction(LocationManager.MODE_CHANGED_ACTION)
                addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
                addAction(Intent.ACTION_SCREEN_OFF)
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
        unconfirmedNetworks.clear()
        mainHandler.removeCallbacks(dropUnconfirmed)
        locationReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        locationReceiver = null
        keyguardSeenUnlocked = false
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
        if (clearNetworks) {
            wifiNetworks.clear()
            unconfirmedNetworks.clear()
            mainHandler.removeCallbacks(dropUnconfirmed)
        }
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
        unconfirmedNetworks.remove(network)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val info = capabilities.transportInfo as? WifiInfo
            val known = wifiNetworks.containsKey(network)
            val previous = wifiNetworks.put(network, info)
            // Capabilities also change with signal strength, link speed and so on. When the
            // network's name, router and security are the same, the outcome can't change.
            if (known && previous?.toCurrentWifi(null) == info?.toCurrentWifi(null)) return
        } else {
            // Before Android 12 the connection is read from WifiManager in update(), so a roam
            // to another router isn't visible here: always re-evaluate.
            wifiNetworks[network] = null
        }
        update()
    }

    private fun onNetworkLost(source: ConnectivityManager.NetworkCallback, network: Network) {
        if (source !== callback) return
        unconfirmedNetworks.remove(network)
        wifiNetworks.remove(network)
        update()
    }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }
}

/** Pure trust rules, kept apart from the monitor so they can be tested. */
internal object WifiTrust {
    private val REDACTED_BSSIDS = setOf("02:00:00:00:00:00", "00:00:00:00:00:00")

    fun normalizeBssid(raw: String?): String? =
        raw?.trim()?.lowercase()?.takeUnless { it.isEmpty() || it in REDACTED_BSSIDS }

    fun canBeTrusted(securityType: Int): Boolean = securityClass(securityType) != null

    /**
     * Before Android 12 the security type can only be read from scan results, so a network is
     * only trusted there when it is pinned to its router's address.
     */
    fun routerRequired(sdkInt: Int): Boolean = sdkInt < Build.VERSION_CODES.S

    /** Whether [wifi] may be saved as trusted, with or without pinning its router. */
    fun canSave(wifi: TrustedNetworkMonitor.CurrentWifi, matchRouter: Boolean, sdkInt: Int): Boolean {
        if (!wifi.canBeTrusted) return false
        if ((matchRouter || routerRequired(sdkInt)) && normalizeBssid(wifi.bssid) == null) return false
        return true
    }

    /**
     * A saved network matches only with the same name, a security type that needs a secret (of
     * the same kind as when it was saved) and, when it was saved with one or always before
     * Android 12, the same readable router address.
     */
    fun matches(saved: TrustedNetwork, wifi: TrustedNetworkMonitor.CurrentWifi, sdkInt: Int): Boolean {
        if (saved.ssid != wifi.ssid) return false
        val currentClass = securityClass(wifi.securityType) ?: return false
        if (saved.securityType != TrustedNetwork.SECURITY_UNKNOWN &&
            securityClass(saved.securityType) != currentClass
        ) {
            return false
        }
        val savedBssid = normalizeBssid(saved.bssid) ?: return !routerRequired(sdkInt)
        val currentBssid = normalizeBssid(wifi.bssid) ?: return false
        return savedBssid == currentBssid
    }

    /**
     * Maps a ScanResult capabilities string (e.g. "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]") to a
     * WifiInfo.SECURITY_TYPE_* value. Open, OWE and WEP-only networks map to types that are
     * never trusted; anything unrecognised is unknown.
     */
    @SuppressLint("InlinedApi")
    fun securityTypeFromCapabilities(capabilities: String?): Int {
        if (capabilities.isNullOrBlank()) return TrustedNetwork.SECURITY_UNKNOWN
        val caps = capabilities.uppercase()
        val keyed = caps.contains("WPA") || caps.contains("RSN")
        return when {
            caps.contains("WAPI-CERT") -> WifiInfo.SECURITY_TYPE_WAPI_CERT
            caps.contains("WAPI-PSK") -> WifiInfo.SECURITY_TYPE_WAPI_PSK
            keyed && (caps.contains("EAP") || caps.contains("802.1X")) -> WifiInfo.SECURITY_TYPE_EAP
            keyed && caps.contains("SAE") -> WifiInfo.SECURITY_TYPE_SAE
            keyed && caps.contains("PSK") -> WifiInfo.SECURITY_TYPE_PSK
            caps.contains("OWE") -> WifiInfo.SECURITY_TYPE_OWE
            caps.contains("WEP") -> WifiInfo.SECURITY_TYPE_WEP
            keyed || caps.contains("WAPI") -> TrustedNetwork.SECURITY_UNKNOWN
            else -> WifiInfo.SECURITY_TYPE_OPEN
        }
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
