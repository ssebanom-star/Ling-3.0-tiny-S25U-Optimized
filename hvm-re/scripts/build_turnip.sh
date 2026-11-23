#!/bin/bash
# libhvmvk.so + libadrenotools 훅 4종(libhook_impl/libmain_hook/libfile_redirect_hook/libgsl_alloc_hook) 빌드 → turnip/out/
# 필요: Android NDK (ANDROID_NDK_HOME), cmake, ninja 또는 make.  libadrenotools 소스: ADRENOTOOLS_DIR (기본 /home/user/bylaws/libadrenotools)
set -e
source "$(dirname "$0")/env.sh"
: "${ANDROID_NDK_HOME:?NDK 경로를 ANDROID_NDK_HOME 으로 지정}"
AT="${ADRENOTOOLS_DIR:-/home/user/bylaws/libadrenotools}"
B="$HVM_RE/turnip/build"; mkdir -p "$B" "$HVM_RE/turnip/out"
cmake -S "$HVM_RE/turnip" -B "$B" -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-30 -DCMAKE_BUILD_TYPE=Release -DADRENOTOOLS_DIR="$AT" -DBUILD_SHARED_LIBS=OFF
cmake --build "$B" -j"$(nproc)"
find "$B" -name 'libhvmvk.so' -o -name 'libhook_impl.so' -o -name 'libmain_hook.so' -o -name 'libfile_redirect_hook.so' -o -name 'libgsl_alloc_hook.so' | while read f; do cp -v "$f" "$HVM_RE/turnip/out/"; done
ls -l "$HVM_RE/turnip/out"
