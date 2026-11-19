package com.ezragubbay.quire.core.designsystem.component

import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.designsystem.theme.FileType
import com.ezragubbay.quire.core.designsystem.theme.QuireTheme
import com.ezragubbay.quire.core.designsystem.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val HEBREW_TITLE = "אלגברה לינארית למדעי המחשב"
private const val LONG_TITLE = "Introduction to Algorithms and Data Structures for Computational Biology, Part Two"

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
class ComponentKitTest {
    @get:Rule val rule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun themed(content: @androidx.compose.runtime.Composable () -> Unit) =
        rule.setContent { QuireTheme(ThemeMode.Light, content = content) }

    private fun hasClick() = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)

    @Test
    fun fileRow_click_invokesCallback_andShowsTitleMetaChipAndTrailing() {
        var clicks = 0
        themed {
            FileRow(
                title = "Lecture 3",
                meta = "PDF - 2.1 MB",
                type = FileType.LECTURE,
                onClick = { clicks++ },
                trailing = { Text("dl", Modifier.testTag("trailing")) },
            )
        }
        rule.onNodeWithTag("file-row").assert(hasClick()).performClick()
        assertEquals(1, clicks)
        rule.onNodeWithTag("file-row-title", useUnmergedTree = true).assertTextEquals("Lecture 3")
        rule.onNodeWithTag("file-row-meta", useUnmergedTree = true).assertTextEquals("PDF - 2.1 MB")
        rule.onNodeWithTag("file-type-chip", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("trailing", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun fileTypeChip_labelPerType() {
        val expected =
            mapOf(
                FileType.LECTURE to R.string.file_type_lecture,
                FileType.RECITATION to R.string.file_type_recitation,
                FileType.HOMEWORK to R.string.file_type_homework,
                FileType.EXAM to R.string.file_type_exam,
                FileType.BOOK to R.string.file_type_book,
                FileType.PAPER to R.string.file_type_paper,
                FileType.OTHER to R.string.file_type_other,
            )
        assertEquals(FileType.entries.toSet(), expected.keys)
        var current by mutableStateOf(FileType.LECTURE)
        rule.setContent { QuireTheme(ThemeMode.Light) { FileTypeChip(current) } }
        expected.forEach { (type, res) ->
            current = type
            rule.waitForIdle()
            rule.onNodeWithTag("file-type-chip", useUnmergedTree = true).assertTextEquals(context.getString(res))
        }
    }

    @Test
    fun fileTypeChip_labelsAreDistinct() {
        val labels = FileType.entries.map { fileTypeLabelRes(it) }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun errorState_retry_invokesCallback_andShowsMessage() {
        var retries = 0
        themed { ErrorState(message = "Could not load files", onRetry = { retries++ }) }
        rule.onNodeWithTag("error-state-message").assertTextEquals("Could not load files")
        rule
            .onNodeWithTag("error-state-retry")
            .assert(hasClick())
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, retries)
    }

    @Test
    fun emptyState_showsTitleAndMessage_andOptionalAction() {
        var acted = false
        themed {
            EmptyState(
                title = "No courses yet",
                message = "Pair a server to see them",
                action = { Text("Pair", Modifier.testTag("empty-action").also { acted = true }) },
            )
        }
        rule.onNodeWithTag("empty-state-title").assertTextEquals("No courses yet")
        rule.onNodeWithTag("empty-state-message").assertTextEquals("Pair a server to see them")
        rule.onNodeWithTag("empty-action").assertIsDisplayed()
        assertTrue(acted)
    }

    @Test
    fun loadingSkeleton_isAnnouncedAsLoading() {
        themed { LoadingSkeleton() }
        rule
            .onNodeWithTag("loading-skeleton")
            .assert(
                SemanticsMatcher("contentDescription is loading") {
                    it.config
                        .getOrNull(SemanticsProperties.ContentDescription)
                        ?.contains(context.getString(R.string.state_loading)) == true
                },
            )
    }

    @Test
    fun sectionHeader_isAHeading() {
        themed { SectionHeader("Lectures") }
        rule
            .onNodeWithTag("section-header")
            .assertTextEquals("Lectures")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun courseCard_click_invokesCallback_meetsTouchTarget() {
        var clicks = 0
        themed { CourseCard(title = "Algorithms", subtitle = "12 files", onClick = { clicks++ }) }
        rule
            .onNodeWithTag("course-card")
            .assert(hasClick())
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, clicks)
        rule.onNodeWithTag("course-card-title", useUnmergedTree = true).assertTextEquals("Algorithms")
        rule.onNodeWithTag("course-card-subtitle", useUnmergedTree = true).assertTextEquals("12 files")
    }

    @Test
    fun topBar_backButton_invokesCallback_andShowsTitle() {
        var backs = 0
        themed { QuireTopBar(title = "Courses", onBack = { backs++ }) }
        rule.onNodeWithTag("top-bar-back").assert(hasClick()).performClick()
        assertEquals(1, backs)
        rule.onNodeWithTag("quire-top-bar").assertIsDisplayed()
    }

    @Test
    fun topBar_withoutOnBack_hasNoBackButton() {
        themed { QuireTopBar(title = "Courses") }
        rule.onNodeWithTag("top-bar-back").assertDoesNotExistCompat()
    }

    @Test
    fun hebrewTitles_renderInRtl_andAreNotClipped() {
        themed {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                androidx.compose.foundation.layout.Column {
                    CourseCard(title = HEBREW_TITLE, subtitle = "12 קבצים", onClick = {})
                    FileRow(HEBREW_TITLE, "PDF", FileType.EXAM, onClick = {})
                }
            }
        }
        rule
            .onNodeWithTag(
                "course-card-title",
                useUnmergedTree = true,
            ).assertTextEquals(HEBREW_TITLE)
            .assertNotOverflowing()
        rule
            .onNodeWithTag(
                "file-row-title",
                useUnmergedTree = true,
            ).assertTextEquals(HEBREW_TITLE)
            .assertNotOverflowing()
    }

    @Test
    fun largeFontScale_doesNotTruncatePrimaryText() {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.3f)) {
                QuireTheme(ThemeMode.Light) {
                    androidx.compose.foundation.layout.Column {
                        CourseCard(title = LONG_TITLE, subtitle = "12 files", onClick = {})
                        FileRow(LONG_TITLE, "PDF", FileType.BOOK, onClick = {})
                    }
                }
            }
        }
        rule.onNodeWithTag("course-card-title", useUnmergedTree = true).assertNotOverflowing()
        rule.onNodeWithTag("file-row-title", useUnmergedTree = true).assertNotOverflowing()
        rule.onNodeWithTag("file-row").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun fileRow_meetsMinimumTouchTarget() {
        themed { FileRow("A", "B", FileType.OTHER, onClick = {}) }
        rule.onNodeWithTag("file-row").assertHeightIsAtLeast(48.dp)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() = assertDoesNotExist()

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertNotOverflowing() =
        apply {
            val results = mutableListOf<TextLayoutResult>()
            fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            assertTrue(results.isNotEmpty())
            results.forEach {
                assertFalse(it.didOverflowHeight)
                assertFalse((0 until it.lineCount).any { line -> it.isLineEllipsized(line) })
            }
        }
}
