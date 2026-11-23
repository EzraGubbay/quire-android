package com.ezragubbay.quire.push

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NotificationPreferencesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()
    private val file: File by lazy { tmp.newFile("settings.preferences_pb").also { it.delete() } }

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    /** A store over [file]; a second call opens the same file as a fresh process would. */
    private fun store(): NotificationPreferencesStore {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        return NotificationPreferencesStore(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    @Test
    fun defaults_everyCategoryOn() {
        val prefs = NotificationPreferences()

        PushCategory.entries.forEach { assertTrue(prefs.isEnabled(it)) }
        assertEquals(listOf(PushCategory.Files, PushCategory.Deadlines, PushCategory.Notes), prefs.categories)
    }

    @Test
    fun categories_keepTheWireOrder_andDropWhatIsOff() {
        val prefs = NotificationPreferences().with(PushCategory.Deadlines, enabled = false)

        assertEquals(listOf("files", "notes"), prefs.categories.map { it.wire })
        assertFalse(prefs.isEnabled(PushCategory.Deadlines))
        assertEquals(PushCategory.entries, prefs.with(PushCategory.Deadlines, enabled = true).categories)
    }

    @Test
    fun store_startsWithEverythingOn() =
        runBlocking {
            val store = store()

            assertEquals(NotificationPreferences(), store.preferences.first())
            PushCategory.entries.forEach { assertTrue(store.isEnabled(it)) }
        }

    @Test
    fun store_persistsAToggleAcrossInstances() =
        runBlocking {
            store().setEnabled(PushCategory.Files, enabled = false)

            val reopened = store()
            assertFalse(reopened.isEnabled(PushCategory.Files))
            assertTrue(reopened.isEnabled(PushCategory.Deadlines))
            assertEquals(listOf(PushCategory.Deadlines, PushCategory.Notes), reopened.preferences.first().categories)
        }

    @Test
    fun store_turningACategoryBackOn_restoresIt() =
        runBlocking {
            val store = store()
            store.setEnabled(PushCategory.Notes, enabled = false)
            store.setEnabled(PushCategory.Notes, enabled = true)

            assertEquals(NotificationPreferences(), store.preferences.first())
        }

    @Test
    fun categoryPolicy_mapsTheStoredToggles_toTheRegisteredCategories() =
        runBlocking {
            val store = store()
            store.setEnabled(PushCategory.Files, enabled = false)

            assertEquals(
                listOf(PushCategory.Deadlines, PushCategory.Notes),
                PushCategoryPolicy({ true }, store).current(),
            )
            // Notifications denied or off for the app: nothing is registered, whatever the toggles say.
            assertEquals(emptyList<PushCategory>(), PushCategoryPolicy({ false }, store).current())
        }
}
