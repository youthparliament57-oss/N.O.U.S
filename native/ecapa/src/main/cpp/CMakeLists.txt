# =============================================================================
# NOUS — ECAPA-TDNN Speaker Verification CMake build
#
# Builds:
#   - libjarvis_ecapa.so (JNI bridge — loaded at runtime via
#     System.loadLibrary("jarvis_ecapa"))
#
# The ECAPA-TDNN model source is expected at src/main/cpp/ecapa/ (git
# submodule or vendor directory). If not present, this CMake produces a
# stub library for dev builds.
# =============================================================================

cmake_minimum_required(VERSION 3.22.1)

project(jarvis_ecapa LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# ─── Check if ECAPA source exists ────────────────────────────────────────────
set(ECAPA_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/ecapa")

if(EXISTS "${ECAPA_SOURCE_DIR}/CMakeLists.txt" OR EXISTS "${ECAPA_SOURCE_DIR}/ecapa.cpp")
    # Full build with ECAPA-TDNN source
    set(ECAPA_AVAILABLE TRUE)
else()
    # Dev/stub mode — produce minimal library that returns safe defaults
    message(STATUS "ECAPA-TDNN source not found — building stub library")
    set(ECAPA_AVAILABLE FALSE)
endif()

# ─── JNI bridge shared library ───────────────────────────────────────────────
add_library(jarvis_ecapa SHARED
    jarvis_ecapa.cpp
)

target_include_directories(jarvis_ecapa PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

if(ECAPA_AVAILABLE)
    target_include_directories(jarvis_ecapa PRIVATE
        "${ECAPA_SOURCE_DIR}"
    )
    target_link_libraries(jarvis_ecapa PRIVATE
        ecapa_model
        log
        android
    )
else()
    # Stub mode — just link Android logging
    target_link_libraries(jarvis_ecapa PRIVATE
        log
        android
    )
    target_compile_definitions(jarvis_ecapa PRIVATE ECAPA_STUB_MODE=1)
endif()

# Strip symbols in release builds
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_link_options(jarvis_ecapa PRIVATE -Wl,--gc-sections,--strip-all)
endif()
