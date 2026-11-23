#!/bin/bash
# driverui/src/*.java -> driverui/out/classes2.dex  (javac + D8; android.jar = tools/android.jar)
set -e
source "$(dirname "$0")/env.sh"
cd "$HVM_RE/driverui"; rm -rf build out; mkdir -p build out
javac --release 8 -Xlint:-options -cp "$HVM_RE/tools/android.jar" -d build $(find src -name '*.java') 2>&1 | grep -v "^Picked" || true
java -cp "$HVM_RE/tools/jadx/lib/jadx-1.5.1-all.jar" com.android.tools.r8.D8 --release --min-api 30 --lib "$HVM_RE/tools/android.jar" --output out $(find build -name '*.class') 2>&1 | grep -v "^Picked" || true
mv out/classes.dex out/classes2.dex; ls -l out
