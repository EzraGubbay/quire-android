package com.ezragubbay.quire.feature.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.push.NotificationPreferences
import com.ezragubbay.quire.push.NotificationPreferencesStore
import com.ezragubbay.quire.push.NotificationPromptStore
import com.ezragubbay.quire.push.PushCategory
import com.ezragubbay.quire.push.PushControls
import com.ezragubbay.quire.push.SystemNotificationState
import com.ezragubbay.quire.push.TestPushResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    // DataStore runs on the test scheduler (no real IO threads), so nothing can resume after resetMain().
    private val storeScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val viewModels = mutableListOf<NotificationSettingsViewModel>()

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = storeScope) {
            tmp.newFile("settings.preferences_pb").also { it.delete() }
        }
    }
    private val preferences by lazy { NotificationPreferencesStore(dataStore) }
    private val prompt by lazy { NotificationPromptStore(dataStore) }
    private val push = FakePushControls()
    private var system = SystemNotificationState()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        storeScope.cancel()
        Dispatchers.resetMain()
    }

    private fun newVm() =
        NotificationSettingsViewModel(preferences, push, { system }, prompt).also { viewModels += it }

    @Test
    fun defaults_everyCategoryOn_idle() =
        runTest(dispatcher) {
            val state = newVm().state.first { it.loaded }

            assertTrue(state.available)
            assertEquals(NotificationPreferences(), state.preferences)
            assertFalse(state.sending)
            assertNull(state.testMessage)
            assertTrue(state.notificationsAllowed)
        }

    @Test
    fun notConfiguredBuild_isUnavailable() =
        runTest(dispatcher) {
            push.available = false

            assertFalse(newVm().state.first { it.loaded }.available)
        }

    @Test
    fun togglingACategory_persists_andRequestsADebouncedSync() =
        runTest(dispatcher) {
            val vm = newVm()
            vm.state.first { it.loaded }

            vm.onCategoryChanged(PushCategory.Files, enabled = false)

            val state = vm.state.first { !it.preferences.isEnabled(PushCategory.Files) }
            assertEquals(listOf(PushCategory.Deadlines, PushCategory.Notes), state.preferences.categories)
            assertFalse(preferences.isEnabled(PushCategory.Files))
            assertEquals(1, push.categoryChanges)
        }

    @Test
    fun systemState_isReReadOnResume() =
        runTest(dispatcher) {
            val vm = newVm()
            vm.state.first { it.loaded }

            system =
                SystemNotificationState(
                    appBlocked = true,
                    blockedChannels = setOf(PushCategory.Deadlines),
                )
            vm.onResume()

            val state = vm.state.first { it.system.appBlocked }
            assertFalse(state.notificationsAllowed)
            assertEquals(setOf(PushCategory.Deadlines), state.system.blockedChannels)
        }

    @Test
    fun permissionMissing_isNotAllowed_andTheRequestIsRemembered() =
        runTest(dispatcher) {
            system = SystemNotificationState(permissionMissing = true)
            val vm = newVm()
            assertFalse(vm.state.first { it.loaded }.notificationsAllowed)

            vm.onPermissionRequested()

            assertTrue(vm.state.first { it.permissionRequestedBefore }.permissionRequestedBefore)
        }

    @Test
    fun permissionResult_reReadsTheSystem_andReRegisters() =
        runTest(dispatcher) {
            system = SystemNotificationState(permissionMissing = true)
            val vm = newVm()
            vm.state.first { it.loaded }

            system = SystemNotificationState()
            vm.onPermissionResult()

            assertTrue(vm.state.first { it.notificationsAllowed }.notificationsAllowed)
            assertEquals(1, push.categoryChanges)
        }

    @Test
    fun sendTest_isSending_whileInFlight_andIgnoresASecondTap() =
        runTest(dispatcher) {
            val vm = newVm()
            vm.state.first { it.loaded }
            val reply = CompletableDeferred<TestPushResult>()
            push.next = reply

            vm.onSendTest()
            assertTrue(vm.state.first { it.sending }.sending)
            vm.onSendTest()
            advanceUntilIdle()
            assertEquals(1, push.tests)

            reply.complete(TestPushResult.Sent("e1"))
            val done = vm.state.first { !it.sending && it.testMessage != null }
            assertEquals(TestPushMessage.Sent, done.testMessage)
        }

    @Test
    fun sendTest_showsEveryOutcome() =
        runTest(dispatcher) {
            val vm = newVm()
            vm.state.first { it.loaded }
            val cases =
                listOf(
                    TestPushResult.Sent("e1") to TestPushMessage.Sent,
                    TestPushResult.Unregistered to TestPushMessage.Reregistering,
                    TestPushResult.Retryable to TestPushMessage.TryLater,
                    TestPushResult.Failed to TestPushMessage.TryLater,
                    TestPushResult.Unavailable to TestPushMessage.ServerNotSetUp,
                    TestPushResult.NoRegistration to TestPushMessage.NotRegistered,
                    TestPushResult.RateLimited to TestPushMessage.RateLimited,
                    TestPushResult.NotConfigured to TestPushMessage.UnavailableInBuild,
                    TestPushResult.Error(ApiResult.Failure.Network(IOException("offline"))) to
                        TestPushMessage.Unreachable,
                    TestPushResult.Error(ApiResult.Failure.Server(500)) to TestPushMessage.TryLater,
                )

            cases.forEach { (result, expected) ->
                push.next = CompletableDeferred(result)
                vm.onSendTest()
                advanceUntilIdle()
                assertEquals("for $result", expected, vm.state.first { !it.sending }.testMessage)
            }
            assertEquals(cases.size, push.tests)
        }

    @Test
    fun outcomeMapping_isPure() {
        assertEquals(TestPushMessage.Reregistering, TestPushResult.Unregistered.toMessage())
        assertEquals(TestPushMessage.ServerNotSetUp, TestPushResult.Unavailable.toMessage())
    }

    private class FakePushControls : PushControls {
        override var available = true
        var categoryChanges = 0
        var tests = 0
        var next = CompletableDeferred<TestPushResult>(TestPushResult.Sent("e0"))

        override fun onCategoriesChanged() {
            categoryChanges++
        }

        override suspend fun sendTest(): TestPushResult {
            tests++
            return next.await()
        }
    }
}
