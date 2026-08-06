// =============================================================================
// NOUS — build-logic Build Script
// =============================================================================

plugins {
    `kotlin-dsl`
}

group = "com.roshan.persona.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Plugin marker artifacts — needed as `implementation` (not compileOnly)
    // so the precompiled script plugin accessor generation can resolve them.
    implementation("com.android.application:com.android.application.gradle.plugin:8.7.2")
    implementation("com.android.library:com.android.library.gradle.plugin:8.7.2")
    implementation("org.jetbrains.kotlin.android:org.jetbrains.kotlin.android.gradle.plugin:2.0.21")
    implementation("org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:2.0.21")
    implementation("org.jetbrains.kotlin.plugin.serialization:org.jetbrains.kotlin.plugin.serialization.gradle.plugin:2.0.21")
    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.0.21-1.0.28")
    implementation("com.google.dagger.hilt.android:com.google.dagger.hilt.android.gradle.plugin:2.52")
    implementation("androidx.room:androidx.room.gradle.plugin:2.7.1")
    implementation("io.gitlab.arturbosch.detekt:io.gitlab.arturbosch.detekt.gradle.plugin:1.23.7")

    // Actual implementation JARs for compile-time API access
    compileOnly("com.android.tools.build:gradle:8.7.2")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
}
