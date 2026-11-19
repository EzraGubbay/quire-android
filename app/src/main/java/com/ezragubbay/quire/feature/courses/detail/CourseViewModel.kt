package com.ezragubbay.quire.feature.courses.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezragubbay.quire.core.data.course.CourseDetail
import com.ezragubbay.quire.core.data.course.CourseFile
import com.ezragubbay.quire.core.data.course.CourseNote
import com.ezragubbay.quire.core.data.course.CourseRepository
import com.ezragubbay.quire.core.data.course.CourseSession
import com.ezragubbay.quire.core.data.course.Loaded
import com.ezragubbay.quire.core.data.course.SyncState
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
private const val FILTER_KEY = "sessionFilter"

/** What the course screen needs from the data layer (a seam so the ViewModel is testable without Room). */
interface CourseSource {
    fun observeCourse(slug: String): Flow<Loaded<CourseDetail?>>

    fun observeSessions(slug: String): Flow<Loaded<List<CourseSession>>>

    fun observeFiles(slug: String): Flow<Loaded<List<CourseFile>>>

    fun observeNotes(slug: String): Flow<Loaded<List<CourseNote>>>

    suspend fun refresh(slug: String): SyncState
}

class RepositoryCourseSource
    @Inject
    constructor(
        private val repository: CourseRepository,
    ) : CourseSource {
        override fun observeCourse(slug: String) = repository.observeCourse(slug)

        override fun observeSessions(slug: String) = repository.observeSessions(slug)

        override fun observeFiles(slug: String) = repository.observeFiles(slug)

        override fun observeNotes(slug: String) = repository.observeNotes(slug)

        override suspend fun refresh(slug: String) = repository.refreshCourse(slug)
    }

@Module
@InstallIn(SingletonComponent::class)
internal interface CourseSourceModule {
    @Binds
    fun courseSource(impl: RepositoryCourseSource): CourseSource
}

/** The session kinds the Sessions tab can filter by; [key] is the server `kind` and the test-tag segment. */
enum class SessionKind(
    val key: String,
) {
    LECTURE("lecture"),
    RECITATION("recitation"),
}

/** A deck attached to a session; [title] is null when the file is not (yet) cached. */
data class SessionDeck(
    val fileId: String,
    val title: String?,
)

data class SessionRow(
    val kind: String,
    val number: Int,
    val title: String,
    val date: String?,
    val decks: List<SessionDeck>,
    val hasNote: Boolean,
) {
    /** The `session-row-{tag}` test-tag segment. */
    val tag: String get() = "$kind-$number"
}

sealed interface CourseUiState {
    /** First load: nothing cached yet and the refresh is running. */
    data object Loading : CourseUiState

    /** Refresh failed and there is no cache to show. */
    data object Error : CourseUiState

    data class Content(
        val title: String,
        /** `code · semester`, or null when neither is known. */
        val subtitle: String?,
        val sessionFilter: SessionKind,
        /** Sessions of [sessionFilter], in timeline order. */
        val sessions: List<SessionRow>,
        val files: List<CourseFile>,
        val notes: List<CourseNote>,
        /** Network is down but cached data is shown. */
        val offline: Boolean,
    ) : CourseUiState
}

/** Sessions of [kind] as a timeline: by date (undated last), then by number. */
internal fun List<CourseSession>.timeline(kind: SessionKind): List<CourseSession> =
    filter { it.kind == kind.key }.sortedWith(
        compareBy<CourseSession> { it.date == null }.thenBy { it.date }.thenBy { it.number },
    )

private data class Bundle(
    val detail: Loaded<CourseDetail?>,
    val sessions: List<CourseSession>,
    val files: List<CourseFile>,
    val notes: List<CourseNote>,
)

private fun toUiState(
    bundle: Bundle,
    selected: SessionKind?,
    slug: String,
): CourseUiState {
    val detail = bundle.detail.data
    val state = bundle.detail.state
    if (detail == null) {
        return when {
            state is SyncState.Failed -> CourseUiState.Error
            else -> CourseUiState.Loading
        }
    }
    val filter =
        selected
            ?: if (bundle.sessions.any { it.kind == SessionKind.LECTURE.key }) {
                SessionKind.LECTURE
            } else if (bundle.sessions.isNotEmpty()) {
                SessionKind.RECITATION
            } else {
                SessionKind.LECTURE
            }
    val titles = bundle.files.associate { it.fileId to it.title }
    val course = detail.course
    return CourseUiState.Content(
        title = course.name.ifBlank { slug },
        subtitle =
            listOfNotNull(course.code, course.semester)
                .filter(String::isNotBlank)
                .joinToString(" · ")
                .ifEmpty { null },
        sessionFilter = filter,
        sessions =
            bundle.sessions.timeline(filter).map { s ->
                SessionRow(
                    kind = s.kind,
                    number = s.number,
                    title = s.title,
                    date = s.date,
                    decks = s.fileIds.map { SessionDeck(it, titles[it]) },
                    hasNote = s.noteId != null,
                )
            },
        files = bundle.files,
        notes = bundle.notes,
        offline = (state as? SyncState.Failed)?.isOffline == true,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CourseViewModel
    @Inject
    constructor(
        private val savedState: SavedStateHandle,
        private val source: CourseSource,
    ) : ViewModel() {
        // Type-safe navigation puts the route's `slug` argument in the SavedStateHandle.
        private val slug: String = checkNotNull(savedState["slug"])

        /** The explicit Lectures / Recitations choice (survives process death); null picks a default. */
        private val selectedFilter = savedState.getStateFlow<String?>(FILTER_KEY, null)

        val state: StateFlow<CourseUiState> =
            combine(
                source.observeCourse(slug),
                source.observeSessions(slug),
                source.observeFiles(slug),
                source.observeNotes(slug),
                selectedFilter,
            ) { detail, sessions, files, notes, selected ->
                toUiState(
                    Bundle(detail, sessions.data, files.data, notes.data),
                    SessionKind.entries.firstOrNull { it.key == selected },
                    slug,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), CourseUiState.Loading)

        fun selectSessionFilter(kind: SessionKind) {
            savedState[FILTER_KEY] = kind.key
        }

        /** Retry from the error state. */
        fun retry() {
            viewModelScope.launch { source.refresh(slug) }
        }
    }
