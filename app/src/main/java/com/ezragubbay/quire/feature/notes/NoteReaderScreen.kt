package com.ezragubbay.quire.feature.notes

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezragubbay.quire.R
import com.ezragubbay.quire.core.designsystem.component.ErrorState
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin

const val NOTE_READER_TAG = "screen-note-reader"
const val NOTE_MARKDOWN_TAG = "note-markdown"
const val NOTE_MATH_TAG = "note-math"

/** Read-only note reader: the note's Markdown rendered block by block (ADR 0002). */
@Composable
fun NoteReaderScreen(
    onBack: () -> Unit,
    viewModel: NoteReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NoteReaderContent(state, onBack, viewModel::retry)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteReaderContent(
    state: NoteReaderUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.testTag(NOTE_READER_TAG),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        (state as? NoteReaderUiState.Ready)?.title ?: stringResource(R.string.note_reader_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("note-reader-back")) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.note_reader_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                NoteReaderUiState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                NoteReaderUiState.Error ->
                    ErrorState(message = stringResource(R.string.note_reader_error), onRetry = onRetry)
                is NoteReaderUiState.Ready -> NoteBody(state.blocks)
            }
        }
    }
}

/** The root content column; present only once content is on screen. Layout direction is left to platform BiDi. */
@Composable
private fun NoteBody(blocks: List<NoteBlock>) {
    val markwon = rememberMarkwon()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(NOTE_MARKDOWN_TAG),
    ) {
        blocks.forEach { block ->
            when (block) {
                is NoteBlock.Text -> BlockView(markwon, block.markdown, Modifier.fillMaxWidth())
                is NoteBlock.Math ->
                    BlockView(
                        markwon = markwon,
                        markdown = "$$\n${block.latex}\n$$",
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag(NOTE_MATH_TAG)
                                .semantics { contentDescription = block.latex },
                        description = block.latex,
                    )
            }
        }
    }
}

@Composable
internal fun BlockView(
    markwon: Markwon,
    markdown: String,
    modifier: Modifier,
    description: String? = null,
) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val body = MaterialTheme.typography.bodyLarge
    val textSizeSp = body.fontSize.value
    val lineHeightPx = with(LocalDensity.current) { body.lineHeight.toPx() }
    val typeface = rememberReadingTypeface()
    AndroidView(
        modifier = modifier,
        factory = { ctx -> TextView(ctx) },
        update = { tv ->
            tv.setTextColor(color)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            tv.setLineSpacing(0f, 1f)
            tv.minHeight = 0
            if (lineHeightPx > 0f) tv.setLineHeight(lineHeightPx.toInt())
            typeface?.let { tv.typeface = it }
            if (description != null) tv.contentDescription = description
            markwon.setMarkdown(tv, markdown)
        },
    )
}

/** Markwon with JLaTeXMath (block + inline) coloured by the theme's `onSurface`; rebuilt on theme or density change. */
@Composable
internal fun rememberMarkwon(): Markwon {
    val context = LocalContext.current
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val textPx =
        with(LocalDensity.current) {
            MaterialTheme.typography.bodyLarge.fontSize
                .toPx()
        }
    return remember(context, color, textPx) { buildMarkwon(context, color, textPx) }
}

private fun buildMarkwon(
    context: Context,
    color: Int,
    textPx: Float,
): Markwon =
    Markwon
        .builder(context)
        .usePlugin(MarkwonInlineParserPlugin.create())
        .usePlugin(
            JLatexMathPlugin.create(textPx) { builder ->
                builder.inlinesEnabled(true)
                builder.theme().textColor(color)
            },
        ).build()

@Composable
private fun rememberReadingTypeface(): Typeface? {
    val context = LocalContext.current
    return remember(context) { ResourcesCompat.getFont(context, R.font.literata) }
}
