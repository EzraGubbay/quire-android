package com.ezragubbay.quire

import com.ezragubbay.quire.buildlogic.SigningConfigResolver
import com.ezragubbay.quire.buildlogic.SigningResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

class SigningConfigResolverTest {
    private val fullEnv =
        mapOf(
            "QUIRE_KEYSTORE_FILE" to "/tmp/release.jks",
            "QUIRE_KEYSTORE_PASSWORD" to "storepw",
            "QUIRE_KEY_ALIAS" to "quire",
            "QUIRE_KEY_PASSWORD" to "keypw",
        )

    private fun props(vararg pairs: Pair<String, String>) = Properties().apply { pairs.forEach { setProperty(it.first, it.second) } }

    @Test
    fun allFourFromEnvResolve() {
        val r = SigningConfigResolver.resolve(fullEnv, null) as SigningResolution.Resolved
        assertEquals("/tmp/release.jks", r.storeFile)
        assertEquals("storepw", r.storePassword)
        assertEquals("quire", r.keyAlias)
        assertEquals("keypw", r.keyPassword)
    }

    @Test
    fun allFourFromPropertiesResolve() {
        val p = props(*fullEnv.map { it.key to it.value }.toTypedArray())
        assertTrue(SigningConfigResolver.resolve(emptyMap(), p) is SigningResolution.Resolved)
    }

    @Test
    fun absentEverywhereReportsAllFourMissing() {
        val r = SigningConfigResolver.resolve(emptyMap(), null) as SigningResolution.Missing
        assertEquals(fullEnv.keys.toList(), r.keys)
    }

    @Test
    fun partialEnvReportsOnlyTheMissingKeys() {
        val env = fullEnv - "QUIRE_KEY_PASSWORD" - "QUIRE_KEY_ALIAS"
        val r = SigningConfigResolver.resolve(env, null) as SigningResolution.Missing
        assertEquals(listOf("QUIRE_KEY_ALIAS", "QUIRE_KEY_PASSWORD"), r.keys)
    }

    @Test
    fun blankValuesCountAsMissing() {
        val env = fullEnv + ("QUIRE_KEY_ALIAS" to "  ")
        val r = SigningConfigResolver.resolve(env, null) as SigningResolution.Missing
        assertEquals(listOf("QUIRE_KEY_ALIAS"), r.keys)
    }

    @Test
    fun envWinsOverPropertiesPerKeyAndPropertiesFillGaps() {
        val env = mapOf("QUIRE_KEYSTORE_FILE" to "/env.jks")
        val p =
            props(
                "QUIRE_KEYSTORE_FILE" to "/props.jks",
                "QUIRE_KEYSTORE_PASSWORD" to "p1",
                "QUIRE_KEY_ALIAS" to "p2",
                "QUIRE_KEY_PASSWORD" to "p3",
            )
        val r = SigningConfigResolver.resolve(env, p) as SigningResolution.Resolved
        assertEquals("/env.jks", r.storeFile)
        assertEquals("p1", r.storePassword)
    }

    @Test
    fun missingMessageNamesKeysButNeverValues() {
        val msg = SigningConfigResolver.failureMessage(listOf("QUIRE_KEY_ALIAS"))
        assertTrue(msg.contains("QUIRE_KEY_ALIAS"))
        assertTrue(msg.contains("keystore.properties"))
    }
}
