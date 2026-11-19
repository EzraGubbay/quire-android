package com.ezragubbay.quire.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.HttpURLConnection

class MockWebServerRuleTest {
    @get:Rule
    val server = MockWebServerRule()

    @Test
    fun servesFixtureBodyFromAssets() {
        server.enqueueFixture("harness-ping.json")

        val (code, body) = get("/ping")

        assertEquals(200, code)
        assertEquals("""{"status":"ok"}""", body.trim())
    }

    @Test
    fun enqueueFixtureHonoursStatusCodeAndRecordsRequest() {
        server.enqueueFixture("harness-ping.json", code = 503)

        val (code, _) = get("/ping")

        assertEquals(503, code)
        assertEquals("/ping", server.takeRequest().path)
        assertTrue(server.baseUrl.startsWith("http://"))
    }

    private fun get(path: String): Pair<Int, String> {
        val conn = java.net.URL(server.baseUrl + path.removePrefix("/")).openConnection() as HttpURLConnection
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to stream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
