package com.ezragubbay.quire.core.network

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {
    private lateinit var server: MockWebServer
    private val tokens = FakeTokenProvider()
    private val config = FakeServerConfig()
    private val events = SessionEvents()
    private lateinit var client: QuireClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        config.url = server.url("/").toString()
        client = QuireClientFactory.create(config, tokens, events, NetworkOptions(allowCleartextLocal = true))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun attachesBearerTokenWhenSessionExists() =
        runBlocking {
            tokens.value = "qd_live_abc"
            server.enqueue(fixtureResponse("getMe.200.json"))
            client.me()
            assertEquals("Bearer qd_live_abc", server.takeRequest().getHeader("Authorization"))
        }

    @Test
    fun omitsAuthorizationWhenNoSession() =
        runBlocking {
            server.enqueue(fixtureResponse("pairDevice.200.json"))
            client.pair("AbC12345", "Pixel 9")
            assertNull(server.takeRequest().getHeader("Authorization"))
        }

    @Test
    fun injectsBaseUrlAtRuntimeUnderMobileApiPrefix() =
        runBlocking {
            server.enqueue(fixtureResponse("getMe.200.json"))
            client.me()
            assertEquals("/api/mobile/v1/me", server.takeRequest().path)
        }

    @Test
    fun baseUrlChangesAreHonoredWithoutRebuildingClient() =
        runBlocking {
            val other = MockWebServer().also { it.start() }
            try {
                config.url = other.url("/").toString()
                other.enqueue(fixtureResponse("getMe.200.json"))
                client.me()
                assertEquals(1, other.requestCount)
                assertEquals(0, server.requestCount)
            } finally {
                other.shutdown()
            }
        }

    @Test
    fun noServerConfiguredIsNetworkFailure() =
        runBlocking {
            config.url = null
            val result = client.me()
            assertTrue(result is ApiResult.Failure.Network)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun cleartextToNonLocalHostIsRejected() =
        runBlocking {
            config.url = "http://example.com"
            assertTrue(client.me() is ApiResult.Failure.Network)
        }

    @Test
    fun cleartextAllowedOnlyForLocalHostsWhenEnabled() {
        assertTrue(ServerUrl.isAllowed("https://quire.example.com", allowCleartextLocal = false))
        assertTrue(ServerUrl.isAllowed("http://10.0.2.2:3000", allowCleartextLocal = true))
        assertTrue(ServerUrl.isAllowed("http://localhost:3000", allowCleartextLocal = true))
        assertTrue(!ServerUrl.isAllowed("http://10.0.2.2:3000", allowCleartextLocal = false))
        assertTrue(!ServerUrl.isAllowed("http://example.com", allowCleartextLocal = true))
        assertTrue(!ServerUrl.isAllowed("not a url", allowCleartextLocal = true))
    }

    private fun expiredAfter(block: suspend () -> Unit): Int =
        runBlocking {
            val seen = mutableListOf<SessionExpired>()
            val job = launch(start = CoroutineStart.UNDISPATCHED) { events.expired.collect { seen += it } }
            block()
            yield()
            job.cancel()
            seen.size
        }

    @Test
    fun unauthorizedOnAuthenticatedCallEmitsSessionExpired() {
        tokens.value = "t"
        server.enqueue(fixtureResponse("error.unauthorized.json", code = 401))
        var result: ApiResult<*>? = null
        assertEquals(1, expiredAfter { result = client.me() })
        assertTrue(result is ApiResult.Failure.Unauthorized)
    }

    @Test
    fun unauthorizedWithoutSessionDoesNotEmitSessionExpired() {
        server.enqueue(fixtureResponse("error.unauthorized.json", code = 401))
        assertEquals(0, expiredAfter { client.pair("x", "y") })
    }

    @Test
    fun successDoesNotEmitSessionExpired() {
        tokens.value = "t"
        server.enqueue(fixtureResponse("getMe.200.json"))
        assertEquals(0, expiredAfter { client.me() })
    }
}
