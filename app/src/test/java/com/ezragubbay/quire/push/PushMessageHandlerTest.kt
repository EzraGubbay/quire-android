package com.ezragubbay.quire.push

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class PushMessageHandlerTest {
    @get:Rule
    val logs = PushLogRule()

    private class FakeLookup : PushNamesLookup {
        var onCourse: suspend (String) -> String? = { "Signals and Systems" }
        var onFile: suspend (String, String) -> FileName? = { _, _ -> FileName("Lecture 3", "lecture") }
        var onNote: suspend (String, String) -> String? = { _, _ -> "Lecture 3 notes" }
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override suspend fun courseName(courseSlug: String): String? {
            calls += "course:$courseSlug"
            return onCourse(courseSlug)
        }

        override suspend fun file(
            courseSlug: String,
            fileId: String,
        ): FileName? {
            calls += "file:$fileId"
            return onFile(courseSlug, fileId)
        }

        override suspend fun noteTitle(
            courseSlug: String,
            noteId: String,
        ): String? {
            calls += "note:$noteId"
            return onNote(courseSlug, noteId)
        }
    }

    private class FakeSync : PushSyncTrigger {
        var filesAdded = 0
        var deleted = 0

        override suspend fun onFilesAdded() {
            filesAdded++
        }

        override suspend fun onMessagesDeleted() {
            deleted++
        }
    }

    private val lookup = FakeLookup()
    private val sync = FakeSync()
    private val posted = mutableListOf<PushNotificationSpec>()
    private var allowed = true
    private var preferences = PushPreferences { true }
    private val factory =
        PushNotificationFactory(
            EnglishTexts,
            { ZoneId.of("UTC") },
            { Instant.parse("2026-11-25T08:00:00Z") },
            { Locale.US },
        )

    private fun handler(deadlineMillis: Long = PushMessageHandler.DEADLINE_MS) =
        PushMessageHandler(
            preferences = { preferences.isEnabled(it) },
            lookup = lookup,
            factory = factory,
            notifier = { spec -> allowed.also { if (it) posted += spec } },
            sync = sync,
            deadlineMillis = deadlineMillis,
        )

    @Test
    fun filesAdded_resolvesNames_postsOnFilesChannelWithCourseTag_andStartsSync() {
        val result = handler().handle(pushData("push.files.added.json"))

        val spec = (result as PushHandleResult.Posted).spec
        assertEquals(PushChannels.FILES, spec.channelId)
        assertEquals("files:3f0b1c7e-2a7d-4d8e-9b1a-6c5e4f3a2b10", spec.tag)
        assertEquals("Signals and Systems", spec.title)
        assertEquals("New lecture: Lecture 3", spec.body)
        assertEquals(listOf(spec), posted)
        assertEquals(1, sync.filesAdded)
    }

    @Test
    fun unknownType_postsNothing_andLooksNothingUp() {
        val result = handler().handle(pushData("push.files.added.json") + ("type" to "course.archived"))

        assertEquals(PushHandleResult.Dropped, result)
        assertTrue(posted.isEmpty())
        assertTrue(lookup.calls.isEmpty())
        assertEquals(0, sync.filesAdded)
    }

    @Test
    fun unknownVersion_andMissingIds_postNothing() {
        assertEquals(PushHandleResult.Dropped, handler().handle(pushData("push.test.json") + ("v" to "9")))
        assertEquals(PushHandleResult.Dropped, handler().handle(pushData("push.files.added.json") - "courseId"))
        assertTrue(posted.isEmpty())
    }

    @Test
    fun slowLookup_stillPostsFallbackText_withinTheDeadline() {
        lookup.onFile = { _, _ -> awaitCancellation() }
        lookup.onCourse = { _ -> awaitCancellation() }

        val started = System.nanoTime()
        val result = handler(deadlineMillis = DEADLINE_MS).handle(pushData("push.files.added.json"))
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        val spec = (result as PushHandleResult.Posted).spec
        assertEquals("Quire", spec.title)
        assertEquals("New file in a course", spec.body)
        assertTrue("took ${elapsedMs}ms for a ${DEADLINE_MS}ms deadline", elapsedMs < DEADLINE_MS + SLACK_MS)
        assertTrue("returned before the deadline: ${elapsedMs}ms", elapsedMs >= DEADLINE_MS - 50)
    }

    @Test
    fun oneSlowLookup_keepsTheOtherResolvedName() {
        lookup.onFile = { _, _ -> awaitCancellation() }

        val spec =
            (
                handler(
                    deadlineMillis = DEADLINE_MS,
                ).handle(pushData("push.files.added.json")) as PushHandleResult.Posted
            ).spec

        assertEquals("Signals and Systems", spec.title)
        assertEquals("New file in Signals and Systems", spec.body)
    }

    @Test
    fun lookupsRunInParallel_underOneOverallDeadline() {
        // Each lookup alone fits the deadline; run one after the other they would not.
        val running = AtomicInteger()
        var overlapped = false
        val slow: suspend () -> Unit = {
            if (running.incrementAndGet() > 1) overlapped = true
            delay(DEADLINE_MS * 2 / 3)
            running.decrementAndGet()
        }
        lookup.onCourse = { slow().let { "Signals and Systems" } }
        lookup.onFile = { _, _ -> slow().let { FileName("Lecture 3", "lecture") } }

        val spec =
            (
                handler(
                    deadlineMillis = DEADLINE_MS,
                ).handle(pushData("push.files.added.json")) as PushHandleResult.Posted
            ).spec

        assertTrue(overlapped)
        assertEquals("Signals and Systems", spec.title)
        assertEquals("New lecture: Lecture 3", spec.body)
    }

    @Test
    fun failingLookup_fallsBack() {
        lookup.onCourse = { error("boom") }
        lookup.onFile = { _, _ -> null }

        val spec = (handler().handle(pushData("push.files.added.json")) as PushHandleResult.Posted).spec

        assertEquals("Quire", spec.title)
        assertEquals("New file in a course", spec.body)
    }

    @Test
    fun categoryOff_suppressesTheNotification() {
        preferences = PushPreferences { it != PushCategory.Files }

        assertEquals(PushHandleResult.Suppressed, handler().handle(pushData("push.files.added.json")))
        assertTrue(posted.isEmpty())
        assertEquals(0, sync.filesAdded)
        // Other categories and the always-on test still post.
        assertTrue(handler().handle(pushData("push.homework.due.json")) is PushHandleResult.Posted)
        assertTrue(handler().handle(pushData("push.test.json")) is PushHandleResult.Posted)
    }

    @Test
    fun notificationsOff_buildsButDoesNotPost() {
        allowed = false
        assertTrue(handler().handle(pushData("push.note.synthesized.json")) is PushHandleResult.NotPosted)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun eachEventGetsItsTag_andOnlyFilesStartASync() {
        val tags =
            listOf("push.files.added.json", "push.homework.due.json", "push.note.synthesized.json", "push.test.json")
                .map { (handler().handle(pushData(it)) as PushHandleResult.Posted).spec.tag }

        assertEquals(
            listOf(
                "files:3f0b1c7e-2a7d-4d8e-9b1a-6c5e4f3a2b10",
                "homework:0e9d8c7b-6a5f-4e3d-9c2b-1a0f9e8d7c6b",
                "note:2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f",
                "test",
            ),
            tags,
        )
        assertEquals(1, sync.filesAdded)
    }

    @Test
    fun noteReady_looksUpNoteTitle_testLooksUpNothing() {
        val note = (handler().handle(pushData("push.note.synthesized.json")) as PushHandleResult.Posted).spec
        assertEquals("Lecture 3 notes", note.body)
        assertTrue(lookup.calls.contains("note:2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f"))

        lookup.calls.clear()
        handler().handle(pushData("push.test.json"))
        assertTrue(lookup.calls.isEmpty())
    }

    private companion object {
        const val DEADLINE_MS = 300L
        const val SLACK_MS = 1_500L
    }
}
