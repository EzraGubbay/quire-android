package com.ezragubbay.quire.feature.notes

/**
 * Markdown from the server (notes, AI answers) writes math the way the Quire web app reads it: `$…$` inline, `$$…$$`
 * display, and, from the model, sometimes LaTeX-style `\(…\)` / `\[…\]`. Markwon's JLaTeXMath plugin understands only
 * `$$…$$` (inline when it sits inside a paragraph line, block when the fences stand on their own lines), so `$…$` and
 * the backslash forms would otherwise show as raw source. Pure; no Android dependencies.
 */
fun mathMarkdownBlocks(markdown: String): List<NoteBlock> =
    segmentMarkdown(normalizeMathDelimiters(markdown)).map { block ->
        if (block is NoteBlock.Text) NoteBlock.Text(markwonInlineMath(block.markdown)) else block
    }

/** Fenced code (an unclosed fence runs to the end, as in CommonMark) and inline code spans: never rewritten. */
private val CODE = Regex("```[\\s\\S]*?(?:```|\\z)|`[^`\\n]*`")
private val DISPLAY_BACKSLASH = Regex("""\\\[([\s\S]*?)\\]""")
private val INLINE_BACKSLASH = Regex("""\\\(([\s\S]*?)\\\)""")
private val SINGLE_LINE_DISPLAY = Regex("""\$\$([^\n]+?)\$\$""")
private val LIST_OR_QUOTE = Regex("""^\s*(?:[-*+]|\d{1,9}[.)]|>)\s""")
private val EXTRA_BLANK_LINES = Regex("\n{3,}")

/**
 * The web app's `normalizeMathDelimiters` (apps/web/src/lib/markdown.ts), ported: outside code, `\[…\]` becomes
 * `$$…$$` and `\(…\)` becomes `$…$` (inner spaces trimmed), then a single-line `$$…$$` is lifted onto its own lines
 * as display math, except on list-item and block-quote lines where that would break the list or quote.
 */
fun normalizeMathDelimiters(markdown: String): String =
    outsideCode(markdown) { prose ->
        promoteDisplayMath(
            prose
                .replace(DISPLAY_BACKSLASH) { "$$" + it.groupValues[1] + "$$" }
                // Trimmed so `\( x \)` still opens and closes under markwonInlineMath's flanking rule.
                .replace(INLINE_BACKSLASH) { "$" + it.groupValues[1].trim() + "$" },
        )
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
 * Rewrites inline `$…$` as Markwon's inline `$$…$$`, outside code. Existing `$$…$$` and escaped `\$` are kept. A `$`
 * opens math only when followed by a non-space, and closes only when preceded by a non-space and not followed by a
 * digit, within one paragraph (Pandoc's rule) — so currency such as "$5 and $10" stays text.
 */
fun markwonInlineMath(markdown: String): String = outsideCode(markdown, ::rewriteInlineDollars)

private fun rewriteInlineDollars(text: String): String {
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '\\' -> {
                out.append(text, i, minOf(i + 2, text.length))
                i += 2
            }
            c == '$' && text.startsWith("$$", i) -> {
                val close = text.indexOf("$$", i + 2)
                val end = if (close < 0) i + 2 else close + 2
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
