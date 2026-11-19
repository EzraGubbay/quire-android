package com.ezragubbay.quire.core.data.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM [SecretCipher] backed by a non-exportable Android Keystore key. Output layout is
 * `iv (12 bytes) || ciphertext+tag`; every call uses a fresh random IV.
 */
class KeystoreCipher(
    private val alias: String = DEFAULT_ALIAS,
) : SecretCipher {
    override fun encrypt(plain: ByteArray): ByteArray =
        try {
            encryptWith(getOrCreateKey(), plain)
        } catch (_: GeneralSecurityException) {
            // The key can be permanently invalidated (e.g. lock screen reset); replace it and retry once.
            deleteKey()
            encryptWith(getOrCreateKey(), plain)
        }

    override fun decrypt(blob: ByteArray): ByteArray {
        if (blob.size <= IV_BYTES) throw GeneralSecurityException("ciphertext too short")
        val key = loadKey() ?: throw GeneralSecurityException("keystore key missing")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
        return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    private fun encryptWith(
        key: SecretKey,
        plain: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun loadKey(): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    private fun deleteKey() = keyStore().deleteEntry(alias)

    private fun getOrCreateKey(): SecretKey = loadKey() ?: generateKey()

    private fun generateKey(): SecretKey {
        val spec =
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
            init(spec)
            generateKey()
        }
    }

    companion object {
        const val DEFAULT_ALIAS = "quire_session_key"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BITS = 256
        private const val TAG_BITS = 128
        private const val IV_BYTES = 12
    }
}
