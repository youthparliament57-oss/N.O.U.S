// :dynamicfeature:drone — on-demand dynamic feature module
//
// Strategy Table 12: This feature is scheduled for Year 2.
// The module is declared and installable via Play Feature Delivery,
// but the full implementation ships in a future release.
plugins {
    id("com.android.dynamic-feature")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.roshan.persona.drone"
    compileSdk = 36

    defaultConfig {
        missingDimensionStrategy("environment", "prod")
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":app"))
    implementation(project(":core:common"))
    implementation(project(":core:telemetry"))
    implementation(project(":core:di"))
    implementation(libs.timber)
}
