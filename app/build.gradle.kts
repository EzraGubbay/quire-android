import com.ezragubbay.quire.buildlogic.SigningConfigResolver
import com.ezragubbay.quire.buildlogic.SigningResolution
import com.ezragubbay.quire.buildlogic.TrackedSecretsCheck
import com.ezragubbay.quire.buildlogic.VersionParser
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val versionPropertiesFile = rootProject.file("version.properties")

fun loadVersion() =
    VersionParser.parse(
        Properties().apply { versionPropertiesFile.reader().use(::load) },
    )

val keystorePropertiesFile = rootProject.file("keystore.properties")
// `-PtestBuildType=minified` points instrumented tests at the R8-minified build (connectedMinifiedAndroidTest).
val testBuildTypeName = providers.gradleProperty("testBuildType").orNull ?: "debug"
val signing =
    SigningConfigResolver.resolve(
        System.getenv(),
        if (keystorePropertiesFile.isFile) {
            Properties().apply { keystorePropertiesFile.reader().use(::load) }
        } else {
            null
        },
    )

android {
    namespace = "com.ezragubbay.quire"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ezragubbay.quire"
        minSdk = 29
        targetSdk = 36
        versionCode = loadVersion().code
        versionName = loadVersion().name
        testInstrumentationRunner = "com.ezragubbay.quire.testing.QuireTestRunner"
        // Wipe the app's storage after every instrumented test, so no test sees what an earlier one stored. The Room
        // database (reading positions, cached lists, downloads), DataStore, WorkManager and the cache/files dirs
        // otherwise outlive a test: a journey that opened a PDF at a cited page left that file's last-read page
        // behind, and every later journey opening the same file resumed there instead of on page 1.
        // Needs the orchestrator (testOptions.execution) and the test-services APK (androidTestUtil).
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        // The minified build runs only the smoke subset (launch, pair, courses, open PDF, chat stream, chat math).
        if (testBuildTypeName == "minified") {
            testInstrumentationRunnerArguments["annotation"] = "com.ezragubbay.quire.testing.Smoke"
        }
    }

    testBuildType = testBuildTypeName

    signingConfigs {
        // Only defined when all four values resolve; otherwise preReleaseBuild fails fast (see below).
        if (signing is SigningResolution.Resolved) {
            create("release") {
                storeFile = file(signing.storeFile)
                storePassword = signing.storePassword
                keyAlias = signing.keyAlias
                keyPassword = signing.keyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "ALLOW_CLEARTEXT_LOCAL", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            // Cleartext (to loopback only) is a debug/test affordance; a shipped build is HTTPS-only.
            buildConfigField("boolean", "ALLOW_CLEARTEXT_LOCAL", "false")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // Release as shipped (R8 + resource shrinking), debug-signed, for the minified smoke run on an emulator.
        // Differences from release: debug signing, and loopback cleartext so the hermetic MockWebServer journeys work.
        create("minified") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            proguardFile("proguard-minified-rules.pro")
            testProguardFiles("proguard-test-rules.pro")
            buildConfigField("boolean", "ALLOW_CLEARTEXT_LOCAL", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // Version-freshness checks depend on the live Maven index, so they flip red with no code change.
        // Dependency bumps are deliberate, separate changes.
        disable +=
            setOf(
                "OldTargetApi",
                "AndroidGradlePluginVersion",
                "GradleDependency",
                "NewerVersionAvailable",
            )
    }

    testOptions {
        animationsDisabled = true
        // One instrumentation process per test (AndroidX Test Orchestrator): required by clearPackageData above, and
        // it also keeps process-wide state (WorkManager, statics, leaked coroutines) from crossing between tests.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("versionPropertiesPath", versionPropertiesFile.absolutePath)
        }
    }

    // The version parser lives in buildSrc (used by this script); unit tests compile it directly.
    sourceSets.getByName("test").java.srcDir(rootProject.file("buildSrc/src/main/kotlin"))

    // Room schema JSON is checked in; the androidTest migration scaffold reads it as assets.
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("app/schemas"))

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.register("printVersion") {
    val file = versionPropertiesFile
    inputs.file(file)
    doLast { println(loadVersion()) }
}

tasks.register("verifyVersion") {
    val file = versionPropertiesFile
    inputs.file(file)
    doLast { println("version.properties OK: ${loadVersion()}") }
}

// Fails assembleRelease/bundleRelease up front when signing is not configured; debug builds and checks never hit it.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        (signing as? SigningResolution.Missing)?.let {
            throw GradleException(
                SigningConfigResolver.failureMessage(it.keys),
            )
        }
    }
}

tasks.register("verifyNoTrackedSecrets") {
    val root = rootProject.projectDir
    doLast {
        val proc = ProcessBuilder("git", "ls-files").directory(root).redirectErrorStream(true).start()
        val tracked = proc.inputStream.bufferedReader().readLines()
        check(proc.waitFor() == 0) { "git ls-files failed: ${tracked.joinToString(" ")}" }
        val violations = TrackedSecretsCheck.findViolations(tracked)
        if (violations.isNotEmpty()) throw GradleException(TrackedSecretsCheck.failureMessage(violations))
        println("verifyNoTrackedSecrets OK")
    }
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.hilt.android)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.latex)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.retrofit.mock)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.compose.preview.scanner.support)
    testImplementation(libs.composable.preview.scanner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestUtil(libs.androidx.test.services)
    // Espresso 3.7+ is needed on API 36+ emulators (older versions crash in InputManager.getInstance).
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.room.testing)
    // WorkManager test driver (WorkManagerTestInitHelper / TestDriver) for the E7 sync journey.
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
