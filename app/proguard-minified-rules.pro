# Minified (emulator smoke) build only. The Hilt test component generated into the androidTest APK constructs the app's
# object graph by class name (factories, injectors, modules), and the tests call a few app classes directly. R8 cannot
# see those references (they live in the other APK), so keep exactly that surface; everything else is shrunk as in release.
#
# Names are not obfuscated: the app and the androidTest APK are each run through R8, and their independently generated
# obfuscated names collide at runtime (e.g. both define a class called t0.s). Shrinking and optimisation still run.
-dontobfuscate
-keep class dagger.hilt.** { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
-keep class **_GeneratedInjector { *; }
-keep class **.Hilt_* { *; }
-keep class **_HiltModules* { *; }
-keep class **_HiltComponents* { *; }
-keep class com.ezragubbay.quire.di.** { *; }
-keep class com.ezragubbay.quire.core.data.session.SessionRepository { *; }
-keep class com.ezragubbay.quire.core.database.QuireDatabase { *; }
-keep class com.ezragubbay.quire.core.download.DownloadWorkerFactory { *; }
-keep class com.ezragubbay.quire.core.network.TokenCache { *; }

# The androidTest APK carries only its own classes and links against the app's copies of these libraries (Compose UI
# test, Room testing, MockWebServer, kotlin stdlib and coroutines all call into them). Shrinking them would break the
# runner, so they are kept whole in this test-only build. App code is still shrunk, optimised and obfuscated, and the
# app's own keep rules (serializers, Retrofit, Room, Hilt) are exactly what the smoke exercises.
-keep class com.ezragubbay.quire.MainActivity { *; }
-keep class javax.inject.** { *; }
-keep class androidx.compose.** { *; }
-keep class androidx.room.** { *; }
-keep class androidx.sqlite.** { *; }
-keep class androidx.work.** { *; }
-keep class androidx.core.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.concurrent.** { *; }
-keep class androidx.tracing.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class dagger.** { *; }
-keep class com.ezragubbay.quire.core.data.session.KeystoreCipher { *; }
-keep class androidx.collection.** { *; }
-keep class androidx.arch.** { *; }
-keep class com.ezragubbay.quire.core.data.session.PendingPairing { *; }
-keep class com.ezragubbay.quire.core.database.DownloadDao { *; }
-keep class com.ezragubbay.quire.core.database.QuireDatabaseKt { *; }
