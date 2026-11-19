package com.ezragubbay.quire.feature.viewer.pdf

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ezragubbay.quire.core.data.course.toModel
import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.download.DownloadRepository
import com.ezragubbay.quire.core.download.DownloadState
import com.ezragubbay.quire.feature.viewer.DownloadToggle
import com.ezragubbay.quire.feature.viewer.FORMAT_PDF
import com.ezragubbay.quire.feature.viewer.OpenFileUseCase
import com.ezragubbay.quire.feature.viewer.OpenPlan
import com.ezragubbay.quire.feature.viewer.OpenSource
import com.ezragubbay.quire.feature.viewer.OpenWithMime
import com.ezragubbay.quire.feature.viewer.ToggleAction
import com.ezragubbay.quire.feature.viewer.ViewerStreamer
import com.ezragubbay.quire.navigation.ViewerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

sealed interface ViewerUiState {
    data object Loading : ViewerUiState

    data object Error : ViewerUiState

    /** Nothing the PDF viewer can show for this file (e.g. Markdown-only, which opens in the notes reader). */
    data object Unsupported : ViewerUiState

    /** Original-only file: no in-app view; offer 'Open with...'. [noApp] once no installed app could open it. */
    data class OpenWith(
        val title: String,
        val busy: Boolean = false,
        val failed: Boolean = false,
        val noApp: Boolean = false,
    ) : ViewerUiState

    /** [startPage] is the 0-based page to open on (the restored last-read page). */
    data class Ready(
        val document: PdfDocument,
        val startPage: Int,
    ) : ViewerUiState
}

/** A one-shot request for the screen to launch the 'Open with...' chooser on [file]. */
data class OpenWithRequest(
    val file: File,
    val mime: String,
    val title: String,
)

/** A downloaded file that can be shared or saved: the local [file], its [mime], and the user-facing [title]. */
data class ShareTarget(
    val file: File,
    val mime: String,
    val title: String,
)

