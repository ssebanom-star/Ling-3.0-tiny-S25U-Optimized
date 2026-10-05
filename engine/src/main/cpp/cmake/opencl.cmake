# OpenCL 헤더 + ICD 로더를 내려받아 ggml-opencl 링크용으로만 사용한다.
# 런타임에는 기기의 /vendor/lib64/libOpenCL.so 가 쓰이며(AndroidManifest의 uses-native-library),
# 우리가 빌드한 libOpenCL.so 는 APK에서 제외한다(engine/build.gradle.kts packaging 참고).
include(FetchContent)

set(LING_OPENCL_TAG v2025.07.22)

FetchContent_Declare(opencl_headers
    GIT_REPOSITORY https://github.com/KhronosGroup/OpenCL-Headers.git
    GIT_TAG        ${LING_OPENCL_TAG}
    GIT_SHALLOW    TRUE)
FetchContent_Declare(opencl_icd_loader
    GIT_REPOSITORY https://github.com/KhronosGroup/OpenCL-ICD-Loader.git
    GIT_TAG        ${LING_OPENCL_TAG}
    GIT_SHALLOW    TRUE)

FetchContent_MakeAvailable(opencl_headers)

set(OPENCL_ICD_LOADER_HEADERS_DIR ${opencl_headers_SOURCE_DIR} CACHE PATH "" FORCE)
set(OPENCL_ICD_LOADER_BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(OPENCL_ICD_LOADER_BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(opencl_icd_loader)

# FindOpenCL 이 위 타깃을 쓰도록 캐시 변수를 미리 채운다(OpenCL_LIBRARY에 타깃 이름 지정)
set(OpenCL_INCLUDE_DIR ${opencl_headers_SOURCE_DIR} CACHE PATH "" FORCE)
set(OpenCL_LIBRARY     OpenCL                       CACHE STRING "" FORCE)
