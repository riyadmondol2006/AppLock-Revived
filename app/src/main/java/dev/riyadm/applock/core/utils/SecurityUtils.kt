package dev.riyadm.applock.core.utils

import android.util.Base64
import dev.riyadm.applock.core.utils.SecurityUtils.MAX_PASSWORD_LENGTH
import java.security.MessageDigest
import java.security.SecureRandom

object SecurityUtils {

    private const val HASH_ALGORITHM = "SHA-256"
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
        return hashPassword(password, generateSalt())
    }

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
}