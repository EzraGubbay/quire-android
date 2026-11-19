package com.ezragubbay.quire.feature.chat.threads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.designsystem.component.QuireTopBar

/** One course's threads (the course screen's Ask destination), with its own top bar and back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseThreadsScreen(
    onBack: () -> Unit,
    onOpenThread: (courseSlug: String, threadId: String) -> Unit,
    viewModel: CourseThreadsViewModel = hiltViewModel(),
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { QuireTopBar(title = stringResource(R.string.chats_course_title), onBack = onBack) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            ThreadListScreen(viewModel, screenTag = "screen-course-threads", onOpenThread = onOpenThread)
        }
    }
}
