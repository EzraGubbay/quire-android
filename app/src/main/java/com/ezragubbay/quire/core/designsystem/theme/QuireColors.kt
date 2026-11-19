package com.ezragubbay.quire.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colours for one file type. [color] is for icons/glyphs/strokes (>= 3:1 on `surface`); [container] with
 * [onContainer] is for chips and badges (>= 4.5:1).
 */
@Immutable
data class FileTypeAccent(
    val color: Color,
    val container: Color,
    val onContainer: Color,
)

/** A complete palette for one brightness: the Material 3 [scheme] plus the per-[FileType] [fileAccents]. */
@Immutable
data class QuireColors(
    val scheme: ColorScheme,
    val fileAccents: Map<FileType, FileTypeAccent>,
) {
    /** Accent for [type]; every [FileType] is guaranteed present. */
    fun accent(type: FileType): FileTypeAccent = fileAccents.getValue(type)

    companion object {
        // Warm "paper" neutrals with an ink-blue brand hue; Material You (dynamic colour) is an opt-in override.
        val Light =
            QuireColors(
                scheme =
                    lightColorScheme(
                        primary = Color(0xFF2F4B9B),
                        onPrimary = Color(0xFFFFFFFF),
                        primaryContainer = Color(0xFFDCE3FF),
                        onPrimaryContainer = Color(0xFF0B1F57),
                        inversePrimary = Color(0xFFB4C3FF),
                        secondary = Color(0xFF565B70),
                        onSecondary = Color(0xFFFFFFFF),
                        secondaryContainer = Color(0xFFDEE1F9),
                        onSecondaryContainer = Color(0xFF171B2C),
                        tertiary = Color(0xFF8A4B00),
                        onTertiary = Color(0xFFFFFFFF),
                        tertiaryContainer = Color(0xFFFFDDB8),
                        onTertiaryContainer = Color(0xFF2C1600),
                        background = Color(0xFFFBF8F3),
                        onBackground = Color(0xFF1C1B1A),
                        surface = Color(0xFFFBF8F3),
                        onSurface = Color(0xFF1C1B1A),
                        surfaceVariant = Color(0xFFE7E1D9),
                        onSurfaceVariant = Color(0xFF4A4741),
                        surfaceTint = Color(0xFF2F4B9B),
                        inverseSurface = Color(0xFF31302D),
                        inverseOnSurface = Color(0xFFF4F0EA),
                        error = Color(0xFFBA1A1A),
                        onError = Color(0xFFFFFFFF),
                        errorContainer = Color(0xFFFFDAD6),
                        onErrorContainer = Color(0xFF410002),
                        outline = Color(0xFF7A766E),
                        outlineVariant = Color(0xFFCCC6BC),
                        scrim = Color(0xFF000000),
                        surfaceBright = Color(0xFFFBF8F3),
                        surfaceDim = Color(0xFFDCD9D3),
                        surfaceContainerLowest = Color(0xFFFFFFFF),
                        surfaceContainerLow = Color(0xFFF6F2EC),
                        surfaceContainer = Color(0xFFF0ECE6),
                        surfaceContainerHigh = Color(0xFFEAE6E0),
                        surfaceContainerHighest = Color(0xFFE4E0DA),
                    ),
                fileAccents =
                    mapOf(
                        FileType.LECTURE to FileTypeAccent(Color(0xFF3B4FB8), Color(0xFFDFE3FF), Color(0xFF0E1A5C)),
                        FileType.RECITATION to FileTypeAccent(Color(0xFF00796B), Color(0xFFC2F0E8), Color(0xFF00201C)),
                        FileType.HOMEWORK to FileTypeAccent(Color(0xFF9A5B00), Color(0xFFFFE0B5), Color(0xFF301800)),
                        FileType.EXAM to FileTypeAccent(Color(0xFFB3261E), Color(0xFFFFDAD5), Color(0xFF410002)),
                        FileType.BOOK to FileTypeAccent(Color(0xFF3B6E2A), Color(0xFFCDEEB9), Color(0xFF0B2000)),
                        FileType.PAPER to FileTypeAccent(Color(0xFF7A3FA0), Color(0xFFF2DAFF), Color(0xFF2E0049)),
                        FileType.OTHER to FileTypeAccent(Color(0xFF5F6368), Color(0xFFE1E3E8), Color(0xFF1B1D21)),
                    ),
            )

        val Dark =
            QuireColors(
                scheme =
                    darkColorScheme(
                        primary = Color(0xFFB4C3FF),
                        onPrimary = Color(0xFF12296F),
                        primaryContainer = Color(0xFF2F4B9B),
                        onPrimaryContainer = Color(0xFFDCE3FF),
                        inversePrimary = Color(0xFF2F4B9B),
                        secondary = Color(0xFFC2C5DD),
                        onSecondary = Color(0xFF2B3042),
                        secondaryContainer = Color(0xFF414659),
                        onSecondaryContainer = Color(0xFFDEE1F9),
                        tertiary = Color(0xFFFFB870),
                        onTertiary = Color(0xFF4A2800),
                        tertiaryContainer = Color(0xFF6A3C00),
                        onTertiaryContainer = Color(0xFFFFDDB8),
                        background = Color(0xFF141311),
                        onBackground = Color(0xFFE6E2DC),
                        surface = Color(0xFF141311),
                        onSurface = Color(0xFFE6E2DC),
                        surfaceVariant = Color(0xFF4A4741),
                        onSurfaceVariant = Color(0xFFCDC7BE),
                        surfaceTint = Color(0xFFB4C3FF),
                        inverseSurface = Color(0xFFE6E2DC),
                        inverseOnSurface = Color(0xFF31302D),
                        error = Color(0xFFFFB4AB),
                        onError = Color(0xFF690005),
                        errorContainer = Color(0xFF93000A),
                        onErrorContainer = Color(0xFFFFDAD6),
                        outline = Color(0xFF979188),
                        outlineVariant = Color(0xFF4A4741),
                        scrim = Color(0xFF000000),
                        surfaceBright = Color(0xFF3B3936),
                        surfaceDim = Color(0xFF141311),
                        surfaceContainerLowest = Color(0xFF0F0E0C),
                        surfaceContainerLow = Color(0xFF1C1B19),
                        surfaceContainer = Color(0xFF201F1D),
                        surfaceContainerHigh = Color(0xFF2B2A27),
                        surfaceContainerHighest = Color(0xFF363431),
                    ),
                fileAccents =
                    mapOf(
                        FileType.LECTURE to FileTypeAccent(Color(0xFFAEBBFF), Color(0xFF2A3470), Color(0xFFDFE3FF)),
                        FileType.RECITATION to FileTypeAccent(Color(0xFF62D6C4), Color(0xFF00504A), Color(0xFFC2F0E8)),
                        FileType.HOMEWORK to FileTypeAccent(Color(0xFFFFC166), Color(0xFF5A3A00), Color(0xFFFFE0B5)),
                        FileType.EXAM to FileTypeAccent(Color(0xFFFFB4A9), Color(0xFF8C1D18), Color(0xFFFFDAD5)),
                        FileType.BOOK to FileTypeAccent(Color(0xFFA5D68F), Color(0xFF24501A), Color(0xFFCDEEB9)),
                        FileType.PAPER to FileTypeAccent(Color(0xFFE0B6FF), Color(0xFF5B2C7A), Color(0xFFF2DAFF)),
                        FileType.OTHER to FileTypeAccent(Color(0xFFC4C7CC), Color(0xFF44474C), Color(0xFFE1E3E8)),
                    ),
            )
    }
}
