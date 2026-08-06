// :native:ecapa — ECAPA-TDNN speaker verification JNI bridge
//
// NOTE: externalNativeBuild (CMake) is configured for both full builds (with
// ECAPA-TDNN source) and stub/dev builds (without source). The Kotlin-side
// EcapaJni declarations load libjarvis_ecapa.so at runtime via
// System.loadLibrary(); if the .so is absent, calls fail gracefully with
// UnsatisfiedLinkError, caught by callers.
plugins {
    id("nous.android.library")
}

android {
    namespace = "com.roshan.persona.ecapa"

    defaultConfig {
        // CMake build produces libjarvis_ecapa.so
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }

        // Target ABIs — arm64-v8a for modern devices, armeabi-v7a for older
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    // Timber for logging
    implementation(libs.timber)
}
