package com.ezragubbay.quire.push

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Names resolved on receipt (Room, then the API). Anything null falls back to generic text. */
data class PushNames(
    val courseName: String? = null,
    /** The file, sheet or note title. */
    val itemTitle: String? = null,
    /** The file's server type label (`lecture`, `homework`, ...), for `files.added`. */
    val itemType: String? = null,
)

/** Everything needed to post one notification; built by [PushNotificationFactory], posted by [PushNotifier]. */
data class PushNotificationSpec(
    val eventId: String,
    val eventType: String,
    val channelId: String,
    /** The notification tag: a newer event with the same tag replaces the older notification (ADR 0003 §2). */
    val tag: String,
    val title: String,
    val body: String,
    val target: PushTarget,
) {
    /** Extras for the tap intent. */
    val extras: Map<String, String> get() = target.extras(eventType)
}

/** The user-visible strings, as a seam so the mapping stays pure. Backed by resources in the app. */
interface PushTexts {
    val appName: String

    fun fileTypeLabel(type: String): String

    fun newFileTyped(
        typeLabel: String,
        title: String,
    ): String

    /** Fallback when the file title is unknown; [courseName] null means "a course". */
    fun newFileIn(courseName: String?): String

    fun newFiles(count: Int): String

    fun homeworkDueTitle(
        number: String,
        due: String,
    ): String

    fun dueToday(time: String): String

    fun dueTomorrow(time: String): String

    fun dueOn(
        date: String,
        time: String,
    ): String

    val homeworkFallbackBody: String
    val noteReadyTitle: String

    fun noteReadyFallback(courseName: String?): String

    val noteFailedTitle: String
    val noteFailedBody: String
    val testTitle: String
    val testBody: String
}

/**
 * Pure mapping from a [PushEvent] plus resolved [PushNames] to a [PushNotificationSpec]: channel, tag, title/body and
 * tap target (ADR 0003 §1, §2). Times are rendered in the device time zone.
 */
class PushNotificationFactory(
    private val texts: PushTexts,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val now: () -> Instant = Instant::now,
    private val locale: () -> Locale = Locale::getDefault,
) {
    fun build(
        event: PushEvent,
        names: PushNames = PushNames(),
    ): PushNotificationSpec {
        val (title, body) = text(event, names)
        return PushNotificationSpec(
            eventId = event.eventId,
            eventType = event.type,
            channelId = PushChannels.forEvent(event),
            tag = tag(event),
            title = title,
            body = body,
            target = target(event),
        )
    }

    private fun text(
        event: PushEvent,
        names: PushNames,
    ): Pair<String, String> =
        when (event) {
            is PushEvent.FilesAdded -> filesText(event, names)
            is PushEvent.HomeworkDue ->
                texts.homeworkDueTitle(event.number, dueLabel(event.dueAt)) to
                    listOfNotNull(names.courseName, names.itemTitle)
                        .joinToString(SEPARATOR)
                        .ifEmpty { texts.homeworkFallbackBody }
            is PushEvent.NoteSynthesized ->
                if (event.ready) {
                    texts.noteReadyTitle to (names.itemTitle ?: texts.noteReadyFallback(names.courseName))
                } else {
                    texts.noteFailedTitle to texts.noteFailedBody
                }
            is PushEvent.Test -> texts.testTitle to texts.testBody
        }

    private fun filesText(
        event: PushEvent.FilesAdded,
        names: PushNames,
    ): Pair<String, String> {
        val title = names.courseName ?: texts.appName
        val body =
            when {
                event.count > 1 -> texts.newFiles(event.count)
                names.itemTitle != null ->
                    texts.newFileTyped(texts.fileTypeLabel(names.itemType.orEmpty()), names.itemTitle)
                else -> texts.newFileIn(names.courseName)
            }
        return title to body
    }

    private fun dueLabel(dueAt: Instant): String {
        val zone = zone()
        val due = dueAt.atZone(zone)
        val today = now().atZone(zone).toLocalDate()
        val time = due.format(DateTimeFormatter.ofPattern(TIME_PATTERN, locale()))
        return when (due.toLocalDate()) {
            today -> texts.dueToday(time)
            today.plusDays(1) -> texts.dueTomorrow(time)
            else -> {
                val date = due.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale()))
                texts.dueOn(date, time)
            }
        }
    }

    companion object {
        private const val SEPARATOR = " · "
        private const val TIME_PATTERN = "HH:mm"

        /** The notification tag (ADR 0003 §2): one per course for files, per assignment, per note, one for tests. */
        fun tag(event: PushEvent): String =
            when (event) {
                is PushEvent.FilesAdded -> "files:${event.courseId}"
                is PushEvent.HomeworkDue -> "homework:${event.assignmentId}"
                is PushEvent.NoteSynthesized -> "note:${event.noteId}"
                is PushEvent.Test -> "test"
            }

        /** What a tap opens (ADR 0003 §1 "Tap opens"). */
        fun target(event: PushEvent): PushTarget =
            when (event) {
                is PushEvent.FilesAdded ->
                    if (event.count == 1) {
                        PushTarget.File(event.courseSlug, event.fileId)
                    } else {
                        PushTarget.Course(event.courseSlug)
                    }
                is PushEvent.HomeworkDue ->
                    event.fileId?.let { PushTarget.File(event.courseSlug, it) } ?: PushTarget.Course(event.courseSlug)
                is PushEvent.NoteSynthesized ->
                    if (event.ready) {
                        PushTarget.Note(event.courseSlug, event.noteId)
                    } else {
                        PushTarget.Course(event.courseSlug)
                    }
                is PushEvent.Test -> PushTarget.Home
            }
    }
}
