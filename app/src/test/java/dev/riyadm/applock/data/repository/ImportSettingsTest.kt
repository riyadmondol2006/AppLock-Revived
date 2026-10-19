package dev.riyadm.applock.data.repository

import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ImportSettingsTest {

    private lateinit var repo: PreferencesRepository

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        repo = PreferencesRepository(context)
    }

    @Test
    fun validValues_areAppliedAndCounted() {
        val applied = repo.importSettings(
            mapOf(
                "applock_enabled" to false,
                "max_failed_attempts" to 3,
                "lockout_duration_seconds" to 120,
                "unlock_time_duration" to 10,
                "theme_mode" to PreferencesRepository.THEME_DARK,
                "backend_implementation" to BackendImplementation.ACCESSIBILITY.name,
                "scramble_keypad" to true
            )
        )

        assertThat(applied).isEqualTo(7)
        assertThat(repo.isProtectEnabled()).isFalse()
        assertThat(repo.getMaxFailedAttempts()).isEqualTo(3)
        assertThat(repo.getLockoutDurationSeconds()).isEqualTo(120)
        assertThat(repo.getUnlockTimeDuration()).isEqualTo(10)
        assertThat(repo.getThemeMode()).isEqualTo(PreferencesRepository.THEME_DARK)
        assertThat(repo.getBackendImplementation()).isEqualTo(BackendImplementation.ACCESSIBILITY)
        assertThat(repo.isScrambleKeypadEnabled()).isTrue()
    }

    @Test
    fun wrongTypes_areIgnored() {
        val applied = repo.importSettings(
            mapOf(
                "applock_enabled" to 1,
                "scramble_keypad" to "true",
                "max_failed_attempts" to "5",
                "lockout_duration_seconds" to true,
                "unlock_time_duration" to listOf(1),
                "theme_mode" to 1,
                "backend_implementation" to null
            )
        )

        assertThat(applied).isEqualTo(0)
        assertThat(repo.isProtectEnabled()).isTrue()
        assertThat(repo.isScrambleKeypadEnabled()).isFalse()
        assertThat(repo.getMaxFailedAttempts()).isEqualTo(5)
        assertThat(repo.getLockoutDurationSeconds()).isEqualTo(30)
        assertThat(repo.getUnlockTimeDuration()).isEqualTo(0)
        assertThat(repo.getThemeMode()).isEqualTo(PreferencesRepository.THEME_SYSTEM)
    }

    @Test
    fun outOfRangeValues_areIgnored() {
        val applied = repo.importSettings(
            mapOf(
                "max_failed_attempts" to 101,
                "lockout_duration_seconds" to -1,
                "unlock_time_duration" to -5
            )
        )
        assertThat(applied).isEqualTo(0)
        assertThat(repo.importSettings(mapOf("max_failed_attempts" to -1))).isEqualTo(0)
        assertThat(repo.importSettings(mapOf("lockout_duration_seconds" to 0))).isEqualTo(0)
        assertThat(repo.importSettings(mapOf("lockout_duration_seconds" to 3601))).isEqualTo(0)
        assertThat(repo.importSettings(mapOf("lockout_duration_seconds" to Long.MAX_VALUE)))
            .isEqualTo(0)
        assertThat(repo.importSettings(mapOf("unlock_time_duration" to Int.MAX_VALUE + 1L)))
            .isEqualTo(0)

        assertThat(repo.getMaxFailedAttempts()).isEqualTo(5)
        assertThat(repo.getLockoutDurationSeconds()).isEqualTo(30)
        assertThat(repo.getUnlockTimeDuration()).isEqualTo(0)
        assertThat(repo.getRemainingLockoutMs()).isEqualTo(0L)
    }

    @Test
    fun rangeBoundaries_areAccepted() {
        assertThat(repo.importSettings(mapOf("max_failed_attempts" to 0))).isEqualTo(1)
        assertThat(repo.getMaxFailedAttempts()).isEqualTo(0)
        assertThat(repo.importSettings(mapOf("max_failed_attempts" to 100))).isEqualTo(1)
        assertThat(repo.getMaxFailedAttempts()).isEqualTo(100)
        assertThat(repo.importSettings(mapOf("lockout_duration_seconds" to 1))).isEqualTo(1)
        assertThat(repo.getLockoutDurationSeconds()).isEqualTo(1)
        assertThat(repo.importSettings(mapOf("lockout_duration_seconds" to 3600))).isEqualTo(1)
        assertThat(repo.getLockoutDurationSeconds()).isEqualTo(3600)
    }

    @Test
    fun numbersFromJson_acceptWholeLongsAndDoublesOnly() {
        // org.json may hand back Integer, Long or Double for the same field
        assertThat(repo.importSettings(mapOf("max_failed_attempts" to 7L))).isEqualTo(1)
        assertThat(repo.getMaxFailedAttempts()).isEqualTo(7)
        assertThat(repo.importSettings(mapOf("max_failed_attempts" to 8.0))).isEqualTo(1)
        assertThat(repo.getMaxFailedAttempts()).isEqualTo(8)

        assertThat(repo.importSettings(mapOf("max_failed_attempts" to 8.5))).isEqualTo(0)
        assertThat(repo.importSettings(mapOf("max_failed_attempts" to Double.NaN))).isEqualTo(0)
        assertThat(repo.importSettings(mapOf("lockout_duration_seconds" to Double.POSITIVE_INFINITY)))
            .isEqualTo(0)
        assertThat(repo.importSettings(mapOf("lockout_duration_seconds" to 1e19))).isEqualTo(0)
        assertThat(repo.getMaxFailedAttempts()).isEqualTo(8)
        assertThat(repo.getLockoutDurationSeconds()).isEqualTo(30)
    }

    @Test
    fun unknownBackendAndThemeNames_areIgnored() {
        repo.setBackendImplementation(BackendImplementation.ACCESSIBILITY)
        val applied = repo.importSettings(
            mapOf(
                "backend_implementation" to "ROOT_MAGIC",
                "theme_mode" to "sepia"
            )
        )
        assertThat(applied).isEqualTo(0)
        assertThat(repo.getBackendImplementation()).isEqualTo(BackendImplementation.ACCESSIBILITY)
        assertThat(repo.getThemeMode()).isEqualTo(PreferencesRepository.THEME_SYSTEM)
    }

    @Test
    fun unknownAndSensitiveKeys_areIgnored() {
        val applied = repo.importSettings(
            mapOf(
                "password" to "1234",
                "pattern" to "1235789",
                "lock_type" to PreferencesRepository.LOCK_TYPE_PATTERN,
                "failed_attempts" to 0,
                "lockout_until" to 0L,
                "something_new" to true
            )
        )
        assertThat(applied).isEqualTo(0)
        assertThat(repo.getLockType()).isEqualTo(PreferencesRepository.LOCK_TYPE_PIN)
        assertThat(repo.getPassword()).isNull()
        assertThat(repo.getPattern()).isNull()
    }

    @Test
    fun mixedInput_appliesOnlyValidValues() {
        val applied = repo.importSettings(
            mapOf(
                "applock_enabled" to 1,
                "max_failed_attempts" to "5",
                "lockout_duration_seconds" to -1,
                "backend_implementation" to "NOPE",
                "amoled_black" to true,
                "unlock_time_duration" to 15,
                "theme_mode" to PreferencesRepository.THEME_LIGHT
            )
        )
        assertThat(applied).isEqualTo(3)
        assertThat(repo.isProtectEnabled()).isTrue()
        assertThat(repo.isAmoledBlackEnabled()).isTrue()
        assertThat(repo.getUnlockTimeDuration()).isEqualTo(15)
        assertThat(repo.getThemeMode()).isEqualTo(PreferencesRepository.THEME_LIGHT)
        assertThat(repo.getMaxFailedAttempts()).isEqualTo(5)
        assertThat(repo.getLockoutDurationSeconds()).isEqualTo(30)
    }

    @Test
    fun exportThenImport_roundTripsEverySetting() {
        repo.setProtectEnabled(false)
        repo.setMaxFailedAttempts(9)
        repo.setLockoutDurationSeconds(45)
        repo.setThemeMode(PreferencesRepository.THEME_DARK)
        repo.setAmoledBlackEnabled(true)
        repo.setLockType(PreferencesRepository.LOCK_TYPE_PATTERN)

        val exported = repo.exportSettings()
        assertThat(exported).doesNotContainKey("lock_type")
        assertThat(exported).doesNotContainKey("password")
        assertThat(exported).doesNotContainKey("pattern")

        assertThat(repo.importSettings(exported)).isEqualTo(exported.size)
        assertThat(repo.isProtectEnabled()).isFalse()
        assertThat(repo.getMaxFailedAttempts()).isEqualTo(9)
        assertThat(repo.getLockoutDurationSeconds()).isEqualTo(45)
    }
}
