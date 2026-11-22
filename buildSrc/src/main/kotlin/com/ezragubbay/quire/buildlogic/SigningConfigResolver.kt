package com.ezragubbay.quire.buildlogic

import java.util.Properties

sealed interface SigningResolution {
    data class Resolved(
        val storeFile: String,
        val storePassword: String,
        val keyAlias: String,
        val keyPassword: String,
    ) : SigningResolution

    data class Missing(
        val keys: List<String>,
    ) : SigningResolution
}

object SigningConfigResolver {
    const val KEYSTORE_FILE = "QUIRE_KEYSTORE_FILE"
    const val KEYSTORE_PASSWORD = "QUIRE_KEYSTORE_PASSWORD"
    const val KEY_ALIAS = "QUIRE_KEY_ALIAS"
    const val KEY_PASSWORD = "QUIRE_KEY_PASSWORD"

    private val ALL = listOf(KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD)

    /** Per key, the environment wins; an untracked keystore.properties fills the gaps. Blank counts as absent. */
    fun resolve(
        env: Map<String, String?>,
        props: Properties?,
    ): SigningResolution {
        fun value(key: String) = (env[key] ?: props?.getProperty(key))?.takeIf { it.isNotBlank() }
        val missing = ALL.filter { value(it) == null }
        if (missing.isNotEmpty()) return SigningResolution.Missing(missing)
        return SigningResolution.Resolved(
            storeFile = value(KEYSTORE_FILE)!!,
            storePassword = value(KEYSTORE_PASSWORD)!!,
            keyAlias = value(KEY_ALIAS)!!,
            keyPassword = value(KEY_PASSWORD)!!,
        )
    }

    fun failureMessage(missing: List<String>) =
        "Release signing is not configured; missing: ${missing.joinToString(", ")}. " +
            "Set them as environment variables (CI) or in an untracked keystore.properties at the repo root " +
            "(local). See docs/RELEASING.md. Debug builds and checks do not need them."
}
