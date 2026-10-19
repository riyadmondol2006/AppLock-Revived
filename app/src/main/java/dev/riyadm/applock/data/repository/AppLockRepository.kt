package dev.riyadm.applock.data.repository

import android.content.Context
import dev.riyadm.applock.core.tile.ProfileTileService
import dev.riyadm.applock.data.manager.BackendServiceManager
import dev.riyadm.applock.features.intruder.capture.IntruderCapture
import dev.riyadm.applock.services.AppLockManager

/**
 * Main repository that coordinates between different specialized repositories and managers.
 * Provides a unified interface for all app lock functionality.
 */
class AppLockRepository(private val context: Context) {

    private val preferencesRepository = PreferencesRepository(context)
    private val lockedAppsRepository = LockedAppsRepository(context)
    private val backendServiceManager = BackendServiceManager()
    val unlockLog = UnlockLogRepository(context)
    val profiles = LockProfilesRepository(context)
    val appPolicies = AppPolicyRepository(
        context,
        globalBiometricEnabled = ::isBiometricAuthEnabled,
        globalAutoPrompt = ::isBiometricAutoPromptEnabled
    )

    fun getLockedApps(): Set<String> = lockedAppsRepository.getLockedApps()
    fun addLockedApp(packageName: String) {
        lockedAppsRepository.addLockedApp(packageName)
        AppLockManager.clearAppUnlockState(packageName)
    }

    fun addMultipleLockedApps(packageNames: Set<String>) {
        lockedAppsRepository.addMultipleLockedApps(packageNames)
        packageNames.forEach(AppLockManager::clearAppUnlockState)
    }
    fun removeLockedApp(packageName: String) = lockedAppsRepository.removeLockedApp(packageName)
    fun isAppLocked(packageName: String): Boolean = lockedAppsRepository.isAppLocked(packageName)

    fun getTriggerExcludedApps(): Set<String> = lockedAppsRepository.getTriggerExcludedApps()
    fun addTriggerExcludedApp(packageName: String) =
        lockedAppsRepository.addTriggerExcludedApp(packageName)

    fun removeTriggerExcludedApp(packageName: String) =
        lockedAppsRepository.removeTriggerExcludedApp(packageName)

    fun isAppTriggerExcluded(packageName: String): Boolean =
        lockedAppsRepository.isAppTriggerExcluded(packageName)

    fun getAntiUninstallApps(): Set<String> = lockedAppsRepository.getAntiUninstallApps()
    fun addAntiUninstallApp(packageName: String) =
        lockedAppsRepository.addAntiUninstallApp(packageName)

    fun removeAntiUninstallApp(packageName: String) =
        lockedAppsRepository.removeAntiUninstallApp(packageName)

    fun isAppAntiUninstall(packageName: String): Boolean =
        lockedAppsRepository.isAppAntiUninstall(packageName)

    fun getPassword(): String? = preferencesRepository.getPassword()
    fun setPassword(password: String) = preferencesRepository.setPassword(password)
    fun validatePassword(inputPassword: String): Boolean =
        preferencesRepository.validatePassword(inputPassword)

    fun getPattern(): String? = preferencesRepository.getPattern()
    fun setPattern(pattern: String) = preferencesRepository.setPattern(pattern)
    fun validatePattern(inputPattern: String): Boolean =
        preferencesRepository.validatePattern(inputPattern)

    fun setLockType(lockType: String) = preferencesRepository.setLockType(lockType)
    fun getLockType(): String = preferencesRepository.getLockType()

    fun setBiometricAuthEnabled(enabled: Boolean) =
        preferencesRepository.setBiometricAuthEnabled(enabled)

    fun isBiometricAuthEnabled(): Boolean = preferencesRepository.isBiometricAuthEnabled()

    fun setUseMaxBrightness(enabled: Boolean) = preferencesRepository.setUseMaxBrightness(enabled)
    fun shouldUseMaxBrightness(): Boolean = preferencesRepository.shouldUseMaxBrightness()
    fun setDisableHaptics(enabled: Boolean) = preferencesRepository.setDisableHaptics(enabled)
    fun shouldDisableHaptics(): Boolean = preferencesRepository.shouldDisableHaptics()
    fun setShowSystemApps(enabled: Boolean) = preferencesRepository.setShowSystemApps(enabled)
    fun shouldShowSystemApps(): Boolean = preferencesRepository.shouldShowSystemApps()

