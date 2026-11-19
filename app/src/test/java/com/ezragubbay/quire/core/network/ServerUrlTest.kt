package com.ezragubbay.quire.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlTest {
    @Test
    fun https_isAlwaysAllowed() {
        assertTrue(ServerUrl.isAllowed("https://quire.example.com", allowCleartextLocal = false))
    }

    @Test
    fun cleartextLocalHosts_allowedOnlyWhenEnabled() {
        listOf("http://localhost:8080", "http://127.0.0.1", "http://10.0.2.2:3000").forEach {
            assertTrue(it, ServerUrl.isAllowed(it, allowCleartextLocal = true))
            assertFalse(it, ServerUrl.isAllowed(it, allowCleartextLocal = false))
        }
    }

    @Test
    fun ipv6Loopback_isTreatedAsLocal() {
        // OkHttp reports the host without brackets, so the allow-list must hold "::1".
        assertTrue(ServerUrl.isAllowed("http://[::1]:8080", allowCleartextLocal = true))
        assertFalse(ServerUrl.isAllowed("http://[::1]:8080", allowCleartextLocal = false))
    }

    @Test
    fun cleartextRemote_andGarbage_areRejected() {
        assertFalse(ServerUrl.isAllowed("http://quire.example.com", allowCleartextLocal = true))
        assertFalse(ServerUrl.isAllowed("https://", allowCleartextLocal = true))
        assertFalse(ServerUrl.isAllowed("not a url", allowCleartextLocal = true))
    }
}
