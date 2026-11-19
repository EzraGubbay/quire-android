package com.ezragubbay.quire.core.network

import okhttp3.Interceptor
import okhttp3.Response

/** Adds `Authorization: Bearer <token>` when a session exists; a 401 on such a call signals [SessionExpired]. */
class AuthInterceptor(
    private val tokens: TokenCache,
    private val events: SessionEvents,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokens.token()
        val request =
            if (token == null) {
                chain.request()
            } else {
                chain
                    .request()
                    .newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            }
        val response = chain.proceed(request)
        if (token != null && response.code == HTTP_UNAUTHORIZED) {
            tokens.invalidate()
            events.emitExpired()
        }
        return response
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
