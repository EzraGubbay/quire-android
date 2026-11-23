package com.ezragubbay.quire.core.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezragubbay.quire.core.designsystem.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO)

    @After
    fun tearDown() = scope.cancel()

    private fun repo(file: File = tmp.newFile("settings.preferences_pb").also { it.delete() }) =
        SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })

    @Test
    fun defaults_areSystemThemeAndNoDeviceColors() =
        runBlocking {
            assertEquals(UserSettings(ThemeMode.System, useDeviceColors = false), repo().settings.first())
        }

    @Test
    fun setThemeMode_persistsEachMode() =
        runBlocking {
            val repo = repo()
            ThemeMode.entries.forEach { mode ->
                repo.setThemeMode(mode)
                assertEquals(mode, repo.settings.first().themeMode)
            }
        }

    @Test
    fun setUseDeviceColors_persistsAndLeavesThemeAlone() =
        runBlocking {
            val repo = repo()
            repo.setThemeMode(ThemeMode.Dark)
            repo.setUseDeviceColors(true)
            assertEquals(UserSettings(ThemeMode.Dark, useDeviceColors = true), repo.settings.first())
            repo.setUseDeviceColors(false)
            assertEquals(UserSettings(ThemeMode.Dark, useDeviceColors = false), repo.settings.first())
        }

    @Test
    fun settings_surviveANewRepositoryOverTheSameFile() =
        runBlocking {
            val file = tmp.newFile("again.preferences_pb").also { it.delete() }
            val firstScope = CoroutineScope(Dispatchers.IO)
            SettingsRepository(PreferenceDataStoreFactory.create(scope = firstScope) { file })
                .setThemeMode(ThemeMode.Light)
            // Join, not just cancel: DataStore releases the file's single-instance lock only once its scope has
            // completed, so reopening straight after an async cancel() races "multiple DataStores for the same file".
            firstScope.coroutineContext[Job]!!.cancelAndJoin()
            val reopened = repo(file)
            assertEquals(ThemeMode.Light, reopened.settings.first().themeMode)
        }

    @Test
    fun themeMode_storedAsItsKey() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromKey(it.key)) }
        assertEquals(ThemeMode.System, ThemeMode.fromKey("garbage"))
        assertEquals(ThemeMode.System, ThemeMode.fromKey(null))
    }
}
