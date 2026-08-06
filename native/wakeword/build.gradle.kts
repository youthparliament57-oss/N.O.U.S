// :native:wakeword — Wake word detection JNI bridge
//
// NOTE: externalNativeBuild (CMake) is configured for both full builds (with
// wake word model source) and stub/dev builds (without source). The Kotlin-side
// WakewordJni declarations load libjarvis_wakeword.so at runtime via
// System.loadLibrary(); if the .so is absent, calls fail gracefully with
// UnsatisfiedLinkError, caught by callers.
plugins {
    id("nous.android.library")
}

android {
    namespace = "com.roshan.persona.wakeword"

    defaultConfig {
        // CMake build produces libjarvis_wakeword.so
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
