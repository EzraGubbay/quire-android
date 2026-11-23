package com.ezragubbay.quire.push

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezragubbay.quire.core.network.FakeServerConfig
import com.ezragubbay.quire.core.network.FakeTokenProvider
import com.ezragubbay.quire.core.network.NetworkOptions
import com.ezragubbay.quire.core.network.QuireClientFactory
import com.ezragubbay.quire.core.network.SessionEvents
import com.ezragubbay.quire.core.network.fixtureResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
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
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

class PushControlsTest {
    @get:Rule val logs = PushLogRule()

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var client: PushClient
    private lateinit var store: NotificationPreferencesStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val retry = FakeRegistrationScheduler()

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
        store =
            NotificationPreferencesStore(
                PreferenceDataStoreFactory.create(
                    scope = scope,
                ) { tmp.newFile("s.preferences_pb").also { it.delete() } },
            )
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun controls(tokens: FcmTokenSource = FakeTokenSource()): RegistrarPushControls {
        val registrar =
            PushRegistrar(
                tokens = tokens,
                client = client,
                device = PushDevice(appVersion = "1.1.0") { true },
                categories = PushCategoryPolicy({ true }, store),
                retry = retry,
                scope = scope,
            )
        return RegistrarPushControls(registrar, store, tokens, scope)
    }

    private fun take(): RecordedRequest = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "no request" }

    private fun RecordedRequest.categories(): JsonElement? =
        Json.parseToJsonElement(body.readUtf8()).jsonObject["categories"]

    private fun wire(vararg values: String) = JsonArray(values.map { JsonPrimitive(it) })

    private fun noRequestWithinTwoDebounces() =
        assertNull(server.takeRequest(RegistrarPushControls.CATEGORY_SYNC_DEBOUNCE_MS * 2, TimeUnit.MILLISECONDS))

    @Test
    fun setCategory_persists_andABurstOfFlipsSendsOnePutWithTheFinalCategories() =
        runBlocking {
            server.enqueue(fixtureResponse("putPushRegistration.200.json"))
            val controls = controls()

            controls.setCategory(PushCategory.Files, enabled = false)
            controls.setCategory(PushCategory.Files, enabled = true)
            controls.setCategory(PushCategory.Files, enabled = false)
            controls.setCategory(PushCategory.Deadlines, enabled = false)

            val put = take()
            assertEquals("PUT", put.method)
            assertEquals(wire("notes"), put.categories())
            noRequestWithinTwoDebounces()
            // Stored in the order the flips were made.
            assertEquals(listOf(PushCategory.Notes), store.preferences.first().categories)
        }

    @Test
    fun aFlipDuringAnInFlightPut_sendsASecondPutWithTheLatestCategories() =
        runBlocking {
            server.enqueue(fixtureResponse("putPushRegistration.200.json").setBodyDelay(1, TimeUnit.SECONDS))
            server.enqueue(fixtureResponse("putPushRegistration.200.json"))
            val controls = controls()

            controls.setCategory(PushCategory.Files, enabled = false)
            assertEquals(wire("deadlines", "notes"), take().categories())
            // The first PUT's response is still on its way.
            controls.setCategory(PushCategory.Notes, enabled = false)

            val second = take()
            assertEquals("PUT", second.method)
            assertEquals(wire("deadlines"), second.categories())
        }

    @Test
    fun notConfigured_storesTheSwitch_butSendsNothing() =
        runBlocking {
            val controls = controls(NoopTokenSource)

            controls.setCategory(PushCategory.Files, enabled = false)

            assertFalse(store.preferences.first { !it.isEnabled(PushCategory.Files) }.isEnabled(PushCategory.Files))
            noRequestWithinTwoDebounces()
            assertEquals(0, server.requestCount)
        }

    @Test
    fun available_followsTheTokenSource() {
        assertTrue(controls().available)
        assertFalse(controls(NoopTokenSource).available)
    }

    @Test
    fun sendTest_callerLeavesMidUnregistered_theReRegistrationStillHappens() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"eventId":"e1","outcome":"unregistered"}""")
                    .setBodyDelay(500, TimeUnit.MILLISECONDS),
            )
            server.enqueue(fixtureResponse("putPushRegistration.200.json"))
            val tokens = FakeTokenSource(tokens = listOf("dead-token", "fresh-token"))
            val controls = controls(tokens)
            val settings = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            settings.launch { controls.sendTest() }
            assertEquals("/api/mobile/v1/push/test", take().path)
            // The user leaves Settings while the test is in flight: its ViewModel scope is cancelled.
            settings.cancel()

            val put = take()
            assertEquals("PUT", put.method)
            assertEquals(
                JsonPrimitive("fresh-token"),
                Json.parseToJsonElement(put.body.readUtf8()).jsonObject["fcmToken"],
            )
            assertEquals(listOf("delete", "autoInit=true", "token"), tokens.calls)
        }
}
