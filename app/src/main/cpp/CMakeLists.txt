cmake_minimum_required(VERSION 3.22.1)

project("blacktube_llama")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Optimization flags for high-powered ARM mobile inference
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -flto -funroll-loops")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -mfpu=neon")
else()
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math")
endif()

# Add llama.cpp as a subdirectory
set(GGML_NATIVE OFF CACHE BOOL "Disable native optimizations for Android cross-compilation")
set(GGML_BLAS OFF CACHE BOOL "Disable BLAS for Android")
set(GGML_CUDA OFF CACHE BOOL "Disable CUDA for Android")
set(GGML_METAL OFF CACHE BOOL "Disable Metal for Android")
set(GGML_VULKAN OFF CACHE BOOL "Disable Vulkan for Android")
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build static libraries internally")

add_subdirectory(llama.cpp)

# Define native llama JNI library
add_library(
    blacktube_llama
    SHARED
    llama_jni.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    blacktube_llama
    ${log-lib}
    llama
)

target_include_directories(
    blacktube_llama
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
)
