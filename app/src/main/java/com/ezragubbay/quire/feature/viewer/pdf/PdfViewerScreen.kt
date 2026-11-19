package com.ezragubbay.quire.feature.viewer.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezragubbay.quire.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PREFETCH_MARGIN = 1
private const val INDICATOR_ALPHA = 0.92f

/** The PDF viewer: lazily rendered pages, pinch / double-tap zoom, a page indicator with jump, and resume. */
@Composable
fun PdfViewerScreen(
    onBack: () -> Unit,
    viewModel: PdfViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val zoom by viewModel.zoom.collectAsStateWithLifecycle()
    val immersive by viewModel.immersive.collectAsStateWithLifecycle()
    PdfViewerContent(
        state = state,
        zoom = zoom,
        immersive = immersive,
        onBack = onBack,
        onRetry = viewModel::retry,
        onPageChanged = viewModel::onPageChanged,
        onZoom = viewModel::updateZoom,
        onToggleImmersive = viewModel::toggleImmersive,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfViewerContent(
    state: ViewerUiState,
    zoom: ZoomState,
    immersive: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onZoom: ((ZoomState) -> ZoomState) -> Unit,
    onToggleImmersive: () -> Unit,
) {
    val ready = state as? ViewerUiState.Ready
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ready?.startPage ?: 0)
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    var jumpOpen by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            if (!immersive) {
                TopAppBar(
                    title = { Text(stringResource(R.string.viewer_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("viewer-back")) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.viewer_back),
                            )
                        }
                    },
                    actions = {
                        if (ready != null) {
                            IconButton(
                                onClick = { jumpOpen = true },
                                modifier = Modifier.testTag("action-jump-to-page"),
                            ) {
                                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.viewer_jump))
                            }
                        }
                    },
                    modifier = Modifier.testTag("viewer-top-bar"),
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                ViewerUiState.Loading ->
                    Box(Modifier.fillMaxSize().testTag("screen-pdf-viewer"), contentAlignment = Alignment.Center) {
                        val label = stringResource(R.string.viewer_loading)
                        CircularProgressIndicator(Modifier.semantics { contentDescription = label })
                    }
                ViewerUiState.Error ->
                    Column(
                        Modifier.fillMaxSize().testTag("screen-pdf-viewer"),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.viewer_error))
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.viewer_retry)) }
                    }
                is ViewerUiState.Ready -> {
                    PageList(state.document, listState, zoom, onZoom, onToggleImmersive, onPageChanged)
                    if (!immersive) {
                        PageIndicator(
                            current = currentPage + 1,
                            total = state.document.pageCount,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        )
                    }
                    if (jumpOpen) {
                        JumpDialog(
                            pageCount = state.document.pageCount,
                            listState = listState,
                            onDismiss = { jumpOpen = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageList(
    document: PdfDocument,
    listState: LazyListState,
    zoom: ZoomState,
    onZoom: ((ZoomState) -> ZoomState) -> Unit,
    onToggleImmersive: () -> Unit,
    onPageChanged: (Int) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportPx = with(LocalDensity.current) { maxWidth.toPx() }
        val currentZoom by rememberUpdatedState(zoom)
        val viewportWidth by rememberUpdatedState(viewportPx)
        val renderWidth = (viewportPx * zoom.renderBucket).roundToInt()

        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex }.collect(onPageChanged)
        }
        // Keep the pages around the viewport rendered (cached) so scrolling does not flash blank pages.
        LaunchedEffect(listState, renderWidth) {
            snapshotFlow {
                val visible = listState.layoutInfo.visibleItemsInfo
                (visible.firstOrNull()?.index ?: 0) to (visible.lastOrNull()?.index ?: 0)
            }.collect { (first, last) ->
                RenderWindow
                    .keep(
                        first,
                        last,
                        document.pageCount,
                        PREFETCH_MARGIN,
                    ).forEach { document.render(it, renderWidth) }
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag("screen-pdf-viewer")
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onToggleImmersive() },
                            onDoubleTap = { onZoom { it.doubleTap(viewportWidth) } },
                        )
                    }.pointerInput(Unit) {
                        // Two-finger pinch + pan; single-finger drags are left alone so the list still scrolls.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.count { it.pressed } > 1) {
                                    val factor = event.calculateZoom()
                                    val dx = event.calculatePan().x
                                    onZoom { it.pinch(factor, viewportWidth).pan(dx, viewportWidth) }
                                    event.changes.forEach { it.consume() }
                                } else if (currentZoom.isZoomed) {
                                    onZoom { it.pan(event.calculatePan().x, viewportWidth) }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
        ) {
            items((0 until document.pageCount).toList(), key = { it }) { index ->
                PageItem(document, index, zoom, renderWidth, maxWidth)
            }
        }
    }
}

@Composable
private fun PageItem(
    document: PdfDocument,
    index: Int,
    zoom: ZoomState,
    renderWidth: Int,
    viewportWidth: Dp,
) {
    var bitmap by remember(document, index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(document, index, renderWidth) {
        document.render(index, renderWidth)?.let { bitmap = it }
    }
    val aspect = bitmap?.let { it.height.toFloat() / it.width } ?: document.firstAspect
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .requiredWidth(viewportWidth * zoom.scale)
                .offset { IntOffset(zoom.panX.roundToInt(), 0) }
                .aspectRatio(1f / aspect)
                .background(Color.White)
                .testTag("pdf-page-${index + 1}"),
        ) {
            bitmap?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun PageIndicator(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag("page-indicator"),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = INDICATOR_ALPHA),
    ) {
        Text(
            "$current / $total",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun JumpDialog(
    pageCount: Int,
    listState: LazyListState,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val target = text.toIntOrNull()?.takeIf { it in 1..pageCount }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.viewer_jump)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.viewer_jump_hint)) },
                suffix = { Text("/ $pageCount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.testTag("jump-page-input"),
            )
        },
        confirmButton = {
            TextButton(
                enabled = target != null,
                onClick = {
                    target?.let { scope.launch { listState.scrollToItem(it - 1) } }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.viewer_jump_confirm)) }
        },
    )
}
