package com.ezragubbay.quire.feature.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezragubbay.quire.core.data.settings.SettingsRepository
import com.ezragubbay.quire.core.data.settings.UserSettings
import com.ezragubbay.quire.core.designsystem.theme.ThemeMode
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    // DataStore runs on the test scheduler (no real IO threads), so nothing can resume after resetMain().
    private val storeScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val viewModels = mutableListOf<SettingsViewModel>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        // Cancel the ViewModels' collectors and the store before resetMain() so no coroutine outlives the test.
        viewModels.forEach { it.viewModelScope.cancel() }
        storeScope.cancel()
        Dispatchers.resetMain()
    }

    private fun newVm(repo: SettingsRepository) = SettingsViewModel(repo).also { viewModels += it }

    private fun repo() =
        SettingsRepository(
            PreferenceDataStoreFactory.create(scope = storeScope) {
                tmp.newFile("s.preferences_pb").also { it.delete() }
            },
        )

    @Test
    fun startsWithDefaults_thenReportsLoaded() =
        runTest(dispatcher) {
            val vm = newVm(repo())
            assertEquals(UserSettings(), vm.settings.value)
            assertFalse(vm.loaded.value)
            vm.loaded.first { it }
            assertTrue(vm.loaded.value)
        }

    @Test
    fun selectingTheme_updatesStateImmediatelyAndPersists() =
        runTest(dispatcher) {
            val repo = repo()
            val vm = newVm(repo)
            vm.loaded.first { it }

            vm.onThemeModeSelected(ThemeMode.Dark)
            assertEquals(ThemeMode.Dark, vm.settings.value.themeMode)

            // Wait for the persisted value to appear.
            assertEquals(ThemeMode.Dark, repo.settings.first { it.themeMode == ThemeMode.Dark }.themeMode)
        }

    @Test
    fun togglingDeviceColors_updatesStateAndPersists() =
        runTest(dispatcher) {
            val repo = repo()
            val vm = newVm(repo)
            vm.loaded.first { it }

            vm.onUseDeviceColorsChanged(true)
            assertTrue(vm.settings.value.useDeviceColors)

            assertTrue(repo.settings.first { it.useDeviceColors }.useDeviceColors)
        }
}
