package com.ezragubbay.quire.feature.chat

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ezragubbay.quire.feature.chat.threads.ChatsViewModel
import com.ezragubbay.quire.feature.chat.threads.ThreadListScreen

/** The Chats tab: recent threads across all courses, plus "New chat". */
@Composable
fun ChatsScreen(
    onOpenThread: (courseSlug: String, threadId: String) -> Unit = { _, _ -> },
    viewModel: ChatsViewModel = hiltViewModel(),
) {
    ThreadListScreen(viewModel, screenTag = "screen-chats", onOpenThread = onOpenThread)
}