    fun setAntiUninstallEnabled(enabled: Boolean) =
        preferencesRepository.setAntiUninstallEnabled(enabled)

    fun isAntiUninstallEnabled(): Boolean = preferencesRepository.isAntiUninstallEnabled()
    fun setProtectEnabled(enabled: Boolean) = preferencesRepository.setProtectEnabled(enabled)
    fun isProtectEnabled(): Boolean = preferencesRepository.isProtectEnabled()

    fun setUnlockTimeDuration(minutes: Int) = preferencesRepository.setUnlockTimeDuration(minutes)

    /** Unlock duration in effect: the active profile's override, else the global setting. */
    fun getUnlockTimeDuration(): Int =
        profiles.activeUnlockDurationOverride() ?: preferencesRepository.getUnlockTimeDuration()

    fun getGlobalUnlockTimeDuration(): Int = preferencesRepository.getUnlockTimeDuration()
    fun setAutoUnlockEnabled(enabled: Boolean) = preferencesRepository.setAutoUnlockEnabled(enabled)
    fun isAutoUnlockEnabled(): Boolean = preferencesRepository.isAutoUnlockEnabled()

    fun setBackendImplementation(backend: BackendImplementation) =
        preferencesRepository.setBackendImplementation(backend)

    fun getBackendImplementation(): BackendImplementation =
        preferencesRepository.getBackendImplementation()

    fun isShowCommunityLink(): Boolean = preferencesRepository.isShowCommunityLink()
    fun setCommunityLinkShown(shown: Boolean) = preferencesRepository.setCommunityLinkShown(shown)
    fun isShowDonateLink(): Boolean = preferencesRepository.isShowDonateLink(context)
    fun setShowDonateLink(show: Boolean) = preferencesRepository.setShowDonateLink(context, show)

    fun isLoggingEnabled(): Boolean = preferencesRepository.isLoggingEnabled()
    fun setLoggingEnabled(enabled: Boolean) = preferencesRepository.setLoggingEnabled(enabled)

    // Appearance
    fun getThemeMode(): String = preferencesRepository.getThemeMode()
    fun setThemeMode(mode: String) = preferencesRepository.setThemeMode(mode)
    fun isDynamicColorEnabled(): Boolean = preferencesRepository.isDynamicColorEnabled()
    fun setDynamicColorEnabled(enabled: Boolean) =
        preferencesRepository.setDynamicColorEnabled(enabled)

    fun isAmoledBlackEnabled(): Boolean = preferencesRepository.isAmoledBlackEnabled()
    fun setAmoledBlackEnabled(enabled: Boolean) =
        preferencesRepository.setAmoledBlackEnabled(enabled)

    // Lock screen behaviour
    fun isScrambleKeypadEnabled(): Boolean = preferencesRepository.isScrambleKeypadEnabled()
    fun setScrambleKeypadEnabled(enabled: Boolean) =
        preferencesRepository.setScrambleKeypadEnabled(enabled)

    fun isBiometricAutoPromptEnabled(): Boolean =
        preferencesRepository.isBiometricAutoPromptEnabled()

    fun setBiometricAutoPromptEnabled(enabled: Boolean) =
        preferencesRepository.setBiometricAutoPromptEnabled(enabled)

    fun isAutoLockNewAppsEnabled(): Boolean = preferencesRepository.isAutoLockNewAppsEnabled()
    fun setAutoLockNewAppsEnabled(enabled: Boolean) =
        preferencesRepository.setAutoLockNewAppsEnabled(enabled)

    fun isUnlockLogEnabled(): Boolean = preferencesRepository.isUnlockLogEnabled()
    fun setUnlockLogEnabled(enabled: Boolean) {
        preferencesRepository.setUnlockLogEnabled(enabled)
        if (!enabled) unlockLog.clear()
    }

    // Brute-force protection
    fun getMaxFailedAttempts(): Int = preferencesRepository.getMaxFailedAttempts()
    fun setMaxFailedAttempts(attempts: Int) = preferencesRepository.setMaxFailedAttempts(attempts)
    fun getLockoutDurationSeconds(): Int = preferencesRepository.getLockoutDurationSeconds()
    fun setLockoutDurationSeconds(seconds: Int) =
        preferencesRepository.setLockoutDurationSeconds(seconds)

