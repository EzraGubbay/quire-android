package com.ezragubbay.quire.core.network

import app.cash.turbine.test
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ChatStreamerTest {
    private lateinit var server: MockWebServer
    private lateinit var streamer: ChatStreamer

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        streamer = streamer(60_000L)
    }

    private fun streamer(idle: Long) =
        ChatStreamer(
            QuireClientFactory.chatStreamApi(
                FakeServerConfig(server.url("/").toString()),
                FakeTokenProvider("tok"),
                SessionEvents(),
                NetworkOptions(allowCleartextLocal = true),
                idle,
            ),
        )

    @After
    fun tearDown() = server.shutdown()

    private fun ndjson(
        name: String,
        throttle: Boolean = false,
    ) = MockResponse()
        .setHeader("Content-Type", "application/x-ndjson")
        .setChunkedBody(fixture(name), 16)
        .also { if (throttle) it.throttleBody(16, 20, TimeUnit.MILLISECONDS) }

    @Test
    fun normalStreamEmitsEventsAndSendsOnlyQuestion() =
        runBlocking {
            server.enqueue(ndjson("chat-stream-normal.ndjson"))
            val events = streamer.ask("sig", "t1", "What is convolution?").toList()
            assertEquals(listOf("User", "Delta", "Delta", "Done"), events.map { it::class.simpleName })
            val req = server.takeRequest()
            assertEquals("/api/mobile/v1/courses/sig/threads/t1/messages", req.path)
            assertEquals("Bearer tok", req.getHeader("Authorization"))
            assertEquals("""{"question":"What is convolution?"}""", req.body.readUtf8())
        }

    @Test
    fun deltasArriveIncrementallyBeforeStreamEnds() =
        runBlocking {
            server.enqueue(ndjson("chat-stream-normal.ndjson", throttle = true))
            streamer.ask("sig", "t1", "q").test(timeout = kotlin.time.Duration.parse("10s")) {
                assertTrue(awaitItem() is ChatEvent.User)
                assertEquals("Convolution slides ", (awaitItem() as ChatEvent.Delta).text)
                assertEquals("one signal over another [1].", (awaitItem() as ChatEvent.Delta).text)
                assertTrue(awaitItem() is ChatEvent.Done)
                awaitComplete()
            }
        }

    @Test
    fun budgetErrorEmittedAsTerminalError() =
        runBlocking {
            server.enqueue(ndjson("chat-stream-error-budget.ndjson"))
            val last = streamer.ask("sig", "t1", "q").toList().last() as ChatEvent.Error
            assertEquals(ErrorCode.RateLimited, last.code)
            assertFalse(last.retryable)
        }

    @Test
    fun preStream503EnvelopeBecomesAiUnavailableError() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503).setBody(fixture("error.ai_unavailable.json")))
            val events = streamer.ask("sig", "t1", "q").toList()
            val e = events.single() as ChatEvent.Error
            assertEquals(ErrorCode.AiUnavailable, e.code)
            assertTrue(e.retryable)
        }

    @Test
    fun preStream429MapsRateLimitedRetryable() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(429).setBody(fixture("error.rate_limited.json")))
            val e = streamer.ask("sig", "t1", "q").toList().single() as ChatEvent.Error
            assertEquals(ErrorCode.RateLimited, e.code)
            assertTrue(e.retryable)
        }

    @Test
    fun connectionDropBeforeDoneYieldsRetryableNetworkError() =
        runBlocking {
            val body =
                Buffer().writeUtf8(
                    fixture("chat-stream-normal.ndjson").lines().take(2).joinToString("\n") + "\n",
                )
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/x-ndjson")
                    .setBody(body)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END),
            )
            val last = streamer.ask("sig", "t1", "q").toList().last() as ChatEvent.Error
            assertEquals("network", last.kind)
            assertTrue(last.retryable)
        }

    @Test
    fun idleStreamTripsWatchdogAsNetworkError() =
        runBlocking {
            server.enqueue(
                ndjson("chat-stream-normal.ndjson").throttleBody(1, 5, TimeUnit.SECONDS),
            )
            val events = withTimeout(10_000) { streamer(300L).ask("sig", "t1", "q").toList() }
            assertEquals("network", (events.last() as ChatEvent.Error).kind)
        }

    @Test
    fun cancellingCollectorStopsAfterPartial() =
        runBlocking {
            server.enqueue(ndjson("chat-stream-normal.ndjson", throttle = true))
            streamer.ask("sig", "t1", "q").test(timeout = kotlin.time.Duration.parse("10s")) {
                assertTrue(awaitItem() is ChatEvent.User)
                assertTrue(awaitItem() is ChatEvent.Delta)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
