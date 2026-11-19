package com.ezragubbay.quire.feature.courses.detail

import androidx.lifecycle.SavedStateHandle
import com.ezragubbay.quire.core.data.course.Course
import com.ezragubbay.quire.core.data.course.CourseCounts
import com.ezragubbay.quire.core.data.course.CourseDetail
import com.ezragubbay.quire.core.data.course.CourseFile
import com.ezragubbay.quire.core.data.course.CourseNote
import com.ezragubbay.quire.core.data.course.CourseSession
import com.ezragubbay.quire.core.data.course.Loaded
import com.ezragubbay.quire.core.data.course.SyncState
import com.ezragubbay.quire.core.designsystem.theme.FileType
import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.feature.courses.files.FileSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
class CourseViewModelTest {
    private class FakeSource : CourseSource {
        val course = MutableSharedFlow<Loaded<CourseDetail?>>(replay = 1)
        val sessions = MutableSharedFlow<Loaded<List<CourseSession>>>(replay = 1)
        val files = MutableSharedFlow<Loaded<List<CourseFile>>>(replay = 1)
        val notes = MutableSharedFlow<Loaded<List<CourseNote>>>(replay = 1)
        val refreshed = mutableListOf<String>()

        override fun observeCourse(slug: String): Flow<Loaded<CourseDetail?>> = course

        override fun observeSessions(slug: String): Flow<Loaded<List<CourseSession>>> = sessions

        override fun observeFiles(slug: String): Flow<Loaded<List<CourseFile>>> = files

        override fun observeNotes(slug: String): Flow<Loaded<List<CourseNote>>> = notes

        override suspend fun refresh(slug: String): SyncState {
            refreshed += slug
            return SyncState.Synced
        }

        fun emit(
            detail: CourseDetail? = DETAIL,
            sessions: List<CourseSession> = emptyList(),
            files: List<CourseFile> = emptyList(),
            notes: List<CourseNote> = emptyList(),
            state: SyncState = SyncState.Synced,
        ) {
            course.tryEmit(Loaded(detail, state))
            this.sessions.tryEmit(Loaded(sessions, state))
            this.files.tryEmit(Loaded(files, state))
            this.notes.tryEmit(Loaded(notes, state))
        }
    }

    private val source = FakeSource()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = CourseViewModel(SavedStateHandle(mapOf("slug" to "signals")), source)

