package com.ezragubbay.quire.feature.chat.conversation

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Spannable
import android.text.style.ClickableSpan
import android.text.style.ReplacementSpan
import android.text.style.TtsSpan
import android.view.View
import androidx.compose.runtime.Immutable
import com.ezragubbay.quire.core.network.CitationDto
import io.noties.markwon.core.spans.CodeBlockSpan
import io.noties.markwon.core.spans.CodeSpan
import io.noties.markwon.image.AsyncDrawableSpan

/** ARGB colours of an inline citation chip; part of the decoration key so a theme switch re-decorates. */
@Immutable
data class CitationChipStyle(
    val container: Int,
    val content: Int,
    val outline: Int,
    val unavailableContent: Int = outline,
)

private const val TEXT_SCALE = 0.8f
private const val PAD_X_EM = 0.35f
private const val STROKE_DP_PX = 2f

/**
 * Atomic pill drawn in place of a `[n]` marker. The underlying text stays `[n]` (copy/selection unchanged); line
 * height is untouched because [getSize] leaves the font metrics alone. [testTag] is `citation-chip-{n}` on the first
 * occurrence of each `n` in a message (the journey finds it through the Espresso helper), else null.
 */
class CitationChipSpan(
    val n: Int,
    val citation: CitationDto,
    val style: CitationChipStyle,
    val testTag: String?,
) : ReplacementSpan() {
    private fun scaled(paint: Paint): Paint = Paint(paint).apply { textSize = paint.textSize * TEXT_SCALE }

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val small = scaled(paint)
        return (small.measureText(n.toString()) + 2 * PAD_X_EM * paint.textSize).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val small = scaled(paint)
        val label = n.toString()
        val width = getSize(paint, text, start, end, null).toFloat()
        val rect = RectF(x, top.toFloat() + 1f, x + width, bottom.toFloat() - 1f)
        val radius = rect.height() / 2f
        val openable = citation.isOpenable
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        if (openable) {
            fill.color = style.container
            canvas.drawRoundRect(rect, radius, radius, fill)
        } else {
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = STROKE_DP_PX
            fill.color = style.outline
            val inset = RectF(rect).apply { inset(STROKE_DP_PX / 2f, STROKE_DP_PX / 2f) }
            canvas.drawRoundRect(inset, radius, radius, fill)
        }
        small.color = if (openable) style.content else style.unavailableContent
        small.textAlign = Paint.Align.CENTER
        val baseline = rect.centerY() - (small.fontMetrics.ascent + small.fontMetrics.descent) / 2f
        canvas.drawText(label, rect.centerX(), baseline, small)
    }
}

/** Opens an openable citation when its chip is tapped; no underline. */
class CitationClickSpan(
    val citation: CitationDto,
    private val onOpen: (CitationDto) -> Unit,
) : ClickableSpan() {
    override fun onClick(widget: View) = onOpen(citation)

    override fun updateDrawState(ds: android.text.TextPaint) {
        ds.isUnderlineText = false
    }
}

private val excluded =
    listOf(CodeSpan::class.java, CodeBlockSpan::class.java, AsyncDrawableSpan::class.java, ClickableSpan::class.java)

/**
 * Decorates every `[n]` with a known citation in a Markwon-rendered text as a chip. Markers inside code, inline
 * LaTeX or links are skipped by span overlap (the rendered text no longer holds backticks). Mutates and returns the
 * receiver; returns it unchanged when [byN] is empty.
 */
fun <T : Spannable> T.applyCitationChips(
    byN: Map<Int, CitationDto>,
    style: CitationChipStyle,
    a11yLabel: (CitationDto) -> String,
    onOpen: (CitationDto) -> Unit,
): T {
    if (byN.isEmpty()) return this
    val tagged = mutableSetOf<Int>()
    for (m in CitationParser.markerMatches(this)) {
        val citation = byN[m.n]
        val start = m.range.first
        val end = m.range.last + 1
        if (citation != null && excluded.none { getSpans(start, end, it).isNotEmpty() }) {
            val tag = if (tagged.add(m.n)) "citation-chip-${m.n}" else null
            setSpan(CitationChipSpan(m.n, citation, style, tag), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(TtsSpan.TextBuilder(a11yLabel(citation)).build(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (citation.isOpenable) {
                setSpan(CitationClickSpan(citation, onOpen), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
    return this
}
