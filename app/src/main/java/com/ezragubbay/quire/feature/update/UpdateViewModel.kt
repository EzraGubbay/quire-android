package com.ezragubbay.quire.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezragubbay.quire.core.update.UpdateChecker
import com.ezragubbay.quire.core.update.UpdatePrompt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Drives the "new version" dialog: checks on resume, and tracks what was hidden for this process. */
@HiltViewModel
class UpdateViewModel
    @Inject
    constructor(
        private val checker: UpdateChecker,
    ) : ViewModel() {
        /** The version hidden by Open or a dismissal; process-only. "Later" is persisted by the checker. */
        private val hidden = MutableStateFlow<String?>(null)
        private var check: Job? = null

        val prompt: StateFlow<UpdatePrompt?> =
            combine(checker.prompt, hidden) { p, h -> p?.takeIf { it.version != h } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        /** Called on every activity resume; the checker itself throttles to once per 6 h. */
        fun onResume() {
            if (check?.isActive == true) return
            check =
                viewModelScope.launch {
                    try {
                        checker.checkIfDue()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught", "SwallowedException") _: Exception,
                    ) {
                        // Best effort: an update check must never reach the UI.
                    }
                }
        }

        fun onLater(p: UpdatePrompt) {
            hidden.value = p.version
            viewModelScope.launch { checker.snooze(p.version) }
        }

        fun onOpened(p: UpdatePrompt) {
            hidden.value = p.version
        }

        fun onDismissed(p: UpdatePrompt) {
            hidden.value = p.version
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
