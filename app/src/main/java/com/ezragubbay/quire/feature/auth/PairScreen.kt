package com.ezragubbay.quire.feature.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme

const val SCREEN_PAIR_TAG = "screen-pair"
const val FIELD_SERVER_URL_TAG = "field-server-url"
const val FIELD_PAIR_CODE_TAG = "field-pair-code"
const val FIELD_DEVICE_NAME_TAG = "field-device-name"
const val ACTION_PAIR_TAG = "action-pair"
const val PAIR_ERROR_TAG = "pair-error"

/** Pairing form. [onPaired] runs once the session is stored; the caller owns navigation. */
@Composable
fun PairScreen(
    viewModel: PairViewModel,
    onPaired: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.paired.collect { onPaired() } }
    PairContent(
        state = state,
        onServerUrlChange = viewModel::onServerUrlChange,
        onCodeChange = viewModel::onCodeChange,
        onDeviceNameChange = viewModel::onDeviceNameChange,
        onPair = viewModel::onPair,
    )
}

@Composable
fun PairContent(
    state: PairUiState,
    onServerUrlChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onPair: () -> Unit,
) {
    val spacing = QuireTheme.spacing
    Surface(modifier = Modifier.fillMaxSize().testTag(SCREEN_PAIR_TAG)) {
        Column(
            modifier =
                Modifier
                    .systemBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(stringResource(R.string.pair_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.pair_subtitle), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = onServerUrlChange,
                label = { Text(stringResource(R.string.pair_server_url)) },
                singleLine = true,
                enabled = !state.loading,
                isError = state.error == PairError.InvalidUrl,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().testTag(FIELD_SERVER_URL_TAG),
            )
            OutlinedTextField(
                value = state.code,
                onValueChange = onCodeChange,
                label = { Text(stringResource(R.string.pair_code)) },
                singleLine = true,
                enabled = !state.loading,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                    ),
                modifier = Modifier.fillMaxWidth().testTag(FIELD_PAIR_CODE_TAG),
            )
            OutlinedTextField(
                value = state.deviceName,
                onValueChange = onDeviceNameChange,
                label = { Text(stringResource(R.string.pair_device_name)) },
                singleLine = true,
                enabled = !state.loading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onPair() }),
                modifier = Modifier.fillMaxWidth().testTag(FIELD_DEVICE_NAME_TAG),
            )
            state.error?.let {
                Text(
                    text = stringResource(it.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(PAIR_ERROR_TAG),
                )
            }
            Button(
                onClick = onPair,
                enabled = state.canPair,
                modifier = Modifier.fillMaxWidth().testTag(ACTION_PAIR_TAG),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.pair_action))
                }
            }
        }
    }
}

@StringRes
private fun PairError.messageRes(): Int =
    when (this) {
        PairError.InvalidUrl -> R.string.pair_error_invalid_url
        PairError.InvalidCode -> R.string.pair_error_invalid_code
        PairError.RateLimited -> R.string.pair_error_rate_limited
        PairError.Unreachable -> R.string.pair_error_unreachable
        PairError.ServerError -> R.string.pair_error_server
        PairError.NotQuireServer -> R.string.pair_error_not_quire
    }
