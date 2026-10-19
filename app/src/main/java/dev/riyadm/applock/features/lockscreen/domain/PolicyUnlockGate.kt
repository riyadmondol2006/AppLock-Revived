package dev.riyadm.applock.features.lockscreen.domain

import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.data.repository.AppLockPolicy
import dev.riyadm.applock.features.lockscreen.ui.canUseBiometrics

/**
 * The [AppLockPolicy] for [packageName], adjusted to whether biometrics can be used right now.
 * Call once per lock-screen session, off the composition.
 */
fun Context.resolvedLockPolicy(packageName: String): AppLockPolicy =
    appLockRepository().policyFor(packageName).resolve(biometricAvailable = canUseBiometrics())

/**
 * Sequencing of the two factors for one lock-screen session. The host routes every biometric and
 * credential result through it and unlocks only when [onBiometricSucceeded] or
 * [onCredentialSucceeded] returns true.
 *
 * - Credential only: biometric results are ignored and no prompt is offered.
 * - Biometric only: the credential input stays hidden unless biometrics fail hard (lockout,
 *   hardware error); then the credential is offered instead. Never "no authentication".
 * - Two-step: biometrics first, then the credential; both are required. A biometric lockout
 *   doesn't skip a factor, it only delays it. If biometrics are unusable when the session starts,
 *   [AppLockPolicy.resolve] has already turned the policy into credential only.
 */
@Stable
class PolicyUnlockGate(val policy: AppLockPolicy) {

    var biometricPassed by mutableStateOf(false)
        private set
    var credentialPassed by mutableStateOf(false)
        private set

    /** Biometric-only session where biometrics failed hard; the credential is offered instead. */
    var credentialFallback by mutableStateOf(false)
        private set

    val showBiometricButton: Boolean
        get() = policy.allowBiometric && !biometricPassed

    val showCredentialInput: Boolean
        get() = when {
            !policy.allowBiometric -> true
            policy.requireBoth -> biometricPassed
            !policy.allowCredential -> credentialFallback
            else -> true
        }

    /** Two-step: biometrics passed, show "Now enter your PIN". */
    val awaitingCredential: Boolean
        get() = policy.requireBoth && biometricPassed && !credentialPassed

    val shouldAutoPromptBiometric: Boolean
        get() = policy.autoPromptBiometric && policy.allowBiometric && !biometricPassed

    val isSatisfied: Boolean
        get() = if (policy.requireBoth) {
            biometricPassed && credentialPassed
        } else {
            (policy.allowBiometric && biometricPassed) || credentialPassed
        }

    /** Returns true when the app may be unlocked now. */
    fun onBiometricSucceeded(): Boolean {
        if (!policy.allowBiometric) return false
        biometricPassed = true
        return isSatisfied
    }

    /** Returns true when the app may be unlocked now. */
    fun onCredentialSucceeded(): Boolean {
        if (!showCredentialInput) return false
        credentialPassed = true
        return isSatisfied
    }

    fun onBiometricError(errorCode: Int) {
        if (policy.allowBiometric && !policy.allowCredential && !policy.requireBoth &&
            errorCode in HARD_BIOMETRIC_ERRORS
        ) {
            credentialFallback = true
        }
    }

    private companion object {
        val HARD_BIOMETRIC_ERRORS = setOf(
            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
            BiometricPrompt.ERROR_NO_SPACE,
            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_VENDOR,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED,
        )
    }
}
