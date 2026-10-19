package com.unifiedmessages.gmessages

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class EncryptedTransferTest {
    private val passphrase = "apple-bridge-candle-dragon-ember-forest-garden-harbor-island"
    private val createdAt = 1_700_000_000_000L

    private fun payload(): String {
        val salt = ByteArray(16) { it.toByte() }
        val nonce = ByteArray(12) { (it + 16).toByte() }
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 600_000, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD("UnifiedMessages-GMessages-UMGM2".toByteArray())
        }
        val plain = """{"SID":"synthetic-only","__createdAtMs":$createdAt}""".toByteArray()
        val encrypted = cipher.doFinal(plain)
        return "UMGM2:" + Base64.getUrlEncoder().withoutPadding().encodeToString(salt + nonce + encrypted)
    }

    @Test fun decryptsEncryptedTransferAndWipesPhrase() {
        val entered = passphrase.toCharArray()
        assertTrue(EncryptedTransfer.decrypt(payload(), entered, createdAt + 1000).contains("synthetic-only"))
        assertTrue(entered.all { it == '\u0000' })
    }

    @Test fun decryptsPythonHelperFixture() {
        val fixture = javaClass.getResource("/python_transfer.txt")!!.readText().trim()
        val decoded = EncryptedTransfer.decrypt(fixture, passphrase.toCharArray(), createdAt)
        assertTrue(decoded.contains("synthetic-SID"))
    }

    @Test fun rejectsWrongPassphraseWithoutDisclosingCookies() {
        val entered = "wrong-words-candle-dragon-ember-forest-garden-harbor-island".toCharArray()
        val error = runCatching {
            EncryptedTransfer.decrypt(payload(), entered, createdAt)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("Wrong passphrase"))
        assertTrue(!error.message!!.contains("synthetic-only"))
        assertTrue(entered.all { it == '\u0000' })
    }

    @Test fun rejectsCorruptedCiphertext() {
        val raw = Base64.getUrlDecoder().decode(payload().removePrefix("UMGM2:"))
        raw[raw.lastIndex] = (raw.last().toInt() xor 1).toByte()
        val damaged = "UMGM2:" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        val error = runCatching {
            EncryptedTransfer.decrypt(damaged, passphrase.toCharArray(), createdAt)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("damaged"))
    }

    @Test fun rejectsExpiredTransfer() {
        val error = runCatching {
            EncryptedTransfer.decrypt(payload(), passphrase.toCharArray(), createdAt + 5 * 60 * 1000 + 1)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("expired"))
    }
}
