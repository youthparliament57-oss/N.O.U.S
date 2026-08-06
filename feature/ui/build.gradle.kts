// :feature:ui
plugins {
    id("nous.android.feature")
    id("nous.android.navigation")
}

android {
    namespace = "com.roshan.persona.ui"
}

dependencies {
    implementation(project(":feature:brain"))
    implementation(project(":feature:persona"))
    implementation(project(":feature:memory"))
    implementation(project(":feature:automation"))
    implementation(project(":feature:security"))
    implementation(project(":feature:connectivity"))
    implementation(project(":feature:productivity"))
    implementation(project(":feature:llm"))
    implementation(project(":feature:voice"))
    implementation(project(":feature:vision"))
    implementation(project(":feature:cognitive"))
    implementation(project(":feature:selfmodify"))
    implementation(project(":feature:system"))
    implementation(project(":feature:agent"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:permissions"))

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // Camera-X for Vision screen
    implementation(libs.bundles.camerax)

    // Biometric prompt for App Lock
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // DataStore for UI prefs
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Google Fonts provider for Compose Typography
    implementation("androidx.compose.ui:ui-text-google-fonts:1.7.6")

    // ViewModel + Hilt ViewModel integration
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.fragment:fragment-ktx:1.8.6")
}
