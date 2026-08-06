// :core:permissions
plugins {
    id("nous.android.library")
    id("nous.android.compose")
    id("nous.android.hilt")
}

android {
    namespace = "com.roshan.persona.permissions"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
}
