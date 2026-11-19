package com.ezragubbay.quire.core.network

import okhttp3.mockwebserver.MockResponse
import okio.Buffer

fun fixture(name: String): String =
    checkNotNull(NetworkTestSupport::class.java.getResourceAsStream("/contract/mobile-v1/$name")) {
        "missing fixture $name"
    }.use { Buffer().readFrom(it).readUtf8() }

fun fixtureResponse(
    name: String,
    code: Int = 200,
): MockResponse =
    MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(fixture(name))

private object NetworkTestSupport

class FakeTokenProvider(
    var value: String? = null,
) : TokenProvider {
    override fun token(): String? = value
}

class FakeServerConfig(
    var url: String? = null,
) : ServerConfigProvider {
    override fun serverUrl(): String? = url
}
