// =============================================================================
// NOUS — Root Settings
// The mind that perceives.
//
// Project: NOUS Android
// Package: com.roshan.persona
// Owner:   Roshan
// =============================================================================
//
// Module declaration order (topological):
//   1. Plugin management (where to find plugins)
//   2. Dependency resolution management (where to find libs)
//   3. Version catalog reference
//   4. Module includes (topological: build-logic → core → feature → dynamic → native → app)
//
// Convention: every Gradle module name is prefixed with its layer:
//   :core:*        — foundation, no Android feature logic
//   :feature:*     — vertical features (brain, voice, vision, ...)
//   :dynamicfeature:* — installable on-demand via Play Feature Delivery
//   :native:*      — NDK / JNI bridges (llama.cpp, ONNX, TFLite)
//   :app           — application entry point
// =============================================================================

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// Toolchain auto-provisioning — allows Gradle to download JDK 17 if not installed locally.
// Needed because the build-logic convention plugins require Java 17 sourceCompatibility.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.")
            }
        }
        // Snapshot repository — disabled by default. Enable per-module via
        // `repositories { mavenCentral { mavenContent { snapshotsOnly() } } }`
        // when a module genuinely needs snapshot deps.
    }
    // Note: `gradle/libs.versions.toml` is auto-detected by Gradle and exposed
    // as `libs` — no explicit versionCatalogs block needed.
}

rootProject.name = "NOUS"

// =============================================================================
// Build-logic (convention plugins) — included via includeBuild() above
// =============================================================================

// =============================================================================
// Core Modules — foundation layer, no Android feature logic
// =============================================================================
include(":core:common")
include(":core:di")
include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:security")
include(":core:designsystem")
include(":core:telemetry")
include(":core:permissions")
include(":core:navigation")
include(":core:testing")

// =============================================================================
// Feature Modules — vertical features, depend only on :core:*
// =============================================================================
include(":feature:brain")
include(":feature:memory")
include(":feature:llm")
include(":feature:voice")
include(":feature:vision")
include(":feature:persona")
include(":feature:cognitive")
include(":feature:automation")
include(":feature:agent")
include(":feature:system")
include(":feature:connectivity")
include(":feature:security")
include(":feature:productivity")
include(":feature:selfmodify")
include(":feature:ui")

// =============================================================================
// Dynamic Feature Modules — installed on-demand via Play Feature Delivery
// These are heavy / rarely-used / Play-policy-risky features that the user can
// download only when needed. Keeps base APK small.
// =============================================================================
include(":dynamicfeature:drone")
include(":dynamicfeature:ar")
include(":dynamicfeature:hacker")
include(":dynamicfeature:voiceclone")

// =============================================================================
// Native Modules — NDK / JNI bridges
// Each wraps a C/C++ library and exposes a Kotlin API to feature modules.
// =============================================================================
include(":native:llamacpp")
include(":native:ecapa")
include(":native:wakeword")
include(":native:whisper")

// =============================================================================
// App Module — application entry point
// =============================================================================
include(":app")
