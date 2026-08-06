// :core:telemetry
plugins {
    id("nous.android.library")
    id("nous.android.hilt")
}

android {
    namespace = "com.roshan.persona.telemetry"
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(project(":core:common"))

    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("androidx.tracing:tracing-ktx:1.2.0")
    implementation("io.opentelemetry:opentelemetry-api:1.42.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.42.0")
}
