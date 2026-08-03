cmake_minimum_required(VERSION 3.22.1)

project("blacktube_llama")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

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
