package com.ezragubbay.quire.feature.courses.list

import com.ezragubbay.quire.core.data.course.Course
import com.ezragubbay.quire.core.data.course.Loaded
import com.ezragubbay.quire.core.data.course.SyncState
import com.ezragubbay.quire.core.database.semesterKey
import com.ezragubbay.quire.core.network.ApiResult
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

@OptIn(ExperimentalCoroutinesApi::class)
class CoursesViewModelTest {
    private class FakeSource : CoursesSource {
        val flow = MutableSharedFlow<Loaded<List<Course>>>(replay = 1)
        var refreshCalls = 0

        override fun observeCourses(): Flow<Loaded<List<Course>>> = flow

        override suspend fun refreshCourses(): SyncState {
            refreshCalls++
            return SyncState.Synced
        }
    }

    private val source = FakeSource()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun course(
        slug: String,
        semester: String?,
        name: String = slug,
    ) = Course(slug, "id-$slug", name, "0001", semester, semesterKey(semester), "lecture", 3)

    private fun failed(offline: Boolean) =
        SyncState.Failed(if (offline) ApiResult.Failure.Network(IOException("down")) else ApiResult.Failure.Server(500))

    private fun TestScope.collectLatest(vm: CoursesViewModel): () -> CoursesUiState {
        var latest: CoursesUiState = vm.state.value
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { latest = it } }
        return { latest }
    }

    @Test
    fun emptyCacheWhileRefreshing_isLoading() =
        runTest {
            val vm = CoursesViewModel(source)
            val latest = collectLatest(vm)
            source.flow.emit(Loaded(emptyList(), SyncState.Refreshing))
            assertEquals(CoursesUiState.Loading, latest())
        }

    @Test
    fun contentIsGroupedBySemesterMostRecentFirst() =
        runTest {
            val vm = CoursesViewModel(source)
            val latest = collectLatest(vm)
            source.flow.emit(
                Loaded(
                    listOf(
                        course("a", "Winter 2025"),
                        course("b", "Spring 2026"),
                        course("c", "Spring 2026"),
                        course("d", null),
                    ),
                    SyncState.Synced,
                ),
            )
            val content = latest() as CoursesUiState.Content
            assertEquals(listOf("Spring 2026", "Winter 2025", null), content.groups.map { it.label })
            assertEquals(listOf("spring-2026", "winter-2025", "other"), content.groups.map { it.tag })
            assertEquals(listOf("b", "c"), content.groups[0].courses.map { it.slug })
            assertFalse(content.offline)
            assertFalse(content.refreshing)
        }

    @Test
    fun refreshingWithCache_showsContentRefreshing() =
        runTest {
            val vm = CoursesViewModel(source)
            val latest = collectLatest(vm)
            source.flow.emit(Loaded(listOf(course("a", "Winter 2026")), SyncState.Refreshing))
            assertTrue((latest() as CoursesUiState.Content).refreshing)
        }

    @Test
    fun syncedWithNoCourses_isEmpty() =
        runTest {
            val vm = CoursesViewModel(source)
            val latest = collectLatest(vm)
            source.flow.emit(Loaded(emptyList(), SyncState.Synced))
            assertEquals(CoursesUiState.Empty, latest())
        }

    @Test
    fun failureWithNoCache_isError() =
        runTest {
            val vm = CoursesViewModel(source)
            val latest = collectLatest(vm)
            source.flow.emit(Loaded(emptyList(), failed(offline = true)))
            assertEquals(CoursesUiState.Error, latest())
        }

    @Test
    fun offlineFailureWithCache_isContentWithOfflineBanner() =
        runTest {
            val vm = CoursesViewModel(source)
            val latest = collectLatest(vm)
            source.flow.emit(Loaded(listOf(course("a", "Winter 2026")), failed(offline = true)))
            assertTrue((latest() as CoursesUiState.Content).offline)
        }

    @Test
    fun serverErrorWithCache_isContentWithoutOfflineBanner() =
        runTest {
            val vm = CoursesViewModel(source)
            val latest = collectLatest(vm)
            source.flow.emit(Loaded(listOf(course("a", "Winter 2026")), failed(offline = false)))
            assertFalse((latest() as CoursesUiState.Content).offline)
        }

    @Test
    fun refreshAndRetry_askTheRepositoryToRefetch() =
        runTest {
            val vm = CoursesViewModel(source)
            vm.refresh()
            vm.retry()
            assertEquals(2, source.refreshCalls)
            assertEquals(CoursesUiState.Loading, vm.state.first())
        }

    @Test
    fun onlyLaterStartsRefetch() =
        runTest {
            val vm = CoursesViewModel(source)
            vm.onStart() // first start: observing already refreshes
            assertEquals(0, source.refreshCalls)
            vm.onStart() // relaunch / return to the app
            assertEquals(1, source.refreshCalls)
        }
}
