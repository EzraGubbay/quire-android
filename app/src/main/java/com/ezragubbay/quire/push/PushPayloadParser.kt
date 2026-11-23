package com.ezragubbay.quire.push

import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Turns an FCM data map (every value a string) into a [PushEvent] (ADR 0003 §2). Pure. Anything this app version
 * does not understand is dropped (null): an unknown `v` or `type`, a missing or blank id, a malformed number or time.
 * That lets the server add events without breaking older clients.
 */
object PushPayloadParser {
    const val SUPPORTED_VERSION = "1"

    private const val OUTCOME_READY = "ready"
    private const val OUTCOME_FAILED = "failed"

    fun parse(data: Map<String, String>): PushEvent? {
        val eventId = data.id("eventId")
        if (data["v"] != SUPPORTED_VERSION || eventId == null) return null
        return when (data["type"]) {
            PushEvent.TYPE_FILES_ADDED -> filesAdded(eventId, data)
            PushEvent.TYPE_HOMEWORK_DUE -> homeworkDue(eventId, data)
            PushEvent.TYPE_NOTE_SYNTHESIZED -> noteSynthesized(eventId, data)
            PushEvent.TYPE_TEST -> PushEvent.Test(eventId)
            else -> null
        }
    }

    private fun filesAdded(
        eventId: String,
        data: Map<String, String>,
    ): PushEvent? {
        val course = data.courseRef()
        val fileId = data.id("fileId")
        val count = data["count"]?.toIntOrNull()?.takeIf { it >= 1 }
        return if (course == null || fileId == null || count == null) {
            null
        } else {
            PushEvent.FilesAdded(eventId, course.slug, course.id, fileId, count)
        }
    }

    private fun homeworkDue(
        eventId: String,
        data: Map<String, String>,
    ): PushEvent? {
        val course = data.courseRef()
        val assignmentId = data.id("assignmentId")
        val number = data.id("number")
        if (course == null || assignmentId == null || number == null) return null
        // fileId is optional: omitted when the assignment has no sheet document.
        return data["dueAt"]?.let(::instantOrNull)?.let { dueAt ->
            PushEvent.HomeworkDue(eventId, course.slug, course.id, assignmentId, number, dueAt, data.id("fileId"))
        }
    }

    private fun noteSynthesized(
        eventId: String,
        data: Map<String, String>,
    ): PushEvent? {
        val course = data.courseRef()
        val noteId = data.id("noteId")
        val ready =
            when (data["outcome"]) {
                OUTCOME_READY -> true
                OUTCOME_FAILED -> false
                else -> null
            }
        return if (course == null || noteId == null || ready == null) {
            null
        } else {
            PushEvent.NoteSynthesized(eventId, course.slug, course.id, noteId, ready)
        }
    }

    private class CourseRef(
        val slug: String,
        val id: String,
    )

    private fun Map<String, String>.courseRef(): CourseRef? {
        val slug = id("course")
        val courseId = id("courseId")
        return if (slug != null && courseId != null) CourseRef(slug, courseId) else null
    }

    private fun Map<String, String>.id(key: String): String? = this[key]?.trim()?.takeIf { it.isNotEmpty() }

    private fun instantOrNull(value: String): Instant? =
        try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }
}
