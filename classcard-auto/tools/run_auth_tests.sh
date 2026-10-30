#!/bin/sh
# Auth exchange tests (phone challenge + authenticator answer + verifier) and the QR round trip, on a plain JVM.
# usage: tools/run_auth_tests.sh [ANDROID_JAR]
set -e
HERE=$(cd "$(dirname "$0")/.." && pwd)
ANDROID_JAR=${1:-${ANDROID_HOME:-$ANDROID_SDK_ROOT}/platforms/android-35/android.jar}
KSTD=$(find "$HOME/.gradle" -name 'kotlin-stdlib-2.1.0.jar' | head -1)
ZXING=$(find "$HOME/.gradle" -name 'core-3.5.3.jar' | head -1)
ONNX=$(find "$HOME/.gradle" -name 'classes.jar' -path '*onnxruntime-android*' | head -1)
OUT=$(mktemp -d)
STUBS=$(mktemp -d)
mkdir -p "$STUBS/net/classcardauto/mobile"
cat > "$STUBS/net/classcardauto/mobile/BuildConfig.java" <<'JAVA'
package net.classcardauto.mobile;
public final class BuildConfig { public static final boolean AUTH_REQUIRED = true; }
JAVA
CP="$HERE/tools/lib/json.jar:$KSTD:$ZXING:$ONNX:$ANDROID_JAR"
for t in AuthFlowTest QrCodecTest; do
  javac -nowarn -d "$OUT" -cp "$CP" -sourcepath "$HERE/app/src/main/java:$HERE/authenticator/src/main/java:$STUBS" "$HERE/solver/src/$t.java" "$HERE/authenticator/src/main/java/net/classcardauto/auth/AuthMath.java" >/dev/null 2>&1 || true
  # javac complains about unrelated app classes pulled in through Certification (no R, no coroutines here); the classes under test are still produced
  [ -f "$OUT/net/classcardauto/mobile/$t.class" ] || { echo "could not compile $t"; exit 1; }
  echo "== $t"; java -cp "$OUT:$CP" "net.classcardauto.mobile.$t" | grep -v '^Picked' | tail -4
done
