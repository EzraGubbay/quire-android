package com.ezragubbay.quire.testing

import androidx.test.core.app.ApplicationProvider
import com.ezragubbay.quire.core.data.session.SessionRepository
import com.ezragubbay.quire.core.network.TokenCache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SessionTestEntryPoint {
    fun sessions(): SessionRepository

    fun tokenCache(): TokenCache
}

/**
 * Puts the persisted session into a known state before the activity launches: [signedIn] seeds a paired
 * session, otherwise the install is fresh. Must run after `HiltAndroidRule` (it uses the Hilt graph) and before
 * the compose/activity rule. The DataStore file outlives a test, so every test seeds explicitly.
 */
class SessionSeedRule(
    private val signedIn: Boolean,
) : ExternalResource() {
    override fun before() {
        val entry =
            EntryPointAccessors.fromApplication(
                ApplicationProvider.getApplicationContext<android.content.Context>(),
                SessionTestEntryPoint::class.java,
            )
        runBlocking {
            if (signedIn) {
                entry.sessions().signIn("https://seeded.example.com", "seed-token", "tester", "Test device")
            } else {
                entry.sessions().signOut()
            }
        }
        entry.tokenCache().invalidate()
    }
}
