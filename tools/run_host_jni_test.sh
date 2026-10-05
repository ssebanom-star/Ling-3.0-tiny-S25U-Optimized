#!/usr/bin/env bash
# 호스트에서 JNI 계층을 실모델로 검증한다.
#   tools/run_host_jni_test.sh /path/to/Ling-3.0-tiny-Q4_0.gguf [build-dir]
set -euo pipefail
MODEL=${1:?model.gguf}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
BUILD=${2:-$ROOT/build-host}

cmake -S "$ROOT/tests/host" -B "$BUILD" -DCMAKE_BUILD_TYPE=Release -G Ninja >/dev/null
cmake --build "$BUILD" -j"$(nproc)" --target ling_jni ling_engine_test

# Kotlin 컴파일 결과(LingNative, NativeCallbacks) 확보
(cd "$ROOT" && ./gradlew -q :engine:compileReleaseKotlin)
KCLASSES=$(find "$ROOT/engine/build" -path "*kotlin-classes/release" -type d | head -1)
STDLIB=$(find ~/.gradle/caches -name "kotlin-stdlib-2*.jar" ! -name "*sources*" | head -1)
CP="$KCLASSES:$STDLIB"

OUT="$BUILD/jni-classes"
mkdir -p "$OUT"
javac -cp "$CP" -d "$OUT" "$ROOT/tests/host/jni/JniSmoke.java"
LIBDIR=$(dirname "$(find "$BUILD" -name 'libling_jni.so' | head -1)")
java -Djava.library.path="$LIBDIR:$BUILD/bin" -cp "$OUT:$CP" JniSmoke "$MODEL"
