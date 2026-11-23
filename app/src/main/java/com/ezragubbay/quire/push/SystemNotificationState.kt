package com.ezragubbay.quire.push

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** What the system allows, as Settings → Notifications shows it (ADR 0003 §4.4, §4.6). */
data class SystemNotificationState(
    /** Android 13+ and `POST_NOTIFICATIONS` not granted: turning a category on asks for it. */
    val permissionMissing: Boolean = false,
    /** The permission is granted (or not needed) but the app's notifications are off in system settings. */
    val appBlocked: Boolean = false,
    /** Categories whose channel the user blocked in system settings. */
    val blockedChannels: Set<PushCategory> = emptySet(),
) {
    /** The app can post at all. */
    val allowed: Boolean get() = !permissionMissing && !appBlocked
}

/** Reads [SystemNotificationState]; a seam so the Settings state is testable on the JVM. */
fun interface SystemNotificationStateReader {
    fun read(): SystemNotificationState
}

/** [SystemNotificationStateReader] over the platform. Cheap: read it again on every resume. */
class AndroidSystemNotificationState(
    private val context: Context,
) : SystemNotificationStateReader {
    override fun read(): SystemNotificationState {
        val permissionMissing =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(NotificationManager::class.java)
        return SystemNotificationState(
            permissionMissing = permissionMissing,
            appBlocked = !permissionMissing && !NotificationManagerCompat.from(context).areNotificationsEnabled(),
            blockedChannels =
                PushCategory.entries
                    .filter {
                        manager.getNotificationChannel(PushChannels.forCategory(it))?.importance ==
                            NotificationManager.IMPORTANCE_NONE
                    }.toSet(),
        )
    }
}

/** One push channel's page in system notification settings. */
fun channelSettingsIntent(
    context: Context,
    category: PushCategory,
): Intent =
    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, PushChannels.forCategory(category))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
