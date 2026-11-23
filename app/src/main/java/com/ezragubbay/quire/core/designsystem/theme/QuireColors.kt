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
        // The web app's Folio palette (`@ezragubbay/folio` tokens.css): parchment neutrals with an oxblood accent
        // (`--eg-accent`, the primary-button fill). Material You (dynamic colour) is an opt-in override.
        val Light =
            QuireColors(
                scheme =
                    lightColorScheme(
                        primary = Color(0xFF6E1E2B),
                        onPrimary = Color(0xFFF6F1E7),
                        primaryContainer = Color(0xFFF3E3E4),
                        onPrimaryContainer = Color(0xFF6E1E2B),
                        inversePrimary = Color(0xFFE28A93),
                        secondary = Color(0xFF4B443B),
                        onSecondary = Color(0xFFF6F1E7),
                        secondaryContainer = Color(0xFFE6CCC6),
                        onSecondaryContainer = Color(0xFF6E1E2B),
                        tertiary = Color(0xFF8A4B00),
                        onTertiary = Color(0xFFFFFFFF),
                        tertiaryContainer = Color(0xFFFFDDB8),
                        onTertiaryContainer = Color(0xFF2C1600),
                        background = Color(0xFFF6F1E7),
                        onBackground = Color(0xFF1F1B16),
                        surface = Color(0xFFF6F1E7),
                        onSurface = Color(0xFF1F1B16),
                        surfaceVariant = Color(0xFFEAE2D3),
                        onSurfaceVariant = Color(0xFF4B443B),
                        surfaceTint = Color(0xFF6E1E2B),
                        inverseSurface = Color(0xFF2E2923),
                        inverseOnSurface = Color(0xFFF6F1E7),
                        error = Color(0xFFBA1A1A),
                        onError = Color(0xFFFFFFFF),
                        errorContainer = Color(0xFFFFDAD6),
                        onErrorContainer = Color(0xFF410002),
                        outline = Color(0xFF6E665A),
                        outlineVariant = Color(0xFFDDD4C4),
                        scrim = Color(0xFF000000),
                        surfaceBright = Color(0xFFFBF8F1),
                        surfaceDim = Color(0xFFDDD4C4),
                        surfaceContainerLowest = Color(0xFFFBF8F1),
                        surfaceContainerLow = Color(0xFFF3EDE2),
                        surfaceContainer = Color(0xFFEFE8DA),
                        surfaceContainerHigh = Color(0xFFEAE2D3),
                        surfaceContainerHighest = Color(0xFFE4DCCC),
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
                        primary = Color(0xFFE28A93),
                        onPrimary = Color(0xFF1C1915),
                        primaryContainer = Color(0xFF452028),
                        onPrimaryContainer = Color(0xFFEFA3AB),
                        inversePrimary = Color(0xFF6E1E2B),
                        secondary = Color(0xFFC9C0B2),
                        onSecondary = Color(0xFF1C1915),
                        secondaryContainer = Color(0xFF4D2A2F),
                        onSecondaryContainer = Color(0xFFEFA3AB),
                        tertiary = Color(0xFFFFB870),
                        onTertiary = Color(0xFF4A2800),
                        tertiaryContainer = Color(0xFF6A3C00),
                        onTertiaryContainer = Color(0xFFFFDDB8),
                        background = Color(0xFF1C1915),
                        onBackground = Color(0xFFEEE7DA),
                        surface = Color(0xFF1C1915),
                        onSurface = Color(0xFFEEE7DA),
                        surfaceVariant = Color(0xFF3A342B),
                        onSurfaceVariant = Color(0xFFC9C0B2),
                        surfaceTint = Color(0xFFE28A93),
                        inverseSurface = Color(0xFFEEE7DA),
                        inverseOnSurface = Color(0xFF2E2923),
                        error = Color(0xFFFFB4AB),
                        onError = Color(0xFF690005),
                        errorContainer = Color(0xFF93000A),
                        onErrorContainer = Color(0xFFFFDAD6),
                        outline = Color(0xFF948A7C),
                        outlineVariant = Color(0xFF3A342B),
                        scrim = Color(0xFF000000),
                        surfaceBright = Color(0xFF3A342B),
                        surfaceDim = Color(0xFF1C1915),
                        surfaceContainerLowest = Color(0xFF17140F),
                        surfaceContainerLow = Color(0xFF201C18),
                        surfaceContainer = Color(0xFF221E19),
                        surfaceContainerHigh = Color(0xFF2E2923),
                        surfaceContainerHighest = Color(0xFF3A342B),
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
