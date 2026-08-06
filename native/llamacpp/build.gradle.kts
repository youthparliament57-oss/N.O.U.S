// :native:llamacpp — llama.cpp JNI bridge
//
// NOTE: externalNativeBuild (CMake) is configured for both full builds (with
// llama.cpp source, enabled via -PbuildFromSource) and stub/dev builds.
// The Kotlin-side LlamaCppJni declarations load libjarvis_llm.so at runtime
// via System.loadLibrary(); if the .so is absent, calls fail gracefully with
// UnsatisfiedLinkError, caught by LlamaCppProvider.
plugins {
    id("nous.android.library")
}

android {
    namespace = "com.roshan.persona.llamacpp"

    defaultConfig {
        // CMake build produces libjarvis_llm.so
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }

        // Target ABIs for LLM inference (arm64-v8a preferred for Vulkan)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
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
    // Native llamacpp implements Brain's LocalLlmProvider interface.
    implementation(project(":feature:brain"))
    // Core common (Result, AppError, CorrelationId) — needed directly
    // because :feature:brain uses `implementation` (not `api`).
    implementation(project(":core:common"))
    // Timber for logging.
    implementation(libs.timber)
}
