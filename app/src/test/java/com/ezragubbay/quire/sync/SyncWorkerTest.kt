package com.ezragubbay.quire.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.ezragubbay.quire.core.data.course.SyncState
import com.ezragubbay.quire.core.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

private class FakeCourseSyncSource(
    var slugs: List<String> = listOf("a", "b"),
    var listResult: SyncState = SyncState.Synced,
    val courseResults: MutableMap<String, SyncState> = mutableMapOf(),
) : CourseSyncSource {
    val calls = mutableListOf<String>()

    override suspend fun refreshCourses(): SyncState {
        calls += "courses"
        return listResult
    }

    override suspend fun courseSlugs(): List<String> = slugs

    override suspend fun refreshCourse(slug: String): SyncState {
        calls += "course:$slug"
        return courseResults[slug] ?: SyncState.Synced
    }
}

@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: SyncSettingsStore
    private val source = FakeCourseSyncSource()
    private var signedIn = true

    private val unauthorized = SyncState.Failed(ApiResult.Failure.Unauthorized())
    private val offline = SyncState.Failed(ApiResult.Failure.Network(IOException("down")))

    @Before
    fun setUp() {
        store = SyncSettingsStore(PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("sync.preferences_pb") })
    }

    @After
    fun tearDown() = scope.cancel()

    private fun run(attempt: Int = 0): Result {
        val runner = SyncRunner(source, store, { signedIn }, clock = { NOW })
        val worker =
            TestListenableWorkerBuilder<SyncWorker>(context)
                .setWorkerFactory(SyncWorkerFactory(SyncDeps(runner)))
                .setRunAttemptCount(attempt)
                .build()
        return runBlocking { worker.doWork() }
    }

    private fun lastSuccess() = runBlocking { store.current().lastSuccessMillis }

    @Test
    fun success_refreshesListThenEachCourse_andStampsLastSuccess() {
        assertEquals(Result.success(), run())
        assertEquals(listOf("courses", "course:a", "course:b"), source.calls)
        assertEquals(NOW, lastSuccess())
    }

    @Test
    fun partialFailure_retries_doesNotStamp_butStillSyncsTheRest() {
        source.courseResults["a"] = offline
        assertEquals(Result.retry(), run())
        assertEquals(listOf("courses", "course:a", "course:b"), source.calls)
        assertNull(lastSuccess())
    }

    @Test
    fun partialFailure_givesUpAfterMaxAttempts_withoutRetrying() {
        source.courseResults["b"] = offline
        assertEquals(Result.failure(), run(attempt = SyncWorker.MAX_ATTEMPTS - 1))
        assertNull(lastSuccess())
    }

    @Test
    fun listFailure_isTransient_andSkipsPerCourseFetches() {
        source.listResult = offline
        assertEquals(Result.retry(), run())
        assertEquals(listOf("courses"), source.calls)
    }

    @Test
    fun unauthorized_stopsImmediately_noRetry_noStamp() {
        source.courseResults["a"] = unauthorized
        assertEquals(Result.failure(), run())
        assertEquals(listOf("courses", "course:a"), source.calls) // 'b' is never asked: one 401, one expiry
        assertNull(lastSuccess())
    }

    @Test
    fun unauthorizedOnList_isNotRetried() {
        source.listResult = unauthorized
        assertEquals(Result.failure(), run())
        assertEquals(listOf("courses"), source.calls)
    }

    @Test
    fun notFoundCourse_doesNotCountAsFailure() {
        source.courseResults["a"] = SyncState.Failed(ApiResult.Failure.NotFound())
        assertEquals(Result.success(), run())
        assertEquals(NOW, lastSuccess())
    }

    @Test
    fun signedOut_isANoOp() {
        signedIn = false
        assertEquals(Result.success(), run())
        assertEquals(emptyList<String>(), source.calls)
        assertNull(lastSuccess())
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
