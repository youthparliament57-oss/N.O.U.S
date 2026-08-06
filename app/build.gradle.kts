// =============================================================================
// NOUS — App Module Build Script
//
// The :app module is the application entry point. It applies:
//   - nous.android.application (flavors, signing, R8, dynamic features)
//   - nous.android.compose     (Compose UI)
//   - nous.android.hilt        (DI)
//   - nous.android.test        (testing)
//   - nous.android.navigation  (Compose Navigation)
// =============================================================================

plugins {
    id("nous.android.application")
    id("nous.android.compose")
    id("nous.android.hilt")
    id("nous.android.test")
    id("nous.android.navigation")
    id("nous.detekt")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.roshan.persona"

    defaultConfig {
        applicationId = "com.roshan.persona"
        versionCode = 2026080401  // CRITICAL FIX #53: Proper versioning (YYYYMMDDNN format)
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // ─── ABI Splits (reduce APK size) ──────────────────────────────────────
    // dev flavor: arm64-v8a only (modern devices, smallest APK)
    // prod flavor: all ABIs (via AAB, Play handles splits)
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    // ─── Debug symbol level (reduce APK size) ──────────────────────────────
    // SYMBOL_TABLE keeps enough for stack traces but strips full debug symbols
    buildTypes {
        debug {
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
        release {
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    // ─── Dexing (D8/R8) — disable global synthetics + bundle optimizations ──
    // Global synthetics merging consumes ~1GB RAM during D8 dexing, which
    // causes OOM kills on 4GB RAM environments. Disabling it trades a
    // slightly larger APK (no shared constant pool) for reliable builds.
    bundle {
        // Disable language/density/abi splits — single universal APK.
        language { enableSplit = false }
        density  { enableSplit = false }
        abi      { enableSplit = false }
    }
}

dependencies {
    // ─── Core ──────────────────────────────────────────────────────────────
    implementation(project(":core:common"))
    implementation(project(":core:di"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    implementation(project(":core:security"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:telemetry"))
    implementation(project(":core:permissions"))
    implementation(project(":core:navigation"))
    implementation(project(":core:testing"))

    // ─── Features (all 15) ────────────────────────────────────────────────
    implementation(project(":feature:brain"))
    implementation(project(":feature:memory"))
    implementation(project(":feature:llm"))
    implementation(project(":feature:voice"))
    implementation(project(":feature:vision"))
    implementation(project(":feature:persona"))
    implementation(project(":feature:cognitive"))
    implementation(project(":feature:automation"))
    implementation(project(":feature:agent"))
    implementation(project(":feature:system"))
    implementation(project(":feature:connectivity"))
    implementation(project(":feature:security"))
    implementation(project(":feature:productivity"))
    implementation(project(":feature:selfmodify"))
    implementation(project(":feature:ui"))

    // ─── Native ────────────────────────────────────────────────────────────
    implementation(project(":native:llamacpp"))
    implementation(project(":native:ecapa"))
    implementation(project(":native:wakeword"))
    implementation(project(":native:whisper"))

    // ─── AndroidX ──────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)  // CRITICAL FIX #40: Use version catalog
    // implementation("androidx.fragment:fragment-ktx")  // CRITICAL FIX #41: Remove unused or use catalog
    implementation(libs.androidx.lifecycle.runtime.ktx)  // CRITICAL FIX #42: Use version catalog
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.startup)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.work.runtime)

    // ─── Coroutines ────────────────────────────────────────────────────────
    implementation(libs.bundles.coroutines)

    // ─── Firebase ──────────────────────────────────────────────────────────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.crashlytics.ndk)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.remote.config)
    implementation(libs.firebase.appcheck)

    // ─── Logging ───────────────────────────────────────────────────────────
    implementation(libs.timber)
    implementation(libs.androidx.room.runtime)  // CRITICAL FIX #43: Use version catalog (was hardcoded)

    // ─── Play Integrity ────────────────────────────────────────────────────
    implementation(libs.play.integrity)

    // ─── Serialization ─────────────────────────────────────────────────────
    implementation(libs.kotlinx.serialization.json)

    // ─── Test ──────────────────────────────────────────────────────────────
    testImplementation(libs.bundles.testing.unit)
    androidTestImplementation(libs.bundles.testing.android)
}
