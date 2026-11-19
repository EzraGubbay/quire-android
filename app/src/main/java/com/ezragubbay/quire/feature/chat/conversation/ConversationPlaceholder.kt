package com.ezragubbay.quire.feature.chat.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ezragubbay.quire.core.designsystem.component.QuireTopBar

/**
 * PLACEHOLDER destination for `ConversationRoute` (E6-S2 skeleton). The conversation story (E6-S3) replaces this
 * with the real screen; it keeps the `screen-conversation` and `chat-input` tags the E6 e2e journey relies on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationPlaceholder(onBack: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("screen-conversation"),
        topBar = { QuireTopBar(title = "Chat", onBack = onBack) },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text("Conversation coming soon")
            OutlinedTextField(
                value = "",
                onValueChange = {},
                enabled = false,
                modifier = Modifier.testTag("chat-input"),
            )
        }
    }
}
