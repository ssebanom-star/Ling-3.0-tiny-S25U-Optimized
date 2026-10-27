#!/bin/sh
# JVM unit tests for the pure-logic classes (no emulator needed).
# usage: tools/run_tests.sh [ANDROID_JAR] [KOTLIN_STDLIB_JAR]
set -e
HERE=$(cd "$(dirname "$0")/.." && pwd)
ANDROID_JAR=${1:-${ANDROID_HOME:-$ANDROID_SDK_ROOT}/platforms/android-35/android.jar}
KSTD=${2:-$(find "$HOME/.gradle" -name 'kotlin-stdlib-2.1.0.jar' | head -1)}
OUT=$(mktemp -d)
CP="$HERE/tools/lib/json.jar:$KSTD:$ANDROID_JAR"  # real org.json must precede the android.jar stubs
SRC="$HERE/app/src/main/java"
for t in MatcherTest HumanTimingTest WrongAnswersTest PauseStateTest FailureLogTest; do
  javac -nowarn -d "$OUT" -cp "$CP" -sourcepath "$SRC" "$HERE/solver/src/$t.java"
  echo "== $t"; java -cp "$OUT:$CP" "net.classcardauto.mobile.$t" | tail -3
done
