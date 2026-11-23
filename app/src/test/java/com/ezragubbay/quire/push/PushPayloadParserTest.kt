package com.ezragubbay.quire.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PushPayloadParserTest {
    @Test
    fun filesAddedFixtureParses() {
        assertEquals(
            PushEvent.FilesAdded(
                eventId = "7a1c2f0e-5b7d-4c2a-9a51-0f3e6d1b2c44",
                courseSlug = "signals-and-systems",
                courseId = "3f0b1c7e-2a7d-4d8e-9b1a-6c5e4f3a2b10",
                fileId = "9c4e2b1a-7d3f-4a6b-8e2c-1f0a9b8c7d6e",
                count = 1,
            ),
            PushPayloadParser.parse(pushData("push.files.added.json")),
        )
    }

    @Test
    fun homeworkDueFixtureParses() {
        val event = PushPayloadParser.parse(pushData("push.homework.due.json")) as PushEvent.HomeworkDue
        assertEquals("c2d3e4f5-6a7b-4c8d-9e0f-1a2b3c4d5e6f", event.assignmentId)
        assertEquals("3", event.number)
        assertEquals(Instant.parse("2026-11-25T21:59:00Z"), event.dueAt)
        assertEquals("d4e5f6a7-8b9c-4d0e-9f1a-2b3c4d5e6f70", event.fileId)
        assertEquals(PushCategory.Deadlines, event.category)
    }

    @Test
    fun homeworkDueWithoutSheet_hasNoFileId() {
        val event = PushPayloadParser.parse(pushData("push.homework.due.json") - "fileId") as PushEvent.HomeworkDue
        assertNull(event.fileId)
    }

    @Test
    fun noteSynthesizedFixtureParses_readyAndFailed() {
        val ready = PushPayloadParser.parse(pushData("push.note.synthesized.json")) as PushEvent.NoteSynthesized
        assertEquals(true, ready.ready)
        assertEquals("0d6f6c3e-4f5a-4b0b-8f0e-9a6b1d2c3e4f", ready.noteId)
        val failed =
            PushPayloadParser.parse(pushData("push.note.synthesized.json") + ("outcome" to "failed"))
                as PushEvent.NoteSynthesized
        assertEquals(false, failed.ready)
    }

    @Test
    fun testFixtureParses() {
        assertEquals(
            PushEvent.Test("f1a2b3c4-d5e6-4f70-8a9b-0c1d2e3f4a5b"),
            PushPayloadParser.parse(pushData("push.test.json")),
        )
    }

    @Test
    fun everyFixtureMapsToItsCategory() {
        val categories =
            listOf("push.files.added.json", "push.homework.due.json", "push.note.synthesized.json", "push.test.json")
                .map { PushPayloadParser.parse(pushData(it))!!.category }
        assertEquals(listOf(PushCategory.Files, PushCategory.Deadlines, PushCategory.Notes, null), categories)
    }

    @Test
    fun unknownTypeOrVersion_isDropped() {
        val files = pushData("push.files.added.json")
        assertNull(PushPayloadParser.parse(files + ("type" to "course.archived")))
        assertNull(PushPayloadParser.parse(files + ("v" to "2")))
        assertNull(PushPayloadParser.parse(files - "v"))
        assertNull(PushPayloadParser.parse(files - "type"))
    }

    @Test
    fun missingOrBlankIds_areDropped() {
        val files = pushData("push.files.added.json")
        for (key in listOf("eventId", "course", "courseId", "fileId", "count")) {
            assertNull("missing $key", PushPayloadParser.parse(files - key))
            assertNull("blank $key", PushPayloadParser.parse(files + (key to " ")))
        }
        val homework = pushData("push.homework.due.json")
        for (key in listOf("course", "courseId", "assignmentId", "number", "dueAt")) {
            assertNull("missing $key", PushPayloadParser.parse(homework - key))
        }
        val note = pushData("push.note.synthesized.json")
        for (key in listOf("course", "courseId", "noteId", "outcome")) {
            assertNull("missing $key", PushPayloadParser.parse(note - key))
        }
        assertNull(PushPayloadParser.parse(pushData("push.test.json") - "eventId"))
    }

    @Test
    fun malformedValues_areDropped() {
        assertNull(PushPayloadParser.parse(pushData("push.files.added.json") + ("count" to "many")))
        assertNull(PushPayloadParser.parse(pushData("push.files.added.json") + ("count" to "0")))
        assertNull(PushPayloadParser.parse(pushData("push.homework.due.json") + ("dueAt" to "tomorrow")))
        assertNull(PushPayloadParser.parse(pushData("push.note.synthesized.json") + ("outcome" to "pending")))
    }

    @Test
    fun extraUnknownKeys_areIgnored() {
        val event = PushPayloadParser.parse(pushData("push.files.added.json") + ("newField" to "x"))
        assertEquals("files.added", event?.type)
    }
}
