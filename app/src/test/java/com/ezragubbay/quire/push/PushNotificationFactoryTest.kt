package com.ezragubbay.quire.push

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class PushNotificationFactoryTest {
    private val jerusalem = ZoneId.of("Asia/Jerusalem")
    private var now = Instant.parse("2026-11-24T10:00:00Z")
    private val factory = PushNotificationFactory(EnglishTexts, { jerusalem }, { now }, { Locale.US })

    private fun event(name: String) = PushPayloadParser.parse(pushData(name))!!

    @Test
    fun filesAdded_single_resolved() {
        val spec =
            factory.build(
                event("push.files.added.json"),
                PushNames(courseName = "Signals and Systems", itemTitle = "Lecture 3", itemType = "lecture"),
            )
        assertEquals(PushChannels.FILES, spec.channelId)
        assertEquals("files:3f0b1c7e-2a7d-4d8e-9b1a-6c5e4f3a2b10", spec.tag)
        assertEquals("Signals and Systems", spec.title)
        assertEquals("New lecture: Lecture 3", spec.body)
        assertEquals(
            PushTarget.File("signals-and-systems", "9c4e2b1a-7d3f-4a6b-8e2c-1f0a9b8c7d6e"),
            spec.target,
        )
        assertEquals(
            mapOf(
                "type" to "files.added",
                "course" to "signals-and-systems",
                "fileId" to "9c4e2b1a-7d3f-4a6b-8e2c-1f0a9b8c7d6e",
            ),
            spec.extras,
        )
    }

    @Test
    fun filesAdded_single_unresolved_fallsBackToGenericText() {
        val spec = factory.build(event("push.files.added.json"))
        assertEquals("Quire", spec.title)
        assertEquals("New file in a course", spec.body)

        val courseOnly = factory.build(event("push.files.added.json"), PushNames(courseName = "Signals"))
        assertEquals("Signals", courseOnly.title)
        assertEquals("New file in Signals", courseOnly.body)
    }

    @Test
    fun filesAdded_many_opensCourse_sameTagReplacesOlderBatch() {
        val single = factory.build(event("push.files.added.json"))
        val many =
            factory.build(
                PushPayloadParser.parse(pushData("push.files.added.json") + ("count" to "5"))!!,
                PushNames(courseName = "Signals and Systems"),
            )
        assertEquals("5 new files", many.body)
        assertEquals(PushTarget.Course("signals-and-systems"), many.target)
        assertEquals(mapOf("type" to "files.added", "course" to "signals-and-systems"), many.extras)
        assertEquals(single.tag, many.tag)
    }

    @Test
    fun homeworkDue_tomorrowInDeviceZone_withSheet() {
        // dueAt 2026-11-24T21:59Z is 23:59 on 24 Nov in Jerusalem (UTC+2); now is 23 Nov there.
        now = Instant.parse("2026-11-23T10:00:00Z")
        val spec =
            factory.build(
                event("push.homework.due.json"),
                PushNames(courseName = "Signals and Systems", itemTitle = "HW 3 sheet"),
            )
        assertEquals(PushChannels.DEADLINES, spec.channelId)
        assertEquals("homework:0e9d8c7b-6a5f-4e3d-9c2b-1a0f9e8d7c6b", spec.tag)
        assertEquals("Homework 3 due tomorrow 23:59", spec.title)
        assertEquals("Signals and Systems · HW 3 sheet", spec.body)
        assertEquals(PushTarget.File("signals-and-systems", "9c4e2b1a-7d3f-4a6b-8e2c-1f0a9b8c7d6e"), spec.target)
    }

    @Test
    fun homeworkDue_today_andWithoutSheetOpensCourse() {
        now = Instant.parse("2026-11-24T08:00:00Z")
        val noSheet = PushPayloadParser.parse(pushData("push.homework.due.json") - "fileId")!!
        val spec = factory.build(noSheet)
        assertEquals("Homework 3 due today 23:59", spec.title)
        assertEquals("Open Quire for the details.", spec.body)
        assertEquals(PushTarget.Course("signals-and-systems"), spec.target)
    }

    @Test
    fun noteSynthesized_readyOpensNote_failedOpensCourse() {
        val ready = factory.build(event("push.note.synthesized.json"), PushNames(itemTitle = "Lecture 3 notes"))
        assertEquals(PushChannels.NOTES, ready.channelId)
        assertEquals("note:2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f", ready.tag)
        assertEquals("Study note ready", ready.title)
        assertEquals("Lecture 3 notes", ready.body)
        assertEquals(PushTarget.Note("signals-and-systems", "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f"), ready.target)
        assertEquals("2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f", ready.extras["noteId"])

        val fallback = factory.build(event("push.note.synthesized.json"))
        assertEquals("A new study note is ready in a course.", fallback.body)

        val failed =
            factory.build(PushPayloadParser.parse(pushData("push.note.synthesized.json") + ("outcome" to "failed"))!!)
        assertEquals("Note synthesis failed", failed.title)
        assertEquals("Open Quire on the web to retry.", failed.body)
        assertEquals(PushTarget.Course("signals-and-systems"), failed.target)
        assertEquals(ready.tag, failed.tag)
    }

    @Test
    fun test_postsOnSystemChannel_opensHome() {
        val spec = factory.build(event("push.test.json"))
        assertEquals(PushChannels.SYSTEM, spec.channelId)
        assertEquals("test", spec.tag)
        assertEquals("Quire notifications work", spec.title)
        assertEquals("Test sent from your server.", spec.body)
        assertEquals(PushTarget.Home, spec.target)
        assertEquals(mapOf("type" to "push.test"), spec.extras)
        assertEquals("7a1c2f0e-5b7d-4c2a-9a51-0f3e6d1b2c44", spec.eventId)
    }
}
