package com.ezragubbay.quire.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ezragubbay.quire.core.data.course.SyncState
import com.ezragubbay.quire.core.network.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private class StubSource(
    val results: Map<String, SyncState> = emptyMap(),
) : CourseSyncSource {
    override suspend fun refreshCourses() = SyncState.Synced

    override suspend fun courseSlugs() = listOf("pinned", "plain")

    override suspend fun refreshCourse(slug: String) = results[slug] ?: SyncState.Synced
}

private class RecordingPinned(
    val explode: Boolean = false,
) : PinnedSync {
    val snapshots = mutableListOf<String>()
    val after = mutableListOf<Pair<String, List<PinFile>>>()
    private val before = listOf(PinFile("f", "t1", true, 1))

    override suspend fun snapshot(slug: String): List<PinFile>? =
        if (slug ==
            "pinned"
        ) {
            before.also { snapshots += slug }
        } else {
            null
        }

    override suspend fun afterRefresh(
        slug: String,
        before: List<PinFile>,
    ) {
        after += slug to before
        if (explode) error("boom")
    }
}

class SyncRunnerPinnedTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: SyncSettingsStore

    @Before
    fun setUp() {
        store = SyncSettingsStore(PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("s.preferences_pb") })
    }

    @After
    fun tearDown() = scope.cancel()

    private fun run(
        source: CourseSyncSource,
        pinned: PinnedSync,
    ) = runBlocking { SyncRunner(source, store, { true }, pinned = pinned).run() }

    @Test
    fun pinnedCourse_isReconciledAfterItsRefresh_withTheBeforeSnapshot() {
        val pinned = RecordingPinned()
        assertEquals(SyncOutcome.Success, run(StubSource(), pinned))
        assertEquals(listOf("pinned"), pinned.after.map { it.first })
        assertEquals(
            listOf("f"),
            pinned.after
                .single()
                .second
                .map { it.fileId },
        )
    }

    @Test
    fun failedRefresh_doesNotReconcile() {
        val offline = SyncState.Failed(ApiResult.Failure.NotFound())
        val pinned = RecordingPinned()
        run(StubSource(mapOf("pinned" to offline)), pinned)
        assertEquals(emptyList<String>(), pinned.after.map { it.first })
    }

    @Test
    fun autoDownloadFailure_neverFailsTheSync() {
        assertEquals(SyncOutcome.Success, run(StubSource(), RecordingPinned(explode = true)))
    }
}
