// :core:navigation
plugins {
    id("nous.android.library")
    id("nous.android.navigation")
}

android {
    namespace = "com.roshan.persona.navigation"
}

dependencies {
    implementation(project(":core:common"))
}
