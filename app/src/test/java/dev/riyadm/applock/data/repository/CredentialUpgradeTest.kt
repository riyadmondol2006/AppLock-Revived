package dev.riyadm.applock.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import dev.riyadm.applock.core.utils.SecurityUtils
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Credentials stored by older versions must keep working after an update
 * (aload0/AppLock #215) and be transparently upgraded to the current format.
 */
@RunWith(RobolectricTestRunner::class)
class CredentialUpgradeTest {

    private lateinit var context: Context
    private lateinit var lockPrefs: SharedPreferences
    private lateinit var repo: PreferencesRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        lockPrefs = context.getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)
        repo = PreferencesRepository(context)
    }

    private fun storeRawPassword(value: String) =
        lockPrefs.edit().putString("password", value).commit()

    private fun storeRawPattern(value: String) =
        lockPrefs.edit().putString("pattern", value).commit()

    private fun legacySha256(value: String) =
        SecurityUtils.hashPassword(value, SecurityUtils.generateSalt())

    @Test
    fun newPin_roundTrip() {
        repo.setPassword("2580")
        assertThat(SecurityUtils.isPbkdf2Hash(repo.getPassword()!!)).isTrue()
        assertThat(repo.validatePassword("2580")).isTrue()
        assertThat(repo.validatePassword("0852")).isFalse()
    }

    @Test
    fun noCredential_rejectsEverything() {
        assertThat(repo.validatePassword("")).isFalse()
        assertThat(repo.validatePassword("1234")).isFalse()
        assertThat(repo.validatePattern("")).isFalse()
        assertThat(repo.validatePattern("1235789")).isFalse()
    }

    @Test
    fun emptyInput_neverMatchesStoredCredential() {
        repo.setPassword("1234")
        repo.setPattern("1235789")
        assertThat(repo.validatePassword("")).isFalse()
        assertThat(repo.validatePattern("")).isFalse()
    }

    @Test
    fun legacyPlainPin_isAcceptedAndRehashed_issue215() {
        storeRawPassword("1234")

        assertThat(repo.validatePassword("1234")).isTrue()

        val upgraded = repo.getPassword()!!
        assertThat(SecurityUtils.isPbkdf2Hash(upgraded)).isTrue()
        assertThat(SecurityUtils.needsRehash(upgraded)).isFalse()
        assertThat(repo.validatePassword("1234")).isTrue()
        assertThat(repo.validatePassword("4321")).isFalse()
    }

    @Test
    fun legacyPlainPin_wrongInputIsRejectedAndNotUpgraded_issue215() {
        storeRawPassword("1234")

        assertThat(repo.validatePassword("0000")).isFalse()
        assertThat(repo.getPassword()).isEqualTo("1234")
        assertThat(repo.validatePassword("1234")).isTrue()
    }

    @Test
    fun legacyPlainPassword_withControlCharsInStorage_isAccepted_issue215() {
        // Very old versions stored the raw input; the user types the same thing again
        storeRawPassword("my\tpass")

        assertThat(repo.validatePassword("my\tpass")).isTrue()
        assertThat(SecurityUtils.isPbkdf2Hash(repo.getPassword()!!)).isTrue()
        assertThat(repo.validatePassword("my\tpass")).isTrue()
    }

    @Test
    fun legacyPlainPassword_containingColon_isAcceptedAndRehashed_issue215() {
        // A plaintext value with one colon looks like the legacy "salt:hash" format
        storeRawPassword("1234:5678")

        assertThat(repo.validatePassword("1234:5678")).isTrue()
        assertThat(SecurityUtils.isPbkdf2Hash(repo.getPassword()!!)).isTrue()
        assertThat(repo.validatePassword("1234:5678")).isTrue()
    }

    @Test
    fun legacySha256Pin_isAcceptedAndRehashed_issue215() {
        val legacy = legacySha256("1234")
        storeRawPassword(legacy)

        assertThat(repo.validatePassword("1234")).isTrue()

        val upgraded = repo.getPassword()!!
        assertThat(upgraded).isNotEqualTo(legacy)
        assertThat(SecurityUtils.isPbkdf2Hash(upgraded)).isTrue()
        assertThat(repo.validatePassword("1234")).isTrue()
    }

    @Test
    fun legacySha256Pin_wrongInputIsRejectedAndNotUpgraded_issue215() {
        val legacy = legacySha256("1234")
        storeRawPassword(legacy)

        assertThat(repo.validatePassword("1243")).isFalse()
        assertThat(repo.getPassword()).isEqualTo(legacy)
    }

    @Test
    fun lowIterationPbkdf2Pin_isRehashedOnSuccess() {
        val weak = SecurityUtils.hashPasswordPbkdf2("1234", SecurityUtils.generateSalt(), 1_000)
        storeRawPassword(weak)

        assertThat(repo.validatePassword("9999")).isFalse()
        assertThat(repo.getPassword()).isEqualTo(weak)

        assertThat(repo.validatePassword("1234")).isTrue()
        val upgraded = repo.getPassword()!!
        assertThat(upgraded).isNotEqualTo(weak)
        assertThat(SecurityUtils.needsRehash(upgraded)).isFalse()
        assertThat(repo.validatePassword("1234")).isTrue()
    }

    @Test
    fun currentPbkdf2Pin_isNotRewrittenOnSuccess() {
        repo.setPassword("1234")
        val stored = repo.getPassword()
        assertThat(repo.validatePassword("1234")).isTrue()
        assertThat(repo.getPassword()).isEqualTo(stored)
    }

    @Test
    fun legacyPlainPattern_isAcceptedAndRehashed_issue215() {
        storeRawPattern("1235789")

        assertThat(repo.validatePattern("1235789")).isTrue()

        val upgraded = repo.getPattern()!!
        assertThat(SecurityUtils.isPbkdf2Hash(upgraded)).isTrue()
        assertThat(repo.validatePattern("1235789")).isTrue()
        assertThat(repo.validatePattern("1235798")).isFalse()
    }

    @Test
    fun legacyPlainPattern_wrongInputIsRejectedAndNotUpgraded_issue215() {
        storeRawPattern("1235789")

        assertThat(repo.validatePattern("123")).isFalse()
        assertThat(repo.validatePattern("12357890")).isFalse()
        assertThat(repo.getPattern()).isEqualTo("1235789")
    }

    @Test
    fun legacyHashedPattern_isAcceptedAndRehashed_issue215() {
        val legacy = legacySha256("1235789")
        storeRawPattern(legacy)

        assertThat(repo.validatePattern("9875321")).isFalse()
        assertThat(repo.getPattern()).isEqualTo(legacy)

        assertThat(repo.validatePattern("1235789")).isTrue()
        val upgraded = repo.getPattern()!!
        assertThat(SecurityUtils.isPbkdf2Hash(upgraded)).isTrue()
        assertThat(repo.validatePattern("1235789")).isTrue()
    }

    @Test
    fun passwordsWithSpacesUnicodeAndControlChars_whatIsSetCanBeValidated() {
        val samples = listOf(
            "correct horse battery staple",
            "  leading and trailing  ",
            "пароль-密码-كلمة",
            "emoji🔒lock",
            "tab\there",
            "new\nline",
            "nul\u0000byte",
            "del\u007Fchar",
            "z".repeat(SecurityUtils.MAX_PASSWORD_LENGTH + 20),
            // Surrogate pair split by the length cap
            "a".repeat(SecurityUtils.MAX_PASSWORD_LENGTH - 1) + "🔒"
        )
        samples.forEach { password ->
            repo.setPassword(password)
            assertThat(repo.validatePassword(password)).isTrue()
            assertThat(repo.validatePassword(SecurityUtils.sanitizePassword(password))).isTrue()
            assertThat(repo.validatePassword(password + "x")).isEqualTo(
                SecurityUtils.sanitizePassword(password + "x") ==
                    SecurityUtils.sanitizePassword(password)
            )
        }
    }

    @Test
    fun passwordWithOnlyControlChars_stillValidatesWithSameKeystrokes() {
        repo.setPassword("\n\t")
        assertThat(repo.validatePassword("\n\t")).isTrue()
        assertThat(repo.validatePassword("1234")).isFalse()
    }

    @Test
    fun switchingFromPinToPattern_leavesConsistentState_issue232() {
        repo.setPassword("1234")
        repo.setLockType(PreferencesRepository.LOCK_TYPE_PIN)

        repo.setPattern("1478963")
        repo.setLockType(PreferencesRepository.LOCK_TYPE_PATTERN)

        assertThat(repo.getLockType()).isEqualTo(PreferencesRepository.LOCK_TYPE_PATTERN)
        assertThat(repo.validatePattern("1478963")).isTrue()
        assertThat(repo.validatePattern("1234")).isFalse()

        // A fresh repository instance (e.g. after a process restart) sees the same state
        val reloaded = PreferencesRepository(context)
        assertThat(reloaded.getLockType()).isEqualTo(PreferencesRepository.LOCK_TYPE_PATTERN)
        assertThat(reloaded.validatePattern("1478963")).isTrue()
    }

    @Test
    fun switchingFromPinToPassword_replacesOldPin_issue232() {
        repo.setPassword("1234")
        repo.setLockType(PreferencesRepository.LOCK_TYPE_PIN)

        repo.setPassword("hunter2 secret")
        repo.setLockType(PreferencesRepository.LOCK_TYPE_PASSWORD)

        assertThat(repo.getLockType()).isEqualTo(PreferencesRepository.LOCK_TYPE_PASSWORD)
        assertThat(repo.validatePassword("hunter2 secret")).isTrue()
        assertThat(repo.validatePassword("1234")).isFalse()
    }

    @Test
    fun switchingFromPatternBackToPin_usesNewPin_issue232() {
        repo.setPattern("1235789")
        repo.setLockType(PreferencesRepository.LOCK_TYPE_PATTERN)

        repo.setPassword("8642")
        repo.setLockType(PreferencesRepository.LOCK_TYPE_PIN)

        assertThat(repo.getLockType()).isEqualTo(PreferencesRepository.LOCK_TYPE_PIN)
        assertThat(repo.validatePassword("8642")).isTrue()
    }

    @Test
    fun credentialSavedBeforeLockType_oldLockTypeStillHasAMatchingCredential_issue232() {
        repo.setPassword("1234")
        repo.setLockType(PreferencesRepository.LOCK_TYPE_PIN)

        // Process dies after the new pattern is saved but before the lock type is switched
        repo.setPattern("1235789")

        val reloaded = PreferencesRepository(context)
        assertThat(reloaded.getLockType()).isEqualTo(PreferencesRepository.LOCK_TYPE_PIN)
        assertThat(reloaded.validatePassword("1234")).isTrue()
    }

    @Test
    fun resettingPin_replacesOldOne_issue232() {
        repo.setPassword("1111")
        repo.setPassword("2222")
        assertThat(repo.validatePassword("1111")).isFalse()
        assertThat(repo.validatePassword("2222")).isTrue()
    }

    @Test
    fun defaultLockType_isPin() {
        assertThat(repo.getLockType()).isEqualTo(PreferencesRepository.LOCK_TYPE_PIN)
    }
}
