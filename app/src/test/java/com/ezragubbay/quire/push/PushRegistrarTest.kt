package com.ezragubbay.quire.push

import com.ezragubbay.quire.core.network.FakeServerConfig
import com.ezragubbay.quire.core.network.FakeTokenProvider
import com.ezragubbay.quire.core.network.NetworkOptions
import com.ezragubbay.quire.core.network.QuireClientFactory
import com.ezragubbay.quire.core.network.QuireJson
import com.ezragubbay.quire.core.network.SessionEvents
import com.ezragubbay.quire.core.network.fixture
import com.ezragubbay.quire.core.network.fixtureResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class PushRegistrarTest {
    @get:Rule
    val logs = PushLogRule()

    private lateinit var server: MockWebServer
    private lateinit var client: PushClient
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val retry = FakeRegistrationScheduler()
    private var signedIn = true
    private var allowed = true

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        client =
            PushClient(
                pushRestApi(
                    QuireClientFactory.okHttpClient(
                        FakeServerConfig(server.url("/").toString()),
                        FakeTokenProvider("device-token"),
                        SessionEvents(),
                        NetworkOptions(allowCleartextLocal = true),
                    ),
                ),
            )
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun registrar(tokens: FcmTokenSource = FakeTokenSource()) =
        PushRegistrar(
            tokens = tokens,
            client = client,
            device = PushDevice(appVersion = "1.1.0") { signedIn },
            categories = PushCategoryPolicy({ allowed }),
            retry = retry,
            scope = scope,
        )

    private fun take(): RecordedRequest = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "no request" }

    private fun RecordedRequest.json() = Json.parseToJsonElement(body.readUtf8()).jsonObject

    @Test
    fun sync_putsTokenPlatformVersionAndAllCategories_matchingTheContractFixture() =
        runBlocking {
            server.enqueue(fixtureResponse("putPushRegistration.200.json"))
            val tokens = FakeTokenSource(tokens = listOf("<fcm registration token>"))

            assertEquals(RegistrationOutcome.Registered, registrar(tokens).sync())

            val request = take()
            assertEquals("PUT", request.method)
            assertEquals("/api/mobile/v1/push/registration", request.path)
            assertEquals("Bearer device-token", request.getHeader("Authorization"))
            assertEquals(
                Json.parseToJsonElement(fixture("putPushRegistration.request.json")),
                Json.parseToJsonElement(request.body.readUtf8()),
            )
            // Auto-init goes on before the token is fetched.
            assertEquals(listOf("autoInit=true", "token"), tokens.calls)
        }

    @Test
    fun sync_sendsThePutEveryTime_noFingerprintSkip() =
        runBlocking {
            repeat(3) { server.enqueue(fixtureResponse("putPushRegistration.200.json")) }
            val registrar = registrar()

            repeat(3) { assertEquals(RegistrationOutcome.Registered, registrar.sync()) }

            repeat(3) { assertEquals("PUT", take().method) }
        }

    @Test
    fun onAppStart_putsOncePerProcess() =
        runBlocking {
            server.enqueue(fixtureResponse("putPushRegistration.200.json"))
            val registrar = registrar()

            registrar.onAppStart()
            registrar.onAppStart()

            assertEquals("PUT", take().method)
            assertNull(server.takeRequest(300, TimeUnit.MILLISECONDS))
        }

    @Test
    fun notConfigured_isANoOp() =
        runBlocking {
            val registrar = registrar(NoopTokenSource)

            assertEquals(RegistrationOutcome.Skipped, registrar.sync())
            registrar.unregisterRemote()
            registrar.clearLocal()
            registrar.onSignedOut()
            assertEquals(TestPushResult.NotConfigured, registrar.sendTest())

            assertEquals(0, server.requestCount)
            assertEquals(0, retry.enqueued)
        }

    @Test
    fun signedOut_skipsWithoutTouchingTheToken() =
        runBlocking {
            signedIn = false
            val tokens = FakeTokenSource()

            assertEquals(RegistrationOutcome.Skipped, registrar(tokens).sync())

            assertTrue(tokens.calls.isEmpty())
            assertEquals(0, server.requestCount)
        }

    @Test
    fun notificationsDenied_registersWithNoCategories() =
        runBlocking {
            allowed = false
            server.enqueue(fixtureResponse("putPushRegistration.200.json"))

            registrar().sync()

            assertEquals(JsonArray(emptyList()), take().json()["categories"])
        }

    @Test
    fun transientFailure_isRetried_permanentIsNot() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503))
            assertEquals(RegistrationOutcome.Retry, registrar().sync())

            server.enqueue(fixtureResponse("error.invalid_request.json", code = 400))
            assertEquals(RegistrationOutcome.Failed, registrar().sync())

            server.enqueue(fixtureResponse("error.unauthorized.json", code = 401))
            assertEquals(RegistrationOutcome.Unauthorized, registrar().sync())
        }

    @Test
    fun requestSync_handsATransientFailureToTheWorker() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(500))

            registrar().requestSync()
            take()

            val deadline = System.currentTimeMillis() + 5_000
            while (retry.enqueued == 0 && System.currentTimeMillis() < deadline) Thread.sleep(10)
            assertEquals(1, retry.enqueued)
        }

    @Test
    fun tokenUnavailable_isRetried_andNeverLogsTheMessage() =
        runBlocking {
            val tokens = FakeTokenSource().apply { failToken = true }

            assertEquals(RegistrationOutcome.Retry, registrar(tokens).sync())

            assertEquals(0, server.requestCount)
            assertTrue(logs.lines.isNotEmpty())
            assertTrue(logs.lines.none { it.contains("SERVICE_NOT_AVAILABLE") })
        }

    @Test
    fun requestBody_neverPrintsTheToken() {
        val body = PushRegistrationRequest("secret-fcm-token", "android", "1.1.0", listOf("files"))
        assertFalse(body.toString().contains("secret-fcm-token"))
    }

    @Test
    fun unregisterRemote_deletesTheRegistration() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(204))

            registrar().unregisterRemote()

            val request = take()
            assertEquals("DELETE", request.method)
            assertEquals("/api/mobile/v1/push/registration", request.path)
        }

    @Test
    fun clearLocal_deletesTheTokenThenDisablesAutoInit() =
        runBlocking {
            val tokens = FakeTokenSource()

            registrar(tokens).clearLocal()

            assertEquals(listOf("delete", "autoInit=false"), tokens.calls)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun onSignedOut_cancelsRetries_andClearsLocally() {
        val tokens = FakeTokenSource()

        registrar(tokens).onSignedOut()

        val deadline = System.currentTimeMillis() + 5_000
        while (tokens.autoInit != false && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertEquals(listOf("delete", "autoInit=false"), tokens.calls)
        assertEquals(1, retry.cancelled)
    }

    @Test
    fun sendTest_sent() =
        runBlocking {
            server.enqueue(fixtureResponse("sendTestPush.200.json"))

            assertEquals(TestPushResult.Sent("7a1c2f0e-5b7d-4c2a-9a51-0f3e6d1b2c44"), registrar().sendTest())

            val request = take()
            assertEquals("POST", request.method)
            assertEquals("/api/mobile/v1/push/test", request.path)
        }

    @Test
    fun sendTest_unregistered_deletesDeadToken_thenRegistersAFreshOne() =
        runBlocking {
            server.enqueue(jsonResponse("""{"eventId":"e1","outcome":"unregistered"}"""))
            server.enqueue(fixtureResponse("putPushRegistration.200.json"))
            val tokens = FakeTokenSource(tokens = listOf("dead-token", "fresh-token"))

            assertEquals(TestPushResult.Unregistered, registrar(tokens).sendTest())

            assertEquals("/api/mobile/v1/push/test", take().path)
            val put = take()
            assertEquals("PUT", put.method)
            assertEquals(JsonPrimitive("fresh-token"), put.json()["fcmToken"])
            // The dead token is deleted before a token is fetched again: it is never re-sent.
            assertEquals(listOf("delete", "autoInit=true", "token"), tokens.calls)
        }

    @Test
    fun sendTest_unregistered_deleteFails_neverResendsTheDeadToken() =
        runBlocking {
            server.enqueue(jsonResponse("""{"eventId":"e1","outcome":"unregistered"}"""))
            val tokens =
                object : FcmTokenSource by FakeTokenSource(tokens = listOf("dead-token")) {
                    override suspend fun delete() = error("offline")
                }

            assertEquals(TestPushResult.Unregistered, registrar(tokens).sendTest())

            take()
            assertNull(server.takeRequest(300, TimeUnit.MILLISECONDS))
            assertEquals(1, retry.enqueued)
        }

    @Test
    fun sendTest_mapsEveryOtherOutcome() =
        runBlocking {
            val registrar = registrar()
            server.enqueue(jsonResponse("""{"eventId":"e1","outcome":"retryable"}"""))
            assertEquals(TestPushResult.Retryable, registrar.sendTest())
            server.enqueue(jsonResponse("""{"eventId":"e1","outcome":"failed"}"""))
            assertEquals(TestPushResult.Failed, registrar.sendTest())
            server.enqueue(fixtureResponse("error.invalid_request.json", code = 400))
            assertEquals(TestPushResult.NoRegistration, registrar.sendTest())
            server.enqueue(fixtureResponse("error.rate_limited.json", code = 429))
            assertEquals(TestPushResult.RateLimited, registrar.sendTest())
            server.enqueue(fixtureResponse("error.push_unavailable.json", code = 503))
            assertEquals(TestPushResult.Unavailable, registrar.sendTest())
            // Only `unregistered` re-registers.
            repeat(5) { assertEquals("/api/mobile/v1/push/test", take().path) }
            assertNull(server.takeRequest(300, TimeUnit.MILLISECONDS))
        }

    @Test
    fun registrationFixturesDecode() {
        val put = Json.parseToJsonElement(fixture("putPushRegistration.200.json")).jsonObject
        assertEquals("true", put["pushEnabled"]!!.jsonPrimitive.content)
        val get =
            QuireJson.decodeFromString(
                PushRegistrationResponse.serializer(),
                fixture("getPushRegistration.200.json"),
            )
        assertEquals(listOf("files", "deadlines", "notes"), get.registration!!.categories)
        assertTrue(get.pushEnabled)
    }

    private fun jsonResponse(body: String) =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)
}
