package com.ezragubbay.quire.feature.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme

private const val PLACEHOLDER_ROWS = 30

/** Placeholder Courses destination: a scrollable list of tagged rows (`course-row-{n}`). Real content: E4. */
@Composable
fun CoursesScreen() {
    val spacing = QuireTheme.spacing
    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize().testTag("screen-courses"),
        contentPadding = PaddingValues(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        items((0 until PLACEHOLDER_ROWS).toList(), key = { it }) { n ->
            Text(
                text = "Course ${n + 1}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.md).testTag("course-row-$n"),
            )
        }
    }
}