@HiltViewModel
class PdfViewerViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        @ApplicationContext context: Context,
        private val downloads: DownloadRepository,
        private val positions: ReadingPositionStore,
        private val db: QuireDatabase,
        private val streamer: ViewerStreamer,
    ) : ViewModel() {
        private val route = savedState.toRoute<ViewerRoute>()
        private val cacheBytes =
            BitmapCachePolicy.cacheBytes(
                (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass,
            )

        private val _state = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)
        val state: StateFlow<ViewerUiState> = _state.asStateFlow()

        private val _zoom = MutableStateFlow(ZoomState())
        val zoom: StateFlow<ZoomState> = _zoom.asStateFlow()

        private val _immersive = MutableStateFlow(false)
        val immersive: StateFlow<Boolean> = _immersive.asStateFlow()

        /** Stream-to-cache progress (0..1), or null while unknown / not streaming. */
        private val _progress = MutableStateFlow<Float?>(null)
        val progress: StateFlow<Float?> = _progress.asStateFlow()

        private val onStreamProgress: (Long, Long?) -> Unit = { read, total ->
            _progress.value = total?.let { (read.toFloat() / it).coerceIn(0f, 1f) }
        }

        /** Persisted-download state for this file; the toggle drives it through [DownloadRepository]. */
        val downloadState: StateFlow<DownloadState> =
            downloads
                .observe(route.fileId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DownloadState.Idle)

        private var title: String? = null

        /** Non-null only once the file is downloaded and on disk: share and save-a-copy are enabled then. */
        val shareTarget: StateFlow<ShareTarget?> =
            downloadState
                .map { state ->
                    if (state !is DownloadState.Done) {
                        null
                    } else {
                        downloads.localFile(route.fileId)?.let {
                            ShareTarget(it, OpenWithMime.forName(it.name), title ?: it.nameWithoutExtension)
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        private val openWithChannel = Channel<OpenWithRequest>(Channel.BUFFERED)
        val openWithRequests = openWithChannel.receiveAsFlow()

        private var loadJob: Job? = null
        private var plan: OpenPlan? = null

        init {
            load()
        }

        fun retry() = load()

        /**
         * Opens per [OpenFileUseCase]: a downloaded file from disk, otherwise streamed into the evictable viewer
         * cache (never a persisted download; the toggle is the only thing that creates one).
         */
        private fun load() {
            loadJob?.cancel()
            _state.value = ViewerUiState.Loading
            _progress.value = null
            loadJob =
                viewModelScope.launch {
                    val meta =
                        db
                            .fileDao()
                            .getForCourse(route.courseSlug)
                            .firstOrNull { it.fileId == route.fileId }
                            ?.toModel()
                    title = meta?.title
                    val chosen = OpenFileUseCase.plan(meta, downloads.localFile(route.fileId))
                    plan = chosen
                    when (chosen) {
                        OpenPlan.Unsupported -> _state.value = ViewerUiState.Unsupported
                        is OpenPlan.OpenWith -> _state.value = ViewerUiState.OpenWith(chosen.title)
                        is OpenPlan.ViewPdf -> showPdf(chosen.source)
                    }
                }
        }

        private suspend fun showPdf(source: OpenSource) {
            val local =
                runCatching { streamer.resolve(source, route.courseSlug, route.fileId, null, onStreamProgress) }
                    .getOrElse { e ->
                        if (e is CancellationException) throw e
                        return _state.update { ViewerUiState.Error }
                    }
            runCatching { PdfDocument.open(local, cacheBytes) }
                .onSuccess { doc ->
                    val saved = positions.lastPage(route.fileId) ?: 0
                    _state.value = ViewerUiState.Ready(doc, RenderWindow.clampPage(saved, doc.pageCount))
                }.onFailure {
                    if (source is OpenSource.Stream) local.delete() // a corrupt cache entry must not stick
                    _state.value = ViewerUiState.Error
                }
        }

        /** Download toggle: Idle/Failed enqueue a persisted download; Running/Done remove it. */
        fun toggleDownload() {
            viewModelScope.launch {
                when (DownloadToggle.actionFor(downloadState.value)) {
                    ToggleAction.Enqueue -> downloads.enqueue(route.courseSlug, route.fileId, FORMAT_PDF)
                    ToggleAction.Remove -> downloads.remove(route.fileId)
                }
            }
        }

        /** 'Open with...' for an original-only file: fetch (or reuse) the original, then ask the screen to launch. */
        fun openWith() {
            val current = _state.value as? ViewerUiState.OpenWith
            val source = (plan as? OpenPlan.OpenWith)?.source
            if (current == null || source == null || current.busy) return
            loadJob?.cancel()
            _state.value = current.copy(busy = true, failed = false, noApp = false)
            loadJob =
                viewModelScope.launch {
                    runCatching {
                        streamer.resolve(
                            source,
                            route.courseSlug,
                            route.fileId,
                            current.title,
                            onStreamProgress,
                        )
                    }.onSuccess {
                        _state.value = current.copy(busy = false)
                        openWithChannel.send(OpenWithRequest(it, OpenWithMime.forName(current.title), current.title))
                    }.onFailure { e ->
                        if (e is CancellationException) throw e
                        _state.value = current.copy(busy = false, failed = true)
                    }
                }
        }

        fun onNoAppFound() {
            (_state.value as? ViewerUiState.OpenWith)?.let { _state.value = it.copy(noApp = true) }
        }

        fun onPageChanged(page: Int) {
            viewModelScope.launch { withContext(NonCancellable) { positions.save(route.fileId, page) } }
        }

        fun updateZoom(transform: (ZoomState) -> ZoomState) = _zoom.update(transform)

        fun toggleImmersive() = _immersive.update { !it }

        override fun onCleared() {
            (_state.value as? ViewerUiState.Ready)?.let { runBlocking { it.document.close() } }
        }
    }
