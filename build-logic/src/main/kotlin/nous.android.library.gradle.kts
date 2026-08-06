// NOUS — Android Library convention plugin
// Plugin ID: `nous.android.library`

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion

plugins {
    id("com.android.library")
    id("nous.kotlin.android")
}

extensions.getByType<LibraryExtension>().apply {
    compileSdk = 36
    ndkVersion = "27.1.12297006"

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        buildConfig = false
        androidResources = true
    }

    // Mirror app flavors so library modules can have flavor-specific deps
    // (e.g., selfmodify: devImplementation for Rhino)
    flavorDimensions += "environment"
    productFlavors {
        create("dev") { dimension = "environment" }
        create("internal") { dimension = "environment" }
        create("prod") { dimension = "environment" }
    }

    // Library modules need matching fallbacks when consuming app has flavors
    defaultConfig {
        missingDimensionStrategy("environment", "prod")
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0", "META-INF/LGPL2.1",
                "META-INF/LICENSE.md", "META-INF/LICENSE-notice.md",
                "META-INF/DEPENDENCIES", "META-INF/LICENSE*",
                "META-INF/NOTICE*", "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    add("coreLibraryDesugaring", "com.android.tools:desugar_jdk_libs:2.1.2")
}
