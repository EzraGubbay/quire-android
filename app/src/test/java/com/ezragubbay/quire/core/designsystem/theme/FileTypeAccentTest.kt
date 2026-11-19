package com.ezragubbay.quire.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTypeAccentTest {
    private val palettes = mapOf("light" to QuireColors.Light, "dark" to QuireColors.Dark)

    @Test
    fun fileTypesAreTheSevenLabels() {
        assertEquals(
            listOf("LECTURE", "RECITATION", "HOMEWORK", "EXAM", "BOOK", "PAPER", "OTHER"),
            FileType.entries.map { it.name },
        )
    }

    @Test
    fun everyFileTypeHasAnAccentInBothSchemes() {
        for ((name, colors) in palettes) {
            for (type in FileType.entries) {
                val accent = colors.fileAccents.getValue(type)
                assertTrue("$name $type must be opaque", accent.color.alpha == 1f && accent.container.alpha == 1f)
            }
            assertEquals(FileType.entries.toSet(), colors.fileAccents.keys)
        }
    }

    @Test
    fun accentsAreLegibleOnSurfaceAndContainer() {
        for ((name, colors) in palettes) {
            for (type in FileType.entries) {
                val a = colors.fileAccents.getValue(type)
                val icon = contrastRatio(a.color, colors.scheme.surface)
                val text = contrastRatio(a.onContainer, a.container)
                assertTrue("$name $type accent/surface = $icon, needs >= 3", icon >= 3.0)
                assertTrue("$name $type onContainer/container = $text, needs >= 4.5", text >= 4.5)
            }
        }
    }

    @Test
    fun accentsAreDistinctWithinAScheme() {
        for ((name, colors) in palettes) {
            val distinct = FileType.entries.map { colors.fileAccents.getValue(it).color }.toSet()
            assertEquals("$name accents must be distinct", FileType.entries.size, distinct.size)
        }
    }

    @Test
    fun lightAndDarkAccentsDiffer() {
        for (type in FileType.entries) {
            assertNotEquals(
                QuireColors.Light.fileAccents
                    .getValue(type)
                    .color,
                QuireColors.Dark.fileAccents
                    .getValue(type)
                    .color,
            )
        }
    }
}
