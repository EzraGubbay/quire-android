package com.ezragubbay.quire.core.data.course

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.designsystem.theme.FileType
import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.core.network.CourseCountsDto
import com.ezragubbay.quire.core.network.CourseDetailDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CourseRepositoryTest {
    private lateinit var db: QuireDatabase
    private val api = FakeBrowseApi()
    private var now = 1_000_000L
    private lateinit var repo: CourseRepository

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), QuireDatabase::class.java)
                .build()
        repo = CourseRepository(db, api) { now }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun <T> Flow<T>.firstMatching(predicate: (T) -> Boolean): T = withTimeout(TIMEOUT) { first(predicate) }

    @Test
    fun firstLoadEmitsLoadingThenSyncedWithData() =
        runBlocking {
            api.courses = ApiResult.Success(listOf(summaryDto("a"), summaryDto("b")))
            val flow = repo.observeCourses()
            val first = flow.first()
            assertTrue(first.data.isEmpty())
            assertEquals(SyncState.Refreshing, first.state)
            val loaded = flow.firstMatching { it.state == SyncState.Synced && it.data.size == 2 }
            assertEquals(listOf("a", "b"), loaded.data.map { it.slug })
        }

    @Test
    fun cacheHitEmitsCachedDataBeforeNetworkAnswers() =
        runBlocking {
            api.courses = ApiResult.Success(listOf(summaryDto("a")))
            repo.refreshCourses()
            api.gate = CompletableDeferred()
            now += TTL_PASSED
            val first = repo.observeCourses().first()
            assertEquals(listOf("a"), first.data.map { it.slug })
            assertEquals(SyncState.Refreshing, first.state)
        }

    @Test
    fun refreshWritesThroughAddUpdateDelete() =
        runBlocking {
            api.courses = ApiResult.Success(listOf(summaryDto("a"), summaryDto("b", name = "Old")))
            assertEquals(SyncState.Synced, repo.refreshCourses())

            api.courses = ApiResult.Success(listOf(summaryDto("b", name = "New"), summaryDto("c")))
            assertEquals(SyncState.Synced, repo.refreshCourses())

            val courses = db.courseDao().getAll()
            assertEquals(listOf("b" to "New", "c" to "c"), courses.map { it.slug to it.name }.sortedBy { it.first })
        }

    @Test
    fun offlineWithCacheKeepsServingCache() =
        runBlocking {
            api.courses = ApiResult.Success(listOf(summaryDto("a")))
            repo.refreshCourses()
            api.courses = networkDown

            val state = repo.refreshCourses()
            assertTrue(state is SyncState.Failed && state.isOffline)
            val loaded = repo.observeCourses().firstMatching { it.state is SyncState.Failed }
            assertEquals(listOf("a"), loaded.data.map { it.slug })
            assertTrue(loaded.showsOfflineBanner)
            assertFalse(loaded.showsError)
        }

    @Test
    fun offlineWithoutCacheSurfacesErrorNotBanner() =
        runBlocking {
            api.courses = networkDown
            val loaded = repo.observeCourses().firstMatching { it.state is SyncState.Failed }
            assertTrue(loaded.data.isEmpty())
            assertTrue(loaded.showsError)
            assertFalse(loaded.showsOfflineBanner)
        }

    @Test
    fun serverErrorIsFailedButNotOffline() =
        runBlocking {
            api.courses = ApiResult.Failure.Server(500, "boom")
            val state = repo.refreshCourses()
            assertTrue(state is SyncState.Failed && !state.isOffline)
        }

    @Test
    fun freshCacheIsNotRefetchedOnReobserveButForcedRefreshIs() =
        runBlocking {
            api.courses = ApiResult.Success(listOf(summaryDto("a")))
            repo.observeCourses().firstMatching { it.state == SyncState.Synced }
            repo.observeCourses().firstMatching { it.state == SyncState.Synced }
            assertEquals(1, api.courseCalls)
            repo.refreshCourses()
            assertEquals(2, api.courseCalls)
        }

    @Test
    fun coursesAreOrderedNewestSemesterFirst() =
        runBlocking {
            api.courses =
                ApiResult.Success(
                    listOf(
                        summaryDto("old", semester = "Winter 2025"),
                        summaryDto("spring", semester = "Spring 2026"),
                        summaryDto("winter", semester = "Winter 2026"),
                    ),
                )
            repo.refreshCourses()
            val courses = repo.observeCourses().firstMatching { it.data.size == 3 }.data
            assertEquals(listOf("spring", "winter", "old"), courses.map { it.slug })
            assertEquals("Spring 2026", courses.first().semester)
        }

    @Test
    fun courseRefreshStoresDetailSessionsFilesNotes() =
        runBlocking {
            api.sessions = ApiResult.Success(listOf(sessionDto(1), sessionDto(2)))
            api.files = ApiResult.Success(listOf(fileDto("f1"), fileDto("f2", type = "exam")))
            api.notes = ApiResult.Success(listOf(noteDto("n1")))
            assertEquals(SyncState.Synced, repo.refreshCourse("a"))

            val detail = repo.observeCourse("a").firstMatching { it.data != null }.data!!
            assertEquals(1, detail.counts.sessions["lecture"])
            assertEquals(2, repo.observeSessions("a").first().data.size)
            val files = repo.observeFiles("a").first().data
            assertEquals(listOf(FileType.LECTURE, FileType.EXAM), files.map { it.type })
            assertEquals(listOf("n1"), repo.observeNotes("a").first().data.map { it.id })
        }

    @Test
    fun courseRefreshDeletesRowsRemovedOnServer() =
        runBlocking {
            api.files = ApiResult.Success(listOf(fileDto("f1"), fileDto("f2")))
            repo.refreshCourse("a")
            api.files = ApiResult.Success(listOf(fileDto("f2", title = "renamed")))
            repo.refreshCourse("a")

            val files = repo.observeFiles("a").first().data
            assertEquals(listOf("f2" to "renamed"), files.map { it.fileId to it.title })
        }

    @Test
    fun courseOfflineKeepsCachedFiles() =
        runBlocking {
            api.files = ApiResult.Success(listOf(fileDto("f1")))
            repo.refreshCourse("a")
            api.detail = { networkDown }

            val state = repo.refreshCourse("a")
            assertTrue(state is SyncState.Failed && state.isOffline)
            assertEquals(1, repo.observeFiles("a").first().data.size)
        }

    @Test
    fun listRefreshPreservesDetailCountsAndChildren() =
        runBlocking {
            api.files = ApiResult.Success(listOf(fileDto("f1")))
            repo.refreshCourse("a")
            api.courses = ApiResult.Success(listOf(summaryDto("a", name = "Renamed")))
            repo.refreshCourses()

            val detail = repo.observeCourse("a").first().data
            assertNotNull(detail)
            assertEquals("Renamed", detail!!.course.name)
            assertEquals(1, detail.counts.sessions["lecture"])
            assertEquals(1, repo.observeFiles("a").first().data.size)
        }

    @Test
    fun courseRemovedFromListDropsItsChildren() =
        runBlocking {
            api.files = ApiResult.Success(listOf(fileDto("f1")))
            repo.refreshCourse("a")
            api.courses = ApiResult.Success(emptyList())
            repo.refreshCourses()

            assertNull(repo.observeCourse("a").first().data)
            assertTrue(repo.observeFiles("a").first().data.isEmpty())
        }

    @Test
    fun unknownCourseSurfacesNotFound() =
        runBlocking {
            api.detail = { ApiResult.Failure.NotFound("nope") }
            val state = repo.refreshCourse("ghost")
            assertTrue(state is SyncState.Failed && state.failure is ApiResult.Failure.NotFound)
        }

    @Test
    fun detailCountsDtoRoundTripsEmptyMaps() =
        runBlocking {
            api.detail = { ApiResult.Success(CourseDetailDto(summaryDto(it), CourseCountsDto(emptyMap(), emptyMap(), 0))) }
            repo.refreshCourse("a")
            assertTrue(repo.observeCourse("a").first().data!!.counts.files.isEmpty())
        }

    private companion object {
        const val TIMEOUT = 5_000L
        const val TTL_PASSED = 10 * 60_000L
    }
}
