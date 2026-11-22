package com.ezragubbay.quire.buildlogic

/**
 * Pure release-asset rules behind `scripts/check-release.sh` (Obtainium compatibility).
 * Each check returns null when it passes, or a human-readable failure message.
 */
object ReleaseChecks {
    fun singleApk(assetNames: List<String>): String? {
        val count = assetNames.count { it.endsWith(".apk", ignoreCase = true) }
        return if (count == 1) null else "Expected exactly one .apk asset, found $count"
    }

    fun versionNameMatchesTag(
        tag: String,
        versionName: String,
    ): String? {
        if (!tag.startsWith("v")) return "Tag must start with 'v', was: $tag"
        val expected = tag.removePrefix("v")
        return if (versionName == expected) null else "versionName '$versionName' must equal tag without 'v' ('$expected')"
    }

    fun versionCodeIncreases(
        code: Int,
        previousCodes: List<Int>,
    ): String? {
        val max = previousCodes.maxOrNull() ?: return null
        return if (code > max) null else "versionCode $code must be strictly greater than previous releases (max $max)"
    }
}
