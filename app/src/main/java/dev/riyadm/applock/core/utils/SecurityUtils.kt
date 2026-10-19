package dev.riyadm.applock.core.utils

import android.util.Base64
import dev.riyadm.applock.core.utils.SecurityUtils.MAX_PASSWORD_LENGTH
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object SecurityUtils {

    private const val HASH_ALGORITHM = "SHA-256"
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KDF_PREFIX = "pbkdf2"
    private const val KDF_ITERATIONS = 60_000
    private const val KDF_KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH = 16
    const val MAX_PASSWORD_LENGTH = 64

    /**
     * Sanitizes the input string by filtering out control characters and null bytes.
     * Also limits the length to [MAX_PASSWORD_LENGTH].
     */
    fun sanitizePassword(input: String): String {
        return input
            .filter { it.code >= 32 && it.code != 127 }
            .take(MAX_PASSWORD_LENGTH)
    }

    /**
     * Generates a random cryptographic salt.
     */
    fun generateSalt(): ByteArray {
        return ByteArray(SALT_LENGTH).also { salt ->
            SecureRandom().nextBytes(salt)
        }
    }

    fun hashPassword(password: String): String {
        return hashPasswordPbkdf2(password, generateSalt(), KDF_ITERATIONS)
    }

    /**
     * Hashes the password with PBKDF2-HMAC-SHA256.
     * Format: pbkdf2:iterations:salt:hash (salt and hash are Base64)
     */
    fun hashPasswordPbkdf2(password: String, salt: ByteArray, iterations: Int): String {
        val hash = pbkdf2(sanitizePassword(password), salt, iterations)
        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP)
        return "$KDF_PREFIX:$iterations:$saltBase64:$hashBase64"
    }

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KDF_KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun isPbkdf2Hash(value: String): Boolean {
        val parts = value.split(":")
        return parts.size == 4 && parts[0] == KDF_PREFIX && parts[1].toIntOrNull() != null
    }

    /** True if the stored hash should be re-created with the current KDF parameters. */
    fun needsRehash(value: String): Boolean {
        if (!isPbkdf2Hash(value)) return true
        return value.split(":")[1].toInt() < KDF_ITERATIONS
    }

    /** True for any hash format produced by this class (legacy SHA-256 or PBKDF2). */
    fun isHashed(value: String): Boolean = isPbkdf2Hash(value) || isSaltedHash(value)

    /**
     * Hashes the password using SHA-256 with the provided salt.
     * Returns a Base64 encoded string containing the salt and the hash.
     * Format: salt:hash
     */
    fun hashPassword(password: String, salt: ByteArray): String {
        val sanitizedPassword = sanitizePassword(password)

        val md = MessageDigest.getInstance(HASH_ALGORITHM)
        md.update(salt)
        val hash = md.digest(sanitizedPassword.toByteArray(Charsets.UTF_8))

        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP)

        return "$saltBase64:$hashBase64"
    }

    fun isSaltedHash(value: String): Boolean {
        val parts = value.split(":")
        if (parts.size != 2) return false

        return try {
            Base64.decode(parts[0], Base64.NO_WRAP)
            Base64.decode(parts[1], Base64.NO_WRAP)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Verifies an input password against a stored salted hash string.
     */
    fun verifyPassword(inputPassword: String, storedSaltedHash: String): Boolean {
        if (isPbkdf2Hash(storedSaltedHash)) return verifyPbkdf2(inputPassword, storedSaltedHash)
        return try {
            val parts = storedSaltedHash.split(":")
            if (parts.size != 2) return false

            val salt = Base64.decode(parts[0], Base64.NO_WRAP)
            val expectedHash = Base64.decode(parts[1], Base64.NO_WRAP)

            val sanitizedInput = sanitizePassword(inputPassword)

            val md = MessageDigest.getInstance(HASH_ALGORITHM)
            md.update(salt)
            val actualHash = md.digest(sanitizedInput.toByteArray(Charsets.UTF_8))

            MessageDigest.isEqual(actualHash, expectedHash)
        } catch (_: Exception) {
            false
        }
    }

    private fun verifyPbkdf2(inputPassword: String, stored: String): Boolean {
        return try {
            val parts = stored.split(":")
            val iterations = parts[1].toInt()
            val salt = Base64.decode(parts[2], Base64.NO_WRAP)
            val expectedHash = Base64.decode(parts[3], Base64.NO_WRAP)
            val actualHash = pbkdf2(sanitizePassword(inputPassword), salt, iterations)
            MessageDigest.isEqual(actualHash, expectedHash)
        } catch (_: Exception) {
            false
        }
    }
}
