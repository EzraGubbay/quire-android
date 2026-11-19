package com.ezragubbay.quire.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class NoServerConfiguredException : IOException("No Quire server configured")

class InsecureServerException(
    host: String,
) : IOException("Refusing cleartext connection to $host")

/** Rewrites the placeholder host Retrofit was built with to the runtime server URL, under [API_PREFIX]. */
internal class BaseUrlInterceptor(
    private val config: ServerConfigProvider,
    private val allowCleartextLocal: Boolean,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val base = config.serverUrl()?.toHttpUrlOrNull() ?: throw NoServerConfiguredException()
        if (!ServerUrl.isAllowed(base, allowCleartextLocal)) throw InsecureServerException(base.host)
        val url =
            request.url
                .newBuilder()
                .scheme(base.scheme)
                .host(base.host)
                .port(base.port)
                .encodedPath(base.encodedPath.trimEnd('/') + request.url.encodedPath)
                .build()
        return chain.proceed(request.newBuilder().url(url).build())
    }

    companion object {
        const val API_PREFIX = "/api/mobile/v1"
    }
}
