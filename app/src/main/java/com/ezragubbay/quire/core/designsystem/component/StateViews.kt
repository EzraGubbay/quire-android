package com.ezragubbay.quire.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme

/** Nothing to show yet. [action] is an optional call-to-action slot (a button). */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    CenteredColumn(modifier.testTag("empty-state")) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("empty-state-title"),
        )
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("empty-state-message"),
            )
        }
        action?.invoke()
    }
}

/** Loading failed: [message] plus a retry button. */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = stringResource(R.string.state_retry),
    retryTag: String = "error-state-retry",
) {
    CenteredColumn(modifier.testTag("error-state")) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("error-state-message"),
        )
        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp).testTag(retryTag)) {
            Text(retryLabel)
        }
    }
}

/** Static placeholder rows shown while content loads; announced to TalkBack as "Loading". */
@Composable
fun LoadingSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 5,
) {
    val spacing = QuireTheme.spacing
    val loading = stringResource(R.string.state_loading)
    Column(
        modifier =
            modifier
                .testTag("loading-skeleton")
                .fillMaxWidth()
                .padding(spacing.md)
                .semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        repeat(rows) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SkeletonBar(widthFraction = 0.7f, height = 16.dp)
                SkeletonBar(widthFraction = 0.4f, height = 12.dp)
            }
        }
    }
}

@Composable
private fun SkeletonBar(
    widthFraction: Float,
    height: androidx.compose.ui.unit.Dp,
) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
    )
}

/** Pull-down-to-refresh wrapper around scrollable [content]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuirePullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.testTag("pull-to-refresh"),
        content = { content() },
    )
}

@Composable
private fun CenteredColumn(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(QuireTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(QuireTheme.spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
