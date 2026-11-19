package com.ezragubbay.quire.testing

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.rules.ExternalResource
import java.util.concurrent.TimeUnit

/**
 * Starts a [MockWebServer] per test and serves JSON fixtures from `androidTest/assets/fixtures/`.
 * Point the app's base URL at [baseUrl] (always ends with `/`).
 */
class MockWebServerRule(
    private val fixtureDir: String = "fixtures",
    /**
     * Answer the app's launch-time session check (`GET .../me`) with `me.json` outside the response queue, so it
     * cannot consume a response enqueued for a later request. Everything else still comes from the queue.
     */
    private val answerSessionCheckOutOfBand: Boolean = false,
) : ExternalResource() {
    val server = MockWebServer()

    val baseUrl: String get() = server.url("/").toString()

    override fun before() {
        if (answerSessionCheckOutOfBand) installSessionCheckDispatcher()
        server.start()
    }

    override fun after() {
        server.shutdown()
    }

    /** Queue the fixture [name] (relative to the fixture dir) as the next JSON response. */
    fun enqueueFixture(
        name: String,
        code: Int = 200,
    ) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(readFixture(name)),
        )
    }

    fun takeRequest(timeoutSeconds: Long = 5): RecordedRequest =
        requireNotNull(server.takeRequest(timeoutSeconds, TimeUnit.SECONDS)) { "No request received" }

    /**
     * Queue a binary asset [name] as a full `200` body for the file-content endpoint, mirroring the
     * `mobile-v1` contract: a strong [etag], `Accept-Ranges: bytes`, a length and a
     * `Content-Disposition` filename. Used by the reader journey for the 5-page PDF deck fixture.
     */
    fun enqueuePdfFixture(
        name: String,
        etag: String = "\"pdf-v1\"",
        contentType: String = "application/pdf",
        filename: String = name,
    ) {
        val bytes = readBinaryFixture(name)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", contentType)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("ETag", etag)
                .setHeader("Content-Disposition", "inline; filename=\"$filename\"")
                .setBody(Buffer().write(bytes)),
        )
    }

    /**
     * Queue a `206 Partial Content` response carrying the byte range [start]..[endInclusive] of the
     * binary asset [name], echoing `Content-Range` and the strong [etag] so a resumed/`Range` request
     * (the download-engine resume path, QMOB-E5-S1) can be exercised.
     */
    fun enqueuePdfRange(
        name: String,
        start: Int,
        endInclusive: Int,
        etag: String = "\"pdf-v1\"",
        contentType: String = "application/pdf",
    ) {
        val bytes = readBinaryFixture(name)
        val slice = bytes.copyOfRange(start, endInclusive + 1)
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", contentType)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("ETag", etag)
                .setHeader("Content-Range", "bytes $start-$endInclusive/${bytes.size}")
                .setBody(Buffer().write(slice)),
        )
    }

    private fun installSessionCheckDispatcher() {
        val me = readFixture("me.json")
        server.dispatcher =
            object : QueueDispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.path?.endsWith("/me") == true) {
                        MockResponse().setHeader("Content-Type", "application/json").setBody(me)
                    } else {
                        super.dispatch(request)
                    }
            }
    }

    private fun readFixture(name: String): String =
        InstrumentationRegistry
            .getInstrumentation()
            .context.assets
            .open("$fixtureDir/$name")
            .bufferedReader()
            .use { it.readText() }

    private fun readBinaryFixture(name: String): ByteArray =
        InstrumentationRegistry
            .getInstrumentation()
            .context.assets
            .open("$fixtureDir/$name")
            .use { it.readBytes() }
}
