package com.ezragubbay.quire.testing

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.rules.ExternalResource
import java.util.concurrent.TimeUnit

/**
 * Starts a [MockWebServer] per test and serves JSON fixtures from `androidTest/assets/fixtures/`.
 * Point the app's base URL at [baseUrl] (always ends with `/`).
 */
class MockWebServerRule(
    private val fixtureDir: String = "fixtures",
) : ExternalResource() {
    val server = MockWebServer()

    val baseUrl: String get() = server.url("/").toString()

    override fun before() {
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

    private fun readFixture(name: String): String =
        InstrumentationRegistry
            .getInstrumentation()
            .context.assets
            .open("$fixtureDir/$name")
            .bufferedReader()
            .use { it.readText() }
}
