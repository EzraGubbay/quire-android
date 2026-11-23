package com.ezragubbay.quire.push

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * The only door to Firebase (ADR 0003 §4.1). Hilt binds [FirebaseTokenSource] when the build had
 * `app/google-services.json` (`BuildConfig.PUSH_CONFIGURED`) and [NoopTokenSource] otherwise, so a build without the
 * file never touches `FirebaseMessaging`. Tests bind a fake.
 */
interface FcmTokenSource {
    /** False when this build cannot receive pushes at all: every push operation is then a no-op. */
    val available: Boolean

    /** The current FCM registration token, minting one if needed; null when none can be had. May throw on I/O. */
    suspend fun token(): String?

    /** Invalidates the current token at FCM, so the next [token] call mints a fresh one. May throw on I/O. */
    suspend fun delete()

    /** FCM auto-init: on while signed in, off after sign-out so no token is minted for a signed-out app. */
    fun setAutoInitEnabled(enabled: Boolean)
}

/** Push is not configured in this build: no token, nothing to delete. */
object NoopTokenSource : FcmTokenSource {
    override val available: Boolean = false

    override suspend fun token(): String? = null

    override suspend fun delete() = Unit

    override fun setAutoInitEnabled(enabled: Boolean) = Unit
}

/** Firebase Cloud Messaging. Only constructed when `BuildConfig.PUSH_CONFIGURED`. */
class FirebaseTokenSource : FcmTokenSource {
    private val messaging: FirebaseMessaging get() = FirebaseMessaging.getInstance()

    override val available: Boolean = true

    override suspend fun token(): String? = messaging.token.await()

    override suspend fun delete() {
        messaging.deleteToken().await()
    }

    override fun setAutoInitEnabled(enabled: Boolean) {
        messaging.isAutoInitEnabled = enabled
    }
}
