#!/bin/bash
# work/apk_src -> out/HorizonVM-patched.apk (재빌드 + zipalign + 디버그키 서명)
set -e
source "$(dirname "$0")/env.sh"
cd "$HVM_RE"
rm -rf work/signed && mkdir -p work/signed out
$APKTOOL b -o work/unsigned.apk work/apk_src 2>&1 | grep -v "^Picked up"
$SIGNER -a work/unsigned.apk --allowResign --out work/signed 2>&1 | grep -E "VERIFY|SUCCESS|ERROR|Warn" || true
cp work/signed/*.apk out/HorizonVM-patched.apk
ls -l out/HorizonVM-patched.apk
