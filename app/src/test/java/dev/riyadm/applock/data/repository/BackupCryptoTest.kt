package dev.riyadm.applock.data.repository

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class BackupCryptoTest {

    private val plaintext = """{"format":"dev.riyadm.applock.backup","version":1,"lockedApps":["a.b"]}"""
        .toByteArray(Charsets.UTF_8)

    // Fewer iterations than the default keeps the tests fast; the code path is the same.
    private val iterations = 10_000

    @Test
    fun roundTrip() {
        val sealed = BackupCrypto.encrypt(plaintext, "correct horse".toCharArray(), iterations)
        assertEquals(16, sealed.salt.size)
        assertEquals(12, sealed.iv.size)
        assertFalse(sealed.ciphertext.contentEquals(plaintext))

        val decrypted = BackupCrypto.decrypt(sealed, "correct horse".toCharArray())
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun roundTripThroughBase64() {
        val sealed = BackupCrypto.encrypt(plaintext, "pässwörd-ü".toCharArray(), iterations)
        val restored = BackupCrypto.Sealed(
            iterations = sealed.iterations,
            salt = BackupCrypto.decode(BackupCrypto.encode(sealed.salt)),
            iv = BackupCrypto.decode(BackupCrypto.encode(sealed.iv)),
            ciphertext = BackupCrypto.decode(BackupCrypto.encode(sealed.ciphertext))
        )
        assertArrayEquals(plaintext, BackupCrypto.decrypt(restored, "pässwörd-ü".toCharArray()))
    }

    @Test
    fun wrongPasswordFails() {
        val sealed = BackupCrypto.encrypt(plaintext, "correct horse".toCharArray(), iterations)
        assertThrows(BackupCrypto.WrongPasswordException::class.java) {
            BackupCrypto.decrypt(sealed, "wrong horse".toCharArray())
        }
        assertThrows(BackupCrypto.WrongPasswordException::class.java) {
            BackupCrypto.decrypt(sealed, CharArray(0))
        }
    }

    @Test
    fun tamperedCiphertextFails() {
        val sealed = BackupCrypto.encrypt(plaintext, "correct horse".toCharArray(), iterations)
        val tampered = sealed.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertThrows(BackupCrypto.WrongPasswordException::class.java) {
            BackupCrypto.decrypt(
                BackupCrypto.Sealed(sealed.iterations, sealed.salt, sealed.iv, tampered),
                "correct horse".toCharArray()
            )
        }
    }

    @Test
    fun saltAndIvAreRandom() {
        val a = BackupCrypto.encrypt(plaintext, "correct horse".toCharArray(), iterations)
        val b = BackupCrypto.encrypt(plaintext, "correct horse".toCharArray(), iterations)
        assertFalse(a.salt.contentEquals(b.salt))
        assertFalse(a.iv.contentEquals(b.iv))
        assertFalse(a.ciphertext.contentEquals(b.ciphertext))
    }

    @Test
    fun shortPasswordRejectedOnEncrypt() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.encrypt(plaintext, "12345".toCharArray(), iterations)
        }
    }

    @Test
    fun unreasonableIterationCountRejected() {
        val sealed = BackupCrypto.encrypt(plaintext, "correct horse".toCharArray(), iterations)
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(
                BackupCrypto.Sealed(Int.MAX_VALUE, sealed.salt, sealed.iv, sealed.ciphertext),
                "correct horse".toCharArray()
            )
        }
    }

    @Test
    fun keyDerivationMatchesJdkPbkdf2() {
        val salt = "NaCl-and-pepper!".toByteArray()
        val password = "pässwörd 🔐".toCharArray()
        val expected = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password, salt, 1_000, 256))
            .encoded
        assertArrayEquals(expected, BackupCrypto.deriveKey(password, salt, 1_000))
    }

    @Test
    fun keyDerivationMatchesRfc7914Vector() {
        // RFC 7914 section 11: PBKDF2-HMAC-SHA256, P="passwd", S="salt", c=1, dkLen=64.
        val expected = (
                "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc" +
                        "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783"
                ).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertArrayEquals(expected, BackupCrypto.deriveKey("passwd".toCharArray(), "salt".toByteArray(), 1, 64))
    }
}
