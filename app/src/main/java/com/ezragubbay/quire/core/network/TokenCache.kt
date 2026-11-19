package com.ezragubbay.quire.core.network

/**
 * In-memory cache in front of a [TokenProvider] whose reads are expensive (secure storage does a Keystore
 * decrypt per call). A non-null token is fetched once and reused; call [invalidate] on sign-out or re-pair.
 * The auth interceptor also invalidates it on a 401. A null (signed-out) result is never cached.
 */
class TokenCache(
    private val delegate: TokenProvider,
) : TokenProvider {
    @Volatile
    private var cached: String? = null

    override fun token(): String? = cached ?: delegate.token().also { cached = it }

    fun invalidate() {
        cached = null
    }
}
