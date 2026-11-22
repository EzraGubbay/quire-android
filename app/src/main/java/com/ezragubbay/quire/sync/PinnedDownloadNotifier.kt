package com.ezragubbay.quire.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ezragubbay.quire.MainActivity
import com.ezragubbay.quire.R

/**
 * Posts one 'N new files downloaded' notification per course (channel [CHANNEL_ID]) when a pinned course's
 * auto-download batch has fully settled. Silent when the user turned it off or notifications are not allowed.
 */
class PinnedDownloadNotifier(
    private val context: Context,
    private val settings: OfflineSettingsStore,
    private val courseName: suspend (slug: String) -> String?,
) {
    /** Called by the download worker whenever a download settles. */
    suspend fun onDownloadSettled(
        slug: String,
        fileId: String,
        success: Boolean,
    ) {
        val count = settings.settle(slug, fileId, success) ?: return
        if (count > 0 && settings.current().notify) post(slug, count)
    }

    private suspend fun post(
        slug: String,
        count: Int,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled() || !hasPermission()) return
        ensureChannel()
        val open =
            PendingIntent.getActivity(
                context,
                slug.hashCode(),
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(courseName(slug) ?: slug)
                .setContentText(context.resources.getQuantityString(R.plurals.pin_new_files_downloaded, count, count))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        // One per course: a later batch replaces the earlier summary instead of stacking.
        try {
            manager.notify(slug, NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    private fun hasPermission() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.sync_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "sync"
        private const val NOTIFICATION_ID = 7002
    }
}
