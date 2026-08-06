# NOUS — llama.cpp CMake build configuration (v2.1)
#
# In dev builds: pre-built .so files are used (this CMake produces stub).
# In CI/Release: this CMake compiles llama.cpp from source with Vulkan.
#
# Usage:
#   Dev:    ./gradlew assembleDebug (uses prebuilt .so or stub)
#   CI:     ./gradlew assembleRelease -PbuildFromSource (compiles from source)
#
# Builds:
#   - libjarvis_llm.so (JNI bridge — loaded at runtime via
#     System.loadLibrary("jarvis_llm"))
# =============================================================================

cmake_minimum_required(VERSION 3.22.1)

project(jarvis_llm LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# ─── llama.cpp source (git submodule) ─────────────────────────────
set(LLAMA_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp")

if(EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt" AND DEFINED ENV{BUILD_FROM_SOURCE})
    # Full build with llama.cpp source (CI/Release with -PbuildFromSource)
    message(STATUS "Building llama.cpp from source")
    set(LLAMA_AVAILABLE TRUE)
    
    # Configure llama.cpp options
    set(LLAMA_BUILD_TESTS        OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES     OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER       OFF CACHE BOOL "" FORCE)
    set(LLAMA_VULKAN             ON CACHE BOOL "" FORCE)
    
    add_subdirectory("${LLAMA_CPP_DIR}" llama_cpp_build)
else()
    # Dev/stub mode — produce minimal library that returns safe defaults
    if(EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
        message(STATUS "llama.cpp found but BUILD_FROM_SOURCE not set — building stub library")
    else()
        message(STATUS "llama.cpp submodule not found — building stub library")
    endif()
    set(LLAMA_AVAILABLE FALSE)
endif()

# ─── JNI bridge shared library ──────────────────────────────────────
add_library(jarvis_llm SHARED
    jarvis_llm.cpp
)

target_include_directories(jarvis_llm PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

if(LLAMA_AVAILABLE)
    target_include_directories(jarvis_llm PRIVATE
        "${LLAMA_CPP_DIR}"
        "${LLAMA_CPP_DIR}/common"
    )
    target_link_libraries(jarvis_llm PRIVATE
        llama
        llama_common
        log
        android
    )
    
    # Vulkan backend
    find_package(Vulkan REQUIRED)
    target_link_libraries(jarvis_llm PRIVATE Vulkan::Vulkan)
    target_compile_definitions(jarvis_llm PRIVATE GGML_USE_VULKAN=1)
else()
    # Stub mode — just link Android logging
    target_link_libraries(jarvis_llm PRIVATE
        log
        android
    )
    target_compile_definitions(jarvis_llm PRIVATE LLAMA_STUB_MODE=1)
endif()

# Strip symbols in release builds
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_link_options(jarvis_llm PRIVATE -Wl,--gc-sections,--strip-all)
endif()
