# =============================================================================
# NOUS — whisper.cpp JNI Bridge CMake build
#
# Builds:
#   - libwhisper.a   (static, from whisper.cpp submodule)
# - libggml.a      (static, ggml tensor backend bundled with whisper.cpp)
#   - libjarvis_whisper.so (JNI bridge — loaded at runtime via
#     System.loadLibrary("jarvis_whisper"))
#
# The whisper.cpp source is expected at src/main/cpp/whisper.cpp/ (git
# submodule). If the submodule is not initialized, the build will fail with
# a clear error message (see below).
# =============================================================================

cmake_minimum_required(VERSION 3.22.1)

project(jarvis_whisper LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# ─── Sanity check: whisper.cpp submodule must be initialized ─────────────────
set(WHISPER_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp")
if(NOT EXISTS "${WHISPER_CPP_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "whisper.cpp submodule not found at ${WHISPER_CPP_DIR}\n"
        "Run: git submodule update --init --recursive"
    )
endif()

# ─── Build whisper.cpp + ggml as static libs ─────────────────────────────────
# We add whisper.cpp's CMake dir as a subdirectory; its own CMakeLists.txt
# builds libwhisper.a and libggml.a with the options we set below.
set(WHISPER_BUILD_TESTS        OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES     OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER       OFF CACHE BOOL "" FORCE)
set(GGML_OPENSSL               OFF CACHE BOOL "" FORCE)
set(GGML_STANDALONE            OFF CACHE BOOL "" FORCE)

# Vulkan backend — enabled if the NDK ships Vulkan headers (API 29+).
# The Java side probes availability at runtime via WhisperJni.nativeIsGpuAvailable.
if(ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "x86_64")
    set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
else()
    set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
endif()

add_subdirectory("${WHISPER_CPP_DIR}" whisper_cpp_build)

# ─── JNI bridge shared library ───────────────────────────────────────────────
add_library(jarvis_whisper SHARED
    jarvis_whisper.cpp
)

target_include_directories(jarvis_whisper PRIVATE
    "${WHISPER_CPP_DIR}/include"
    "${WHISPER_CPP_DIR}/ggml/include"
)

# Link whisper + ggml + Android log + JNI.
target_link_libraries(jarvis_whisper PRIVATE
    whisper
    ggml
    log
    android
)

# Strip symbols in release builds — saves ~30% on .so size.
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_link_options(jarvis_whisper PRIVATE -Wl,--gc-sections,--strip-all)
endif()
