#!/usr/bin/env bash
# Phase 0: S25U 실측용 llama.cpp 도구(llama-bench, llama-completion)를 앱과 같은 커밋으로 빌드한다.
# Qualcomm snapdragon-toolchain 이미지(NDK r29 + OpenCL + Hexagon SDK)를 사용하므로 docker 만 있으면 된다.
#
#   tools/phase0/build_bench_tools.sh      → build-phase0/pkg/{bin,lib}
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
IMG=${HEXAGON_IMAGE:-ghcr.io/snapdragon-toolchain/arm64-android:v0.7}
BUILD=$ROOT/build-phase0
mkdir -p "$BUILD"

docker run --rm --platform linux/amd64 -u "$(id -u):$(id -g)" \
    -v "$ROOT/third_party/llama.cpp:/src:ro" -v "$BUILD:/build" "$IMG" bash -lc '
set -e
cmake -S /src -B /build/b -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-33 -DANDROID_STL=c++_shared \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=ON -DGGML_BACKEND_DL=ON -DGGML_CPU_ALL_VARIANTS=ON -DGGML_NATIVE=OFF \
  -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF \
  -DGGML_OPENCL=ON -DGGML_OPENCL_EMBED_KERNELS=ON -DGGML_OPENCL_USE_ADRENO_KERNELS=ON \
  -DGGML_HEXAGON=ON -DHEXAGON_SDK_ROOT=$HEXAGON_SDK_ROOT -DHEXAGON_TOOLS_ROOT=$HEXAGON_TOOLS_ROOT \
  -DPREBUILT_LIB_DIR=android_aarch64 \
  -DLLAMA_BUILD_COMMON=ON -DLLAMA_BUILD_TOOLS=ON -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF \
  -DLLAMA_BUILD_SERVER=OFF -DLLAMA_OPENSSL=OFF
cmake --build /build/b -j"$(nproc)" --target llama-bench llama-completion htp-v73 htp-v75 htp-v79 htp-v81
rm -rf /build/pkg && mkdir -p /build/pkg/bin /build/pkg/lib
cp /build/b/bin/llama-bench /build/b/bin/llama-completion /build/pkg/bin/
cp /build/b/bin/*.so /build/pkg/lib/
cp /build/b/ggml/src/ggml-hexagon/libggml-htp-v79.so /build/pkg/lib/
for v in v73 v75 v81; do cp "$(find /build/b -name libggml-htp-$v.so | head -1)" /build/pkg/lib/; done
# c++ 런타임 동봉
cp $ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so /build/pkg/lib/
'
echo "done → $BUILD/pkg"
ls "$BUILD/pkg/bin" | grep -E "llama-(bench|completion)"
