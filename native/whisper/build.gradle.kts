// :native:whisper — whisper.cpp JNI bridge
//
// NOTE: externalNativeBuild (CMake) is configured for builds with the
// whisper.cpp git submodule source. The Kotlin-side WhisperJni declarations
// load libjarvis_whisper.so at runtime via System.loadLibrary(); if the .so
// is absent, calls fail gracefully with UnsatisfiedLinkError, caught by
// WhisperSttProvider. Strategy Module 4 §2.1 allows dev builds without native libs.
//
// Build modes:
//   - With submodule (CI/Release): CMake compiles whisper.cpp + ggml from source
//   - Without submodule (Dev): CMake produces error — use pre-built .so or stubs

plugins {
    id("nous.android.library")
}

android {
    namespace = "com.roshan.persona.whisper"

    defaultConfig {
        // CMake build configuration for whisper.cpp
        externalNativeBuild {
            cmake {
                cppFlags += listOf(
                    "-std=c++17",
                    "-O2",  // Optimization level
                    "-fvisibility=hidden",  // Hide symbols except JNI exports
                    "-ffunction-sections",  // Enable dead code elimination
                    "-fdata-sections"
                )
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DWHISPER_BUILD_TESTS=OFF",
                    "-DWHISPER_BUILD_EXAMPLES=OFF",
                    "-DWHISPER_BUILD_SERVER=OFF"
                )
            }
        }

        // Target ABIs — arm64-v8a primary (Vulkan support), armeabi-v7a fallback
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
