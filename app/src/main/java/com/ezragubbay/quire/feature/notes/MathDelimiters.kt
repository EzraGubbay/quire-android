package com.ezragubbay.quire.feature.notes

/**
 * Markdown from the server (notes, AI answers) writes math the way the Quire web app reads it: `$…$` inline, `$$…$$`
 * display, and, from the model, sometimes LaTeX-style `\(…\)` / `\[…\]`. Markwon's JLaTeXMath plugin understands only
 * `$$…$$` (inline when it sits inside a paragraph line, block when the fences stand on their own lines), so `$…$` and
 * the backslash forms would otherwise show as raw source. Display math is normalised before the note is segmented into
 * blocks; inline math is rewritten per prose block afterwards. Pure; no Android dependencies.
 */
fun mathMarkdownBlocks(markdown: String): List<NoteBlock> =
    segmentMarkdown(normalizeMathDelimiters(markdown)).map { block ->
        if (block is NoteBlock.Text) NoteBlock.Text(markwonInlineMath(block.markdown)) else block
    }

/** Fenced code (an unclosed fence runs to the end, as in CommonMark) and inline code spans: never rewritten. */
private val CODE = Regex("```[\\s\\S]*?(?:```|\\z)|`[^`\\n]*`")

// `(?<!\\)`: a TeX line break `\\` followed by `[2pt]` or `(` is not a delimiter.
private val DISPLAY_BACKSLASH = Regex("""(?<!\\)\\\[([\s\S]*?)(?<!\\)\\]""")
private val SINGLE_LINE_DISPLAY = Regex("""\$\$([^\n]+?)\$\$""")
private val LIST_OR_QUOTE = Regex("""^\s*(?:[-*+]|\d{1,9}[.)]|>)\s""")
private val EXTRA_BLANK_LINES = Regex("\n{3,}")

/**
 * The display half of the web app's `normalizeMathDelimiters` (apps/web/src/lib/markdown.ts), ported: outside code,
 * `\[…\]` becomes `$$…$$`, then a single-line `$$…$$` is lifted onto its own lines as display math, except on list-item
 * and block-quote lines where that would break the list or quote. Inline `\(…\)` is left to [markwonInlineMath].
 */
fun normalizeMathDelimiters(markdown: String): String =
    outsideCode(markdown) { prose ->
        promoteDisplayMath(prose.replace(DISPLAY_BACKSLASH) { "$$" + it.groupValues[1] + "$$" })
    }

private fun promoteDisplayMath(text: String): String =
    text
        .split("\n")
        .joinToString("\n") { line ->
            if (LIST_OR_QUOTE.containsMatchIn(line)) {
                line
            } else {
                line.replace(SINGLE_LINE_DISPLAY) { "\n\n$$\n" + it.groupValues[1].trim() + "\n$$\n\n" }
            }
        }.replace(EXTRA_BLANK_LINES, "\n\n")

/**
 * Rewrites inline `$…$` and `\(…\)` as Markwon's inline `$$…$$`, outside code. Existing `$$…$$` and escaped `\$` are
 * kept. `\(…\)` is explicit, so it always converts (inner spaces trimmed). A `$` opens math only when followed by a
 * non-space, and closes only when preceded by a non-space and not followed by a digit, within one paragraph (Pandoc's
 * rule) — so currency such as "$5 and $10" stays text. An unclosed `$$` (mid-stream) leaves the rest untouched, so a
 * later `$y$` is not paired with it.
 */
fun markwonInlineMath(markdown: String): String = outsideCode(markdown, ::rewriteInlineDollars)

private fun rewriteInlineDollars(text: String): String {
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        val paren = if (text.startsWith("\\(", i)) parenClose(text, i) else null
        when {
            paren != null -> {
                out.append("$$").append(text.substring(i + 2, paren).trim()).append("$$")
                i = paren + 2
            }
            c == '\\' -> {
                out.append(text, i, minOf(i + 2, text.length))
                i += 2
            }
            c == '$' && text.startsWith("$$", i) -> {
                val close = text.indexOf("$$", i + 2)
                val end = if (close < 0) text.length else close + 2
                out.append(text, i, end)
                i = end
            }
            c == '$' -> {
                val close = inlineClose(text, i)
                if (close == null) {
                    out.append(c)
                    i++
                } else {
                    out.append("$$").append(text, i + 1, close).append("$$")
                    i = close + 1
                }
            }
            else -> {
                out.append(c)
                i++
            }
        }
    }
    return out.toString()
}

/** Index of the `\)` closing the `\(` at [open], skipping escaped pairs such as `\\`, or null when unclosed. */
private fun parenClose(
    text: String,
    open: Int,
): Int? {
    var j = open + 2
    while (j < text.length - 1 && !text.startsWith("\\)", j)) {
        j += if (text[j] == '\\') 2 else 1
    }
    return j.takeIf { text.startsWith("\\)", it) }
}

/** Index of the `$` closing the inline math opened at [open], or null when it does not open one. */
private fun inlineClose(
    text: String,
    open: Int,
): Int? {
    var close: Int? = null
    var j = open + 1
    // Opens only before a non-space; the scan stops at the first `$` or at a blank line (end of paragraph).
    var scanning = text.getOrNull(j)?.isWhitespace() == false
    while (scanning && j < text.length) {
        when {
            text[j] == '\\' -> j += 2
            text.startsWith("\n\n", j) -> scanning = false
            text[j] == '$' -> {
                if (!text[j - 1].isWhitespace() && text.getOrNull(j + 1)?.isDigit() != true) close = j
                scanning = false
            }
            else -> j++
        }
    }
    return close
}

/** Applies [transform] to the runs of [markdown] outside code, leaving code fences and spans untouched. */
private fun outsideCode(
    markdown: String,
    transform: (String) -> String,
): String {
    val out = StringBuilder(markdown.length)
    var last = 0
    for (match in CODE.findAll(markdown)) {
        out.append(transform(markdown.substring(last, match.range.first))).append(match.value)
        last = match.range.last + 1
    }
    return out.append(transform(markdown.substring(last))).toString()
}
