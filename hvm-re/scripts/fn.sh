#!/bin/bash
# 함수 찾기:  fn.sh <regex>          -> 주소/네임스페이스/이름/크기 목록
#             fn.sh -d <hexaddr>     -> 해당 함수 디컴파일 소스 출력 (decomp/native/src 에 있는 경우)
source "$(dirname "$0")/env.sh"
if [ "$1" = "-d" ]; then
  grep -l "^// ===== @0*${2#0x} " "$HVM_RE"/decomp/native/src/*.c | head -1 | while read f; do
    awk -v a="@0*${2#0x}" '$0 ~ "^// ===== "a" " {p=1; print; next} /^\/\/ ===== @/ {p=0} p' "$f"; done
else
  grep -i -E "$1" "$HVM_RE/decomp/native/function_index.tsv" | head -${N:-60}
fi
