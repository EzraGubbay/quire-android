package com.ezragubbay.quire.feature.viewer.share

/** Turns a file title into a safe file name for a saved copy. Pure. */
object FileNameSanitizer {
    const val MAX_LENGTH = 120
    private const val FALLBACK = "Quire file"
    private val reserved = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

    fun suggestedName(
        title: String,
        extension: String,
    ): String {
        val suffix = ".$extension"
        var base = title.replace(reserved, "_").trim().trimStart('.')
        if (base.endsWith(suffix, ignoreCase = true)) base = base.dropLast(suffix.length)
        base = base.trim().trimEnd('.').trim()
        if (base.isEmpty()) base = FALLBACK
        val limit = MAX_LENGTH - suffix.length
        if (base.length > limit) {
            var end = limit
            if (Character.isHighSurrogate(base[end - 1])) end-- // never split a surrogate pair
            base = base.substring(0, end).trimEnd()
        }
        return base + suffix
    }
}
