package com.ezragubbay.quire.core.update

/** `MAJOR.MINOR.PATCH` with an optional `-rcN` suffix; the only shapes this project tags. */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    /** Release-candidate number; null for a final release (which sorts after every `-rcN` of the same triple). */
    val rc: Int? = null,
) : Comparable<AppVersion> {
    val isPrerelease: Boolean get() = rc != null

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(
            this,
            other,
            AppVersion::major,
            AppVersion::minor,
            AppVersion::patch,
            { it.rc ?: Int.MAX_VALUE },
        )

    override fun toString(): String = "$major.$minor.$patch" + (rc?.let { "-rc$it" } ?: "")

    companion object {
        /** Digit runs are capped at 9, so every group fits an Int. */
        private val SHAPE = Regex("""^[vV]?(\d{1,9})\.(\d{1,9})\.(\d{1,9})(?:-rc(\d{1,9}))?$""")

        /** Parses `1.2.3`, `v1.2.3` or `1.2.3-rc1`; null for anything else (never throws). */
        fun parse(text: String): AppVersion? =
            SHAPE.matchEntire(text)?.let { m ->
                val (major, minor, patch) = m.groupValues.subList(1, TRIPLE + 1).map(String::toInt)
                AppVersion(major, minor, patch, m.groups[TRIPLE + 1]?.value?.toInt())
            }

        private const val TRIPLE = 3
    }
}
