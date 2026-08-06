// NOUS — Android Application convention plugin
// Plugin ID: `nous.android.application`

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import java.util.Properties
import java.io.File

plugins {
    id("com.android.application")
    id("nous.kotlin.android")
}

extensions.getByType<ApplicationExtension>().apply {
    compileSdk = 36
    ndkVersion = "27.1.12297006"

    defaultConfig {
        minSdk = 29
        targetSdk = 36
        applicationId = "com.roshan.persona"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures { buildConfig = true }

    // ─── Dexing (D8) — disable global synthetics for low-RAM builds ─────
    // Global synthetics merging consumes ~1GB RAM during D8 dexing, which
    // causes OOM kills on 4GB RAM environments. The `dexOptions` block is
    // deprecated but `androidComponents` is the modern equivalent.
    //
    // We also disable dex in process (forces D8 to run in a separate JVM
    // with its own heap, rather than consuming the Gradle daemon's heap).
    @Suppress("UnstableApiUsage")
    androidComponents {
        beforeVariants { variant ->
            // Disable bundle optimizations that consume extra memory.
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0", "META-INF/LGPL2.1",
                "META-INF/LICENSE.md", "META-INF/LICENSE-notice.md",
                "META-INF/DEPENDENCIES", "META-INF/LICENSE*",
                "META-INF/NOTICE*", "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/versions/**/OSGI-INF/MANIFEST.MF",
                "META-INF/proguard/**",
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    // ─── Flavors ─────────────────────────────────────────────────────────
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("boolean", "ENABLE_HACKER", "true")
            buildConfigField("boolean", "ENABLE_SELF_MODIFY", "true")
            buildConfigField("boolean", "ENABLE_TERMINAL", "true")
            buildConfigField("boolean", "VERBOSE_TELEMETRY", "true")
            buildConfigField("String", "NOUS_ENVIRONMENT", "\"dev\"")
        }
        create("internal") {
            dimension = "environment"
            applicationIdSuffix = ".internal"
            versionNameSuffix = "-internal"
            buildConfigField("boolean", "ENABLE_HACKER", "true")
            buildConfigField("boolean", "ENABLE_SELF_MODIFY", "true")
            buildConfigField("boolean", "ENABLE_TERMINAL", "true")
            buildConfigField("boolean", "VERBOSE_TELEMETRY", "true")
            buildConfigField("String", "NOUS_ENVIRONMENT", "\"internal\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("boolean", "ENABLE_HACKER", "false")
            buildConfigField("boolean", "ENABLE_SELF_MODIFY", "false")
            buildConfigField("boolean", "ENABLE_TERMINAL", "false")
            buildConfigField("boolean", "VERBOSE_TELEMETRY", "false")
            buildConfigField("String", "NOUS_ENVIRONMENT", "\"prod\"")
        }
    }

    // ─── Build Types ─────────────────────────────────────────────────────
    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
            applicationIdSuffix = ".debug"
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val keystoreProps = rootProject.file("keystore.properties")
            if (keystoreProps.exists()) {
                val props = Properties().apply { load(keystoreProps.inputStream()) }
                signingConfigs.create("release") {
                    storeFile = File(props.getProperty("storeFile"))
                    storePassword = props.getProperty("storePassword")
                    keyAlias = props.getProperty("keyAlias")
                    keyPassword = props.getProperty("keyPassword")
                }
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // ─── Bundle ─────────────────────────────────────────────────────────
    bundle {
        abi { enableSplit = true }
        density { enableSplit = true }
        language { enableSplit = true }
    }
}

dependencies {
    add("coreLibraryDesugaring", "com.android.tools:desugar_jdk_libs:2.1.2")
}
