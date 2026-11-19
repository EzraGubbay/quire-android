package com.ezragubbay.quire.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ezragubbay.quire.core.designsystem.theme.FileType
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme
import com.ezragubbay.quire.core.designsystem.theme.ThemeMode

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    QuireTheme(if (dark) ThemeMode.Dark else ThemeMode.Light) {
        Surface(color = MaterialTheme.colorScheme.background) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@QuirePreviews
@Composable
fun QuireTopBarPreview() =
    PreviewSurface {
        QuireTopBar(title = "Courses", onBack = {})
    }

@QuirePreviews
@Composable
fun CourseCardPreview() =
    PreviewSurface {
        Column(Modifier.padding(16.dp)) {
            CourseCard(title = "Algorithms / אלגוריתמים", subtitle = "12 files", onClick = {})
        }
    }

@QuirePreviews
@Composable
fun FileRowPreview() =
    PreviewSurface {
        Column {
            FileRow("Lecture 3: Graphs", "PDF - 2.1 MB", FileType.LECTURE, onClick = {}, trailing = { Text("v") })
            FileRow("תרגיל בית 2", "PDF - 340 KB", FileType.HOMEWORK, onClick = {})
        }
    }

@QuirePreviews
@Composable
fun FileTypeChipPreview() =
    PreviewSurface {
        Column(Modifier.padding(16.dp)) { FileType.entries.forEach { FileTypeChip(it, Modifier.padding(2.dp)) } }
    }

@QuirePreviews
@Composable
fun SectionHeaderPreview() = PreviewSurface { SectionHeader("Lectures") }

@QuirePreviews
@Composable
fun EmptyStatePreview() =
    PreviewSurface {
        Column(Modifier.fillMaxWidth().height(320.dp)) {
            EmptyState("No courses yet", message = "Pair with a server to see your courses.", action = {
                Button(onClick = {}) { Text("Pair") }
            })
        }
    }

@QuirePreviews
@Composable
fun ErrorStatePreview() =
    PreviewSurface {
        Column(Modifier.fillMaxWidth().height(320.dp)) { ErrorState("Could not load files.", onRetry = {}) }
    }

@QuirePreviews
@Composable
fun LoadingSkeletonPreview() = PreviewSurface { LoadingSkeleton() }
