// =============================================================================
// NOUS — Root Build Script
//
// This file is intentionally minimal. All real configuration lives in
// convention plugins under build-logic/. Each module applies the appropriate
// convention plugin (jarvis.android.application / .library / .feature / ...)
// and inherits sane defaults.
//
// Version catalog is declared in gradle/libs.versions.toml and exposed to all
// modules as `libs`.
// =============================================================================

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.binary.compatibility.validator) apply false
    alias(libs.plugins.versions) apply false
    alias(libs.plugins.dependency.check) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.paparazzi) apply false
}
