package com.ezragubbay.quire.core.network

/**
 * Supplies the current device token, or null when signed out. Implemented by secure storage (#25).
 * Called on OkHttp's IO threads for every request, so it must be fast and non-suspending (cache in memory).
 */
fun interface TokenProvider {
    fun token(): String?
}

/**
 * Supplies the server root URL the user paired with (for example `https://quire.example.com`), or null when
 * none is configured yet. Read per request so pairing can set it at runtime.
 */
fun interface ServerConfigProvider {
    fun serverUrl(): String?
}
