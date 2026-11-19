package com.ezragubbay.quire.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ServerUrl {
    private val localHosts = setOf("localhost", "127.0.0.1", "10.0.2.2", "::1")

    /** HTTPS always; cleartext only for emulator/localhost hosts and only when [allowCleartextLocal] (debug). */
    fun isAllowed(
        raw: String,
        allowCleartextLocal: Boolean,
    ): Boolean = raw.toHttpUrlOrNull()?.let { isAllowed(it, allowCleartextLocal) } ?: false

    internal fun isAllowed(
        url: HttpUrl,
        allowCleartextLocal: Boolean,
    ): Boolean = url.isHttps || (allowCleartextLocal && url.host in localHosts)
}
