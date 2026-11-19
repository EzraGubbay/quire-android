package com.ezragubbay.quire.core.data.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class KeystoreCipherTest {
    private val alias = "quire_test_session_key"
    private val cipher = KeystoreCipher(alias)

    @After
    fun tearDown() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    @Test
    fun roundTrip_returnsOriginalBytes() {
        val plain = "device-token-abc123".toByteArray()
        assertArrayEquals(plain, cipher.decrypt(cipher.encrypt(plain)))
    }

    @Test
    fun ciphertext_doesNotContainPlaintext_andIsRandomized() {
        val plain = "device-token-abc123".toByteArray()
        val a = cipher.encrypt(plain)
        val b = cipher.encrypt(plain)
        assertFalse(String(a, Charsets.ISO_8859_1).contains("device-token"))
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun key_isNonExportable() {
        cipher.encrypt(byteArrayOf(1))
        val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null)
        assertTrue(key.encoded == null)
    }

    @Test(expected = GeneralSecurityException::class)
    fun tamperedCiphertext_isRejected() {
        val blob = cipher.encrypt("x".toByteArray())
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 1).toByte()
        cipher.decrypt(blob)
    }

    @Test(expected = GeneralSecurityException::class)
    fun truncatedCiphertext_isRejected() {
        cipher.decrypt(byteArrayOf(1, 2, 3))
    }
}
