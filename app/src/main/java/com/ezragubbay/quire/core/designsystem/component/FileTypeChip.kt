package com.ezragubbay.quire.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.designsystem.theme.FileType
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme

/** Label string for [type]. */
@StringRes
fun fileTypeLabelRes(type: FileType): Int =
    when (type) {
        FileType.LECTURE -> R.string.file_type_lecture
        FileType.RECITATION -> R.string.file_type_recitation
        FileType.HOMEWORK -> R.string.file_type_homework
        FileType.EXAM -> R.string.file_type_exam
        FileType.BOOK -> R.string.file_type_book
        FileType.PAPER -> R.string.file_type_paper
        FileType.OTHER -> R.string.file_type_other
    }

/** Small tonal label for a file's [FileType], coloured from `QuireTheme.fileAccent`. Not interactive. */
@Composable
fun FileTypeChip(
    type: FileType,
    modifier: Modifier = Modifier,
) {
    val accent = QuireTheme.fileAccent(type)
    Surface(
        modifier = modifier,
        color = accent.container,
        contentColor = accent.onContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = stringResource(fileTypeLabelRes(type)),
            style = MaterialTheme.typography.labelMedium,
            modifier =
                Modifier
                    .testTag("file-type-chip")
                    .padding(horizontal = QuireTheme.spacing.sm, vertical = QuireTheme.spacing.xs),
        )
    }
}
