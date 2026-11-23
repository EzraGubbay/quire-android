package com.ezragubbay.quire.push

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The push hooks the app's session lifecycle calls (ADR 0003 §4.2): `MainActivity` on start/resume, `AppViewModel`
 * on pairing, sign-out and expiry. A seam so those test without Firebase or WorkManager.
 */
interface PushSession {
    /** Once per process: register on every app start. */
    fun onAppStart()

    /** The app came to the foreground: re-register if notifications were allowed or turned off meanwhile. */
    fun onResume()

    /** Pairing stored a session: register with the new device token and arm the one-time permission card. */
    fun onSignedIn()

    /** Sign-out, before `DELETE /auth/token`: best-effort `DELETE /push/registration`. */
    suspend fun unregisterRemote()

    /** After sign-out's revoke, or on session expiry: delete the FCM token, then disable auto-init (background). */
    fun onSignedOut()
}

class AppPushSession(
    private val registrar: PushRegistrar,
    private val prompt: NotificationPromptStore,
    private val scope: CoroutineScope,
) : PushSession {
    override fun onAppStart() = registrar.onAppStart()

    override fun onResume() = registrar.refreshIfChanged()

    override fun onSignedIn() {
        scope.launch { prompt.armAfterPairing() }
        registrar.requestSync()
    }

    override suspend fun unregisterRemote() = registrar.unregisterRemote()

    override fun onSignedOut() = registrar.onSignedOut()
}
