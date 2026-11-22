package com.ezragubbay.quire.buildlogic

object TrackedSecretsCheck {
    private val EXTENSIONS = setOf("jks", "keystore", "p12")
    private val NAMES = setOf("keystore.properties", "google-services.json")

    fun findViolations(trackedPaths: List<String>): List<String> =
        trackedPaths.filter { path ->
            val name = path.substringAfterLast('/')
            name in NAMES || name.substringAfterLast('.', "") in EXTENSIONS
        }

    fun failureMessage(violations: List<String>) =
        "Secret files are tracked by git (remove with `git rm --cached` and rotate if ever pushed): " +
            violations.joinToString(", ")
}
