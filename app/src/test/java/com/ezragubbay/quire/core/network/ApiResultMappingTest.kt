package com.ezragubbay.quire.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiResultMappingTest {
    private lateinit var server: MockWebServer
    private val tokens = FakeTokenProvider("t")
    private val config = FakeServerConfig()
    private lateinit var client: QuireClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        config.url = server.url("/").toString()
        client = QuireClientFactory.create(config, tokens, SessionEvents(), NetworkOptions(allowCleartextLocal = true))
    }

    @After
    fun tearDown() = server.shutdown()

    private inline fun <reified F : ApiResult.Failure> assertMaps(
        fixture: String,
        code: Int,
    ): F {
        server.enqueue(fixtureResponse(fixture, code))
        val result = runBlocking { client.me() }
        assertTrue("expected ${F::class.simpleName} but was $result", result is F)
        return result as F
    }

    @Test
    fun successDecodesBody() {
        server.enqueue(fixtureResponse("getMe.200.json"))
        val result = runBlocking { client.me() } as ApiResult.Success
        assertEquals("local", result.value.login)
        assertEquals("Pixel 9", result.value.device.name)
    }

    @Test
    fun pairSuccessDecodesTokenAndPostsRequestBody() {
        server.enqueue(fixtureResponse("pairDevice.200.json"))
        val result = runBlocking { client.pair("AbC12345", "Pixel 9") } as ApiResult.Success
        assertEquals("qd_live_3f9a8c7e6d5b4a3928171605f4e3d2c1", result.value.token)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/mobile/v1/auth/pair", req.path)
        assertTrue(req.body.readUtf8().let { "\"code\":\"AbC12345\"" in it && "\"deviceName\":\"Pixel 9\"" in it })
    }

    @Test
    fun revokeTokenHandles204() {
        server.enqueue(MockResponse().setResponseCode(204))
        val result = runBlocking { client.revokeToken() }
        assertTrue(result is ApiResult.Success)
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/api/mobile/v1/auth/token", req.path)
    }

    @Test
    fun unauthorized() {
        assertEquals(
            "Missing or invalid bearer token",
            assertMaps<ApiResult.Failure.Unauthorized>("error.unauthorized.json", 401).message,
        )
    }

    @Test
    fun notFound() {
        assertMaps<ApiResult.Failure.NotFound>("error.not_found.json", 404)
    }

    @Test
    fun invalidRequest() {
        assertEquals(
            "code: Invalid pairing code",
            assertMaps<ApiResult.Failure.InvalidRequest>("error.invalid_request.json", 400).message,
        )
    }

    @Test
    fun rateLimited() {
        assertMaps<ApiResult.Failure.RateLimited>("error.rate_limited.json", 429)
    }

    @Test
    fun aiUnavailable() {
        assertMaps<ApiResult.Failure.AiUnavailable>("error.ai_unavailable.json", 503)
    }

    @Test
    fun internalIsServer() {
        assertEquals(500, assertMaps<ApiResult.Failure.Server>("error.internal.json", 500).httpCode)
    }

    @Test
    fun envelopeCodeWinsOverHttpStatus() {
        assertMaps<ApiResult.Failure.NotFound>("error.not_found.json", 400)
    }

    @Test
    fun unknownEnvelopeCodeIsUnknown() {
        server.enqueue(MockResponse().setResponseCode(418).setBody("""{"error":{"code":"teapot","message":"short"}}"""))
        val result = runBlocking { client.me() } as ApiResult.Failure.Unknown
        assertEquals(418, result.httpCode)
        assertEquals("short", result.message)
    }

    @Test
    fun nonEnvelopeErrorBodyFallsBackToHttpStatus() {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>bad gateway</html>"))
        assertTrue(runBlocking { client.me() } is ApiResult.Failure.Server)
        server.enqueue(MockResponse().setResponseCode(401))
        assertTrue(runBlocking { client.me() } is ApiResult.Failure.Unauthorized)
        server.enqueue(MockResponse().setResponseCode(429))
        assertTrue(runBlocking { client.me() } is ApiResult.Failure.RateLimited)
    }

    @Test
    fun malformedSuccessBodyIsUnknown() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{not json"))
        assertTrue(runBlocking { client.me() } is ApiResult.Failure.Unknown)
    }

    @Test
    fun wrongShapeSuccessBodyIsUnknown() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"hello":"world"}"""))
        assertTrue(runBlocking { client.me() } is ApiResult.Failure.Unknown)
    }

    @Test
    fun timeoutIsNetwork() {
        val fast =
            QuireClientFactory.create(
                config,
                tokens,
                SessionEvents(),
                NetworkOptions(allowCleartextLocal = true, timeoutMillis = 200),
            )
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertTrue(runBlocking { fast.me() } is ApiResult.Failure.Network)
    }

    @Test
    fun connectionDropIsNetwork() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertTrue(runBlocking { client.me() } is ApiResult.Failure.Network)
    }
}
