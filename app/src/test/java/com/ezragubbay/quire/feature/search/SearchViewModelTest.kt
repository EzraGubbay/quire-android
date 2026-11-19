package com.ezragubbay.quire.feature.search

import com.ezragubbay.quire.core.data.course.Course
import com.ezragubbay.quire.core.data.course.CourseFile
import com.ezragubbay.quire.core.data.course.SearchResults
import com.ezragubbay.quire.core.designsystem.theme.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private class FakeSource : SearchSource {
        val queries = mutableListOf<String>()
        var results = SearchResults(emptyList(), emptyList())

        override suspend fun search(query: String): SearchResults {
            queries += query
            return results
        }
    }

    private class FakeRecents(
        initial: List<String> = emptyList(),
    ) : RecentSearches {
        private val state = MutableStateFlow(initial)
        override val recents: StateFlow<List<String>> = state

        override suspend fun add(query: String) {
            state.value = (listOf(query) + state.value.filterNot { it == query }).take(RECENT_LIMIT)
        }

        override suspend fun clear() {
            state.value = emptyList()
        }
    }

    private val source = FakeSource()
    private val recents = FakeRecents()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun course(slug: String) = Course(slug, "id-$slug", slug, "1", null, 0, "lecture", 0)

    private fun file(id: String) =
        CourseFile(
            id,
            "c",
            id,
            FileType.LECTURE,
            null,
            null,
            null,
            null,
            1L,
            true,
            false,
            false,
            "2026-11-02T09:30:00Z",
        )

    private fun TestScope.collect(vm: SearchViewModel): () -> SearchUiState {
        var latest = vm.state.value
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { latest = it } }
        return { latest }
    }

    @Test
    fun emptyQueryShowsRecentsAndDoesNotSearch() =
        runTest {
            val vm = SearchViewModel(source, FakeRecents(listOf("a", "b")))
            val latest = collect(vm)
            advanceTimeBy(1_000)
            assertEquals(SearchUiState.Recents(listOf("a", "b")), latest())
            assertTrue(source.queries.isEmpty())
        }

    @Test
    fun queryIsDebouncedAndOnlyLastIsSearched() =
        runTest {
            source.results = SearchResults(listOf(course("sig")), listOf(file("f1")))
            val vm = SearchViewModel(source, recents)
            val latest = collect(vm)
            vm.onQueryChange("s")
            advanceTimeBy(100)
            vm.onQueryChange("si")
            advanceTimeBy(100)
            vm.onQueryChange("sig")
            advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
            assertEquals(listOf("sig"), source.queries)
            val state = latest() as SearchUiState.Results
            assertEquals(listOf("sig"), state.courses.map { it.slug })
            assertEquals(listOf("f1"), state.files.map { it.fileId })
        }

    @Test
    fun noMatchesIsNoResults() =
        runTest {
            val vm = SearchViewModel(source, recents)
            val latest = collect(vm)
            vm.onQueryChange("zzz")
            advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
            assertEquals(SearchUiState.NoResults, latest())
        }

    @Test
    fun blankQueryReturnsToRecents() =
        runTest {
            val vm = SearchViewModel(source, FakeRecents(listOf("a")))
            val latest = collect(vm)
            vm.onQueryChange("x")
            advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
            vm.onQueryChange("  ")
            assertEquals(SearchUiState.Recents(listOf("a")), latest())
        }

    @Test
    fun openingAResultRecordsTheQueryAndRecentsKeepFive() =
        runTest {
            val vm = SearchViewModel(source, recents)
            collect(vm)
            listOf("a", "b", "c", "d", "e", "f").forEach {
                vm.onQueryChange(it)
                vm.onResultOpened()
            }
            assertEquals(listOf("f", "e", "d", "c", "b"), recents.recents.value)
        }

    @Test
    fun clearRecentsEmptiesThem() =
        runTest {
            val vm = SearchViewModel(source, FakeRecents(listOf("a")))
            val latest = collect(vm)
            vm.clearRecents()
            assertEquals(SearchUiState.Recents(emptyList()), latest())
        }

    @Test
    fun tappingARecentFillsTheQuery() =
        runTest {
            val vm = SearchViewModel(source, FakeRecents(listOf("conv")))
            collect(vm)
            vm.onQueryChange("conv")
            assertEquals("conv", vm.query.value)
        }
}
