package com.shilapi.xcertplay

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Synology endpoint settings; the DSM password is encrypted with Android Keystore key
 * or a device-bound fallback key for automotive head units without lock-screen Keystore.
 */
internal object SynologyLogSettings {
    private const val TAG = "SynologyLogSettings"
    private const val PREFS = "xcertplay_synology"
    private const val KEY_QC_ID = "quickconnect_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password_encrypted"
    private const val KEY_FOLDER = "destination_folder"
    private const val KEY_OVERRIDE_URL = "override_url"
    private const val KEY_ALIAS = "xcertplay_synology_password"

    private const val PREFIX_KEYSTORE = "ks:"
    private const val PREFIX_DEVICE_BOUND = "db:"

    fun load(context: Context): Config {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(KEY_PASSWORD, null)
        val decryptedPassword = if (!encrypted.isNullOrBlank()) {
            runCatching { decrypt(context, encrypted) }.getOrElse { error ->
                Log.w(TAG, "Failed to decrypt saved password, retaining encrypted payload", error)
                ""
            }
        } else {
            ""
        }
        return Config(
            quickConnectId = prefs.getString(KEY_QC_ID, "").orEmpty(),
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
            password = decryptedPassword,
            destinationFolder = prefs.getString(KEY_FOLDER, "/docker/navitool-dashboard/data/logs").orEmpty().ifBlank { "/docker/navitool-dashboard/data/logs" },
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
            val encrypted = runCatching { encrypt(context, config.password) }.getOrElse { error ->
                Log.e(TAG, "Failed to encrypt password with primary key; using device fallback", error)
                encryptDeviceBound(context, config.password)
            }
            editor.putString(KEY_PASSWORD, encrypted)
        }
        editor.apply()
    }

    data class Config(
        val quickConnectId: String = "",
        val username: String = "",
        val password: String = "",
        val destinationFolder: String = "/docker/navitool-dashboard/data/logs",
        val overrideUrl: String = "",
    )

    private fun encrypt(context: Context, value: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            val packed = ByteBuffer.allocate(4 + iv.size + encrypted.size)
                .putInt(iv.size).put(iv).put(encrypted).array()
            PREFIX_KEYSTORE + Base64.encodeToString(packed, Base64.NO_WRAP)
        } catch (t: Throwable) {
            Log.w(TAG, "AndroidKeyStore encryption unavailable on this head unit, falling back to device-bound AES: ${t.message}")
            encryptDeviceBound(context, value)
        }
    }

    private fun decrypt(context: Context, value: String): String {
        return when {
            value.startsWith(PREFIX_KEYSTORE) -> {
                val raw = value.removePrefix(PREFIX_KEYSTORE)
                decryptKeyStore(raw)
            }
            value.startsWith(PREFIX_DEVICE_BOUND) -> {
                val raw = value.removePrefix(PREFIX_DEVICE_BOUND)
                decryptDeviceBound(context, raw)
            }
            else -> {
                // Legacy payload without prefix: try KeyStore first, then device bound
                runCatching { decryptKeyStore(value) }.getOrElse {
                    decryptDeviceBound(context, value)
                }
            }
        }
    }

    private fun decryptKeyStore(rawBase64: String): String {
        val payload = ByteBuffer.wrap(Base64.decode(rawBase64, Base64.NO_WRAP))
        val ivLength = payload.int
        require(ivLength in 12..16 && payload.remaining() > ivLength)
        val iv = ByteArray(ivLength).also(payload::get)
        val encrypted = ByteArray(payload.remaining()).also(payload::get)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(StandardCharsets.UTF_8)
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

    private fun getDeviceBoundKey(context: Context): SecretKey {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val seed = "${context.packageName}|${Build.FINGERPRINT}|$androidId|xcertplay-synology-salt"
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    private fun encryptDeviceBound(context: Context, value: String): String {
        val key = getDeviceBoundKey(context)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val packed = ByteBuffer.allocate(4 + iv.size + encrypted.size)
            .putInt(iv.size).put(iv).put(encrypted).array()
        return PREFIX_DEVICE_BOUND + Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decryptDeviceBound(context: Context, rawBase64: String): String {
        val key = getDeviceBoundKey(context)
        val payload = ByteBuffer.wrap(Base64.decode(rawBase64, Base64.NO_WRAP))
        val ivLength = payload.int
        require(ivLength in 12..16 && payload.remaining() > ivLength)
        val iv = ByteArray(ivLength).also(payload::get)
        val encrypted = ByteArray(payload.remaining()).also(payload::get)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(StandardCharsets.UTF_8)
    }
}
