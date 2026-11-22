# R8 rules for the instrumentation (androidTest) APK of the minified build. AGP shrinks the test APK together with the
# app, so the test libraries' optional/compile-only references (AndroidX Test, Guava, errorprone) must not fail the build.
-dontwarn androidx.concurrent.futures.**
-dontwarn javax.lang.model.**
-dontwarn com.google.errorprone.annotations.**

# The test APK is loaded by name: the runner, JUnit, and Hilt's test application (named in QuireTestRunner) and the
# generated Hilt test components. Keep the test side whole; only the app under test is meaningfully minified.
-keep class dagger.hilt.android.testing.** { *; }
-keep class androidx.test.** { *; }
-keep class org.junit.** { *; }
-keep class com.ezragubbay.quire.** { *; }

# Must match the app's minified build (see proguard-minified-rules.pro): the two APKs share one classloader.
-dontobfuscate
