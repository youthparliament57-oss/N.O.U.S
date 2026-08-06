// NOUS — Compose convention plugin
// Plugin ID: `nous.android.compose`

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

// Try Application extension first, fall back to Library
extensions.findByType(com.android.build.api.dsl.ApplicationExtension::class.java)?.apply {
    buildFeatures { compose = true }
}
extensions.findByType(com.android.build.api.dsl.LibraryExtension::class.java)?.apply {
    buildFeatures { compose = true }
}

dependencies {
    add("implementation", platform("androidx.compose:compose-bom:2024.10.00"))
    add("androidTestImplementation", platform("androidx.compose:compose-bom:2024.10.00"))

    add("implementation", "androidx.compose.ui:ui")
    add("implementation", "androidx.compose.ui:ui-graphics")
    add("implementation", "androidx.compose.ui:ui-tooling-preview")
    add("implementation", "androidx.compose.material3:material3")
    add("implementation", "androidx.compose.material:material-icons-extended")
    add("implementation", "androidx.compose.foundation:foundation")
    add("implementation", "androidx.compose.runtime:runtime")
    add("implementation", "androidx.compose.animation:animation")
    add("implementation", "androidx.activity:activity-compose:1.9.3")
    add("implementation", "androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    add("implementation", "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    add("debugImplementation", "androidx.compose.ui:ui-tooling")
    add("debugImplementation", "androidx.compose.ui:ui-test-manifest")
}
