package com.ezragubbay.quire.feature.viewer.pdf

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ezragubbay.quire.core.download.DownloadRepository
import com.ezragubbay.quire.core.download.DownloadState
import com.ezragubbay.quire.navigation.ViewerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val PDF_FORMAT = "pdf"

sealed interface ViewerUiState {
    data object Loading : ViewerUiState

    data object Error : ViewerUiState

    /** [startPage] is the 0-based page to open on (the restored last-read page). */
    data class Ready(
        val document: PdfDocument,
        val startPage: Int,
    ) : ViewerUiState
}

@HiltViewModel
class PdfViewerViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        @ApplicationContext context: Context,
        private val downloads: DownloadRepository,
        private val positions: ReadingPositionStore,
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

        private var loadJob: Job? = null

        init {
            load()
        }

        fun retry() = load()

        /** Gets the PDF through the download engine (enqueued if absent), then opens it from the cached file. */
        private fun load() {
            loadJob?.cancel()
            _state.value = ViewerUiState.Loading
            loadJob =
                viewModelScope.launch {
                    val file = downloads.localFile(route.fileId)
                    if (file == null) {
                        downloads.enqueue(route.courseSlug, route.fileId, PDF_FORMAT)
                        val terminal =
                            downloads.observe(route.fileId).first {
                                it is DownloadState.Done ||
                                    it is DownloadState.Failed
                            }
                        if (terminal is DownloadState.Failed) return@launch fail()
                    }
                    val local = file ?: downloads.localFile(route.fileId) ?: return@launch fail()
                    runCatching { PdfDocument.open(local, cacheBytes) }
                        .onSuccess { doc ->
                            val saved = positions.lastPage(route.fileId) ?: 0
                            _state.value = ViewerUiState.Ready(doc, RenderWindow.clampPage(saved, doc.pageCount))
                        }.onFailure { fail() }
                }
        }

        private fun fail() {
            _state.value = ViewerUiState.Error
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
