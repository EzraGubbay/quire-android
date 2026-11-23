package com.ezragubbay.quire.push

/**
 * Where tapping a push notification leads (ADR 0003 §1 "Tap opens", §4.5). The notification carries it as extras on
 * an explicit [ACTION_OPEN_FROM_PUSH] intent to `MainActivity`. Reading it back and navigating is QMOB-E8-S4 (#64);
 * until then a tap opens the app at its start screen.
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
    }
}
