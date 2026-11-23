package com.ezragubbay.quire.push

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PushChannelsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test
    fun ensure_createsOneChannelPerCategoryPlusSystem_withTheirImportance() {
        PushChannels.ensure(context)

        val channels = PushChannels.ALL.associateWith { manager.getNotificationChannel(it) }
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channels.getValue("push_files").importance)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channels.getValue("push_deadlines").importance)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channels.getValue("push_notes").importance)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channels.getValue("push_system").importance)
        assertEquals("New course files", channels.getValue("push_files").name)
        assertEquals("Homework deadlines", channels.getValue("push_deadlines").name)
        assertEquals("Study notes", channels.getValue("push_notes").name)
        assertEquals("Quire", channels.getValue("push_system").name)
    }

    @Test
    fun ensure_isIdempotent() {
        PushChannels.ensure(context)
        PushChannels.ensure(context)

        assertEquals(4, manager.notificationChannels.count { it.id.startsWith("push_") })
    }

    @Test
    fun eachEventPostsOnItsCategoryChannel() {
        val channels =
            listOf("push.files.added.json", "push.homework.due.json", "push.note.synthesized.json", "push.test.json")
                .map { PushChannels.forEvent(PushPayloadParser.parse(pushData(it))!!) }
        assertEquals(listOf("push_files", "push_deadlines", "push_notes", "push_system"), channels)
    }

    @Test
    fun resourceTexts_renderTheAdrStrings() {
        val texts = ResourcePushTexts(context)
        assertEquals("New Lecture: Lecture 3", texts.newFileTyped(texts.fileTypeLabel("lecture"), "Lecture 3"))
        assertEquals("New file in a course", texts.newFileIn(null))
        assertEquals("5 new files", texts.newFiles(5))
        assertEquals("Homework 3 due tomorrow 23:59", texts.homeworkDueTitle("3", texts.dueTomorrow("23:59")))
        assertEquals("Open Quire on the web to retry.", texts.noteFailedBody)
        assertEquals("Quire notifications work", texts.testTitle)
    }
}
