package com.unifiedmessages.gmessages

import org.json.JSONObject
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Five-minute PBKDF2-HMAC-SHA256 plus AES-256-GCM envelope. */
internal object EncryptedTransfer {
    private const val PREFIX = "UMGM2:"
    private val AAD = "UnifiedMessages-GMessages-UMGM2".toByteArray(Charsets.US_ASCII)
    private const val PBKDF2_ITERATIONS = 600_000
    private const val MAX_AGE_MS = 5 * 60 * 1000L
    private const val MAX_CLOCK_SKEW_MS = 2 * 60 * 1000L
    private const val MAX_TEXT_LENGTH = 100_000

    fun decrypt(text: String, passphrase: CharArray, nowMs: Long = System.currentTimeMillis()): String {
        var keyBytes: ByteArray? = null
        var plainBytes: ByteArray? = null
        try {
            require(text.startsWith(PREFIX) && text.length <= MAX_TEXT_LENGTH) {
                "Paste the encrypted transfer from the desktop helper."
            }
            require(passphrase.count { it == '-' } == 8) { "Enter the nine-word phrase shown on your PC." }
            val bytes = try {
                Base64.getUrlDecoder().decode(text.substring(PREFIX.length))
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Encrypted transfer is damaged.")
            }
            require(bytes.size > 16 + 12 + 16 && bytes.size <= 75_000) {
                "Encrypted transfer is damaged or too large."
            }
            val salt = bytes.copyOfRange(0, 16)
            val nonce = bytes.copyOfRange(16, 28)
            val keySpec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, 256)
            try {
                keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(keySpec).encoded
            } finally {
                keySpec.clearPassword()
            }
            val decrypted = try {
                Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes!!, "AES"), GCMParameterSpec(128, nonce))
                    updateAAD(AAD)
                }.doFinal(bytes, 28, bytes.size - 28)
            } catch (_: java.security.GeneralSecurityException) {
                throw IllegalArgumentException("Wrong passphrase or damaged encrypted transfer.")
            }
            plainBytes = decrypted
            val json = decrypted.toString(Charsets.UTF_8)
            val data = JSONObject(json)
            val createdAt = data.optLong("__createdAtMs", -1)
            require(createdAt > 0 && nowMs - createdAt in -MAX_CLOCK_SKEW_MS..MAX_AGE_MS) {
                "Transfer expired. Run the desktop helper again."
            }
            return json
        } finally {
            passphrase.fill('\u0000')
            keyBytes?.fill(0)
            plainBytes?.fill(0)
        }
    }
}
