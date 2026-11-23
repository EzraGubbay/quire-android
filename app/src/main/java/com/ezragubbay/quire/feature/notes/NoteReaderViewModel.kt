package com.ezragubbay.quire.feature.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.core.network.BrowseApi
import com.ezragubbay.quire.navigation.NoteReaderRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NoteReaderUiState {
    data object Loading : NoteReaderUiState

    data object Error : NoteReaderUiState

    data class Ready(
        val title: String,
        val blocks: List<NoteBlock>,
    ) : NoteReaderUiState
}

@HiltViewModel
class NoteReaderViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val api: BrowseApi,
    ) : ViewModel() {
        private val route = savedState.toRoute<NoteReaderRoute>()

        private val _state = MutableStateFlow<NoteReaderUiState>(NoteReaderUiState.Loading)
        val state: StateFlow<NoteReaderUiState> = _state.asStateFlow()

        init {
            load()
        }

        fun retry() = load()

        private fun load() {
            _state.value = NoteReaderUiState.Loading
            viewModelScope.launch {
                _state.value =
                    when (val result = api.note(route.courseSlug, route.noteId)) {
                        is ApiResult.Success ->
                            NoteReaderUiState.Ready(result.value.title, mathMarkdownBlocks(result.value.markdown))
                        is ApiResult.Failure -> NoteReaderUiState.Error
                    }
            }
        }
    }
