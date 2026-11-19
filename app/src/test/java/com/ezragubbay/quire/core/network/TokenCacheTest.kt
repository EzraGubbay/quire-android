package com.ezragubbay.quire.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TokenCacheTest {
    private lateinit var server: MockWebServer
    private val config = FakeServerConfig()
    private var reads = 0
    private var current: String? = "tok1"
    private val cache = TokenCache { reads++.let { current } }
    private lateinit var client: QuireClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        config.url = server.url("/").toString()
        client = QuireClientFactory.create(config, cache, SessionEvents(), NetworkOptions(allowCleartextLocal = true))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun providerReadOnceAcrossRequests() =
        runBlocking {
            repeat(3) { server.enqueue(fixtureResponse("getMe.200.json")) }
            repeat(3) { client.me() }
            assertEquals(1, reads)
        }

    @Test
    fun invalidateRefetchesFromProvider() =
        runBlocking {
            repeat(2) { server.enqueue(fixtureResponse("getMe.200.json")) }
            client.me()
            current = "tok2"
            cache.invalidate()
            client.me()
            server.takeRequest()
            assertEquals("Bearer tok2", server.takeRequest().getHeader("Authorization"))
            assertEquals(2, reads)
        }

    @Test
    fun unauthorizedInvalidatesCache() =
        runBlocking {
            server.enqueue(fixtureResponse("error.unauthorized.json", code = 401))
            server.enqueue(fixtureResponse("getMe.200.json"))
            client.me()
            current = "tok2"
            client.me()
            server.takeRequest()
            assertEquals("Bearer tok2", server.takeRequest().getHeader("Authorization"))
        }

    @Test
    fun nullIsNotCached() =
        runBlocking {
            current = null
            server.enqueue(fixtureResponse("getMe.200.json"))
            client.me()
            current = "tok2"
            server.enqueue(fixtureResponse("getMe.200.json"))
            client.me()
            server.takeRequest()
            assertEquals("Bearer tok2", server.takeRequest().getHeader("Authorization"))
        }
}
