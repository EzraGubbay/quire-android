package com.ezragubbay.quire.push

import android.content.Intent
import com.ezragubbay.quire.navigation.CourseRoute
import com.ezragubbay.quire.navigation.CoursesRoute
import com.ezragubbay.quire.navigation.NoteReaderRoute
import com.ezragubbay.quire.navigation.ViewerRoute

/**
 * Where tapping a push notification leads (ADR 0003 §1 "Tap opens", §4.5). The notification carries it as extras on
 * an explicit [ACTION_OPEN_FROM_PUSH] intent to `MainActivity`, which reads it back with [fromIntent] /
 * [fromLaunchIntent] and hands it to `AppViewModel.openFromPush`; `AppShell` navigates along [backStack].
 */
sealed interface PushTarget {
    data object Home : PushTarget

    data class Course(
        val courseSlug: String,
    ) : PushTarget

    data class File(
        val courseSlug: String,
        val fileId: String,
    ) : PushTarget

    data class Note(
        val courseSlug: String,
        val noteId: String,
    ) : PushTarget

    /** The shell routes a tap opens, bottom first: Courses → course → file / note. Back walks them in reverse. */
    fun backStack(): List<Any> =
        when (this) {
            Home -> listOf(CoursesRoute)
            is Course -> listOf(CoursesRoute, CourseRoute(courseSlug))
            is File -> listOf(CoursesRoute, CourseRoute(courseSlug), ViewerRoute(courseSlug, fileId))
            is Note -> listOf(CoursesRoute, CourseRoute(courseSlug), NoteReaderRoute(courseSlug, noteId))
        }

    /** The intent extras for this target ([EXTRA_TYPE] is the event's wire type). */
    fun extras(eventType: String): Map<String, String> =
        buildMap {
            put(EXTRA_TYPE, eventType)
            when (val target = this@PushTarget) {
                Home -> Unit
                is Course -> put(EXTRA_COURSE, target.courseSlug)
                is File -> {
                    put(EXTRA_COURSE, target.courseSlug)
                    put(EXTRA_FILE_ID, target.fileId)
                }
                is Note -> {
                    put(EXTRA_COURSE, target.courseSlug)
                    put(EXTRA_NOTE_ID, target.noteId)
                }
            }
        }

    companion object {
        const val ACTION_OPEN_FROM_PUSH = "com.ezragubbay.quire.action.OPEN_FROM_PUSH"
        const val EXTRA_TYPE = "type"
        const val EXTRA_COURSE = "course"
        const val EXTRA_FILE_ID = "fileId"
        const val EXTRA_NOTE_ID = "noteId"

        /**
         * The target of a notification tap, or null when [intent] is not one ([ACTION_OPEN_FROM_PUSH]). Missing,
         * blank or unknown extras never fail: no course or an unknown type opens [Home]; a missing file or note id
         * opens the [Course]. `MainActivity` is exported, so the extras are untrusted: they only pick a screen.
         */
        fun fromIntent(intent: Intent?): PushTarget? {
            if (intent?.action != ACTION_OPEN_FROM_PUSH) return null
            return from(
                type = intent.getStringExtra(EXTRA_TYPE),
                course = intent.getStringExtra(EXTRA_COURSE),
                fileId = intent.getStringExtra(EXTRA_FILE_ID),
                noteId = intent.getStringExtra(EXTRA_NOTE_ID),
            )
        }

        /**
         * [fromIntent] for the intent that created the activity, guarded against replays (ADR 0003 §4.5): null when
         * the activity was [restored] (configuration change, process-death restore) or relaunched from Recents
         * ([Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY]), where the intent is the old notification's.
         */
        fun fromLaunchIntent(
            intent: Intent?,
            restored: Boolean,
        ): PushTarget? {
            val fromRecents = intent != null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
            return if (restored || fromRecents) null else fromIntent(intent)
        }

        private fun from(
            type: String?,
            course: String?,
            fileId: String?,
            noteId: String?,
        ): PushTarget {
            val slug = course?.takeIf { it.isNotBlank() } ?: return Home
            val file = fileId?.takeIf { it.isNotBlank() }
            val note = noteId?.takeIf { it.isNotBlank() }
            return when (type) {
                PushEvent.TYPE_FILES_ADDED, PushEvent.TYPE_HOMEWORK_DUE -> file?.let { File(slug, it) } ?: Course(slug)
                PushEvent.TYPE_NOTE_SYNTHESIZED -> note?.let { Note(slug, it) } ?: Course(slug)
                else -> Home
            }
        }
    }
}
