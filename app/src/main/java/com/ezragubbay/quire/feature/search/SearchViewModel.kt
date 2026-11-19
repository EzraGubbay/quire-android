package com.ezragubbay.quire.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezragubbay.quire.core.data.course.Course
import com.ezragubbay.quire.core.data.course.CourseFile
import com.ezragubbay.quire.core.data.course.SearchRepository
import com.ezragubbay.quire.core.data.course.SearchResults
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

const val SEARCH_DEBOUNCE_MS = 250L
const val RECENT_LIMIT = 5
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** What the Search screen needs from the data layer: a query over the Room cache (a seam, testable without Room). */
interface SearchSource {
    suspend fun search(query: String): SearchResults
}

class RepositorySearchSource
    @Inject
    constructor(
        private val repository: SearchRepository,
    ) : SearchSource {
        override suspend fun search(query: String) = repository.search(query)
    }

/** The last [RECENT_LIMIT] searches, newest first. */
interface RecentSearches {
    val recents: StateFlow<List<String>>

    suspend fun add(query: String)

    suspend fun clear()
}

@Module
@InstallIn(SingletonComponent::class)
internal interface SearchModule {
    @Binds
    fun searchSource(impl: RepositorySearchSource): SearchSource

    @Binds
    fun recentSearches(impl: DataStoreRecentSearches): RecentSearches
}

sealed interface SearchUiState {
    /** Empty query: the saved recent searches (possibly none). */
    data class Recents(
        val queries: List<String>,
    ) : SearchUiState

    data class Results(
        val courses: List<Course>,
        val files: List<CourseFile>,
    ) : SearchUiState

    data object NoResults : SearchUiState
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val source: SearchSource,
        private val recentSearches: RecentSearches,
    ) : ViewModel() {
        private val _query = MutableStateFlow("")

        /** The text in the field (updates immediately; the search itself is debounced). */
        val query: StateFlow<String> = _query

        val state: StateFlow<SearchUiState> =
            _query
                .map { it.trim() }
                .distinctUntilChanged()
                .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
                .flatMapLatest { q ->
                    if (q.isEmpty()) {
                        recentSearches.recents.map<List<String>, SearchUiState> { SearchUiState.Recents(it) }
                    } else {
                        flowOf(source.search(q)).map { it.toUiState() }
                    }
                }.stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                    SearchUiState.Recents(emptyList()),
                )

        fun onQueryChange(value: String) {
            _query.value = value
        }

        /** A result was tapped: remember the query that found it. */
        fun onResultOpened() {
            val q = _query.value.trim()
            if (q.isNotEmpty()) viewModelScope.launch { recentSearches.add(q) }
        }

        fun clearRecents() {
            viewModelScope.launch { recentSearches.clear() }
        }
    }

private fun SearchResults.toUiState(): SearchUiState =
    if (isEmpty) SearchUiState.NoResults else SearchUiState.Results(courses, files)
