package dev.riyadm.applock.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LockoutTest {

    private lateinit var lockPrefs: SharedPreferences
    private lateinit var settingsPrefs: SharedPreferences
    private lateinit var repo: PreferencesRepository

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        lockPrefs = context.getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)
        settingsPrefs = context.getSharedPreferences("app_lock_settings", Context.MODE_PRIVATE)
        repo = PreferencesRepository(context)
    }

    private fun fail(times: Int): Long {
        var last = 0L
        repeat(times) { last = repo.registerFailedAttempt() }
        return last
    }

    /** Pretends the current lockout has run out so the next streak can start. */
    private fun expireLockout() = lockPrefs.edit().putLong("lockout_until", 0L).commit()

    @Test
    fun defaults() {
        assertThat(repo.getMaxFailedAttempts()).isEqualTo(5)
        assertThat(repo.getLockoutDurationSeconds()).isEqualTo(30)
        assertThat(repo.getRemainingLockoutMs()).isEqualTo(0L)
    }

    @Test
    fun failuresBelowLimit_doNotLockOut() {
        repeat(4) { assertThat(repo.registerFailedAttempt()).isEqualTo(0L) }
        assertThat(repo.getFailedAttemptCount()).isEqualTo(4)
        assertThat(repo.getRemainingLockoutMs()).isEqualTo(0L)
    }

    @Test
    fun reachingLimit_locksOutForConfiguredDuration() {
        val before = System.currentTimeMillis()
        val until = fail(5)
        val after = System.currentTimeMillis()

        assertThat(until).isAtLeast(before + 30_000L)
        assertThat(until).isAtMost(after + 30_000L)
        assertThat(repo.getLockoutUntil()).isEqualTo(until)
        assertThat(repo.getFailedAttemptCount()).isEqualTo(0)
        assertThat(repo.getRemainingLockoutMs()).isIn(com.google.common.collect.Range.closed(1L, 30_000L))
    }

    @Test
    fun customLimit_isRespected() {
        repo.setMaxFailedAttempts(2)
        repo.setLockoutDurationSeconds(10)
        assertThat(repo.registerFailedAttempt()).isEqualTo(0L)
        assertThat(repo.registerFailedAttempt()).isGreaterThan(0L)
        assertThat(repo.getRemainingLockoutMs()).isAtMost(10_000L)
    }

    @Test
    fun zeroMaxAttempts_disablesLockout() {
        repo.setMaxFailedAttempts(0)
        repeat(50) { assertThat(repo.registerFailedAttempt()).isEqualTo(0L) }
        assertThat(repo.getRemainingLockoutMs()).isEqualTo(0L)
    }

    @Test
    fun negativeMaxAttempts_disablesLockout() {
        repo.setMaxFailedAttempts(-3)
        repeat(10) { assertThat(repo.registerFailedAttempt()).isEqualTo(0L) }
        assertThat(repo.getRemainingLockoutMs()).isEqualTo(0L)
    }

    @Test
    fun repeatedLockouts_escalateAndCapAtSixteenTimes() {
        repo.setMaxFailedAttempts(1)
        repo.setLockoutDurationSeconds(10)
        val expected = listOf(1L, 2L, 4L, 8L, 16L, 16L, 16L)

        expected.forEach { multiplier ->
            val before = System.currentTimeMillis()
            val until = repo.registerFailedAttempt()
            val after = System.currentTimeMillis()
            assertThat(until).isAtLeast(before + 10_000L * multiplier)
            assertThat(until).isAtMost(after + 10_000L * multiplier)
            assertThat(repo.getRemainingLockoutMs()).isAtMost(10_000L * 16)
            expireLockout()
        }
    }

    @Test
    fun success_resetsCountStreakAndLockout() {
        repo.setMaxFailedAttempts(2)
        repo.setLockoutDurationSeconds(10)
        fail(2)
        expireLockout()
        fail(1)
        assertThat(repo.getFailedAttemptCount()).isEqualTo(1)

        repo.resetFailedAttempts()

        assertThat(repo.getFailedAttemptCount()).isEqualTo(0)
        assertThat(repo.getLockoutUntil()).isEqualTo(0L)
        assertThat(repo.getRemainingLockoutMs()).isEqualTo(0L)
        // Streak was reset too: the next lockout is back to the base duration
        val before = System.currentTimeMillis()
        val until = fail(2)
        assertThat(until - before).isAtMost(10_000L + 1_000L)
    }

    @Test
    fun resetWhileLockedOut_clearsLockout() {
        fail(5)
        assertThat(repo.getRemainingLockoutMs()).isGreaterThan(0L)
        repo.resetFailedAttempts()
        assertThat(repo.getRemainingLockoutMs()).isEqualTo(0L)
    }

    @Test
    fun remainingTime_isCappedWhenLockoutEndIsFarInTheFuture() {
        // e.g. the clock was moved back after a lockout started
        lockPrefs.edit().putLong("lockout_until", Long.MAX_VALUE).commit()
        assertThat(repo.getRemainingLockoutMs()).isEqualTo(30_000L * 16)

        repo.setLockoutDurationSeconds(5)
        assertThat(repo.getRemainingLockoutMs()).isEqualTo(5_000L * 16)
    }

    @Test
    fun remainingTime_isZeroWhenLockoutIsInThePast() {
        lockPrefs.edit().putLong("lockout_until", 1L).commit()
        assertThat(repo.getRemainingLockoutMs()).isEqualTo(0L)
        lockPrefs.edit().putLong("lockout_until", System.currentTimeMillis() - 1_000L).commit()
        assertThat(repo.getRemainingLockoutMs()).isEqualTo(0L)
    }

    @Test
    fun negativeLockoutDuration_neverThrowsAndNeverLocksOut() {
        repo.setLockoutDurationSeconds(-1)
        repo.setMaxFailedAttempts(1)
        repeat(6) { repo.registerFailedAttempt() }
        assertThat(repo.getRemainingLockoutMs()).isEqualTo(0L)

        lockPrefs.edit().putLong("lockout_until", Long.MAX_VALUE).commit()
        assertThat(repo.getRemainingLockoutMs()).isEqualTo(0L)

        repo.setLockoutDurationSeconds(Int.MIN_VALUE)
        repeat(6) { repo.registerFailedAttempt() }
        assertThat(repo.getRemainingLockoutMs()).isEqualTo(0L)
    }

    @Test
    fun absurdLockoutDuration_neverThrowsOrOverflows() {
        repo.setLockoutDurationSeconds(Int.MAX_VALUE)
        repo.setMaxFailedAttempts(1)
        repeat(6) {
            val until = repo.registerFailedAttempt()
            assertThat(until).isGreaterThan(System.currentTimeMillis())
            val remaining = repo.getRemainingLockoutMs()
            assertThat(remaining).isGreaterThan(0L)
            assertThat(remaining).isAtMost(Int.MAX_VALUE * 1000L * 16)
        }
    }

    @Test
    fun corruptCounters_doNotThrow() {
        lockPrefs.edit()
            .putInt("failed_attempts", Int.MAX_VALUE - 1)
            .putInt("lockout_streak", Int.MAX_VALUE)
            .commit()
        repo.setMaxFailedAttempts(5)
        val until = repo.registerFailedAttempt()
        assertThat(until).isGreaterThan(0L)
        assertThat(repo.getRemainingLockoutMs()).isAtMost(30_000L * 16)
    }
}
