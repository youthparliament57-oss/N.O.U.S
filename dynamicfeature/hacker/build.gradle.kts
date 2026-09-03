// :dynamicfeature:hacker — on-demand dynamic feature module (FLAVOR-GATED)
//
// Per ADR-0009: This module is only available in `dev` and `internal` flavors.
// In `prod` (Play Store), the module is completely excluded.
//
// Flavor Behavior:
//   dev      → Bundled with APK
//   internal → Dynamic feature (on-demand)
//   prod     → NOT AVAILABLE (excluded)
//
// Runtime access is gated by BuildConfig.ENABLE_HACKER flag.

plugins {
    id("com.android.dynamic-feature")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.roshan.persona.hacker"
    compileSdk = 36

    defaultConfig {
        // Only applies to non-prod flavors
        missingDimensionStrategy("environment", "internal")
        minSdk = 29

        // Build config flag for runtime gating
        buildConfigField("boolean", "ENABLE_HACKER", "true")
        
        // Additional per-feature flags (can be overridden by Remote Config)
        buildConfigField("boolean", "ENABLE_WIFI_PASSWORD", "true")
        buildConfigField("boolean", "ENABLE_STEGANOGRAPHY", "true")
        buildConfigField("boolean", "ENABLE_TOR", "true")
        buildConfigField("boolean", "ENABLE_TERMINAL", "true")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Flavor-specific configuration per ADR-0009
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            // In dev: bundled, all features enabled
        }
        create("internal") {
            dimension = "environment"
            // In internal: on-demand delivery, features enabled
        }
        // Note: 'prod' flavor should NOT include this module at all.
        // The app's build.gradle.kts should exclude :dynamicfeature:hacker
        // when building prod flavor.
    }

    buildFeatures {
        buildConfig = true  // Required for ENABLE_HACKER flag
    }
}

dependencies {
    implementation(project(":app"))
    implementation(project(":core:common"))
    implementation(project(":core:telemetry"))
    implementation(project(":core:di"))
    implementation(libs.timber)
}

// ─── Validation: Warn if building hacker module for prod ────────────
tasks.configureEach {
    if (name.contains("prod", ignoreCase = true)) {
        doFirst {
            logger.warn(
                "⚠️  HACKER MODULE: Building for 'prod' flavor violates ADR-0009! " +
                "This module should be excluded from Play Store builds."
            )
        }
    }
}
