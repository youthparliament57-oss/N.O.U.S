// :core:common
plugins {
    id("nous.android.library")
}

android {
    namespace = "com.roshan.persona.common"
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    api("com.jakewharton.timber:timber:5.0.1")
    // javax.inject for qualifier annotations (Hilt-compatible, no runtime dep)
    api("javax.inject:javax.inject:1")
}
