package com.ezragubbay.quire.core.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ezragubbay.quire.MainActivity
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.database.DownloadEntity
import com.ezragubbay.quire.push.notificationsAllowed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

const val DOWNLOAD_CHANNEL_ID = "downloads"
const val DOWNLOAD_NOTIFICATION_ID = 7003
const val DOWNLOAD_THROTTLE_MS = 500L
private const val PROGRESS_MAX = 100
private const val OPEN_FLAGS =
    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
private const val HEARTBEAT_MS = DOWNLOAD_STALE_MS / 2

/** Shows or clears the download progress notification. A seam so the observer is testable without the system. */
interface DownloadNotificationPoster {
    fun show(summary: DownloadProgressSummary)

    fun clear()
}

/** [DownloadNotificationPoster] over the platform notification manager (silent, ongoing, low importance). */
class SystemDownloadNotificationPoster(
    private val context: Context,
) : DownloadNotificationPoster {
    override fun show(summary: DownloadProgressSummary) {
        if (!notificationsAllowed(context)) return
        ensureChannel()
        try {
            val notification = buildDownloadNotification(context, summary)
            NotificationManagerCompat.from(context).notify(DOWNLOAD_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    override fun clear() {
        try {
            NotificationManagerCompat.from(context).cancel(DOWNLOAD_NOTIFICATION_ID)
        } catch (_: SecurityException) {
            // Nothing to cancel.
        }
    }

    private fun ensureChannel() {
        val channel =
            NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                context.getString(R.string.downloads_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

internal fun buildDownloadNotification(
    context: Context,
    summary: DownloadProgressSummary,
): Notification {
    val res = context.resources
    val text =
        buildString {
            summary.totalBytes?.let {
                append(
                    context.getString(
                        R.string.downloads_progress_text,
                        Formatter.formatShortFileSize(context, summary.progressBytes),
                        Formatter.formatShortFileSize(context, it),
                    ),
                )
            }
            if (summary.queued > 0) {
                if (isNotEmpty()) append(" · ")
                append(res.getQuantityString(R.plurals.downloads_progress_queued, summary.queued, summary.queued))
            }
        }
    val open =
        PendingIntent.getActivity(
            context,
            DOWNLOAD_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .addFlags(OPEN_FLAGS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    val percent = summary.percent
    return NotificationCompat
        .Builder(context, DOWNLOAD_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(res.getQuantityString(R.plurals.downloads_progress_title, summary.running, summary.running))
        .apply { if (text.isNotEmpty()) setContentText(text) }
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setProgress(PROGRESS_MAX, percent ?: 0, percent == null)
        // Safety net: if updates stop (worker killed), the notification removes itself.
        .setTimeoutAfter(DOWNLOAD_STALE_MS)
        .setContentIntent(open)
        .build()
}

/**
 * Observes the downloads table and keeps one summary notification in step with it. Purely a Room observer, so it
 * covers user downloads and pinned auto-downloads alike, and clears itself when rows settle or are removed.
 */
@OptIn(FlowPreview::class)
class DownloadProgressNotifier(
    private val rows: Flow<List<DownloadEntity>>,
    private val poster: DownloadNotificationPoster,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val started = AtomicBoolean(false)

    private class Snapshot(
        val summary: DownloadProgressSummary?,
        val bucket: Long,
    )

    /** Idempotent. Clears any leftover from a previous process, then follows the table. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        poster.clear()
        scope.launch {
            rows
                .map {
                    val now = clock()
                    Snapshot(summarize(it, now), now / HEARTBEAT_MS)
                }
                // Byte counts change constantly; only what the user can see (counts, whole percent) re-posts. The
                // heartbeat bucket also re-posts a live download at least every HEARTBEAT_MS, so the notification's
                // own timeout (DOWNLOAD_STALE_MS) never removes one that is still being written; a dead worker
                // emits nothing, so the timeout still clears it.
                .distinctUntilChangedBy { snap ->
                    snap.summary?.let { s -> listOf(s.running, s.queued, s.percent, snap.bucket) }
                }.map { it.summary }
                .sample(DOWNLOAD_THROTTLE_MS)
                .collect { if (it == null) poster.clear() else poster.show(it) }
        }
    }
}
