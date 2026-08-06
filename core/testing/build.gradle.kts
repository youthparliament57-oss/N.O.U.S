// :core:testing
plugins {
    id("nous.android.library")
    id("nous.android.test")
}

android {
    namespace = "com.roshan.persona.testing"
}

dependencies {
    api(project(":core:common"))

    api("junit:junit:4.13.2")
    api("io.mockk:mockk:1.13.13")
    api("app.cash.turbine:turbine:1.2.0")
    api("com.google.truth:truth:1.4.4")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    api("androidx.arch.core:core-testing:2.2.0")
}
