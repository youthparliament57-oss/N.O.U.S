// :core:di
plugins {
    id("nous.android.library")
    id("nous.android.hilt")
}

android {
    namespace = "com.roshan.persona.di"
}

dependencies {
    api(project(":core:common"))
    api("androidx.work:work-runtime-ktx:2.10.0")
}
