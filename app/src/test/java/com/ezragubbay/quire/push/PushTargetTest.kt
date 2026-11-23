package com.ezragubbay.quire.push

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.ezragubbay.quire.MainActivity
import com.ezragubbay.quire.navigation.CourseRoute
import com.ezragubbay.quire.navigation.CoursesRoute
import com.ezragubbay.quire.navigation.NoteReaderRoute
import com.ezragubbay.quire.navigation.ViewerRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PushTargetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** The tap intent the notifier builds for [target] of [type] (same action and extras). */
    private fun tapIntent(
        type: String,
        target: PushTarget,
    ): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(PushTarget.ACTION_OPEN_FROM_PUSH)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { target.extras(type).forEach { (key, value) -> putExtra(key, value) } }

    private fun pushIntent(vararg extras: Pair<String, String?>): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(PushTarget.ACTION_OPEN_FROM_PUSH)
            .apply { extras.forEach { (key, value) -> putExtra(key, value) } }

    // --- fromIntent: every event type round-trips through the notifier's extras ---------------------

    @Test
    fun filesAdded_single_opensTheFile() {
        val target = PushTarget.File("signals-and-systems", "f1")
        assertEquals(target, PushTarget.fromIntent(tapIntent(PushEvent.TYPE_FILES_ADDED, target)))
    }

    @Test
    fun filesAdded_many_opensTheCourse() {
        val target = PushTarget.Course("signals-and-systems")
        assertEquals(target, PushTarget.fromIntent(tapIntent(PushEvent.TYPE_FILES_ADDED, target)))
    }

    @Test
    fun homeworkDue_withSheet_opensTheSheet() {
        val target = PushTarget.File("signals-and-systems", "sheet-1")
        assertEquals(target, PushTarget.fromIntent(tapIntent(PushEvent.TYPE_HOMEWORK_DUE, target)))
    }

    @Test
    fun homeworkDue_withoutSheet_opensTheCourse() {
        val target = PushTarget.Course("signals-and-systems")
        assertEquals(target, PushTarget.fromIntent(tapIntent(PushEvent.TYPE_HOMEWORK_DUE, target)))
    }

    @Test
    fun noteSynthesized_ready_opensTheNote() {
        val target = PushTarget.Note("signals-and-systems", "n1")
        assertEquals(target, PushTarget.fromIntent(tapIntent(PushEvent.TYPE_NOTE_SYNTHESIZED, target)))
    }

    @Test
    fun noteSynthesized_failed_opensTheCourse() {
        val target = PushTarget.Course("signals-and-systems")
        assertEquals(target, PushTarget.fromIntent(tapIntent(PushEvent.TYPE_NOTE_SYNTHESIZED, target)))
    }

    @Test
    fun pushTest_opensHome() {
        assertEquals(PushTarget.Home, PushTarget.fromIntent(tapIntent(PushEvent.TYPE_TEST, PushTarget.Home)))
    }

    @Test
    fun everyFactoryTarget_roundTrips() {
        listOf("push.files.added.json", "push.homework.due.json", "push.note.synthesized.json", "push.test.json")
            .map { requireNotNull(PushPayloadParser.parse(pushData(it))) }
            .forEach { event ->
                val target = PushNotificationFactory.target(event)
                assertEquals(target, PushTarget.fromIntent(tapIntent(event.type, target)))
            }
    }

    // --- fromIntent: missing or unexpected extras -----------------------------------------------------

    @Test
    fun notAPushIntent_isNull() {
        assertNull(PushTarget.fromIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)))
        assertNull(PushTarget.fromIntent(Intent(context, MainActivity::class.java)))
        assertNull(PushTarget.fromIntent(null))
    }

    @Test
    fun missingCourse_fallsBackToHome() {
        assertEquals(
            PushTarget.Home,
            PushTarget.fromIntent(pushIntent(PushTarget.EXTRA_TYPE to PushEvent.TYPE_FILES_ADDED, "fileId" to "f1")),
        )
        assertEquals(
            PushTarget.Home,
            PushTarget.fromIntent(pushIntent(PushTarget.EXTRA_TYPE to PushEvent.TYPE_FILES_ADDED, "course" to " ")),
        )
    }

    @Test
    fun missingType_orUnknownType_fallsBackToHome() {
        assertEquals(PushTarget.Home, PushTarget.fromIntent(pushIntent("course" to "c", "fileId" to "f1")))
        assertEquals(
            PushTarget.Home,
            PushTarget.fromIntent(pushIntent(PushTarget.EXTRA_TYPE to "course.archived", "course" to "c")),
        )
        assertEquals(PushTarget.Home, PushTarget.fromIntent(pushIntent()))
    }

    @Test
    fun missingFileOrNoteId_fallsBackToTheCourse() {
        assertEquals(
            PushTarget.Course("c"),
            PushTarget.fromIntent(pushIntent(PushTarget.EXTRA_TYPE to PushEvent.TYPE_FILES_ADDED, "course" to "c")),
        )
        assertEquals(
            PushTarget.Course("c"),
            PushTarget.fromIntent(
                pushIntent(PushTarget.EXTRA_TYPE to PushEvent.TYPE_HOMEWORK_DUE, "course" to "c", "fileId" to ""),
            ),
        )
        assertEquals(
            PushTarget.Course("c"),
            PushTarget.fromIntent(pushIntent(PushTarget.EXTRA_TYPE to PushEvent.TYPE_NOTE_SYNTHESIZED, "course" to "c")),
        )
    }

    @Test
    fun aNoteIdOnAFilesEvent_isIgnored() {
        assertEquals(
            PushTarget.Course("c"),
            PushTarget.fromIntent(
                pushIntent(PushTarget.EXTRA_TYPE to PushEvent.TYPE_FILES_ADDED, "course" to "c", "noteId" to "n1"),
            ),
        )
    }

    // --- Replay guards (cold start) -------------------------------------------------------------------

    @Test
    fun launch_freshActivity_handlesThePush() {
        val target = PushTarget.File("c", "f1")
        assertEquals(
            target,
            PushTarget.fromLaunchIntent(tapIntent(PushEvent.TYPE_FILES_ADDED, target), restored = false),
        )
    }

    @Test
    fun launch_restoredActivity_ignoresThePush() {
        val intent = tapIntent(PushEvent.TYPE_FILES_ADDED, PushTarget.File("c", "f1"))
        assertNull(PushTarget.fromLaunchIntent(intent, restored = true))
    }

    @Test
    fun launch_fromRecents_ignoresThePush() {
        val intent =
            tapIntent(PushEvent.TYPE_FILES_ADDED, PushTarget.File("c", "f1"))
                .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(PushTarget.fromLaunchIntent(intent, restored = false))
    }

    @Test
    fun launch_fromTheLauncher_isNull() {
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
        assertNull(PushTarget.fromLaunchIntent(intent, restored = false))
    }

    // --- backStack ------------------------------------------------------------------------------------

    @Test
    fun backStack_walksCoursesThenCourseThenTarget() {
        assertEquals(listOf(CoursesRoute), PushTarget.Home.backStack())
        assertEquals(listOf(CoursesRoute, CourseRoute("c")), PushTarget.Course("c").backStack())
        assertEquals(
            listOf(CoursesRoute, CourseRoute("c"), ViewerRoute("c", "f1")),
            PushTarget.File("c", "f1").backStack(),
        )
        assertEquals(
            listOf(CoursesRoute, CourseRoute("c"), NoteReaderRoute("c", "n1")),
            PushTarget.Note("c", "n1").backStack(),
        )
    }
}
