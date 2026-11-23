package com.ezragubbay.quire.push

import java.time.Instant

/** A user-facing push toggle (ADR 0003 §1). [wire] is the value sent in `PUT /push/registration` `categories`. */
enum class PushCategory(
    val wire: String,
) {
    Files("files"),
    Deadlines("deadlines"),
    Notes("notes"),
}

/** A parsed, validated push (ADR 0003 §1–2). Payloads carry ids only; names are resolved on receipt. */
sealed interface PushEvent {
    val eventId: String

    /** The wire `type`, also passed to the tap intent. */
    val type: String

    /** The toggle that governs this event; null for events that are always on ([Test]). */
    val category: PushCategory?

    /** New course files; a burst is coalesced server-side into one event with [count] > 1. */
    data class FilesAdded(
        override val eventId: String,
        val courseSlug: String,
        val courseId: String,
        /** The newest file in the batch. */
        val fileId: String,
        val count: Int,
    ) : PushEvent {
        override val type get() = TYPE_FILES_ADDED
        override val category get() = PushCategory.Files
    }

    /** Homework due within 24 h. [fileId] is the assignment sheet, when there is one. */
    data class HomeworkDue(
        override val eventId: String,
        val courseSlug: String,
        val courseId: String,
        val assignmentId: String,
        val number: String,
        val dueAt: Instant,
        val fileId: String?,
    ) : PushEvent {
        override val type get() = TYPE_HOMEWORK_DUE
        override val category get() = PushCategory.Deadlines
    }

    /** A synthesized study note finished ([ready]) or failed. */
    data class NoteSynthesized(
        override val eventId: String,
        val courseSlug: String,
        val courseId: String,
        val noteId: String,
        val ready: Boolean,
    ) : PushEvent {
        override val type get() = TYPE_NOTE_SYNTHESIZED
        override val category get() = PushCategory.Notes
    }

    /** "Send test notification" from Settings. */
    data class Test(
        override val eventId: String,
    ) : PushEvent {
        override val type get() = TYPE_TEST
        override val category: PushCategory? get() = null
    }

    companion object {
        const val TYPE_FILES_ADDED = "files.added"
        const val TYPE_HOMEWORK_DUE = "homework.due"
        const val TYPE_NOTE_SYNTHESIZED = "note.synthesized"
        const val TYPE_TEST = "push.test"
    }
}
