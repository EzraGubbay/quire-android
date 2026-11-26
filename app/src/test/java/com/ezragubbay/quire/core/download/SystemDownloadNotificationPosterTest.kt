package com.ezragubbay.quire.core.download

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SystemDownloadNotificationPosterTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val poster = SystemDownloadNotificationPoster(app)

    @Before
    fun grant() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun summary(
        running: Int = 1,
        queued: Int = 0,
        total: Long? = 1000L,
    ) = DownloadProgressSummary(running, queued, 250L, total)

    private fun posted(): Notification {
        assertEquals(1, shadowOf(manager).allNotifications.size)
        return shadowOf(manager).allNotifications.single()
    }

    @Test
    fun postsOngoingSilentProgressNotificationOnLowChannel() {
        poster.show(summary(queued = 2))
        val n = posted()
        val channel = manager.getNotificationChannel(DOWNLOAD_CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertEquals("Downloads", channel.name.toString())
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(100, n.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertEquals(25, n.extras.getInt(Notification.EXTRA_PROGRESS))
        assertFalse(n.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertEquals("Downloading 1 file", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(
            n.extras
                .getCharSequence(Notification.EXTRA_TEXT)
                .toString()
                .contains("2 queued"),
        )
        assertEquals(DOWNLOAD_STALE_MS, n.timeoutAfter)
        assertNotNull(n.contentIntent)
    }

    @Test
    fun unknownTotalIsIndeterminateAndPluralTitle() {
        poster.show(summary(running = 3, total = null))
        val n = posted()
        assertTrue(n.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertEquals("Downloading 3 files", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    @Test
    fun secondShowReplacesFirst() {
        poster.show(summary())
        poster.show(summary(running = 2))
        val n = posted()
        assertEquals("Downloading 2 files", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertNotNull(shadowOf(manager).getNotification(null, DOWNLOAD_NOTIFICATION_ID))
    }

    @Test
    fun clearRemovesIt() {
        poster.show(summary())
        poster.clear()
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun deniedPermissionPostsNothing() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        poster.show(summary())
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun appNotificationsBlockedPostsNothing() {
        shadowOf(manager).setNotificationsEnabled(false)
        poster.show(summary())
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }
}
