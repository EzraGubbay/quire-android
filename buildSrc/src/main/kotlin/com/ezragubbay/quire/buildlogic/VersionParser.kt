package com.ezragubbay.quire.buildlogic

import java.util.Properties

data class AppVersion(
    val name: String,
    val code: Int,
) {
    override fun toString() = "$name ($code)"
}

object VersionParser {
    private val SEMVER = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-rc[1-9]\d*)?""")

    fun parse(props: Properties): AppVersion {
        val name = props.getProperty("VERSION_NAME")?.trim()
        require(name != null && SEMVER.matches(name)) {
            "VERSION_NAME must be MAJOR.MINOR.PATCH with optional -rcN, was: $name"
        }
        val rawCode = props.getProperty("VERSION_CODE")?.trim()
        val code = rawCode?.takeIf { it.matches(Regex("""\d+""")) }?.toIntOrNull()
        require(code != null && code > 0) { "VERSION_CODE must be a positive integer, was: $rawCode" }
        return AppVersion(name, code)
    }
}
