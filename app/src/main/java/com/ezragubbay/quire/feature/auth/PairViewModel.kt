package com.ezragubbay.quire.feature.auth

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezragubbay.quire.BuildConfig
import com.ezragubbay.quire.core.network.ServerUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

const val PAIR_CODE_LENGTH = 8
const val DEFAULT_SERVER_URL = "https://"

data class PairUiState(
    val serverUrl: String = DEFAULT_SERVER_URL,
    val code: String = "",
    val deviceName: String = "",
    val loading: Boolean = false,
    val error: PairError? = null,
) {
    val canPair: Boolean
        get() = !loading && serverUrl.isNotBlank() && code.length == PAIR_CODE_LENGTH && deviceName.isNotBlank()
}

@HiltViewModel
class PairViewModel
    @Inject
    constructor(
        private val pairer: Pairer,
    ) : ViewModel() {
        private val _state = MutableStateFlow(PairUiState(deviceName = Build.MODEL.orEmpty()))
        val state: StateFlow<PairUiState> = _state.asStateFlow()

        private val _paired = Channel<Unit>(Channel.BUFFERED)

        /** Fires once after a successful pairing (the session is already stored). */
        val paired: Flow<Unit> = _paired.receiveAsFlow()

        /** Explains why the user is back here: the stored session was revoked or expired. */
        fun onSessionExpired() = _state.update { it.copy(error = PairError.SessionExpired, loading = false) }

        fun onServerUrlChange(value: String) = _state.update { it.copy(serverUrl = value, error = null) }

        /** Uppercases and keeps only letters/digits (paste-friendly: spaces and dashes are dropped), max 8. */
        fun onCodeChange(value: String) =
            _state.update {
                it.copy(code = value.uppercase().filter(Char::isLetterOrDigit).take(PAIR_CODE_LENGTH), error = null)
            }

        fun onDeviceNameChange(value: String) = _state.update { it.copy(deviceName = value, error = null) }

        fun onPair() {
            val current = _state.value
            if (!current.canPair) return
            val url = current.serverUrl.trim()
            if (!ServerUrl.isAllowed(url, BuildConfig.DEBUG)) {
                _state.update { it.copy(error = PairError.InvalidUrl) }
                return
            }
            _state.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                when (val result = pairer.pair(url, current.code, current.deviceName.trim())) {
                    PairResult.Success -> {
                        _state.update { it.copy(loading = false, code = "") }
                        _paired.send(Unit)
                    }
                    is PairResult.Failure -> _state.update { it.copy(loading = false, error = result.error) }
                }
            }
        }
    }
