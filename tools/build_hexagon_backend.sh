#!/usr/bin/env bash
# Hexagon NPU 백엔드를 Qualcomm snapdragon-toolchain 도커 이미지(Android NDK r29 + Hexagon SDK 6.6)로 빌드해
# engine/src/main/hexagonLibs/arm64-v8a/ 에 넣는다. 이후 Gradle 빌드가 APK 에 자동 포함하고,
# 앱은 런타임에 nativeLibraryDir 에서 libggml-hexagon.so 를 동적 로드한다(GGML_BACKEND_DL).
#
#   tools/build_hexagon_backend.sh            # 빌드 + 복사
#   ./gradlew :app:assembleRelease            # NPU 포함 APK
#
# 주의: 앱의 libggml-base.so 와 같은 llama.cpp 커밋(third_party/llama.cpp)으로 빌드해야 ABI 가 맞는다.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
IMG=${HEXAGON_IMAGE:-ghcr.io/snapdragon-toolchain/arm64-android:v0.7}
BUILD=$ROOT/build-hexagon
OUT=$ROOT/engine/src/main/hexagonLibs/arm64-v8a
mkdir -p "$BUILD" "$OUT"

docker run --rm --platform linux/amd64 -u "$(id -u):$(id -g)" \
    -v "$ROOT/third_party/llama.cpp:/src:ro" -v "$BUILD:/build" "$IMG" bash -lc '
set -e
cmake -S /src -B /build -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-33 -DANDROID_STL=c++_shared \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_FLAGS="-march=armv8.7a+fp16+dotprod+i8mm -D_GNU_SOURCE" \
  -DCMAKE_CXX_FLAGS="-march=armv8.7a+fp16+dotprod+i8mm -D_GNU_SOURCE" \
  -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384" \
  -DCMAKE_MODULE_LINKER_FLAGS="-Wl,-z,max-page-size=16384" \
  -DBUILD_SHARED_LIBS=ON -DGGML_BACKEND_DL=ON -DGGML_CPU_ALL_VARIANTS=ON -DGGML_NATIVE=OFF \
  -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF -DGGML_OPENCL=OFF \
  -DGGML_HEXAGON=ON -DHEXAGON_SDK_ROOT=$HEXAGON_SDK_ROOT -DHEXAGON_TOOLS_ROOT=$HEXAGON_TOOLS_ROOT \
  -DPREBUILT_LIB_DIR=android_aarch64 \
  -DLLAMA_BUILD_COMMON=OFF -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_TOOLS=OFF \
  -DLLAMA_BUILD_SERVER=OFF -DLLAMA_OPENSSL=OFF
cmake --build /build --target ggml-hexagon htp-v73 htp-v75 htp-v79 htp-v81 -j"$(nproc)"
'

cp -v "$(find "$BUILD" -name libggml-hexagon.so | head -1)" "$OUT/"
for v in v73 v75 v79 v81; do
    cp -v "$(find "$BUILD" -name "libggml-htp-$v.so" | head -1)" "$OUT/"
done
echo "done → $OUT"
