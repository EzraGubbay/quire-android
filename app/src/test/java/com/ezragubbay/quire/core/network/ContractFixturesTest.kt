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
        assertEquals("1.2.0", dto.apiVersion)
        assertEquals("2026-11-01T08:00:00Z", dto.device.createdAt)
    }

    @Test
    fun everyErrorFixtureDecodesIntoEnvelope() {
        listOf("unauthorized", "not_found", "invalid_request", "rate_limited", "ai_unavailable", "internal").forEach {
            val env = json.decodeFromString<ErrorEnvelope>(fixture("error.$it.json"))
            assertEquals(it, env.error.code)
        }
    }

    @Test
    fun uploadFixturesDecodeIntoUploadJobDto() {
        val queuedDoc = json.decodeFromString<UploadJobDto>(fixture("createUpload.202.document.json"))
        assertEquals("queued", queuedDoc.status)
        assertEquals("document", queuedDoc.kind)

        val queuedNote = json.decodeFromString<UploadJobDto>(fixture("createUpload.202.note.json"))
        assertEquals("note", queuedNote.kind)

        val doneDoc = json.decodeFromString<UploadJobDto>(fixture("getUpload.200.done.json"))
        assertEquals("done", doneDoc.status)
        assertEquals("document", doneDoc.kind)
        assertEquals("6f1c2a52-8b0e-4c6a-9d63-2a3a7c1d9e10", doneDoc.fileId)

        val doneNote = json.decodeFromString<UploadJobDto>(fixture("getUpload.200.note.json"))
        assertEquals("note", doneNote.kind)
        assertEquals("3c9d8e7f-6a5b-4c3d-9e2f-1a0b9c8d7e6f", doneNote.noteId)
    }

    @Test
    fun uploadErrorFixturesDecodeIntoEnvelope() {
        listOf("createUpload.400.json", "createUpload.413.json").forEach {
            val env = json.decodeFromString<ErrorEnvelope>(fixture(it))
            assertEquals("invalid_request", env.error.code)
        }
    }
}
