package com.ezragubbay.quire.feature.notes

/** One renderable chunk of a note: Markdown prose (inline math stays inside it) or a `$$ … $$` equation. */
sealed interface NoteBlock {
    data class Text(
        val markdown: String,
    ) : NoteBlock

    /** [latex] is the formula source without the `$$` delimiters. */
    data class Math(
        val latex: String,
    ) : NoteBlock
}

private const val FENCE = "```"
private const val DELIM = "$$"

/**
 * Splits [markdown] into ordered [NoteBlock]s: prose runs and block-math (`$$ … $$`). Delimiters inside fenced code
 * are not math; an unterminated `$$` stays prose. Blank prose runs are dropped. Pure; no Android dependencies.
 */
fun segmentMarkdown(markdown: String): List<NoteBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<NoteBlock>()
    val prose = mutableListOf<String>()

    fun flush() {
        val text = prose.joinToString("\n").trim()
        if (text.isNotEmpty()) blocks += NoteBlock.Text(text)
        prose.clear()
    }

    var inFence = false
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        if (trimmed.startsWith(FENCE)) {
            inFence = !inFence
            prose += line
            i++
        } else if (!inFence && trimmed.startsWith(DELIM)) {
            val end = findMathEnd(lines, i)
            if (end == null) {
                prose += line
                i++
            } else {
                flush()
                blocks += NoteBlock.Math(end.first)
                i = end.second + 1
            }
        } else {
            prose += line
            i++
        }
    }
    flush()
    return blocks
}

/** Returns the formula and the index of the line holding the closing `$$`, or null when unterminated. */
private fun findMathEnd(
    lines: List<String>,
    start: Int,
): Pair<String, Int>? {
    val first = lines[start].trim().removePrefix(DELIM)
    val sameLine = first.trimEnd().endsWith(DELIM)
    val body = mutableListOf(if (sameLine) first.trimEnd().removeSuffix(DELIM) else first)
    var end: Int? = if (sameLine) start else null
    var j = start + 1
    while (end == null && j < lines.size) {
        val t = lines[j].trim()
        if (t.endsWith(DELIM)) {
            body += t.removeSuffix(DELIM)
            end = j
        } else {
            body += lines[j]
        }
        j++
    }
    return end?.let { body.joinToString("\n").trim() to it }
}
