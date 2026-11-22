package com.ezragubbay.quire

import com.ezragubbay.quire.buildlogic.ReleaseChecks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseChecksTest {
    @Test
    fun singleApkPasses() {
        assertNull(ReleaseChecks.singleApk(listOf("quire-android-1.0.0.apk", "quire-android-1.0.0.apk.sha256")))
    }

    @Test
    fun noApkFails() {
        assertNotNull(ReleaseChecks.singleApk(listOf("quire-android-1.0.0.apk.sha256")))
        assertNotNull(ReleaseChecks.singleApk(emptyList()))
    }

    @Test
    fun twoApksFail() {
        assertNotNull(ReleaseChecks.singleApk(listOf("a.apk", "b.apk")))
    }

    @Test
    fun apkMatchIsCaseInsensitive() {
        assertNotNull(ReleaseChecks.singleApk(listOf("a.apk", "B.APK")))
    }

    @Test
    fun versionNameMustEqualTagWithoutV() {
        assertNull(ReleaseChecks.versionNameMatchesTag("v1.0.0", "1.0.0"))
        assertNull(ReleaseChecks.versionNameMatchesTag("v1.0.0-rc1", "1.0.0-rc1"))
    }

    @Test
    fun versionNameMismatchFails() {
        assertNotNull(ReleaseChecks.versionNameMatchesTag("v1.0.0", "1.0.1"))
        assertNotNull(ReleaseChecks.versionNameMatchesTag("v1.0.0-rc1", "1.0.0"))
    }

    @Test
    fun tagWithoutVPrefixFails() {
        assertNotNull(ReleaseChecks.versionNameMatchesTag("1.0.0", "1.0.0"))
    }

    @Test
    fun versionCodeStrictlyGreaterThanAllPrevious() {
        assertNull(ReleaseChecks.versionCodeIncreases(3, listOf(1, 2)))
        assertNull(ReleaseChecks.versionCodeIncreases(1, emptyList()))
    }

    @Test
    fun versionCodeEqualOrLowerFails() {
        assertNotNull(ReleaseChecks.versionCodeIncreases(2, listOf(1, 2)))
        assertNotNull(ReleaseChecks.versionCodeIncreases(1, listOf(5, 2)))
    }

    @Test
    fun failureMessagesNameTheProblem() {
        assertEquals(true, ReleaseChecks.singleApk(listOf("a.apk", "b.apk"))!!.contains("2"))
    }
}