    private fun TestScope.latest(vm: CourseViewModel): () -> CourseUiState {
        var latest: CourseUiState = vm.state.value
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { latest = it } }
        return { latest }
    }

    @Test
    fun content_exposesHeaderAndTabData() =
        runTest {
            val vm = vm()
            val ui = latest(vm)
            source.emit(sessions = listOf(lecture(1)), files = listOf(file("f1")), notes = listOf(note("n1")))

            val content = ui() as CourseUiState.Content
            assertEquals("Signals and Systems", content.title)
            assertEquals("0002 · Winter 2026", content.subtitle)
            assertEquals(listOf("f1"), content.files.map { it.fileId })
            assertEquals(listOf("n1"), content.notes.map { it.id })
            assertFalse(content.offline)
        }

    @Test
    fun filesTab_filterAndSortSurviveRecreation() =
        runTest {
            val handle = SavedStateHandle(mapOf("slug" to "signals"))
            val vm = CourseViewModel(handle, source)
            val ui = latest(vm)
            source.emit(
                files =
                    listOf(
                        file("l", "Zeta", FileType.LECTURE),
                        file("r", "Alpha", FileType.RECITATION),
                    ),
            )
            vm.toggleFileType(FileType.RECITATION)
            vm.selectFileSort(FileSort.TITLE)

            val view = (ui() as CourseUiState.Content).filesView
            assertEquals(listOf(FileType.RECITATION), view.groups.map { it.type })
            assertEquals(FileSort.TITLE, view.sort)

            val recreated = latest(CourseViewModel(handle, source))
            assertEquals(setOf(FileType.RECITATION), (recreated() as CourseUiState.Content).filesView.selectedTypes)

            vm.toggleFileType(FileType.RECITATION)
            assertEquals(2, (ui() as CourseUiState.Content).filesView.groups.size)
        }

    @Test
    fun sessions_defaultToLectures_andFilterSwitchesToRecitations() =
        runTest {
            val vm = vm()
            val ui = latest(vm)
            source.emit(sessions = listOf(recitation(1), lecture(2), lecture(1)))

            var content = ui() as CourseUiState.Content
            assertEquals(SessionKind.LECTURE, content.sessionFilter)
            assertEquals(listOf("lecture-1", "lecture-2"), content.sessions.map { it.tag })

            vm.selectSessionFilter(SessionKind.RECITATION)
            content = ui() as CourseUiState.Content
            assertEquals(SessionKind.RECITATION, content.sessionFilter)
            assertEquals(listOf("recitation-1"), content.sessions.map { it.tag })
        }

    @Test
    fun sessions_defaultToRecitations_whenThereAreNoLectures() =
        runTest {
            val vm = vm()
            val ui = latest(vm)
            source.emit(sessions = listOf(recitation(1)))

            val content = ui() as CourseUiState.Content
            assertEquals(SessionKind.RECITATION, content.sessionFilter)
            assertEquals(listOf("recitation-1"), content.sessions.map { it.tag })
        }

    @Test
    fun sessionRow_resolvesDeckTitles_andNoteIndicator() =
        runTest {
            val vm = vm()
            val ui = latest(vm)
            source.emit(
                sessions = listOf(lecture(1, fileIds = listOf("f1", "gone"), noteId = "n1"), lecture(2)),
                files = listOf(file("f1", title = "Convolution")),
            )

            val rows = (ui() as CourseUiState.Content).sessions
            val first = rows.first()
            assertTrue(first.hasNote)
            assertEquals(listOf("f1" to "Convolution", "gone" to null), first.decks.map { it.fileId to it.title })
            assertFalse(rows[1].hasNote)
            assertTrue(rows[1].decks.isEmpty())
        }

    @Test
    fun timeline_ordersByDate_undatedLast_thenNumber() {
        val ordered =
            listOf(
                lecture(3, date = null),
                lecture(2, date = "2026-11-09"),
                lecture(1, date = "2026-11-02"),
                lecture(4, date = null),
            ).timeline(SessionKind.LECTURE)
        assertEquals(listOf(1, 2, 3, 4), ordered.map { it.number })
    }

    @Test
    fun loading_whenNothingCachedAndRefreshing() =
        runTest {
            val vm = vm()
            val ui = latest(vm)
            source.emit(detail = null, state = SyncState.Refreshing)
            assertEquals(CourseUiState.Loading, ui())
        }

    @Test
    fun error_whenRefreshFailedWithNoCache() =
        runTest {
            val vm = vm()
            val ui = latest(vm)
            source.emit(detail = null, state = SyncState.Failed(ApiResult.Failure.Server(500)))
            assertEquals(CourseUiState.Error, ui())
        }

    @Test
    fun offline_whenNetworkDownButCached() =
        runTest {
            val vm = vm()
            val ui = latest(vm)
            source.emit(
                sessions = listOf(lecture(1)),
                state = SyncState.Failed(ApiResult.Failure.Network(IOException("down"))),
            )
            assertTrue((ui() as CourseUiState.Content).offline)
        }

    @Test
    fun retry_refreshesTheCourse() =
        runTest {
            val vm = vm()
            vm.retry()
            assertEquals(listOf("signals"), source.refreshed)
        }

    @Test
    fun noSessions_leavesFilterEmpty_withoutCrashing() =
        runTest {
            val vm = vm()
            val ui = latest(vm)
            source.emit()
            val content = ui() as CourseUiState.Content
            assertTrue(content.sessions.isEmpty())
        }

    private companion object {
        val DETAIL =
            CourseDetail(
                Course("signals", "id", "Signals and Systems", "0002", "Winter 2026", 1, "lecture", 2),
                CourseCounts(emptyMap(), emptyMap(), 0),
            )

        fun lecture(
            number: Int,
            date: String? = "2026-11-0$number",
            fileIds: List<String> = emptyList(),
            noteId: String? = null,
        ) = CourseSession("signals", "lecture", number, "Lecture $number", date, fileIds, noteId)

        fun recitation(number: Int) =
            CourseSession("signals", "recitation", number, "Rec $number", null, emptyList(), null)

        fun file(
            id: String,
            title: String = id,
            type: FileType = FileType.LECTURE,
        ) = CourseFile(
            id,
            "signals",
            title,
            type,
            1,
            null,
            null,
            10,
            100L,
            true,
            false,
            false,
            "2026-11-01T00:00:00Z",
        )

        fun note(id: String) = CourseNote(id, "signals", "slug-$id", "Note $id", "lecture", 1, "2026-11-01T00:00:00Z")
    }
}
