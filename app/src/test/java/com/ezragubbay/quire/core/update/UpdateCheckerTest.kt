package com.ezragubbay.quire.core.update

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UpdateCheckerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO)
    private val file by lazy { tmp.newFile("settings.preferences_pb").also { it.delete() } }
    private val dataStore by lazy { PreferenceDataStoreFactory.create(scope = scope) { file } }

    private class FakeSource(
        var result: FetchResult = FetchResult.NoRelease,
    ) : LatestReleaseSource {
        var calls = 0

        override suspend fun fetch(): FetchResult {
            calls++
            return result
        }
    }

    private var now = START

    @After
    fun tearDown() = scope.cancel()

    private fun found(tag: String = "v1.2.0") = FetchResult.Found(LatestRelease(tag, "$URL/$tag"))

    private fun checker(
        source: FakeSource,
        current: String = "1.1.3",
        store: UpdateStore = UpdateStore(dataStore),
    ) = UpdateChecker(source, store, current) { now }

    private fun UpdateChecker.prompt() = runBlocking { prompt.first() }

    private fun UpdateChecker.check() = runBlocking { checkIfDue() }

    @Test
    fun firstRun_fetches_thenThrottlesForSixHours() {
        val source = FakeSource(found())
        val checker = checker(source)
        checker.check()
        assertEquals(1, source.calls)
        now += UpdateChecker.CHECK_INTERVAL_MS - MINUTE
        checker.check()
        assertEquals(1, source.calls)
        now += MINUTE
        checker.check()
        assertEquals(2, source.calls)
    }

    @Test
    fun networkError_recordsNothing_soTheNextResumeRetries() {
        val source = FakeSource(FetchResult.NetworkError)
        val checker = checker(source)
        checker.check()
        checker.check()
        assertEquals(2, source.calls)
    }

    @Test
    fun httpError_backsOffForTheWholeWindow() {
        val source = FakeSource(FetchResult.HttpError)
        val checker = checker(source)
        checker.check()
        now += MINUTE
        checker.check()
        assertEquals(1, source.calls)
    }

    @Test
    fun clockGoingBackwards_fetches() {
        val source = FakeSource(found())
        val checker = checker(source)
        checker.check()
        now -= HOUR
        checker.check()
        assertEquals(2, source.calls)
    }

    @Test
    fun newerRelease_prompts_equalOrOlder_doesNot() {
        val checker = checker(FakeSource(found("v1.2.0")))
        checker.check()
        assertEquals(UpdatePrompt("1.2.0", "$URL/v1.2.0"), checker.prompt())
        listOf("1.2.0", "1.3.0").forEach { current ->
            val other = checker(FakeSource(found("v1.2.0")), current = current, store = fresh())
            other.check()
            assertNull(other.prompt())
        }
    }

    @Test
    fun candidateBuild_isOfferedTheFinalRelease() {
        val checker = checker(FakeSource(found("v1.2.0")), current = "1.2.0-rc1")
        checker.check()
        assertEquals("1.2.0", checker.prompt()?.version)
    }

    @Test
    fun unparseableCurrentVersion_neverPrompts() {
        val checker = checker(FakeSource(found()), current = "dev")
        checker.check()
        assertNull(checker.prompt())
    }

    @Test
    fun prereleaseTag_neverPrompts() {
        val checker = checker(FakeSource(found("v1.3.0-rc1")))
        checker.check()
        assertNull(checker.prompt())
    }

    @Test
    fun snooze_hidesThatVersion_butANewerOnePromptsAgain() {
        val source = FakeSource(found("v1.2.0"))
        val checker = checker(source)
        checker.check()
        runBlocking { checker.snooze("1.2.0") }
        assertNull(checker.prompt())
        source.result = found("v1.2.1")
        now += UpdateChecker.CHECK_INTERVAL_MS
        checker.check()
        assertEquals("1.2.1", checker.prompt()?.version)
    }

    @Test
    fun restart_emitsTheCachedPromptWithoutFetching() {
        checker(FakeSource(found())).check()
        val restartedSource = FakeSource(FetchResult.NetworkError)
        assertEquals("1.2.0", checker(restartedSource).prompt()?.version)
        assertEquals(0, restartedSource.calls)
    }

    @Test
    fun noReleaseAfterFound_clearsThePrompt() {
        val source = FakeSource(found())
        val checker = checker(source)
        checker.check()
        source.result = FetchResult.NoRelease
        now += UpdateChecker.CHECK_INTERVAL_MS
        checker.check()
        assertNull(checker.prompt())
    }

    private fun fresh(): UpdateStore =
        UpdateStore(
            PreferenceDataStoreFactory.create(scope = scope) {
                File(tmp.root, "s${System.nanoTime()}.preferences_pb")
            },
        )

    private companion object {
        const val START = 1_000_000_000L
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val URL = "https://github.com/EzraGubbay/quire-android/releases/tag"
    }
}
