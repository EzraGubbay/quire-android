package com.ezragubbay.quire.feature.downloads

import com.ezragubbay.quire.core.download.DownloadItem
import com.ezragubbay.quire.core.download.DownloadState
import com.ezragubbay.quire.sync.FakePinController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    private class FakeSource : DownloadsSource {
        val items = MutableStateFlow<List<DownloadItem>?>(null)
        val bytes = MutableStateFlow(0L)
        val labels = MutableStateFlow(DownloadLabels(emptyMap(), emptyMap()))
        val removed = mutableListOf<String>()
        val retried = mutableListOf<DownloadItem>()
        var removedAll = false

        override fun observeItems() = kotlinx.coroutines.flow.flow { items.collect { if (it != null) emit(it) } }

        override fun observeStorageBytes() = bytes

        override fun observeLabels() = labels

        override suspend fun remove(fileId: String) {
            removed += fileId
        }

        override suspend fun removeAll() {
            removedAll = true
        }

        override suspend fun retry(item: DownloadItem) {
            retried += item
        }
    }

    private val source = FakeSource()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun item(
        id: String,
        course: String,
        state: DownloadState,
        format: String = "pdf",
    ) = DownloadItem(id, course, format, state, 0L)

    private fun done(
        id: String,
        course: String = "c1",
        size: Long = 2048,
    ) = item(id, course, DownloadState.Done("/p/$id", size))

    private fun newVm() = DownloadsViewModel(source, FakePinController())

    private fun kotlinx.coroutines.test.TestScope.content(vm: DownloadsViewModel): () -> DownloadsUiState {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return { vm.state.value }
    }

    @Test
    fun loading_untilFirstEmission() =
        runTest {
            val state = content(newVm())
            assertEquals(DownloadsUiState.Loading, state())
        }

    @Test
    fun empty_whenNoDownloads() =
        runTest {
            val state = content(newVm())
            source.items.value = emptyList()
            assertEquals(DownloadsUiState.Empty, state())
        }

    @Test
    fun groupsByCourse_withNamesAndTitles() =
        runTest {
            val state = content(newVm())
            source.labels.value = DownloadLabels(mapOf("f1" to "Lecture 1"), mapOf("c1" to "Calculus"))
            source.items.value = listOf(done("f1", "c1"), done("f2", "c2"), done("f3", "c1"))
            val c = state() as DownloadsUiState.Content
            assertEquals(listOf("c1", "c2"), c.groups.map { it.courseId })
            assertEquals("Calculus", c.groups[0].courseName)
            assertEquals("c2", c.groups[1].courseName) // unknown course falls back to the slug
            assertEquals(listOf("f1", "f3"), c.groups[0].rows.map { it.fileId })
            assertEquals("Lecture 1", c.groups[0].rows[0].title)
            assertEquals("f3", c.groups[0].rows[1].title) // unknown file falls back to the id
        }

    @Test
    fun doneRow_showsFormattedSize() =
        runTest {
            val state = content(newVm())
            source.items.value = listOf(done("f1", size = 1_572_864))
            val row =
                (state() as DownloadsUiState.Content)
                    .groups
                    .single()
                    .rows
                    .single()
            assertEquals(DownloadRowStatus.Done, row.status)
            assertEquals("1.5 MB", row.sizeLabel)
            assertEquals(null, row.progress)
        }

    @Test
    fun runningRow_exposesFractionAndProgressLabel() =
        runTest {
            val state = content(newVm())
            source.items.value = listOf(item("f1", "c1", DownloadState.Running(1024, 4096)))
            val row =
                (state() as DownloadsUiState.Content)
                    .groups
                    .single()
                    .rows
                    .single()
            assertEquals(DownloadRowStatus.Downloading, row.status)
            assertEquals(0.25f, row.progress)
            assertEquals("1.0 KB / 4.0 KB", row.sizeLabel)
        }

    @Test
    fun runningRow_withUnknownTotal_isIndeterminate() =
        runTest {
            val state = content(newVm())
            source.items.value = listOf(item("f1", "c1", DownloadState.Running(0, null)))
            val row =
                (state() as DownloadsUiState.Content)
                    .groups
                    .single()
                    .rows
                    .single()
            assertEquals(DownloadRowStatus.Downloading, row.status)
            assertEquals(null, row.progress)
            assertEquals("0 B", row.sizeLabel)
        }

    @Test
    fun failedRow_carriesReason() =
        runTest {
            val state = content(newVm())
            source.items.value = listOf(item("f1", "c1", DownloadState.Failed("HTTP 404")))
            val row =
                (state() as DownloadsUiState.Content)
                    .groups
                    .single()
                    .rows
                    .single()
            assertEquals(DownloadRowStatus.Failed, row.status)
            assertEquals("HTTP 404", row.error)
        }

    @Test
    fun storage_isFormattedTotal() =
        runTest {
            val state = content(newVm())
            source.bytes.value = 3L * 1024 * 1024 * 1024
            source.items.value = listOf(done("f1"))
            assertEquals("3.0 GB", (state() as DownloadsUiState.Content).storageLabel)
        }

    @Test
    fun remove_delegates() =
        runTest {
            val vm = newVm()
            vm.remove("f1")
            assertEquals(listOf("f1"), source.removed)
        }

    @Test
    fun removeAll_delegates() =
        runTest {
            val vm = newVm()
            vm.removeAll()
            assertTrue(source.removedAll)
        }

    @Test
    fun retry_reenqueuesTheKnownItem() =
        runTest {
            val vm = newVm()
            val state = content(vm)
            val failed = item("f1", "c1", DownloadState.Failed("x"), format = "original")
            source.items.value = listOf(failed)
            state()
            vm.retry("f1")
            assertEquals(listOf(failed), source.retried)
        }

    @Test
    fun retry_unknownFile_isNoOp() =
        runTest {
            val vm = newVm()
            vm.retry("nope")
            assertTrue(source.retried.isEmpty())
        }
}
