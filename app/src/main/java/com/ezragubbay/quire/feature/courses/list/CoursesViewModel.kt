package com.ezragubbay.quire.feature.courses.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezragubbay.quire.core.data.course.Course
import com.ezragubbay.quire.core.data.course.CourseRepository
import com.ezragubbay.quire.core.data.course.Loaded
import com.ezragubbay.quire.core.data.course.SyncState
import com.ezragubbay.quire.core.data.course.showsError
import com.ezragubbay.quire.core.data.course.showsLoading
import com.ezragubbay.quire.core.data.course.showsOfflineBanner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** What the Courses screen needs from the data layer (a seam so the ViewModel is testable without Room). */
interface CoursesSource {
    fun observeCourses(): Flow<Loaded<List<Course>>>

    suspend fun refreshCourses(): SyncState
}

class RepositoryCoursesSource
    @Inject
    constructor(
        private val repository: CourseRepository,
    ) : CoursesSource {
        override fun observeCourses() = repository.observeCourses()

        override suspend fun refreshCourses() = repository.refreshCourses()
    }

@Module
@InstallIn(SingletonComponent::class)
internal interface CoursesSourceModule {
    @Binds
    fun coursesSource(impl: RepositoryCoursesSource): CoursesSource
}

/** Courses sharing one semester header. [tag] is the `course-group-{tag}` test-tag segment. */
data class SemesterGroup(
    val label: String?,
    val tag: String,
    val courses: List<Course>,
)

sealed interface CoursesUiState {
    /** First load: nothing cached yet and the refresh is running. */
    data object Loading : CoursesUiState

    /** Refreshed fine and there are no courses. */
    data object Empty : CoursesUiState

    /** Refresh failed and there is no cache to show. */
    data object Error : CoursesUiState

    data class Content(
        val groups: List<SemesterGroup>,
        /** Network is down but cached courses are shown. */
        val offline: Boolean,
        val refreshing: Boolean,
    ) : CoursesUiState
}

internal fun Loaded<List<Course>>.toUiState(): CoursesUiState =
    when {
        showsLoading -> CoursesUiState.Loading
        showsError -> CoursesUiState.Error
        data.isEmpty() -> CoursesUiState.Empty
        else ->
            CoursesUiState.Content(
                groups =
                    data
                        .groupBy { it.semester?.trim()?.takeIf(String::isNotEmpty) }
                        .toSortedMap(SemesterOrder)
                        .map { (label, courses) -> SemesterGroup(label, semesterSlug(label), courses) },
                offline = showsOfflineBanner,
                refreshing = state == SyncState.Refreshing,
            )
    }

@HiltViewModel
class CoursesViewModel
    @Inject
    constructor(
        private val source: CoursesSource,
    ) : ViewModel() {
        val state: StateFlow<CoursesUiState> =
            source
                .observeCourses()
                .map { it.toUiState() }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                    CoursesUiState.Loading,
                )

        private var started = false

        /**
         * The screen entered the STARTED state. The first start is covered by observing (which refreshes if stale);
         * later ones (relaunch, returning to the app) refetch so an old cache shows its offline state honestly.
         */
        fun onStart() {
            if (started) refresh() else started = true
        }

        /** Pull-to-refresh. */
        fun refresh() {
            viewModelScope.launch { source.refreshCourses() }
        }

        /** Retry from the error state. */
        fun retry() = refresh()
    }
