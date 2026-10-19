package dev.riyadm.applock.core.utils

import android.util.Base64
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecurityUtilsTest {

    @Test
    fun pbkdf2_roundTrip() {
        val hash = SecurityUtils.hashPassword("1234")
        assertThat(SecurityUtils.isPbkdf2Hash(hash)).isTrue()
        assertThat(SecurityUtils.isHashed(hash)).isTrue()
        assertThat(SecurityUtils.verifyPassword("1234", hash)).isTrue()
    }

    @Test
    fun pbkdf2_wrongPinRejected() {
        val hash = SecurityUtils.hashPassword("1234")
        assertThat(SecurityUtils.verifyPassword("1235", hash)).isFalse()
        assertThat(SecurityUtils.verifyPassword("", hash)).isFalse()
        assertThat(SecurityUtils.verifyPassword("12345", hash)).isFalse()
    }

    @Test
    fun pbkdf2_format_isPrefixIterationsSaltHash() {
        val parts = SecurityUtils.hashPassword("1234").split(":")
        assertThat(parts).hasSize(4)
        assertThat(parts[0]).isEqualTo("pbkdf2")
        assertThat(parts[1].toInt()).isAtLeast(60_000)
        assertThat(Base64.decode(parts[2], Base64.NO_WRAP)).hasLength(16)
        assertThat(Base64.decode(parts[3], Base64.NO_WRAP)).hasLength(32)
    }

    @Test
    fun saltsAreUniquePerHash() {
        val hashes = List(5) { SecurityUtils.hashPassword("1234") }
        assertThat(hashes.toSet()).hasSize(hashes.size)
        assertThat(hashes.map { it.split(":")[2] }.toSet()).hasSize(hashes.size)
        hashes.forEach { assertThat(SecurityUtils.verifyPassword("1234", it)).isTrue() }
    }

    @Test
    fun legacySaltedSha256_isRecognisedAndVerifies() {
        val legacy = SecurityUtils.hashPassword("1234", SecurityUtils.generateSalt())
        assertThat(legacy.split(":")).hasSize(2)
        assertThat(SecurityUtils.isSaltedHash(legacy)).isTrue()
        assertThat(SecurityUtils.isPbkdf2Hash(legacy)).isFalse()
        assertThat(SecurityUtils.isHashed(legacy)).isTrue()
        assertThat(SecurityUtils.verifyPassword("1234", legacy)).isTrue()
        assertThat(SecurityUtils.verifyPassword("4321", legacy)).isFalse()
    }

    @Test
    fun plaintextWithOneColon_isNotMistakenForLegacyHash_issue215() {
        listOf("1234:5678", "abcd:efgh", "pass:word", "AAAA:AAAA", ":").forEach {
            assertThat(SecurityUtils.isSaltedHash(it)).isFalse()
            assertThat(SecurityUtils.isHashed(it)).isFalse()
        }
    }

    @Test
    fun needsRehash_trueForLegacyAndLowerIterations() {
        val salt = SecurityUtils.generateSalt()
        assertThat(SecurityUtils.needsRehash(SecurityUtils.hashPassword("1234", salt))).isTrue()
        assertThat(SecurityUtils.needsRehash(SecurityUtils.hashPasswordPbkdf2("1234", salt, 1_000)))
            .isTrue()
        assertThat(SecurityUtils.needsRehash(SecurityUtils.hashPasswordPbkdf2("1234", salt, 59_999)))
            .isTrue()
        assertThat(SecurityUtils.needsRehash("1234")).isTrue()
    }

    @Test
    fun needsRehash_falseForCurrentAndHigherIterations() {
        val salt = SecurityUtils.generateSalt()
        assertThat(SecurityUtils.needsRehash(SecurityUtils.hashPassword("1234"))).isFalse()
        assertThat(SecurityUtils.needsRehash(SecurityUtils.hashPasswordPbkdf2("1234", salt, 70_000)))
            .isFalse()
    }

    @Test
    fun lowerIterationHash_stillVerifies() {
        val old = SecurityUtils.hashPasswordPbkdf2("1234", SecurityUtils.generateSalt(), 1_000)
        assertThat(SecurityUtils.verifyPassword("1234", old)).isTrue()
        assertThat(SecurityUtils.verifyPassword("0000", old)).isFalse()
    }

    @Test
    fun malformedStoredHashes_neverThrowAndNeverMatch() {
        val inputs = listOf(
            "", ":", "::", ":::", "pbkdf2:x:y:z", "pbkdf2:0:AAAA:AAAA", "pbkdf2:-5:AAAA:AAAA",
            "pbkdf2:1000:!!!:???", "pbkdf2:1000::", "%%%:%%%", "pbkdf2:99999999999:AAAA:AAAA"
        )
        inputs.forEach { stored ->
            assertThat(SecurityUtils.verifyPassword("1234", stored)).isFalse()
            SecurityUtils.needsRehash(stored)
        }
    }

    @Test
    fun sanitizePassword_stripsControlCharsAndCapsLength() {
        assertThat(SecurityUtils.sanitizePassword("a\u0000b\nc\td\u007Fe")).isEqualTo("abcde")
        assertThat(SecurityUtils.sanitizePassword("pass word")).isEqualTo("pass word")
        assertThat(SecurityUtils.sanitizePassword("пароль密码")).isEqualTo("пароль密码")
        assertThat(SecurityUtils.sanitizePassword("x".repeat(100)))
            .hasLength(SecurityUtils.MAX_PASSWORD_LENGTH)
    }

    @Test
    fun hashing_isConsistentWithSanitize() {
        val hash = SecurityUtils.hashPassword("ab\ncd")
        assertThat(SecurityUtils.verifyPassword("abcd", hash)).isTrue()
        assertThat(SecurityUtils.verifyPassword("ab\ncd", hash)).isTrue()

        val long = "y".repeat(80)
        val longHash = SecurityUtils.hashPassword(long)
        assertThat(SecurityUtils.verifyPassword(long, longHash)).isTrue()
        assertThat(SecurityUtils.verifyPassword(long.take(64), longHash)).isTrue()
        assertThat(SecurityUtils.verifyPassword(long.take(63), longHash)).isFalse()
    }
}
