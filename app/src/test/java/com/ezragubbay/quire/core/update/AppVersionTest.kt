package com.ezragubbay.quire.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    private fun v(s: String) = checkNotNull(AppVersion.parse(s)) { "unparseable $s" }

    @Test
    fun compare_isNumericNotLexical() {
        assertTrue(v("1.1.10") > v("1.1.9"))
        assertTrue(v("2.0.0") > v("1.99.99"))
        assertTrue(v("1.2.0") > v("1.1.99"))
    }

    @Test
    fun vPrefix_isAcceptedAndIgnored() {
        assertEquals(v("1.2.0"), v("v1.2.0"))
        assertNotNull(AppVersion.parse("V1.2.0"))
    }

    @Test
    fun releaseSortsAfterItsOwnReleaseCandidates() {
        assertTrue(v("1.2.0") > v("1.2.0-rc1"))
        assertTrue(v("1.2.0-rc2") > v("1.2.0-rc1"))
        assertTrue(v("1.2.0-rc1").isPrerelease)
        assertFalse(v("1.2.0").isPrerelease)
    }

    @Test
    fun equalVersions_areNotNewer() {
        assertFalse(v("1.2.0") > v("1.2.0"))
        assertEquals(0, v("1.2.0").compareTo(v("v1.2.0")))
    }

    @Test
    fun toString_isTheBareVersion() {
        assertEquals("1.2.0", v("v1.2.0").toString())
        assertEquals("1.2.0-rc1", v("1.2.0-rc1").toString())
    }

    @Test
    fun malformed_parsesToNull() {
        listOf("", "1.2", "1.2.3.4", "1.2.3-beta", "latest", " 1.2.3", "1.2.x").forEach {
            assertNull("'$it'", AppVersion.parse(it))
        }
    }
}
