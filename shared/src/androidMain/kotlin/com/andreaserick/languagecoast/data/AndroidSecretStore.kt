package com.andreaserick.languagecoast.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores API keys and tokens encrypted with an AES-256-GCM key held in the Android KeyStore.
 * Ciphertext is written to a private SharedPreferences file excluded from Auto Backup and device transfer.
 */
class AndroidSecretStore(context: Context) : SecretStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cache = HashMap<String, String?>()
    private val _version = MutableStateFlow(0L)
    override val version: StateFlow<Long> = _version.asStateFlow()

    @Synchronized
    override fun get(key: String): String? {
        if (cache.containsKey(key)) return cache[key]
        val encoded = prefs.getString(key, null) ?: run {
            cache[key] = null
            return null
        }
        val decrypted = runCatching { decrypt(encoded) }
            .onFailure { logError(TAG, "Stored secret '$key' could not be decrypted; clearing entry", it) }
            .getOrNull()
        if (decrypted == null) {
            prefs.edit().remove(key).apply()
        }
        cache[key] = decrypted
        return decrypted
    }

    @Synchronized
    override fun put(key: String, value: String) {
        val encrypted = runCatching { encrypt(value) }
            .onFailure { logError(TAG, "Secret '$key' could not be encrypted", it) }
            .getOrNull() ?: return
        prefs.edit().putString(key, encrypted).apply()
        cache[key] = value
        _version.update { it + 1L }
    }

    @Synchronized
    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
        cache[key] = null
        _version.update { it + 1L }
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(1 + iv.size + ciphertext.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(ciphertext, 0, combined, 1 + iv.size, ciphertext.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String? {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        if (combined.size < 2) return null
        val ivLength = combined[0].toInt() and 0xFF
        if (ivLength < 12 || combined.size <= 1 + ivLength) return null
        val iv = combined.copyOfRange(1, 1 + ivLength)
        val ciphertext = combined.copyOfRange(1 + ivLength, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        internal const val PREFS_NAME = "languagecoast_secrets"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "languagecoast_api_secrets_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val TAG = "AndroidSecretStore"
    }
}
