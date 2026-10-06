package com.shilapi.xcertplay

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Synology endpoint settings; the DSM password is encrypted with an Android Keystore key. */
internal object SynologyLogSettings {
    private const val PREFS = "xcertplay_synology"
    private const val KEY_QC_ID = "quickconnect_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password_encrypted"
    private const val KEY_FOLDER = "destination_folder"
    private const val KEY_OVERRIDE_URL = "override_url"
    private const val KEY_ALIAS = "xcertplay_synology_password"

    fun load(context: Context): Config {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(KEY_PASSWORD, null)
        return Config(
            quickConnectId = prefs.getString(KEY_QC_ID, "").orEmpty(),
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
            password = encrypted?.let { runCatching { decrypt(it) }.getOrElse {
                prefs.edit().remove(KEY_PASSWORD).apply()
                ""
            } }.orEmpty(),
            destinationFolder = prefs.getString(KEY_FOLDER, "/docker/navitool-dashboard/data/logs").orEmpty(),
            overrideUrl = prefs.getString(KEY_OVERRIDE_URL, "").orEmpty(),
        )
    }

    fun save(context: Context, config: Config) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putString(KEY_QC_ID, config.quickConnectId.trim())
            .putString(KEY_USERNAME, config.username.trim())
            .putString(KEY_FOLDER, config.destinationFolder.trim().ifBlank { "/docker/navitool-dashboard/data/logs" })
            .putString(KEY_OVERRIDE_URL, config.overrideUrl.trim())
        if (config.password.isBlank()) {
            editor.remove(KEY_PASSWORD)
        } else {
            editor.putString(KEY_PASSWORD, encrypt(config.password))
        }
        editor.apply()
    }

    data class Config(
        val quickConnectId: String,
        val username: String,
        val password: String,
        val destinationFolder: String,
        val overrideUrl: String,
    )

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(ByteBuffer.allocate(4 + iv.size + encrypted.size)
            .putInt(iv.size).put(iv).put(encrypted).array(), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val payload = ByteBuffer.wrap(Base64.decode(value, Base64.NO_WRAP))
        val ivLength = payload.int
        require(ivLength in 12..16 && payload.remaining() > ivLength)
        val iv = ByteArray(ivLength).also(payload::get)
        val encrypted = ByteArray(payload.remaining()).also(payload::get)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
