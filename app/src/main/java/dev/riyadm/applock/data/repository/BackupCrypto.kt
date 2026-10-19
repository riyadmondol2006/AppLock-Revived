package dev.riyadm.applock.data.repository

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based encryption for backup files: AES-256-GCM with a key derived by
 * PBKDF2-HMAC-SHA256 (password encoded as UTF-8). Uses only JDK APIs so it can be unit tested
 * on the JVM.
 */
object BackupCrypto {
    const val KDF = "PBKDF2-HMAC-SHA256"
    const val CIPHER = "AES-256-GCM"
    const val DEFAULT_ITERATIONS = 210_000
    const val MIN_PASSWORD_LENGTH = 6

    /** Range accepted when reading a file, so a crafted file can't make us hang. */
    private val ACCEPTED_ITERATIONS = 10_000..5_000_000

    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BYTES = 32
    private const val TAG_BITS = 128
    private const val HMAC = "HmacSHA256"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    class Sealed(
        val iterations: Int,
        val salt: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray
    )

    /** The password is wrong or the file was modified. */
    class WrongPasswordException(cause: Throwable? = null) :
        GeneralSecurityException("Wrong password or damaged file", cause)

    fun encrypt(
        plaintext: ByteArray,
        password: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom()
    ): Sealed {
        require(password.size >= MIN_PASSWORD_LENGTH) { "Password too short" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val key = deriveKey(password, salt, iterations)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            return Sealed(iterations, salt, iv, cipher.doFinal(plaintext))
        } finally {
            key.fill(0)
        }
    }

    /** Returns the plaintext, or throws [WrongPasswordException]; never returns partial data. */
    fun decrypt(sealed: Sealed, password: CharArray): ByteArray {
        require(sealed.iterations in ACCEPTED_ITERATIONS) { "Unsupported key derivation settings" }
        require(sealed.salt.size >= SALT_BYTES && sealed.iv.size == IV_BYTES) { "Damaged backup file" }
        if (password.isEmpty()) throw WrongPasswordException()
        val key = deriveKey(password, sealed.salt, sealed.iterations)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed.iv))
            return cipher.doFinal(sealed.ciphertext)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException(e)
        } finally {
            key.fill(0)
        }
    }

    /** PBKDF2 (RFC 8018) with HMAC-SHA256. */
    internal fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int, length: Int = KEY_BYTES): ByteArray {
        require(iterations > 0 && length > 0)
        val passwordBytes = password.toUtf8()
        try {
            val mac = Mac.getInstance(HMAC)
            mac.init(SecretKeySpec(passwordBytes, HMAC))
            val hLen = mac.macLength
            val result = ByteArray(length)
            val u = ByteArray(hLen)
            val t = ByteArray(hLen)
            var block = 1
            var offset = 0
            while (offset < length) {
                mac.update(salt)
                mac.update(ByteBuffer.allocate(4).putInt(block).array())
                mac.doFinal(u, 0)
                u.copyInto(t)
                repeat(iterations - 1) {
                    mac.update(u)
                    mac.doFinal(u, 0)
                    for (i in 0 until hLen) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
                }
                val count = minOf(hLen, length - offset)
                t.copyInto(result, offset, 0, count)
                offset += count
                block++
            }
            u.fill(0)
            t.fill(0)
            return result
        } finally {
            passwordBytes.fill(0)
        }
    }

    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decode(text: String): ByteArray = try {
        Base64.getDecoder().decode(text)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Damaged backup file", e)
    }

    private fun CharArray.toUtf8(): ByteArray {
        val buffer = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(this))
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        if (buffer.hasArray()) buffer.array().fill(0)
        return bytes
    }
}
