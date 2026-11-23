package com.ezragubbay.quire.testing

/**
 * Marks an existing journey case as part of the minified-build smoke subset (launch, pair, courses, open PDF, chat
 * stream, chat math). The `minified` build runs only these (see `testInstrumentationRunnerArguments["annotation"]` in
 * `app/build.gradle.kts`); every case still runs unchanged on the debug build. [step] names the smoke step it covers.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Smoke(
    val step: String,
)
