package com.ezragubbay.quire.push

import android.Manifest
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

/** Posts a built notification. A seam so the handler is testable without the notification system. */
fun interface PushNotifier {
    /** Returns false when nothing was posted (notifications denied or off). */
    fun post(spec: PushNotificationSpec): Boolean
}

/** True when the app may post notifications: the runtime permission (Android 13+) and the app-level switch. */
fun notificationsAllowed(context: Context): Boolean {
    val permitted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/** [PushNotifier] over the platform: tag per [PushNotificationSpec.tag], explicit tap intent to `MainActivity`. */
class SystemPushNotifier(
    private val context: Context,
) : PushNotifier {
    override fun post(spec: PushNotificationSpec): Boolean {
        if (!notificationsAllowed(context)) return false
        // Idempotent. QuireApplication creates the channels at start only when push is configured; posting must not
        // depend on that (instrumented tests run under the Hilt test application, which never creates them).
        PushChannels.ensure(context)
        val intent =
            Intent(context, MainActivity::class.java)
                .setAction(PushTarget.ACTION_OPEN_FROM_PUSH)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
        spec.extras.forEach { (key, value) -> intent.putExtra(key, value) }
        val tap =
            PendingIntent.getActivity(
                context,
                spec.eventId.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat
                .Builder(context, spec.channelId)
                .setSmallIcon(R.drawable.ic_stat_quire)
                .setContentTitle(spec.title)
                .setContentText(spec.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(spec.body))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()
        return try {
            NotificationManagerCompat.from(context).notify(spec.tag, NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
            false
        }
    }

    companion object {
        /** All push notifications share this id; the tag tells them apart (and replaces older versions). */
        const val NOTIFICATION_ID = 8001
    }
}
