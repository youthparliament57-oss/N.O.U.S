// NOUS — Android Feature convention plugin
// Plugin ID: `nous.android.feature`

plugins {
    id("nous.android.library")
    id("nous.android.compose")
    id("nous.android.hilt")
}

dependencies {
    add("implementation", project(":core:common"))
    add("implementation", project(":core:telemetry"))
    add("implementation", project(":core:di"))
}
