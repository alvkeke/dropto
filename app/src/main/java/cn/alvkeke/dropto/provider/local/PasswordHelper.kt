package cn.alvkeke.dropto.provider.local

import android.util.Base64
import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHelper {

    private const val TAG = "PasswordHelper"

    private const val SEPARATOR = ":"
    private const val ITERATIONS = 100_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    fun encode(password: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = hash(password, salt, ITERATIONS)
        return listOf(
            ITERATIONS.toString(),
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(hash, Base64.NO_WRAP),
        ).joinToString(SEPARATOR)
    }

    /** An empty password matches an empty record, any other empty side fails. */
    fun matches(password: String, stored: String): Boolean {
        if (password.isEmpty() && stored.isEmpty()) {
            return true
        } else if (password.isEmpty() || stored.isEmpty()) {
            return false
        }

        val parts = stored.split(SEPARATOR)
        if (parts.size != 3) return false
        val iterations = parts[0].toIntOrNull() ?: return false
        val salt = decodeBase64(parts[1]) ?: return false
        val expected = decodeBase64(parts[2]) ?: return false

        return MessageDigest.isEqual(hash(password, salt, iterations), expected)
    }

    private fun hash(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun decodeBase64(value: String): ByteArray? = try {
        Base64.decode(value, Base64.NO_WRAP)
    } catch (e: IllegalArgumentException) {
        Log.e(TAG, "Broken password record: $e")
        null
    }
}
