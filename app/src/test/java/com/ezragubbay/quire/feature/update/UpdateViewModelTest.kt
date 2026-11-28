package com.ezragubbay.quire.feature.update

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import com.ezragubbay.quire.core.update.FetchResult
import com.ezragubbay.quire.core.update.LatestRelease
import com.ezragubbay.quire.core.update.LatestReleaseSource
import com.ezragubbay.quire.core.update.UpdateChecker
import com.ezragubbay.quire.core.update.UpdatePrompt
import com.ezragubbay.quire.core.update.UpdateStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO)
    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("s.preferences_pb").also { it.delete() } }
    }

    private class FakeSource(
        var result: suspend () -> FetchResult = { FetchResult.NoRelease },
    ) : LatestReleaseSource {
        var calls = 0

        override suspend fun fetch(): FetchResult {
            calls++
            return result()
        }
    }

    private val release = FetchResult.Found(LatestRelease("v1.2.0", "$PAGE/v1.2.0"))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    private val created = mutableListOf<UpdateViewModel>()
    private val watchers = mutableListOf<Job>()

    @After
    fun tearDown() {
        // Stop the view models' own scopes before Main is reset, or their collectors resume on a missing Main.
        watchers.forEach { it.cancel() }
        runBlocking {
            created.forEach {
                it.viewModelScope.coroutineContext.job
                    .cancelAndJoin()
            }
        }
        Dispatchers.resetMain()
        scope.cancel()
    }

    private fun vm(source: FakeSource) = UpdateViewModel(checker(source)).also { created += it }

    private fun checker(source: FakeSource) = UpdateChecker(source, UpdateStore(dataStore), "1.1.3")

    /** Subscribes like the UI does: before the check starts, and for as long as the test runs. */
    private fun UpdateViewModel.watch() {
        watchers += scope.launch { prompt.collect {} }
    }

    private fun UpdateViewModel.await(present: Boolean): UpdatePrompt? {
        waitUntil { (prompt.value != null) == present }
        return prompt.value
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition never held" }
            Thread.sleep(POLL_MS)
        }
    }

    /** A source that answers [release] only once the test lets it, i.e. after the UI has subscribed. */
    private fun gatedSource(gate: CompletableDeferred<Unit>) =
        FakeSource {
            gate.await()
            release
        }

    @Test
    fun twoResumesWhileFetching_fetchOnce() {
        val gate = CompletableDeferred<Unit>()
        val source = gatedSource(gate)
        val vm = vm(source)
        vm.watch()
        vm.onResume()
        waitUntil { source.calls == 1 }
        vm.onResume()
        gate.complete(Unit)
        assertEquals("1.2.0", vm.await(present = true)?.version)
        assertEquals(1, source.calls)
    }

    @Test
    fun newerRelease_isEmitted() {
        val gate = CompletableDeferred<Unit>()
        val vm = vm(gatedSource(gate))
        vm.watch()
        vm.onResume()
        gate.complete(Unit)
        assertEquals(UpdatePrompt("1.2.0", "$PAGE/v1.2.0"), vm.await(present = true))
    }

    @Test
    fun later_hidesNow_andPersistsAcrossAFreshViewModel() {
        val gate = CompletableDeferred<Unit>()
        val source = gatedSource(gate)
        val vm = vm(source)
        vm.watch()
        vm.onResume()
        gate.complete(Unit)
        vm.onLater(checkNotNull(vm.await(present = true)))
        vm.await(present = false)
        waitUntil { runBlocking { checker(source).prompt.first() } == null }
        val fresh = vm(source)
        fresh.watch()
        // The cached release is there (no fetch happened), yet the snooze keeps it hidden.
        waitUntil { runBlocking { UpdateStore(dataStore).state.first().latestTag } != null }
    }

    @Test
    fun dismissed_hidesNow_butAFreshViewModelShowsItAgain() {
        val gate = CompletableDeferred<Unit>()
        val source = gatedSource(gate)
        val vm = vm(source)
        vm.watch()
        vm.onResume()
        gate.complete(Unit)
        vm.onDismissed(checkNotNull(vm.await(present = true)))
        vm.await(present = false)
        val fresh = vm(source)
        fresh.watch()
        assertEquals("1.2.0", fresh.await(present = true)?.version)
    }

    @Test
    fun opened_hidesForThisSession() {
        val gate = CompletableDeferred<Unit>()
        val vm = vm(gatedSource(gate))
        vm.watch()
        vm.onResume()
        gate.complete(Unit)
        vm.onOpened(checkNotNull(vm.await(present = true)))
        vm.await(present = false)
    }

    @Test
    fun throwingSource_neverCrashesAndNeverPrompts() {
        val source = FakeSource { error("boom") }
        val vm = vm(source)
        vm.watch()
        vm.onResume()
        waitUntil { source.calls == 1 }
        Thread.sleep(SETTLE_MS)
        assertNull(vm.prompt.value)
    }

    private companion object {
        const val PAGE = "https://github.com/EzraGubbay/quire-android/releases/tag"
        const val TIMEOUT_MS = 5_000L
        const val SETTLE_MS = 300L
        const val POLL_MS = 10L
    }
}
