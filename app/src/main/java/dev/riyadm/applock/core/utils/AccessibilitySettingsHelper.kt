package dev.riyadm.applock.core.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

fun Context.isAccessibilityServiceEnabled(): Boolean {
    val accessibilityServiceName =
        "$packageName/$packageName.services.AppLockAccessibilityService"
    val enabledServices = Settings.Secure.getString(
        contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    if (enabledServices?.contains(accessibilityServiceName) == true) {
        return true
    } else {
        if (enabledServices?.contains("$packageName/.services.AppLockAccessibilityService") == true) {
            return true
        }
    }
    return false
}

fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("AccessibilitySettings", "Unable to open accessibility settings", e)
    }
}

/**
 * Android 13+ blocks enabling accessibility for sideloaded apps until the user allows
 * "restricted settings" from the app's App info page.
 */
fun isRestrictedSettingsGuidanceNeeded(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * Opens this app's App info page, where battery usage and "Allow restricted settings" live.
 */
fun openAppInfoSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "package:${context.packageName}".toUri()
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("AccessibilitySettings", "Unable to open app details", e)
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }
}

fun Context.enableAccessibilityServiceWithShizuku(serviceComponentName: ComponentName): Boolean {
    val TAG = "ShizukuAccessibilityStarter"
    val serviceString = serviceComponentName.flattenToString()

    if (!Shizuku.pingBinder()) {
        Log.e(TAG, "Shizuku is not available or permission denied.")
        return false
    }

    try {
        val getCurrentCommand = "settings get secure enabled_accessibility_services"
        val currentServices = exec(getCurrentCommand).first()

        val servicesSet = currentServices.split(':')
            .filter { it.isNotBlank() }
            .toMutableSet()

        if (servicesSet.contains(serviceString)) {
            Log.i(TAG, "Service '$serviceString' is already enabled.")
            return true
        }

        servicesSet.add(serviceString)
        val newServicesList = servicesSet.joinToString(":")

        val enableServiceCommand =
            "settings put secure enabled_accessibility_services $newServicesList"
        val enableGlobalCommand = "settings put secure accessibility_enabled 1"

        exec(enableServiceCommand, enableGlobalCommand)

        Log.i(TAG, "Successfully enabled service: $serviceString")
        return true

    } catch (e: Exception) {
        Log.e(
            TAG,
            "Failed to enable Accessibility Service with Shizuku for $serviceString: ${e.message}"
        )
        e.printStackTrace()
        return false
    }
}

private fun exec(vararg command: String): List<String> {
    val output = mutableListOf<String>()
    if (Shizuku.pingBinder()) {
        Log.i("ShizukuPermissionHandler", "Shizuku is running")
    }
    val m = Shizuku::class.java.getDeclaredMethod(
        "newProcess",
        Array<String>::class.java,
        Array<String>::class.java,
        String::class.java
    )
    m.isAccessible = true
    val process =
        m.invoke(null, arrayOf("sh", "-c", *command), null, "/") as ShizukuRemoteProcess
    process.apply {
        waitFor()
        Log.i("ShizukuPermissionHandler", "Process exited with code ${exitValue()}")
        inputStream.bufferedReader().use {
            output.addAll(it.readLines())
        }
        errorStream.bufferedReader().use {
            output.addAll(it.readLines().map { line -> "error: $line" })
        }
    }
    return output
}
