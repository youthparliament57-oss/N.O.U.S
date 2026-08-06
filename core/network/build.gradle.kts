// :core:network
plugins {
    id("nous.android.library")
    id("nous.android.hilt")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.roshan.persona.network"
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:telemetry"))

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okio:okio:3.9.1")
}
