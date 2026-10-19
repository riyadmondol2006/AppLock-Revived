package dev.riyadm.applock.features.lockscreen.domain

import android.content.Context
import com.google.common.truth.Truth.assertThat
import dev.riyadm.applock.data.repository.AppLockPolicy
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.PreferencesRepository
import dev.riyadm.applock.data.repository.SecurityLevel
import dev.riyadm.applock.data.repository.UnlockLogRepository.Method
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** A passed biometric step must never clear the PIN lockout unless it completes the unlock. */
@RunWith(RobolectricTestRunner::class)
class PolicyUnlockGateLockoutTest {

    private lateinit var repo: AppLockRepository
    private lateinit var prefs: PreferencesRepository

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        repo = AppLockRepository(context)
        prefs = PreferencesRepository(context)
        repo.setUnlockLogEnabled(true)
    }

    private fun gate(level: SecurityLevel) = PolicyUnlockGate(
        AppLockPolicy.from(level, globalBiometricEnabled = true, globalAutoPrompt = true)
            .resolve(biometricAvailable = true)
    )

    private fun failPin(times: Int) = repeat(times) { repo.recordUnlockAttempt(PKG, false, Method.PIN) }

    private fun PolicyUnlockGate.passBiometric(): Boolean =
        onBiometricSucceeded(repo, PKG, record = { it() })

    @Test
    fun twoStep_biometricStep_keepsLockout() {
        failPin(5)
        val lockedUntil = prefs.getLockoutUntil()
        assertThat(repo.getRemainingLockoutMs()).isGreaterThan(0L)

        val gate = gate(SecurityLevel.TWO_STEP)
        assertThat(gate.passBiometric()).isFalse()

        assertThat(gate.awaitingCredential).isTrue()
        assertThat(prefs.getLockoutUntil()).isEqualTo(lockedUntil)
        assertThat(repo.getRemainingLockoutMs()).isGreaterThan(0L)
    }

    @Test
    fun twoStep_biometricStep_keepsFailedCountAndStreak() {
        failPin(3)
        assertThat(gate(SecurityLevel.TWO_STEP).passBiometric()).isFalse()
        assertThat(prefs.getFailedAttemptCount()).isEqualTo(3)

        // Two more wrong PINs still reach the limit of five.
        failPin(2)
        assertThat(repo.getRemainingLockoutMs()).isGreaterThan(0L)
    }

    @Test
    fun twoStep_biometricStep_isLoggedWithoutUnlocking() {
        gate(SecurityLevel.TWO_STEP).passBiometric()
        val entries = repo.unlockLog.getEntries()
        assertThat(entries).hasSize(1)
        assertThat(entries.single().method).isEqualTo(Method.BIOMETRIC)
        assertThat(entries.single().packageName).isEqualTo(PKG)
    }

    @Test
    fun twoStep_biometricStep_notLoggedWhenHistoryOff() {
        repo.setUnlockLogEnabled(false)
        gate(SecurityLevel.TWO_STEP).passBiometric()
        assertThat(repo.unlockLog.getEntries()).isEmpty()
    }

    @Test
    fun twoStep_finalCredential_clearsFailedAttempts() {
        failPin(3)
        val gate = gate(SecurityLevel.TWO_STEP)
        gate.passBiometric()
        assertThat(gate.showCredentialInput).isTrue()

        repo.recordUnlockAttempt(PKG, true, Method.PIN)
        assertThat(gate.onCredentialSucceeded()).isTrue()
        assertThat(prefs.getFailedAttemptCount()).isEqualTo(0)
    }

    @Test
    fun biometricOnly_success_unlocksAndResetsCounter() {
        failPin(3)
        assertThat(gate(SecurityLevel.BIOMETRIC_ONLY).passBiometric()).isTrue()
        assertThat(prefs.getFailedAttemptCount()).isEqualTo(0)
    }

    @Test
    fun default_biometricSuccess_unlocksAndClearsLockout() {
        failPin(5)
        assertThat(gate(SecurityLevel.DEFAULT).passBiometric()).isTrue()
        assertThat(repo.getRemainingLockoutMs()).isEqualTo(0L)
    }

    @Test
    fun credentialOnly_biometricIsIgnoredAndNotRecorded() {
        failPin(3)
        assertThat(gate(SecurityLevel.CREDENTIAL_ONLY).passBiometric()).isFalse()
        assertThat(prefs.getFailedAttemptCount()).isEqualTo(3)
        assertThat(repo.unlockLog.getEntries().none { it.method == Method.BIOMETRIC }).isTrue()
    }

    @Test
    fun twoStep_biometricBlockedOnlyDuringLockout() {
        val gate = gate(SecurityLevel.TWO_STEP)
        assertThat(gate.isBiometricBlocked(0L)).isFalse()
        assertThat(gate.isBiometricBlocked(10_000L)).isTrue()

        gate.passBiometric()
        assertThat(gate.isBiometricBlocked(10_000L)).isFalse()
    }

    @Test
    fun otherLevels_neverBlockBiometric() {
        for (level in listOf(SecurityLevel.DEFAULT, SecurityLevel.BIOMETRIC_ONLY)) {
            assertThat(gate(level).isBiometricBlocked(10_000L)).isFalse()
        }
    }

    private companion object {
        const val PKG = "com.example.locked"
    }
}
