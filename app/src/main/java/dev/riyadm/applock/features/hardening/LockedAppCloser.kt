package dev.riyadm.applock.features.hardening

import android.app.ActivityManager
import android.app.IActivityManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.data.repository.HardeningPreferences
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.util.concurrent.Executors

/**
 * Closes locked apps when the screen turns off or "Lock all now" is used (opt-in).
 *
 * With Shizuku the apps are force-stopped. Without it only
 * [ActivityManager.killBackgroundProcesses] is available, which only ends processes that are
 * already in the background and, from Android 14, only works on our own processes.
 * System apps, the default launcher, the current keyboard and this app are never touched.
 */
object LockedAppCloser {
    private const val TAG = "LockedAppCloser"

    @Volatile
    private var appContext: Context? = null
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "locked-app-closer") }

    enum class Method { FORCE_STOP, BACKGROUND_ONLY, UNAVAILABLE }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) closeLockedApps(context)
        }
    }

    fun register(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        try {
            ContextCompat.registerReceiver(
                app,
                screenOffReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unable to register screen-off receiver", e)
        }
    }

    /** Called when the user locks everything at once. */
    fun onLockAllNow() {
        appContext?.let(::closeLockedApps)
    }

    /** How locked apps would be closed right now. Does binder calls; don't call on the main thread. */
    fun currentMethod(): Method = when {
        isShizukuReady() -> Method.FORCE_STOP
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> Method.BACKGROUND_ONLY
        else -> Method.UNAVAILABLE
    }

    private fun closeLockedApps(context: Context) {
        val app = context.applicationContext
        if (!HardeningPreferences(app).closeLockedAppsOnScreenOff) return
        executor.execute {
            try {
                val repository = app.appLockRepository()
                if (!repository.isProtectEnabled()) return@execute
                val targets = repository.getLockedApps() - protectedPackages(app)
                if (targets.isEmpty()) return@execute
                when (currentMethod()) {
                    Method.FORCE_STOP -> targets.forEach { forceStop(app, it) }
                    Method.BACKGROUND_ONLY -> targets.forEach { killBackground(app, it) }
                    Method.UNAVAILABLE -> Log.d(TAG, "Closing apps needs Shizuku on this Android version")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to close locked apps", e)
            }
        }
    }

    private fun protectedPackages(context: Context): Set<String> {
        val pm = context.packageManager
        val result = mutableSetOf(context.packageName, "android")
        try {
            pm.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName?.let(result::add)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to resolve launcher", e)
        }
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let(ComponentName::unflattenFromString)
            ?.packageName
            ?.let(result::add)
        context.appLockRepository().getLockedApps().forEach { pkg ->
            if (isSystemApp(pm, pkg)) result += pkg
        }
        return result
    }

    private fun isSystemApp(pm: PackageManager, packageName: String): Boolean = try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            pm.getApplicationInfo(packageName, 0)
        }
        info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    } catch (_: PackageManager.NameNotFoundException) {
        // Not installed (any more): nothing to close.
        true
    }

    private fun isShizukuReady(): Boolean = try {
        Shizuku.pingBinder() && !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    private fun forceStop(context: Context, packageName: String) {
        try {
            SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)
                .let(::ShizukuBinderWrapper)
                .let(IActivityManager.Stub::asInterface)
                .forceStopPackage(packageName, Process.myUid() / PER_USER_RANGE)
        } catch (e: Exception) {
            Log.e(TAG, "Force stop failed, falling back", e)
            killBackground(context, packageName)
        }
    }

    private fun killBackground(context: Context, packageName: String) {
        try {
            context.getSystemService(ActivityManager::class.java)?.killBackgroundProcesses(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "killBackgroundProcesses failed", e)
        }
    }

    private const val PER_USER_RANGE = 100_000
}
