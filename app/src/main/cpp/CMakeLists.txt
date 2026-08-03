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
)
