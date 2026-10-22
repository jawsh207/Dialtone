# Native speech-to-text for voicemail transcription.
# transcribe.cpp (MIT, vendors ggml) runs the Moonshine model on the CPU.
cmake_minimum_required(VERSION 3.22.1)
project(dialtone_transcriber C CXX)

# The engine is built as static libraries linked into one JNI .so, so everything
# must be position-independent.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# --- transcribe.cpp: library only, CPU only, statically linked ---------------
set(TRANSCRIBE_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_BUILD_TOOLS     OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_BUILD_SHARED    OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_METAL           OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_VULKAN          OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_CUDA            OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_HIP             OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_USE_OPENMP      OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_USE_SYSTEM_BLAS OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_INSTALL         OFF CACHE BOOL "" FORCE)

if(ANDROID AND CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    # Cross-compiling: don't probe the build machine's CPU. Target ARMv8.2 with
    # dot-product and fp16, which every Pixel GrapheneOS supports (Pixel 6 and newer).
    set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
endif()

add_subdirectory(transcribe.cpp EXCLUDE_FROM_ALL)
# transcribe.cpp 0.4.1 points its public include dir at ${CMAKE_SOURCE_DIR}/include, which
# only works when it is the top-level project. Add the real path when embedding it.
target_include_directories(transcribe PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/transcribe.cpp/include)

# --- JNI bridge ----------------------------------------------------------------
add_library(dialtone_transcriber SHARED transcriber_jni.cpp)
target_link_libraries(dialtone_transcriber PRIVATE transcribe)
if(ANDROID)
    target_link_libraries(dialtone_transcriber PRIVATE log)
    # 16 KB page alignment (required for new apps on Android 15+ devices).
    target_link_options(dialtone_transcriber PRIVATE "-Wl,-z,max-page-size=16384")
endif()
