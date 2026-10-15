package com.unifiedmessages.app.matrix

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.Session
import org.matrix.rustcomponents.sdk.SlidingSyncVersion
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps SDK session tokens and the Rust store passphrase encrypted by Android Keystore. */
internal class MatrixSecretStore(context: Context) {
    private val preferences = context.getSharedPreferences("matrix_credentials_v1", Context.MODE_PRIVATE)

    data class Record(val session: Session, val storePassphrase: String)

    @Synchronized
    fun read(reference: String): Record? {
        val ciphertext = preferences.getString(reference, null) ?: return null
        val bytes = Base64.decode(ciphertext, Base64.NO_WRAP)
        require(bytes.size > 12) { "Saved Matrix session is damaged." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val value = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        val session = Session(
            value.getString("accessToken"),
            value.optNullable("refreshToken"),
            value.getString("userId"),
            value.getString("deviceId"),
            value.getString("homeserverUrl"),
            value.optNullable("oauthData"),
            value.optNullable("slidingSyncVersion")?.let(SlidingSyncVersion::valueOf) ?: SlidingSyncVersion.NONE,
        )
        return Record(session, value.getString("storePassphrase"))
    }

    @Synchronized
    fun write(reference: String, record: Record) {
        val session = record.session
        val value = JSONObject().apply {
            put("accessToken", session.accessToken)
            put("refreshToken", session.refreshToken)
            put("userId", session.userId)
            put("deviceId", session.deviceId)
            put("homeserverUrl", session.homeserverUrl)
            put("oauthData", session.oauthData)
            put("slidingSyncVersion", session.slidingSyncVersion.name)
            put("storePassphrase", record.storePassphrase)
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString(reference, Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) {
            "Could not save the Matrix session on this device."
        }
    }

    @Synchronized
    fun remove(reference: String) {
        check(preferences.edit().remove(reference).commit()) { "Could not remove the Matrix session." }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun JSONObject.optNullable(name: String): String? =
        if (has(name) && !isNull(name)) getString(name) else null

    companion object { private const val KEY_ALIAS = "unifiedmessages.matrix.sessions.v1" }
}
