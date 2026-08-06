// :feature:brain — 6-layer brain orchestrator
plugins {
    id("nous.android.feature")
}

android {
    namespace = "com.roshan.persona.brain"
    buildFeatures { buildConfig = true }
}

dependencies {
    // Hilt already applied via nous.android.feature plugin
    // Coroutines + timber already from :core:common (api)

    // Time API
    implementation("jakarta.annotation:jakarta.annotation-api:2.1.1")

    // For tests
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("io.mockk:mockk:1.13.13")
}
