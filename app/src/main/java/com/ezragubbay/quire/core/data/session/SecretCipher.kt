package com.ezragubbay.quire.core.data.session

import java.security.GeneralSecurityException

/**
 * Symmetric authenticated encryption for small secrets. [decrypt] must throw [GeneralSecurityException]
 * for anything it cannot authenticate (tampered, truncated, wrong or lost key); callers treat that as "no data".
 */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray

    @Throws(GeneralSecurityException::class)
    fun decrypt(blob: ByteArray): ByteArray
}
