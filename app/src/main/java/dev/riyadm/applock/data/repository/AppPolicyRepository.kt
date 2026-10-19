package dev.riyadm.applock.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How a single locked app is unlocked. There is still only one credential and the device biometrics. */
enum class SecurityLevel {
    /** Follow the global biometric settings. */
    DEFAULT,

    /** Fingerprint / face. Falls back to the credential when biometrics can't be used. */
    BIOMETRIC_ONLY,

    /** PIN / pattern / password only, no biometric prompt. */
    CREDENTIAL_ONLY,

    /** Biometrics and then the credential, both required. */
    TWO_STEP;

    companion object {
        fun fromName(name: String?): SecurityLevel =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * What a lock screen may offer for one app. Use [resolve] with the current biometric availability
 * before acting on it: an unusable biometric factor always falls back to the credential,
 * never to "no authentication".
 */
@Immutable
data class AppLockPolicy(
    val level: SecurityLevel,
    val allowBiometric: Boolean,
    val allowCredential: Boolean,
    val requireBoth: Boolean,
    val autoPromptBiometric: Boolean,
) {
    /** True when the credential input is hidden until biometrics fail or turn out to be unusable. */
    val biometricFirst: Boolean get() = allowBiometric && (!allowCredential || requireBoth)

    /**
     * Adjusts the policy to what the device can do right now. Without usable biometrics the
     * credential is always required.
     */
    fun resolve(biometricAvailable: Boolean): AppLockPolicy =
        if (biometricAvailable || !allowBiometric) {
            this
        } else {
            copy(
                allowBiometric = false,
                allowCredential = true,
                requireBoth = false,
                autoPromptBiometric = false
            )
        }

    companion object {
        fun from(
            level: SecurityLevel,
            globalBiometricEnabled: Boolean,
            globalAutoPrompt: Boolean,
        ): AppLockPolicy = when (level) {
            SecurityLevel.DEFAULT -> AppLockPolicy(
                level = level,
                allowBiometric = globalBiometricEnabled,
                allowCredential = true,
                requireBoth = false,
                autoPromptBiometric = globalBiometricEnabled && globalAutoPrompt
            )

            SecurityLevel.BIOMETRIC_ONLY -> AppLockPolicy(
                level = level,
                allowBiometric = true,
                allowCredential = false,
                requireBoth = false,
                autoPromptBiometric = true
            )

            SecurityLevel.CREDENTIAL_ONLY -> AppLockPolicy(
                level = level,
                allowBiometric = false,
                allowCredential = true,
                requireBoth = false,
                autoPromptBiometric = false
            )

            SecurityLevel.TWO_STEP -> AppLockPolicy(
                level = level,
                allowBiometric = true,
                allowCredential = true,
                requireBoth = true,
                autoPromptBiometric = true
            )
        }
    }
}

/**
 * Per-app [SecurityLevel]s, stored in their own preferences file. Apps without an entry use
 * [SecurityLevel.DEFAULT].
 */
class AppPolicyRepository(
    context: Context,
    private val globalBiometricEnabled: () -> Boolean,
    private val globalAutoPrompt: () -> Boolean,
) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _levels = MutableStateFlow(readAll())

    /** Every app with a non-default level. */
    val levels: StateFlow<Map<String, SecurityLevel>> = _levels.asStateFlow()

    fun levelFor(packageName: String): SecurityLevel = _levels.value[packageName] ?: SecurityLevel.DEFAULT

    fun policyFor(packageName: String): AppLockPolicy =
        AppLockPolicy.from(levelFor(packageName), globalBiometricEnabled(), globalAutoPrompt())

    fun setLevel(packageName: String, level: SecurityLevel) {
        if (packageName.isBlank()) return
        synchronized(this) {
            preferences.edit(commit = true) {
                if (level == SecurityLevel.DEFAULT) remove(packageName) else putString(packageName, level.name)
            }
            _levels.value = readAll()
        }
    }

    /** Replaces every level, e.g. from a backup. */
    fun replaceAll(levels: Map<String, SecurityLevel>) {
        synchronized(this) {
            preferences.edit(commit = true) {
                clear()
                levels.forEach { (pkg, level) ->
                    if (pkg.isNotBlank() && level != SecurityLevel.DEFAULT) putString(pkg, level.name)
                }
            }
            _levels.value = readAll()
        }
    }

    private fun readAll(): Map<String, SecurityLevel> =
        preferences.all.mapNotNull { (key, value) ->
            val level = SecurityLevel.fromName(value as? String)
            if (level == SecurityLevel.DEFAULT) null else key to level
        }.toMap()

    private companion object {
        const val PREFS_NAME = "app_lock_policies"
    }
}
