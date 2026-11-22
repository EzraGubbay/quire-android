package com.ezragubbay.quire.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezragubbay.quire.sync.SyncInterval
import com.ezragubbay.quire.sync.SyncRunner
import com.ezragubbay.quire.sync.SyncSettings
import com.ezragubbay.quire.sync.SyncSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Settings → Sync section renders. */
data class SyncUiState(
    val settings: SyncSettings = SyncSettings(),
    val syncing: Boolean = false,
)

/** The Sync section's state and callbacks, bundled so [SettingsContent] stays one optional parameter wider. */
class SyncActions(
    val state: SyncUiState,
    val onEnabledChanged: (Boolean) -> Unit,
    val onIntervalSelected: (SyncInterval) -> Unit,
    val onWifiOnlyChanged: (Boolean) -> Unit,
    val onSyncNow: () -> Unit,
)

/**
 * Settings → Sync. Writes go to [SyncSettingsStore]; the `SyncCoordinator` observes it and reschedules the
 * periodic work, so every change is applied idempotently. 'Sync now' runs one pass in-process.
 */
@HiltViewModel
class SyncSettingsViewModel
    @Inject
    constructor(
        private val store: SyncSettingsStore,
        private val runner: SyncRunner,
    ) : ViewModel() {
        private val syncing = MutableStateFlow(false)

        val state: StateFlow<SyncUiState> =
            combine(store.settings, syncing) { settings, busy -> SyncUiState(settings, busy) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SyncUiState())

        fun onEnabledChanged(enabled: Boolean) {
            viewModelScope.launch { store.setEnabled(enabled) }
        }

        fun onIntervalSelected(interval: SyncInterval) {
            viewModelScope.launch { store.setInterval(interval) }
        }

        fun onWifiOnlyChanged(wifiOnly: Boolean) {
            viewModelScope.launch { store.setWifiOnly(wifiOnly) }
        }

        fun onSyncNow() {
            if (syncing.value) return
            syncing.value = true
            viewModelScope.launch {
                try {
                    runner.run()
                } finally {
                    syncing.value = false
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
