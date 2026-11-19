package com.ezragubbay.quire.feature.chat.ask

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ezragubbay.quire.core.data.course.toModel
import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.navigation.ViewerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A one-shot request for the viewer to open the document-scoped chat [threadId] of [courseSlug]. */
data class AskRequest(
    val courseSlug: String,
    val threadId: String,
    val documentTitle: String,
)

/** Drives the viewer's 'Ask about this document' action, scoped to the viewed file (the [ViewerRoute]). */
@HiltViewModel
class AskAboutDocumentViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val db: QuireDatabase,
        private val askAboutDocument: AskAboutDocumentUseCase,
    ) : ViewModel() {
        private val route = savedState.toRoute<ViewerRoute>()
        private val channel = Channel<AskRequest>(Channel.BUFFERED)
        val requests = channel.receiveAsFlow()
        private var asking = false

        fun ask() {
            if (asking) return
            asking = true
            viewModelScope.launch {
                try {
                    val result = askAboutDocument(route.courseSlug, route.fileId)
                    if (result is ApiResult.Success) {
                        val title =
                            db
                                .fileDao()
                                .getForCourse(route.courseSlug)
                                .firstOrNull { it.fileId == route.fileId }
                                ?.toModel()
                                ?.title
                        channel.send(AskRequest(route.courseSlug, result.value.id, title ?: result.value.title))
                    }
                } finally {
                    asking = false
                }
            }
        }
    }
