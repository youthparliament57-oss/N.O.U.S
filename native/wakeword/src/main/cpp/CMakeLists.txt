# =============================================================================
# NOUS — Wake Word Detection CMake build
#
# Builds:
#   - libjarvis_wakeword.so (JNI bridge — loaded at runtime via
#     System.loadLibrary("jarvis_wakeword"))
#
# The wake word model source is expected at src/main/cpp/wakeword-model/ (git
# submodule or vendor directory). If not present, this CMake produces a
# stub library for dev builds.
# =============================================================================

cmake_minimum_required(VERSION 3.22.1)

project(jarvis_wakeword LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# ─── Check if wake word model source exists ──────────────────────────────────
set(WW_MODEL_DIR "${CMAKE_CURRENT_SOURCE_DIR}/wakeword-model")

if(EXISTS "${WW_MODEL_DIR}/CMakeLists.txt" OR EXISTS "${WW_MODEL_DIR}/detect.cpp")
    set(WW_AVAILABLE TRUE)
else()
    # Dev/stub mode — produce minimal library that returns safe defaults
    message(STATUS "Wake word model source not found — building stub library")
    set(WW_AVAILABLE FALSE)
endif()

# ─── JNI bridge shared library ───────────────────────────────────────────────
add_library(jarvis_wakeword SHARED
    jarvis_wakeword.cpp
)

target_include_directories(jarvis_wakeword PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

if(WW_AVAILABLE)
    target_include_directories(jarvis_wakeword PRIVATE
        "${WW_MODEL_DIR}"
    )
    target_link_libraries(jarvis_wakeword PRIVATE
        wakeword_model
        log
        android
    )
else()
    # Stub mode — just link Android logging
    target_link_libraries(jarvis_wakeword PRIVATE
        log
        android
    )
    target_compile_definitions(jarvis_wakeword PRIVATE WAKEWORD_STUB_MODE=1)
endif()

# Strip symbols in release builds
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_link_options(jarvis_wakeword PRIVATE -Wl,--gc-sections,--strip-all)
endif()
