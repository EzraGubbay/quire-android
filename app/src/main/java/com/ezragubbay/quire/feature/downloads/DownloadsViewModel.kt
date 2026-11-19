package com.ezragubbay.quire.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezragubbay.quire.core.database.CourseDao
import com.ezragubbay.quire.core.database.FileDao
import com.ezragubbay.quire.core.download.DownloadItem
import com.ezragubbay.quire.core.download.DownloadRepository
import com.ezragubbay.quire.core.download.DownloadState
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** Display names for downloads, keyed by file id and course slug (from the cached course tree). */
data class DownloadLabels(
    val fileTitles: Map<String, String>,
    val courseNames: Map<String, String>,
)

/** What the Downloads tab needs from the data layer (a seam so the ViewModel is testable without Room). */
interface DownloadsSource {
    fun observeItems(): Flow<List<DownloadItem>>

    fun observeStorageBytes(): Flow<Long>

    fun observeLabels(): Flow<DownloadLabels>

    suspend fun remove(fileId: String)

    suspend fun removeAll()

    suspend fun retry(item: DownloadItem)
}

class RepositoryDownloadsSource
    @Inject
    constructor(
        private val repository: DownloadRepository,
        private val files: FileDao,
        private val courses: CourseDao,
    ) : DownloadsSource {
        override fun observeItems() = repository.observeAll()

        override fun observeStorageBytes() = repository.observeStorageBytes()

        override fun observeLabels(): Flow<DownloadLabels> =
            combine(files.observeTitles(), courses.observeAll()) { f, c ->
                DownloadLabels(f.associate { it.fileId to it.title }, c.associate { it.slug to it.name })
            }

        override suspend fun remove(fileId: String) = repository.remove(fileId)

        override suspend fun removeAll() = repository.removeAll()

        override suspend fun retry(item: DownloadItem) = repository.enqueue(item.courseSlug, item.fileId, item.format)
    }

@Module
@InstallIn(SingletonComponent::class)
internal interface DownloadsSourceModule {
    @Binds
    fun downloadsSource(impl: RepositoryDownloadsSource): DownloadsSource
}

enum class DownloadRowStatus { Downloading, Done, Failed }

data class DownloadRowUi(
    val fileId: String,
    val title: String,
    val sizeLabel: String,
    val status: DownloadRowStatus,
    /** 0..1 while downloading with a known total; null otherwise (indeterminate or not running). */
    val progress: Float?,
    val error: String?,
)

/** Rows of one course. [courseId] is the `download-group-{courseId}` tag segment (the course slug). */
data class DownloadGroupUi(
    val courseId: String,
    val courseName: String,
    val rows: List<DownloadRowUi>,
)

sealed interface DownloadsUiState {
    data object Loading : DownloadsUiState

    data object Empty : DownloadsUiState

    data class Content(
        val groups: List<DownloadGroupUi>,
        val storageLabel: String,
    ) : DownloadsUiState
}

private fun DownloadItem.toRow(labels: DownloadLabels): DownloadRowUi {
    val title = labels.fileTitles[fileId] ?: fileId
    return when (val s = state) {
        is DownloadState.Done ->
            DownloadRowUi(fileId, title, formatBytes(s.sizeBytes), DownloadRowStatus.Done, null, null)
        is DownloadState.Failed ->
            DownloadRowUi(fileId, title, "", DownloadRowStatus.Failed, null, s.reason)
        is DownloadState.Running ->
            DownloadRowUi(
                fileId,
                title,
                s.totalBytes?.let { "${formatBytes(s.progressBytes)} / ${formatBytes(it)}" }
                    ?: formatBytes(s.progressBytes),
                DownloadRowStatus.Downloading,
                s.fraction,
                null,
            )
        DownloadState.Idle -> DownloadRowUi(fileId, title, "", DownloadRowStatus.Downloading, null, null)
    }
}

internal fun toUiState(
    items: List<DownloadItem>,
    bytes: Long,
    labels: DownloadLabels,
): DownloadsUiState =
    if (items.isEmpty()) {
        DownloadsUiState.Empty
    } else {
        DownloadsUiState.Content(
            groups =
                items
                    .groupBy { it.courseSlug }
                    .map { (slug, rows) ->
                        DownloadGroupUi(slug, labels.courseNames[slug] ?: slug, rows.map { it.toRow(labels) })
                    },
            storageLabel = formatBytes(bytes),
        )
    }

@HiltViewModel
class DownloadsViewModel
    @Inject
    constructor(
        private val source: DownloadsSource,
    ) : ViewModel() {
        private val latest = MutableStateFlow<List<DownloadItem>>(emptyList())

        val state: StateFlow<DownloadsUiState> =
            combine(
                source.observeItems(),
                source.observeStorageBytes(),
                source.observeLabels(),
            ) { items, bytes, labels ->
                latest.value = items
                toUiState(items, bytes, labels)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), DownloadsUiState.Loading)

        fun remove(fileId: String) {
            viewModelScope.launch { source.remove(fileId) }
        }

        fun removeAll() {
            viewModelScope.launch { source.removeAll() }
        }

        /** Re-enqueues a failed download (resuming from its partial file). */
        fun retry(fileId: String) {
            val item = latest.value.firstOrNull { it.fileId == fileId } ?: return
            viewModelScope.launch { source.retry(item) }
        }
    }
