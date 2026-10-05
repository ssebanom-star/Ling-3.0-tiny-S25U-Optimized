#!/usr/bin/env bash
# Phase 0: adb 로 연결된 S25U 에서 Ling-3.0-tiny 실측 스윕을 돌리고 결과를 bench/results/ 에 저장한다.
#
#   tools/phase0/bench_s25u.sh /path/Ling-3.0-tiny-Q4_0.gguf [/path/other-quant.gguf ...]
#
# 측정 항목 (docs/02-design.md §6, Phase 0)
#   1) CPU 스레드 스윕   : t=2,3,4,5,6,8 × pp128/pp512/tg128 (repack on)
#   2) repack on/off     : t=4
#   3) 가속기            : HTP0(NPU, 세션 2개 레이어 분할) / GPUOpenCL 전체 오프로드
#   4) 지속 부하(열)     : tg128 × 10회 반복, 회차별 tok/s
set -euo pipefail
[ $# -ge 1 ] || { echo "usage: $0 model.gguf [...]"; exit 1; }
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
PKG=$ROOT/build-phase0/pkg
DEV=/data/local/tmp/ling
OUT=$ROOT/bench/results
STAMP=$(date +%Y%m%d-%H%M%S)
mkdir -p "$OUT"
[ -x "$PKG/bin/llama-bench" ] || { echo "먼저 tools/phase0/build_bench_tools.sh 실행"; exit 1; }

adb shell mkdir -p $DEV/gguf
adb push --sync "$PKG/bin" "$PKG/lib" $DEV/ >/dev/null
for m in "$@"; do adb push --sync "$m" $DEV/gguf/ ; done

REPORT=$OUT/$STAMP.md
{
  echo "# S25U Phase 0 — $STAMP"
  echo
  echo '```'
  adb shell getprop ro.product.model; adb shell getprop ro.soc.model; adb shell getprop ro.build.version.release
  adb shell 'for c in /sys/devices/system/cpu/cpu[0-9]*; do echo "$(basename $c) $(cat $c/cpufreq/cpuinfo_max_freq)"; done'
  adb shell 'grep -m1 Features /proc/cpuinfo; grep MemTotal /proc/meminfo'
  echo '```'
} > "$REPORT"

run() { # $1=title, rest=llama-bench args
  local title=$1; shift
  echo "== $title"
  { echo; echo "## $title"; echo; } >> "$REPORT"
  # ggml 동적 백엔드는 실행 파일 디렉터리와 cwd 에서 탐색 → cwd 를 lib/ 로
  adb shell "cd $DEV/lib && LD_LIBRARY_PATH=$DEV/lib ADSP_LIBRARY_PATH=$DEV/lib GGML_HEXAGON_DEVICES=HTP0:0,HTP0:1 ../bin/llama-bench -o md $*" \
    | tee -a "$REPORT"
}

for m in "$@"; do
  name=$(basename "$m")
  M="-m $DEV/gguf/$name"
  run "$name CPU 스레드 스윕" "$M -p 128,512 -n 128 -t 2,3,4,5,6,8 -r 3 -dev none"
  run "$name repack off (t=4)" "$M -p 128,512 -n 128 -t 4 -r 3 -dev none --repack 0"
  run "$name Hexagon NPU" "$M -p 128,512 -n 128 -t 4 -r 3 -dev HTP0/HTP1 -ngl 99" || echo "(NPU 실패)" >> "$REPORT"
  run "$name Adreno OpenCL" "$M -p 128,512 -n 128 -t 4 -r 3 -dev GPUOpenCL -ngl 99" || echo "(GPU 실패)" >> "$REPORT"
done

m1=$(basename "$1")
{ echo; echo "## 지속 부하 (tg128 × 10, t=4, $m1)"; echo; } >> "$REPORT"
for i in $(seq 1 10); do
  t=$(adb shell "cd $DEV/lib && LD_LIBRARY_PATH=$DEV/lib ../bin/llama-bench -m $DEV/gguf/$m1 -p 0 -n 128 -t 4 -r 1 -dev none -o csv" | tail -1 | awk -F, '{print $(NF-1)}' | tr -d '"')
  temp=$(adb shell dumpsys thermalservice 2>/dev/null | grep -m1 -i "Thermal Status" || true)
  echo "run $i: $t tok/s  $temp" | tee -a "$REPORT"
done
echo "report → $REPORT"
