cmake_minimum_required(VERSION 3.22)
project(ling_engine C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

set(ROOT ${CMAKE_CURRENT_LIST_DIR}/../../../..)
set(LLAMA_DIR ${ROOT}/third_party/llama.cpp)

option(LING_OPENCL    "Adreno GPU(OpenCL) 백엔드 빌드"  ON)
option(LING_KLEIDIAI  "Arm KleidiAI CPU 커널"           OFF)

# ---------------------------------------------------------------------------
# llama.cpp / ggml 구성
#  - 백엔드는 동적 라이브러리(GGML_BACKEND_DL)로 빌드해 런타임에 nativeLibraryDir 에서 로드
#    → CPU 변형(armv8.x/i8mm/sve...) 중 기기에 맞는 것을 자동 선택(GGML_CPU_ALL_VARIANTS)
#    → Hexagon 백엔드 .so 를 별도 빌드해 jniLibs 에 넣으면 같은 방식으로 자동 인식
#  - OpenMP 끔: ggml 자체 스레드풀을 써야 cpumask/poll 제어가 적용된다
# ---------------------------------------------------------------------------
set(BUILD_SHARED_LIBS      ON  CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL        ON  CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS  ON  CACHE BOOL "" FORCE)
set(GGML_NATIVE            OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE         OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI      ${LING_KLEIDIAI} CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP        OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL          OFF CACHE BOOL "" FORCE)

if (LING_OPENCL AND ANDROID_ABI STREQUAL "arm64-v8a")
    include(cmake/opencl.cmake)
    set(GGML_OPENCL                      ON CACHE BOOL "" FORCE)
    set(GGML_OPENCL_EMBED_KERNELS        ON CACHE BOOL "" FORCE)
    set(GGML_OPENCL_USE_ADRENO_KERNELS   ON CACHE BOOL "" FORCE)
endif ()

# 고정 커밋 llama.cpp 에 로컬 패치 적용(멱등). 내용: patches/README.md
include(${ROOT}/patches/apply.cmake)

add_subdirectory(${LLAMA_DIR} build-llama)

# 동적 백엔드(MODULE)는 ggml 이 bin/ 으로 내보낸다 → AGP 가 패키징하는 라이브러리 출력 경로로 옮긴다
if (CMAKE_LIBRARY_OUTPUT_DIRECTORY)
    foreach (backend IN LISTS GGML_AVAILABLE_BACKENDS)
        if (TARGET ${backend})
            set_target_properties(${backend} PROPERTIES LIBRARY_OUTPUT_DIRECTORY ${CMAKE_LIBRARY_OUTPUT_DIRECTORY})
        endif ()
    endforeach ()
endif ()

add_library(ling_jni SHARED
    ling_jni.cpp
    ling_engine.cpp)

target_include_directories(ling_jni PRIVATE ${CMAKE_CURRENT_LIST_DIR})
target_compile_options(ling_jni PRIVATE -O3 -fvisibility=hidden)
target_link_libraries(ling_jni PRIVATE llama ggml ggml-base android log)
# 16KB 페이지 기기 호환
target_link_options(ling_jni PRIVATE "-Wl,-z,max-page-size=16384")
