package com.ezragubbay.quire.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ContractFixturesTest {
    private val json = QuireJson

    @Test
    fun pairRequestFixtureMatchesRequestDto() {
        val dto = json.decodeFromString<PairRequest>(fixture("pairDevice.request.json"))
        assertEquals(PairRequest("AbC12345", "Pixel 9"), dto)
        assertEquals(
            Json.parseToJsonElement(fixture("pairDevice.request.json")),
            Json.parseToJsonElement(json.encodeToString(PairRequest.serializer(), dto)),
        )
    }

    @Test
    fun pairResponseFixtureDecodes() {
        val dto = json.decodeFromString<PairResponse>(fixture("pairDevice.200.json"))
        assertEquals("9e8d7c6b-5a49-4382-b1a0-0f1e2d3c4b5a", dto.device.id)
        assertNotNull(dto.token)
    }

    @Test
    fun meFixtureDecodes() {
        val dto = json.decodeFromString<MeResponse>(fixture("getMe.200.json"))
        assertEquals("local", dto.login)
        assertEquals("1.0.0", dto.apiVersion)
        assertEquals("2026-11-01T08:00:00Z", dto.device.createdAt)
    }

    @Test
    fun everyErrorFixtureDecodesIntoEnvelope() {
        listOf("unauthorized", "not_found", "invalid_request", "rate_limited", "ai_unavailable", "internal").forEach {
            val env = json.decodeFromString<ErrorEnvelope>(fixture("error.$it.json"))
            assertEquals(it, env.error.code)
        }
    }
}
