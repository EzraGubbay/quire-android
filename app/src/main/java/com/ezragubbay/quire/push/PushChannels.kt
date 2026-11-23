package com.ezragubbay.quire.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.ezragubbay.quire.R

/** The push notification channels, one per category plus `push_system` (ADR 0003 §4.4). The E7 `sync` is separate. */
object PushChannels {
    const val FILES = "push_files"
    const val DEADLINES = "push_deadlines"
    const val NOTES = "push_notes"
    const val SYSTEM = "push_system"

    val ALL = listOf(FILES, DEADLINES, NOTES, SYSTEM)

    /** The channel an event posts on. */
    fun forEvent(event: PushEvent): String =
        when (event) {
            is PushEvent.FilesAdded -> FILES
            is PushEvent.HomeworkDue -> DEADLINES
            is PushEvent.NoteSynthesized -> NOTES
            is PushEvent.Test -> SYSTEM
        }

    /** Creates (or refreshes the names of) every push channel. Idempotent: the user's own channel settings are kept. */
    fun ensure(context: Context) {
        val channels =
            listOf(
                NotificationChannel(
                    FILES,
                    context.getString(R.string.push_channel_files),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    DEADLINES,
                    context.getString(R.string.push_channel_deadlines),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
                NotificationChannel(
                    NOTES,
                    context.getString(R.string.push_channel_notes),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    SYSTEM,
                    context.getString(R.string.push_channel_system),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(channels)
    }
}
