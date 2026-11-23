# source scripts/env.sh
export HVM_RE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export APKTOOL="java -jar $HVM_RE/tools/apktool.jar"
export SIGNER="java -jar $HVM_RE/tools/uber-apk-signer.jar"
export GHIDRA_HEADLESS="$(ls -d $HVM_RE/tools/gh/ghidra_*)/support/analyzeHeadless"
export JADX="$HVM_RE/tools/jadx/bin/jadx"
