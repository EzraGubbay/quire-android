package com.ezragubbay.quire.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Thin FCM adapter (ADR 0003 §4.3), not exported. All logic lives in [PushMessageHandler] and [PushRegistrar], which
 * are tested without Firebase. Only reached in builds with `google-services.json`; otherwise FCM never starts it.
 */
@AndroidEntryPoint
class QuireMessagingService : FirebaseMessagingService() {
    @Inject lateinit var handler: PushMessageHandler

    @Inject lateinit var registration: PushRegistrationScheduler

    @Inject lateinit var sync: PushSyncTrigger

    /** Runs synchronously on FCM's thread, within the handler's own deadline. */
    override fun onMessageReceived(message: RemoteMessage) {
        handler.handle(message.data)
    }

    /** A new token: register it through the retrying worker (this service has no UI scope). */
    override fun onNewToken(token: String) {
        registration.enqueue()
    }

    /** FCM dropped messages stored for this device: catch up with one metadata sync. */
    override fun onDeletedMessages() {
        runBlocking { sync.onMessagesDeleted() }
    }
}
