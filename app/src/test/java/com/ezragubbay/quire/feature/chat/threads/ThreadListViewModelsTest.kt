package com.ezragubbay.quire.feature.chat.threads

import androidx.lifecycle.SavedStateHandle
import com.ezragubbay.quire.core.data.course.Loaded
import com.ezragubbay.quire.core.data.course.SyncState
import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.core.network.ThreadDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

private fun thread(
    id: String,
    slug: String = "sig",
    updatedAt: String = "2026-11-03T10:00:00Z",
) = ThreadDto(id, slug, "T$id", null, "2026-11-01T00:00:00Z", updatedAt)

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadListViewModelsTest {
    private class FakeSource : ThreadListSource {
        val recent = MutableSharedFlow<Loaded<List<ThreadDto>>>(replay = 1)
        val course = MutableSharedFlow<Loaded<List<ThreadDto>>>(replay = 1)
        var recentRefreshes = 0
        val courseRefreshes = mutableListOf<String>()
        var created: ApiResult<ThreadDto> = ApiResult.Success(thread("new", "alg"))
        var createdIn: String? = null
        var defaultCourse: String? = "alg"

        override fun observeRecent(): Flow<Loaded<List<ThreadDto>>> = recent

        override fun observeCourse(slug: String): Flow<Loaded<List<ThreadDto>>> = course

        override suspend fun refreshRecent(): SyncState {
            recentRefreshes++
            return SyncState.Synced
        }

        override suspend fun refreshCourse(slug: String): SyncState {
            courseRefreshes += slug
            return SyncState.Synced
        }

        override suspend fun createThread(slug: String): ApiResult<ThreadDto> {
            createdIn = slug
            return created
        }

        override suspend fun defaultCourseSlug() = defaultCourse
    }

    private val source = FakeSource()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.latest(vm: ThreadListViewModel): () -> ThreadsUiState {
        var value: ThreadsUiState = vm.state.value
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { value = it } }
        return { value }
    }

    private fun failed(offline: Boolean) =
        SyncState.Failed(if (offline) ApiResult.Failure.Network(IOException("down")) else ApiResult.Failure.Server(500))

    @Test
    fun loadingWhileNothingCachedAndRefreshing() =
        runTest {
            val state = latest(ChatsViewModel(source))
            source.recent.emit(Loaded(emptyList(), SyncState.Refreshing))
            assertEquals(ThreadsUiState.Loading, state())
        }

    @Test
    fun threadsAreShownNewestFirst() =
        runTest {
            val state = latest(ChatsViewModel(source))
            val data =
                listOf(
                    thread("old", updatedAt = "2026-11-01T00:00:00Z"),
                    thread("new", updatedAt = "2026-11-09T00:00:00Z"),
                )
            source.recent.emit(Loaded(data, SyncState.Synced))
            val content = state() as ThreadsUiState.Content
            assertEquals(listOf("new", "old"), content.threads.map { it.id })
            assertFalse(content.offline)
        }

    @Test
    fun emptyAfterSuccessfulRefresh() =
        runTest {
            val state = latest(ChatsViewModel(source))
            source.recent.emit(Loaded(emptyList(), SyncState.Synced))
            assertEquals(ThreadsUiState.Empty, state())
        }

    @Test
    fun offlineWithCacheKeepsThreadsAndFlagsBanner() =
        runTest {
            val state = latest(ChatsViewModel(source))
            source.recent.emit(Loaded(listOf(thread("a")), failed(offline = true)))
            val content = state() as ThreadsUiState.Content
            assertTrue(content.offline)
            assertEquals(listOf("a"), content.threads.map { it.id })
        }

    @Test
    fun serverErrorWithCacheIsNotOffline() =
        runTest {
            val state = latest(ChatsViewModel(source))
            source.recent.emit(Loaded(listOf(thread("a")), failed(offline = false)))
            assertFalse((state() as ThreadsUiState.Content).offline)
        }

    @Test
    fun failedWithNoCacheIsError() =
        runTest {
            val state = latest(ChatsViewModel(source))
            source.recent.emit(Loaded(emptyList(), failed(offline = true)))
            assertEquals(ThreadsUiState.Error, state())
        }

    @Test
    fun firstStartDoesNotRefreshButLaterStartsDo() =
        runTest {
            val vm = ChatsViewModel(source)
            vm.onStart()
            assertEquals(0, source.recentRefreshes)
            vm.onStart()
            assertEquals(1, source.recentRefreshes)
            vm.refresh()
            assertEquals(2, source.recentRefreshes)
        }

    @Test
    fun newThreadFromChatsTabCreatesInDefaultCourseAndOpensIt() =
        runTest {
            val vm = ChatsViewModel(source)
            vm.newThread()
            assertEquals("alg", source.createdIn)
            assertEquals(ThreadsEvent.OpenConversation("alg", "new"), vm.events.first())
        }

    @Test
    fun newThreadFailureAndNoCourseReportCreateFailed() =
        runTest {
            val vm = ChatsViewModel(source)
            source.created = ApiResult.Failure.Network(IOException("down"))
            vm.newThread()
            assertEquals(ThreadsEvent.CreateFailed, vm.events.first())

            source.defaultCourse = null
            source.createdIn = null
            vm.newThread()
            assertEquals(ThreadsEvent.CreateFailed, vm.events.first())
            assertEquals(null, source.createdIn)
        }

    @Test
    fun courseThreadsRefreshesAndCreatesInItsCourse() =
        runTest {
            val vm = CourseThreadsViewModel(SavedStateHandle(mapOf("slug" to "sig")), source)
            val state = latest(vm)
            source.course.emit(Loaded(listOf(thread("a")), SyncState.Synced))
            assertEquals(listOf("a"), (state() as ThreadsUiState.Content).threads.map { it.id })
            vm.refresh()
            assertEquals(listOf("sig"), source.courseRefreshes)
            vm.newThread()
            assertEquals("sig", source.createdIn)
        }
}
