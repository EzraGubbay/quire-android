package com.ezragubbay.quire

import com.ezragubbay.quire.buildlogic.AppVersion
import com.ezragubbay.quire.buildlogic.VersionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.util.Properties

class VersionParserTest {
    private fun props(
        name: String?,
        code: String?,
    ) = Properties().apply {
        name?.let { setProperty("VERSION_NAME", it) }
        code?.let { setProperty("VERSION_CODE", it) }
    }

    @Test
    fun parsesValidVersion() {
        assertEquals(AppVersion("0.1.0", 1), VersionParser.parse(props("0.1.0", "1")))
    }

    @Test
    fun trimsWhitespace() {
        assertEquals(AppVersion("1.2.3", 42), VersionParser.parse(props(" 1.2.3 ", " 42 ")))
    }

    @Test
    fun acceptsReleaseCandidateSuffix() {
        assertEquals(AppVersion("1.0.0-rc2", 7), VersionParser.parse(props("1.0.0-rc2", "7")))
    }

    @Test
    fun rejectsBadSemver() {
        listOf("1.0", "1.0.0.0", "v1.0.0", "1.0.0-beta", "1.0.0-rc", "01.0.0", "").forEach {
            assertThrows("name=$it", IllegalArgumentException::class.java) {
                VersionParser.parse(props(it, "1"))
            }
        }
    }

    @Test
    fun rejectsNonPositiveOrNonNumericCode() {
        listOf("0", "-1", "abc", "1.5", "").forEach {
            assertThrows("code=$it", IllegalArgumentException::class.java) {
                VersionParser.parse(props("0.1.0", it))
            }
        }
    }

    @Test
    fun rejectsMissingKeys() {
        assertThrows(IllegalArgumentException::class.java) { VersionParser.parse(props(null, "1")) }
        assertThrows(IllegalArgumentException::class.java) { VersionParser.parse(props("0.1.0", null)) }
    }

    @Test
    fun formatsForPrint() {
        assertEquals("0.1.0 (1)", AppVersion("0.1.0", 1).toString())
    }

    @Test
    fun builtAppVersionComesFromVersionProperties() {
        val file = File(checkNotNull(System.getProperty("versionPropertiesPath")))
        val expected = VersionParser.parse(Properties().apply { file.reader().use(::load) })
        assertEquals(expected.name, BuildConfig.VERSION_NAME)
        assertEquals(expected.code, BuildConfig.VERSION_CODE)
    }
}
