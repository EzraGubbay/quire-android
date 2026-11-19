package com.ezragubbay.quire.core.data.session

import java.security.GeneralSecurityException

/** Reversible, non-identity cipher for JVM tests: reverses bytes, flips them and prefixes a marker. */
class FakeCipher : SecretCipher {
    var failDecrypt = false

    override fun encrypt(plain: ByteArray): ByteArray =
        byteArrayOf(MARKER) + plain.reversedArray().map { (it.toInt() xor MASK).toByte() }

    override fun decrypt(blob: ByteArray): ByteArray {
        if (failDecrypt || blob.isEmpty() || blob[0] != MARKER) throw GeneralSecurityException("fake decrypt failure")
        return blob
            .drop(1)
            .map { (it.toInt() xor MASK).toByte() }
            .toByteArray()
            .reversedArray()
    }

    private companion object {
        const val MARKER: Byte = 0x7F
        const val MASK = 0x5A
    }
}
