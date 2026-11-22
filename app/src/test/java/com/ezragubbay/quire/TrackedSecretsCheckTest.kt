package com.ezragubbay.quire

import com.ezragubbay.quire.buildlogic.TrackedSecretsCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackedSecretsCheckTest {
    @Test
    fun cleanTreeHasNoViolations() {
        val files = listOf("app/build.gradle.kts", "app/src/main/java/com/x/KeystoreCipher.kt", "docs/RELEASING.md")
        assertTrue(TrackedSecretsCheck.findViolations(files).isEmpty())
    }

    @Test
    fun flagsEachSecretKindAtAnyDepth() {
        val files =
            listOf(
                "release.jks",
                "app/debug.keystore",
                "keys/upload.p12",
                "keystore.properties",
                "app/google-services.json",
                "app/build.gradle.kts",
            )
        assertEquals(files.dropLast(1), TrackedSecretsCheck.findViolations(files))
    }

    @Test
    fun doesNotFlagSimilarNames() {
        val files = listOf("keystore.properties.example", "docs/google-services.json.md", "jks.md")
        assertTrue(TrackedSecretsCheck.findViolations(files).isEmpty())
    }

    @Test
    fun messageListsOffendingPaths() {
        assertTrue(TrackedSecretsCheck.failureMessage(listOf("a.jks")).contains("a.jks"))
    }
}
