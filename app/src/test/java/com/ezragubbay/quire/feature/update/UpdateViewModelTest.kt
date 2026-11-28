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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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

    @After
    fun tearDown() {
        // Stop the view models' own scopes before Main is reset, or their collectors resume on a missing Main.
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

    /** Keeps the prompt subscribed (it is WhileSubscribed) and polls until it is present or absent. */
    private fun UpdateViewModel.await(present: Boolean): UpdatePrompt? {
        val keepAlive = scope.launch { prompt.collect {} }
        try {
            val deadline = System.currentTimeMillis() + TIMEOUT_MS
            while ((prompt.value != null) != present) {
                check(System.currentTimeMillis() < deadline) { "prompt never became present=$present" }
                Thread.sleep(POLL_MS)
            }
            return prompt.value
        } finally {
            keepAlive.cancel()
        }
    }

    @Test
    fun twoResumesWhileFetching_fetchOnce() {
        val gate = CompletableDeferred<FetchResult>()
        val source = FakeSource { gate.await() }
        val vm = vm(source)
        vm.onResume()
        waitUntil { source.calls == 1 }
        vm.onResume()
        Thread.sleep(SETTLE_MS)
        gate.complete(FetchResult.NoRelease)
        Thread.sleep(SETTLE_MS)
        assertEquals(1, source.calls)
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition never held" }
            Thread.sleep(POLL_MS)
        }
    }

    @Test
    fun newerRelease_isEmitted() {
        val vm = vm(FakeSource { release })
        vm.onResume()
        assertEquals(UpdatePrompt("1.2.0", "$PAGE/v1.2.0"), vm.await(present = true))
    }

    @Test
    fun later_hidesNow_andPersistsAcrossAFreshViewModel() {
        val source = FakeSource { release }
        val vm = vm(source)
        vm.onResume()
        val shown = checkNotNull(vm.await(present = true))
        vm.onLater(shown)
        vm.await(present = false)
        runBlocking { withTimeout(TIMEOUT_MS) { checker(source).prompt.first { it == null } } }
        assertNull(vm(source).prompt.value)
    }

    @Test
    fun dismissed_hidesNow_butAFreshViewModelShowsItAgain() {
        val source = FakeSource { release }
        val vm = vm(source)
        vm.onResume()
        val shown = checkNotNull(vm.await(present = true))
        vm.onDismissed(shown)
        vm.await(present = false)
        assertEquals("1.2.0", vm(source).await(present = true)?.version)
    }

    @Test
    fun opened_hidesForThisSession() {
        val vm = vm(FakeSource { release })
        vm.onResume()
        vm.onOpened(checkNotNull(vm.await(present = true)))
        vm.await(present = false)
    }

    @Test
    fun throwingSource_neverCrashesAndNeverPrompts() {
        val vm = vm(FakeSource { error("boom") })
        vm.onResume()
        assertNull(vm.prompt.value)
    }

    private companion object {
        const val PAGE = "https://github.com/EzraGubbay/quire-android/releases/tag"
        const val TIMEOUT_MS = 5_000L
        const val SETTLE_MS = 300L
        const val POLL_MS = 10L
    }
}
