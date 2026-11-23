# R8 keep rules for the release/minified build. AndroidX, Compose, OkHttp, Room and Hilt ship consumer rules; the rules
# below cover what those cannot see (generated serializers, Retrofit's reflective proxies, Room/Hilt entry by name).

# --- kotlinx.serialization -------------------------------------------------------------------------------------
# @Serializable wire models are (de)serialised through generated `$serializer` classes and `Companion.serializer()`,
# which R8 would otherwise strip or rename.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-keep,includedescriptorclasses class com.ezragubbay.quire.**$$serializer { *; }

# --- Retrofit --------------------------------------------------------------------------------------------------
# Retrofit builds service proxies from interface annotations and generic return types (Signature, kept above), and
# reads the Kotlin Continuation type of suspend functions.
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# --- Room ------------------------------------------------------------------------------------------------------
# The generated *_Impl classes are located by name (Class.forName) from the @Database class.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# --- Hilt / Dagger ---------------------------------------------------------------------------------------------
# Hilt and Dagger bundle consumer rules; keep WorkManager workers (instantiated by class name) constructible.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# --- JLaTeXMath (Markwon ext-latex) ----------------------------------------------------------------------------
# Many built-in commands (\dfrac, \tfrac, \operatorname, \textbf, the cases/pmatrix/aligned environments, …) are
# TeX macros the engine invokes by name: MacroInfo does Class.forName("org.scilab.forge.jlatexmath.NewCommandMacro")
# and getDeclaredMethod("executeMacro"). R8 renamed the class and removed the method, so every formula using one threw
# and Markwon drew its raw LaTeX source instead. The library ships no consumer rules; keep the engine as-is.
-keep class org.scilab.forge.jlatexmath.** { *; }

# --- Logging ---------------------------------------------------------------------------------------------------
# Strip verbose/debug/info logging from shipped builds, so nothing written for development reaches logcat.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
