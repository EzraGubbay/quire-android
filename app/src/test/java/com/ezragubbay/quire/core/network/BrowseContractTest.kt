package com.ezragubbay.quire.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BrowseContractTest {
    private val json = QuireJson
    private lateinit var server: MockWebServer
    private lateinit var client: QuireClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val config = FakeServerConfig(server.url("/").toString())
        client =
            QuireClientFactory.create(
                config,
                FakeTokenProvider("t"),
                SessionEvents(),
                NetworkOptions(allowCleartextLocal = true),
            )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun courseSummaryDecodes() {
        val dto = json.decodeFromString<List<CourseSummaryDto>>(fixture("listCourses.200.json")).single()
        assertEquals("signals-and-systems", dto.slug)
        assertEquals("044130", dto.code)
        assertEquals("Winter 2026", dto.semester)
        assertEquals(12, dto.fileCount)
    }

    @Test
    fun courseDetailDecodesCounts() {
        val dto = json.decodeFromString<CourseDetailDto>(fixture("getCourse.200.json"))
        assertEquals(13, dto.counts.sessions["lecture"])
        assertEquals(5, dto.counts.files["homework"])
        assertEquals(4, dto.counts.notes)
    }

    @Test
    fun sessionDecodes() {
        val dto = json.decodeFromString<List<CourseSessionDto>>(fixture("listSessions.200.json")).single()
        assertEquals("lecture", dto.kind)
        assertEquals(3, dto.number)
        assertEquals("2026-11-02", dto.date)
        assertEquals(1, dto.fileIds.size)
        assertEquals("0d6f6c3e-4f5a-4b0b-8f0e-9a6b1d2c3e4f", dto.noteId)
    }

    @Test
    fun fileDecodesSessionAndFlags() {
        val dto = json.decodeFromString<List<CourseFileDto>>(fixture("listFiles.200.json")).single()
        assertEquals("lecture", dto.type)
        assertEquals(3, dto.session?.number)
        assertNull(dto.folder)
        assertEquals(1_048_576L, dto.sizeBytes)
        assertTrue(dto.hasPdf && dto.hasOriginal && !dto.hasMarkdown)
        assertEquals("2026-11-02T09:30:00Z", dto.updatedAt)
    }

    @Test
    fun noteDecodes() {
        val dto = json.decodeFromString<List<NoteDto>>(fixture("listNotes.200.json")).single()
        assertEquals("lecture-3-notes", dto.slug)
        assertEquals("lecture", dto.session?.kind)
        assertEquals(3, dto.session?.number)
    }

    @Test
    fun clientHitsBrowseEndpoints() {
        listOf(
            "listCourses.200.json" to "/api/mobile/v1/courses",
            "getCourse.200.json" to "/api/mobile/v1/courses/signals-and-systems",
            "listSessions.200.json" to "/api/mobile/v1/courses/signals-and-systems/sessions",
            "listFiles.200.json" to "/api/mobile/v1/courses/signals-and-systems/files",
            "listNotes.200.json" to "/api/mobile/v1/courses/signals-and-systems/notes",
        ).forEach { (fixture, _) -> server.enqueue(fixtureResponse(fixture)) }
        runBlocking {
            assertTrue(client.courses() is ApiResult.Success)
            assertTrue(client.course("signals-and-systems") is ApiResult.Success)
            assertTrue(client.sessions("signals-and-systems") is ApiResult.Success)
            assertTrue(client.files("signals-and-systems") is ApiResult.Success)
            assertTrue(client.notes("signals-and-systems") is ApiResult.Success)
        }
        val paths = (1..5).map { server.takeRequest().path }
        assertEquals(
            listOf(
                "/api/mobile/v1/courses",
                "/api/mobile/v1/courses/signals-and-systems",
                "/api/mobile/v1/courses/signals-and-systems/sessions",
                "/api/mobile/v1/courses/signals-and-systems/files",
                "/api/mobile/v1/courses/signals-and-systems/notes",
            ),
            paths,
        )
    }

    @Test
    fun unknownCourseMapsToNotFound() {
        server.enqueue(fixtureResponse("error.not_found.json", 404))
        assertTrue(runBlocking { client.course("nope") } is ApiResult.Failure.NotFound)
    }
}
