# 호스트(x86/arm64 리눅스·맥)에서 ling_engine 을 실제 모델로 검증하는 테스트 빌드
#   cmake -S tests/host -B build-host -DCMAKE_BUILD_TYPE=Release && cmake --build build-host -j
#   ./build-host/ling_engine_test /path/to/Ling-3.0-tiny-Q4_0.gguf
cmake_minimum_required(VERSION 3.22)
project(ling_host_test C CXX)

set(CMAKE_CXX_STANDARD 17)
set(ROOT ${CMAKE_CURRENT_LIST_DIR}/../..)

set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL        OFF CACHE BOOL "" FORCE)
add_subdirectory(${ROOT}/third_party/llama.cpp build-llama)

add_executable(ling_engine_test
    engine_test.cpp
    ${ROOT}/engine/src/main/cpp/ling_engine.cpp)
target_include_directories(ling_engine_test PRIVATE ${ROOT}/engine/src/main/cpp)
target_link_libraries(ling_engine_test PRIVATE llama ggml)

# ---- JNI 시그니처 검증용 호스트 공유 라이브러리 (tests/host/jni/JniSmoke.java 에서 로드) ----
find_package(JNI)
if (JNI_FOUND)
    add_library(ling_jni SHARED
        ${ROOT}/engine/src/main/cpp/ling_jni.cpp
        ${ROOT}/engine/src/main/cpp/ling_engine.cpp)
    target_include_directories(ling_jni PRIVATE ${ROOT}/engine/src/main/cpp ${CMAKE_CURRENT_LIST_DIR}/jni/stub ${JNI_INCLUDE_DIRS})
    target_link_libraries(ling_jni PRIVATE llama ggml)
endif ()
