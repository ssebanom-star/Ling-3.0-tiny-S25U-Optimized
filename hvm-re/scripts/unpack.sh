#!/bin/bash
# APK -> work/apk_src (smali + res + lib). 수정 대상은 work/apk_src 아래.
source "$(dirname "$0")/env.sh"
$APKTOOL d -f -o "$HVM_RE/work/apk_src" "${1:-$HVM_RE/apk/original.apk}"
