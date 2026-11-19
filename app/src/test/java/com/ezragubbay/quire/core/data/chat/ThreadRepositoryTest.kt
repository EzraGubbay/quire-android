package com.ezragubbay.quire.core.data.chat

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ezragubbay.quire.core.data.course.Loaded
import com.ezragubbay.quire.core.data.course.SyncState
import com.ezragubbay.quire.core.data.course.showsOfflineBanner
import com.ezragubbay.quire.core.database.CourseEntity
import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.core.network.ChatApi
import com.ezragubbay.quire.core.network.ChatMessageDto
import com.ezragubbay.quire.core.network.ChatStreamApi
import com.ezragubbay.quire.core.network.ChatStreamer
import com.ezragubbay.quire.core.network.PostMessageRequest
import com.ezragubbay.quire.core.network.ThreadDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import java.io.IOException

private fun thread(
    id: String,
    slug: String = "sig",
    updatedAt: String = "2026-11-03T10:00:00Z",
    documentId: String? = null,
) = ThreadDto(id, slug, "Thread $id", documentId, "2026-11-01T10:00:00Z", updatedAt)

private class FakeThreadsApi : ChatApi {
    var recent: ApiResult<List<ThreadDto>> = ApiResult.Success(emptyList())
    var course: ApiResult<List<ThreadDto>> = ApiResult.Success(emptyList())
    var created: ApiResult<ThreadDto> = ApiResult.Success(thread("new"))
    var recentCalls = 0

    override suspend fun recentThreads(limit: Int?): ApiResult<List<ThreadDto>> {
        recentCalls++
        return recent
    }

    override suspend fun courseThreads(
        slug: String,
        documentId: String?,
    ) = course

    override suspend fun createThread(
        slug: String,
        title: String?,
        documentId: String?,
    ) = created

    override suspend fun deleteThread(
        slug: String,
        threadId: String,
    ): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun messages(
        slug: String,
        threadId: String,
    ): ApiResult<List<ChatMessageDto>> = ApiResult.Success(emptyList())
}

private object NoStream : ChatStreamApi {
    override suspend fun ask(
        courseSlug: String,
        threadId: String,
        body: PostMessageRequest,
    ): Response<ResponseBody> = error("not used")
}

@RunWith(RobolectricTestRunner::class)
class ThreadRepositoryTest {
    private lateinit var db: QuireDatabase
    private val api = FakeThreadsApi()
    private lateinit var repo: ThreadRepository

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    QuireDatabase::class.java,
                ).build()
        repo = ThreadRepository(db, ChatRepository(api, ChatStreamer(NoStream)))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun <T> Flow<T>.firstMatching(predicate: (T) -> Boolean): T =
        withTimeout(5_000) { first(predicate) }

    private val settled: (Loaded<List<ThreadDto>>) -> Boolean = {
        it.state is SyncState.Synced ||
            it.state is SyncState.Failed
    }

    @Test
    fun recentIsNewestFirstAndWrittenThrough() =
        runBlocking {
            api.recent =
                ApiResult.Success(
                    listOf(
                        thread("old", updatedAt = "2026-11-01T00:00:00Z"),
                        thread("new", "alg", updatedAt = "2026-11-05T00:00:00Z"),
                    ),
                )
            val loaded = repo.observeRecent().firstMatching { it.state == SyncState.Synced && it.data.size == 2 }
            assertEquals(listOf("new", "old"), loaded.data.map { it.id })
            assertEquals(
                listOf("new", "old"),
                db
                    .threadDao()
                    .observeRecent()
                    .first()
                    .map { it.id },
            )
        }

    @Test
    fun offlineWithCacheServesCachedThreadsAndShowsBanner() =
        runBlocking {
            api.recent = ApiResult.Success(listOf(thread("a")))
            assertEquals(SyncState.Synced, repo.refreshRecent())

            api.recent = ApiResult.Failure.Network(IOException("down"))
            val loaded = repo.observeRecent().firstMatching(settled)
            assertEquals(listOf("a"), loaded.data.map { it.id })
            assertTrue(loaded.state is SyncState.Failed)
            assertTrue(loaded.showsOfflineBanner)
        }

    @Test
    fun offlineWithoutCacheHasNoBannerData() =
        runBlocking {
            api.recent = ApiResult.Failure.Network(IOException("down"))
            val loaded = repo.observeRecent().firstMatching(settled)
            assertTrue(loaded.data.isEmpty())
            assertTrue(!loaded.showsOfflineBanner)
        }

    @Test
    fun refreshRemovesThreadsTheServerDropped() =
        runBlocking {
            api.recent = ApiResult.Success(listOf(thread("a"), thread("b")))
            repo.refreshRecent()
            api.recent = ApiResult.Success(listOf(thread("b")))
            repo.refreshRecent()
            assertEquals(
                listOf("b"),
                db
                    .threadDao()
                    .observeRecent()
                    .first()
                    .map { it.id },
            )
        }

    @Test
    fun courseListIsScopedToItsCourse() =
        runBlocking {
            api.recent = ApiResult.Success(listOf(thread("a", "sig"), thread("b", "alg")))
            repo.refreshRecent()
            api.course = ApiResult.Success(listOf(thread("a", "sig", documentId = "doc")))
            val loaded = repo.observeCourse("sig").firstMatching { it.state == SyncState.Synced }
            assertEquals(listOf("a"), loaded.data.map { it.id })
            assertEquals("doc", loaded.data.single().documentId)
            assertEquals(
                listOf("b"),
                db
                    .threadDao()
                    .observeCourse("alg")
                    .first()
                    .map { it.id },
            )
        }

    @Test
    fun createThreadCachesItAndFailureDoesNot() =
        runBlocking {
            val ok = repo.createThread("sig")
            assertEquals("new", (ok as ApiResult.Success).value.id)
            assertEquals(
                listOf("new"),
                db
                    .threadDao()
                    .observeRecent()
                    .first()
                    .map { it.id },
            )

            api.created = ApiResult.Failure.Network(IOException("down"))
            repo.createThread("sig")
            assertEquals(
                1,
                db
                    .threadDao()
                    .observeRecent()
                    .first()
                    .size,
            )
        }

    @Test
    fun defaultCourseIsLatestThreadsCourseElseFirstCachedCourse() =
        runBlocking {
            assertEquals(null, repo.defaultCourseSlug())
            db.courseDao().replaceAll(listOf(CourseEntity("sig", "i", "Sig", null, null, 0, "lecture", 0)))
            assertEquals("sig", repo.defaultCourseSlug())
            api.recent = ApiResult.Success(listOf(thread("a", "alg")))
            repo.refreshRecent()
            assertEquals("alg", repo.defaultCourseSlug())
        }
}
