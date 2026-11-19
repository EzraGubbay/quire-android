package com.ezragubbay.quire.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ezragubbay.quire.core.data.settings.SettingsRepository
import com.ezragubbay.quire.core.data.settings.UserSettings
import com.ezragubbay.quire.core.data.settings.settingsDataStore
import com.ezragubbay.quire.core.designsystem.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Exposes [UserSettings] to the UI. Changes show immediately (optimistic state) and are persisted in the
 * background, so the theme applies without an activity restart and survives relaunch.
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {
    private val _settings = MutableStateFlow(UserSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private val _loaded = MutableStateFlow(false)

    /** True once the persisted settings have been read; the splash screen waits on this to avoid a theme flash. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect {
                _settings.value = it
                _loaded.value = true
            }
        }
    }

    fun onThemeModeSelected(mode: ThemeMode) {
        _settings.value = _settings.value.copy(themeMode = mode)
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun onUseDeviceColorsChanged(enabled: Boolean) {
        _settings.value = _settings.value.copy(useDeviceColors = enabled)
        viewModelScope.launch { repository.setUseDeviceColors(enabled) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                    SettingsViewModel(SettingsRepository(app.settingsDataStore))
                }
            }
    }
}
