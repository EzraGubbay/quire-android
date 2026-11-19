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
}

val versionPropertiesFile = rootProject.file("version.properties")

fun loadVersion() =
    VersionParser.parse(
        Properties().apply { versionPropertiesFile.reader().use(::load) },
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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("versionPropertiesPath", versionPropertiesFile.absolutePath)
        }
    }

    // The version parser lives in buildSrc (used by this script); unit tests compile it directly.
    sourceSets.getByName("test").java.srcDir(rootProject.file("buildSrc/src/main/kotlin"))

    buildFeatures {
        compose = true
        buildConfig = true
    }
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
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
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
    androidTestImplementation(libs.okhttp.mockwebserver)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