    fun getRemainingLockoutMs(): Long = preferencesRepository.getRemainingLockoutMs()

    /**
     * Single entry point for lock screens to report the outcome of a credential attempt.
     * Handles lockout bookkeeping and the unlock history.
     * Returns the lockout end timestamp after a failure (0 if no lockout started).
     */
    fun recordUnlockAttempt(
        packageName: String,
        success: Boolean,
        method: UnlockLogRepository.Method
    ): Long {
        if (isUnlockLogEnabled()) unlockLog.add(packageName, success, method)
        IntruderCapture.onUnlockAttempt(context, packageName, success, method)
        return if (success) {
            preferencesRepository.resetFailedAttempts()
            0L
        } else if (method == UnlockLogRepository.Method.BIOMETRIC) {
            // The system biometric prompt has its own throttling
            0L
        } else {
            preferencesRepository.registerFailedAttempt()
        }
    }

    /**
     * Records a factor that passed without completing the unlock (the biometric step of a two-step
     * unlock). It only goes into the unlock history; the failed-attempt lockout and the intruder
     * streak are cleared by the final unlock alone.
     */
    fun recordIntermediateFactor(packageName: String, method: UnlockLogRepository.Method) {
        if (isUnlockLogEnabled()) unlockLog.add(packageName, true, method)
    }

    // Per-app security level and lock profiles
    fun policyFor(packageName: String): AppLockPolicy = appPolicies.policyFor(packageName)

    fun getActiveProfile(): LockProfile = profiles.active()

    /**
     * Activates a profile and forgets every running unlock, so all apps of the new profile ask
     * for authentication again. Only call this from authenticated UI.
     */
    fun switchProfile(id: String): Boolean {
        if (profiles.switchTo(id) == null) return false
        AppLockManager.lockAllNow()
        ProfileTileService.requestUpdate(context)
        return true
    }

    fun setProfileMembership(profileId: String, packageName: String, member: Boolean) {
        if (profileId == profiles.active().id) {
            if (member) addLockedApp(packageName) else removeLockedApp(packageName)
        } else {
            profiles.setStoredMembership(profileId, packageName, member)
        }
    }

    // Update checks
    fun isUpdateCheckEnabled(): Boolean = preferencesRepository.isUpdateCheckEnabled()
    fun setUpdateCheckEnabled(enabled: Boolean) = preferencesRepository.setUpdateCheckEnabled(enabled)
    fun getLastUpdateCheck(): Long = preferencesRepository.getLastUpdateCheck()
    fun setLastUpdateCheck(timestamp: Long) = preferencesRepository.setLastUpdateCheck(timestamp)
    fun getSkippedUpdateVersion(): String? = preferencesRepository.getSkippedUpdateVersion()
    fun setSkippedUpdateVersion(version: String?) =
        preferencesRepository.setSkippedUpdateVersion(version)

    // Backup
    internal fun exportSettings(): Map<String, Any?> = preferencesRepository.exportSettings()
    internal fun importSettings(values: Map<String, Any?>): Int =
        preferencesRepository.importSettings(values)

    internal fun replaceAppLists(locked: Set<String>, excluded: Set<String>, antiUninstall: Set<String>) =
        lockedAppsRepository.replaceAll(locked, excluded, antiUninstall)

    /** Restores profiles from a backup; every running unlock is forgotten if the active one changed. */
    internal fun restoreProfiles(profiles: List<LockProfile>, activeId: String, activeApps: Set<String>): Boolean {
        val previousActive = this.profiles.snapshot().activeId
        if (!this.profiles.restore(profiles, activeId, activeApps)) return false
        if (previousActive != activeId) AppLockManager.lockAllNow()
        ProfileTileService.requestUpdate(context)
        return true
    }

    fun setActiveBackend(backend: BackendImplementation) =
        backendServiceManager.setActiveBackend(backend)

    companion object {
        private const val TAG = "AppLockRepository"

        fun shouldStartService(repository: AppLockRepository, serviceClass: Class<*>): Boolean {
            return repository.backendServiceManager.shouldStartService(
                serviceClass,
                repository.getBackendImplementation()
            )
        }
    }
}

enum class BackendImplementation {
    ACCESSIBILITY,
    USAGE_STATS,
    SHIZUKU
}
