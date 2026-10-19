package com.unifiedmessages.gmessages

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores only AES-GCM ciphertext; the AES key never leaves Android Keystore. */
internal class GoogleSessionStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "gmessages")
    private val file = File(directory, "session.bin")
    private val alias = "UnifiedMessages_GMessages_Debug_Session"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    fun exists(): Boolean = file.isFile

    fun write(session: String) {
        directory.mkdirs()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(session.toByteArray(Charsets.UTF_8))
        val staging = File(directory, "session.bin.tmp")
        staging.outputStream().use { stream ->
            stream.write(cipher.iv.size)
            stream.write(cipher.iv)
            stream.write(encrypted)
            stream.flush()
        }
        check(staging.renameTo(file)) { "Could not save the encrypted Google Messages session." }
    }

    fun read(): String? {
        if (!file.isFile) return null
        val bytes = file.readBytes()
        require(bytes.isNotEmpty() && bytes[0].toInt() in 12..16) { "Stored session is damaged." }
        val ivSize = bytes[0].toInt()
        require(bytes.size > 1 + ivSize + 16) { "Stored session is incomplete." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 1 + ivSize)))
        }
        return cipher.doFinal(bytes.copyOfRange(1 + ivSize, bytes.size)).toString(Charsets.UTF_8)
    }

    fun erase() {
        file.delete()
        File(directory, "session.bin.tmp").delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }
}
