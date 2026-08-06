# =============================================================================
# NOUS — ProGuard / R8 Rules
#
# R8 full mode is enabled (gradle.properties: android.enableR8.fullMode=true).
# This file contains keep rules for reflection-based libraries that R8 cannot
# automatically shrink.
#
# Per-module consumer-rules.pro files declare rules that propagate to consumers
# of each library module.
# =============================================================================

# ─── General ────────────────────────────────────────────────────────────────

# Keep generic signatures of classes, methods, and fields that are referenced
# by reflection (used by serialization libraries).
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable

# ─── Hilt ───────────────────────────────────────────────────────────────────
# Hilt generates classes that reference original classes via reflection.
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory$ViewModelFactoriesEntryPoint { *; }
-keep class * extends androidx.hilt.work.HiltWorkerFactory { *; }
-keepclassmembers class * { @javax.inject.Inject *; }
-keepclassmembers class * { @dagger.hilt.android.lifecycle.HiltViewModel *; }

# ─── Room ───────────────────────────────────────────────────────────────────
# Room generates DAO implementations via codegen — no reflection needed.
# But the Database class is instantiated via reflection.
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# ─── SQLCipher ──────────────────────────────────────────────────────────────
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# ─── Retrofit ───────────────────────────────────────────────────────────────
# Retrofit uses reflection for service interfaces.
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ─── OkHttp ─────────────────────────────────────────────────────────────────
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ─── kotlinx.serialization ──────────────────────────────────────────────────
# Keep the @Serializable annotation and serializer companion lookups.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep,includedescriptorclasses class com.roshan.persona.**$$serializer { *; }
-keepclassmembers class com.roshan.persona.** {
    *** Companion;
}
-keepclasseswithmembers class com.roshan.persona.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ─── Llama.cpp JNI ──────────────────────────────────────────────────────────
# Native bridge classes that JNI calls into.
-keep class com.roshan.persona.llamacpp.** { *; }
-keep class com.roshan.persona.ecapa.** { *; }
-keep class com.roshan.persona.wakeword.** { *; }

# Native method names must not be renamed (JNI lookup).
-keepclasseswithmembernames class * {
    native <methods>;
}

# ─── ONNX Runtime ───────────────────────────────────────────────────────────
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ─── TFLite ─────────────────────────────────────────────────────────────────
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# ─── MediaPipe ──────────────────────────────────────────────────────────────
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# ─── Rhino JS engine (sideload only) ────────────────────────────────────────
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# ─── BouncyCastle ───────────────────────────────────────────────────────────
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ─── Coroutines ─────────────────────────────────────────────────────────────
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ─── Firebase Crashlytics (auto-handled by plugin) ──────────────────────────
# Crashlytics plugin auto-injects keep rules — nothing to add here.

# ─── BuildConfig (don't strip) ──────────────────────────────────────────────
-keep class com.roshan.persona.BuildConfig { *; }

# ─── Enums used in switch statements ────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
