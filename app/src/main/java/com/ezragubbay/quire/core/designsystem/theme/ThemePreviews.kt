package com.ezragubbay.quire.core.designsystem.theme

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
private fun Swatch(
    name: String,
    color: Color,
    onColor: Color,
) {
    Box(
        modifier = Modifier.size(width = 104.dp, height = 48.dp).background(color),
        contentAlignment = Alignment.Center,
    ) { Text(name, color = onColor, style = MaterialTheme.typography.labelSmall) }
}

@Composable
private fun PaletteSpecimen() {
    val c = MaterialTheme.colorScheme
    Surface(color = c.background) {
        Column(
            Modifier.padding(QuireTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(QuireTheme.spacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(QuireTheme.spacing.sm)) {
                Swatch("primary", c.primary, c.onPrimary)
                Swatch("primaryCont.", c.primaryContainer, c.onPrimaryContainer)
                Swatch("secondary", c.secondary, c.onSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(QuireTheme.spacing.sm)) {
                Swatch("tertiary", c.tertiary, c.onTertiary)
                Swatch("error", c.error, c.onError)
                Swatch("surfaceVar.", c.surfaceVariant, c.onSurfaceVariant)
            }
            FileType.entries.forEach { type ->
                val accent = QuireTheme.fileAccent(type)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(QuireTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(16.dp).background(accent.color))
                    Box(Modifier.background(accent.container).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(
                            type.name.lowercase(),
                            color = accent.onContainer,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeSpecimen() {
    val t = MaterialTheme.typography
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.padding(QuireTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(QuireTheme.spacing.xs),
        ) {
            Text("Display Small", style = t.displaySmall)
            Text("Headline Medium", style = t.headlineMedium)
            Text("Headline Small", style = t.headlineSmall)
            Text("Title Large", style = t.titleLarge)
            Text("Title Medium", style = t.titleMedium)
            Text("Body Large: the quick brown fox jumps over the lazy dog.", style = t.bodyLarge)
            Text("Body Medium: the quick brown fox jumps over the lazy dog.", style = t.bodyMedium)
            Text("Body Small: the quick brown fox jumps over the lazy dog.", style = t.bodySmall)
            Text("שם קורס בעברית: אלגברה לינארית", style = t.bodyLarge)
            Text("Label Large", style = t.labelLarge)
        }
    }
}

@Preview(name = "Palette light", showBackground = true)
@Composable
private fun PaletteLightPreview() = QuireTheme(ThemeMode.Light) { PaletteSpecimen() }

@Preview(name = "Palette dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PaletteDarkPreview() = QuireTheme(ThemeMode.Dark) { PaletteSpecimen() }

@Preview(name = "Type light", showBackground = true)
@Composable
private fun TypeLightPreview() = QuireTheme(ThemeMode.Light) { TypeSpecimen() }

@Preview(name = "Type dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TypeDarkPreview() = QuireTheme(ThemeMode.Dark) { TypeSpecimen() }
