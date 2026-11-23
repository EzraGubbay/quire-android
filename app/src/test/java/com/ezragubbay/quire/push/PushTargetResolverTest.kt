package com.ezragubbay.quire.push

import com.ezragubbay.quire.core.network.FakeServerConfig
import com.ezragubbay.quire.core.network.FakeTokenProvider
import com.ezragubbay.quire.core.network.NetworkOptions
import com.ezragubbay.quire.core.network.QuireClientFactory
import com.ezragubbay.quire.core.network.SessionEvents
import com.ezragubbay.quire.core.network.fixtureResponse
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.TimeUnit

class PushTargetResolverTest {
    private lateinit var server: MockWebServer

    /** API paths (below `/api/mobile/v1`) answered with a `404 not_found`. */
    private val missing = Collections.synchronizedSet(mutableSetOf<String>())

    /** Every path the resolver asked for. */
    private val seen = Collections.synchronizedList(mutableListOf<String>())

    /** Answers every request after this long (to force the resolver's timeout). */
    private var delayMillis = 0L

    private var serverError = false

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty().removePrefix(API_PREFIX)
                    seen += path
                    val response =
                        when {
                            serverError -> fixtureResponse("error.internal.json", 500)
                            path in missing -> fixtureResponse("error.not_found.json", 404)
                            path == "/courses/$COURSE" -> fixtureResponse("getCourse.200.json")
                            path == "/courses/$COURSE/files/$FILE" -> fixtureResponse("getFile.200.json")
                            path == "/courses/$COURSE/notes/$NOTE" -> fixtureResponse("getNote.200.json")
                            else -> fixtureResponse("error.not_found.json", 404)
                        }
                    return response.setHeadersDelay(delayMillis, TimeUnit.MILLISECONDS)
                }
            }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun resolver(timeoutMillis: Long = ApiPushTargetResolver.TIMEOUT_MS): PushTargetResolver {
        val config = FakeServerConfig(server.url("/").toString())
        val tokens = FakeTokenProvider("device-token")
        val options = NetworkOptions(allowCleartextLocal = true)
        val events = SessionEvents()
        val browse = QuireClientFactory.create(config, tokens, events, options)
        val push = PushClient(pushRestApi(QuireClientFactory.okHttpClient(config, tokens, events, options)))
        return ApiPushTargetResolver(browse, push, timeoutMillis)
    }

    @Test
    fun home_isNotChecked() =
        runBlocking {
            assertEquals(PushTargetResolution(PushTarget.Home), resolver().resolve(PushTarget.Home))
            assertTrue(seen.isEmpty())
        }

    @Test
    fun course_200_opensTheCourse() =
        runBlocking {
            val target = PushTarget.Course(COURSE)
            assertEquals(PushTargetResolution(target), resolver().resolve(target))
            assertEquals(listOf("/courses/$COURSE"), seen.toList())
        }

    @Test
    fun course_404_opensCourses_withTheCourseMessage() =
        runBlocking {
            missing += "/courses/$COURSE"
            assertEquals(
                PushTargetResolution(PushTarget.Home, PushTargetMissing.Course),
                resolver().resolve(PushTarget.Course(COURSE)),
            )
        }

    @Test
    fun file_200_opensTheFile() =
        runBlocking {
            val target = PushTarget.File(COURSE, FILE)
            assertEquals(PushTargetResolution(target), resolver().resolve(target))
            assertTrue("checks the course and the file: $seen", seen.containsAll(COURSE_AND_FILE))
        }

    @Test
    fun file_404_opensTheCourse_withTheFileMessage() =
        runBlocking {
            missing += "/courses/$COURSE/files/$FILE"
            assertEquals(
                PushTargetResolution(PushTarget.Course(COURSE), PushTargetMissing.File),
                resolver().resolve(PushTarget.File(COURSE, FILE)),
            )
        }

    @Test
    fun file_courseGone_opensCourses_withTheCourseMessage() =
        runBlocking {
            missing += "/courses/$COURSE"
            missing += "/courses/$COURSE/files/$FILE"
            assertEquals(
                PushTargetResolution(PushTarget.Home, PushTargetMissing.Course),
                resolver().resolve(PushTarget.File(COURSE, FILE)),
            )
        }

    @Test
    fun note_200_opensTheNote() =
        runBlocking {
            val target = PushTarget.Note(COURSE, NOTE)
            assertEquals(PushTargetResolution(target), resolver().resolve(target))
        }

    @Test
    fun note_404_opensTheCourse_withTheNoteMessage() =
        runBlocking {
            missing += "/courses/$COURSE/notes/$NOTE"
            assertEquals(
                PushTargetResolution(PushTarget.Course(COURSE), PushTargetMissing.Note),
                resolver().resolve(PushTarget.Note(COURSE, NOTE)),
            )
        }

    @Test
    fun timeout_navigatesAnyway() =
        runBlocking {
            delayMillis = 3_000
            val target = PushTarget.File(COURSE, FILE)
            val started = System.nanoTime()

            val resolution = resolver(timeoutMillis = 200).resolve(target)

            assertEquals(PushTargetResolution(target), resolution)
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            assertTrue("gave up after the timeout, not the response: $elapsedMs ms", elapsedMs < 2_000)
        }

    @Test
    fun offline_navigatesAnyway() =
        runBlocking {
            val resolver = resolver()
            server.shutdown()
            val target = PushTarget.Note(COURSE, NOTE)
            assertEquals(PushTargetResolution(target), resolver.resolve(target))
        }

    @Test
    fun serverError_navigatesAnyway() =
        runBlocking {
            serverError = true
            val target = PushTarget.File(COURSE, FILE)
            assertEquals(PushTargetResolution(target), resolver().resolve(target))
        }

    private companion object {
        const val API_PREFIX = "/api/mobile/v1"
        const val COURSE = "signals-and-systems"
        const val FILE = "6f1c2a52-8b0e-4c6a-9d63-2a3a7c1d9e10"
        const val NOTE = "0d6f6c3e-4f5a-4b0b-8f0e-9a6b1d2c3e4f"
        val COURSE_AND_FILE = listOf("/courses/$COURSE", "/courses/$COURSE/files/$FILE")
    }
}
