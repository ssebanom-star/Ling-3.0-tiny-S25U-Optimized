#!/bin/bash
# turnip/out/*.so 를 work/apk_src/lib/arm64-v8a 에 넣고, libhvm_android.so 의 "libvulkan.so" 문자열을 "libhvmvk.so" 로 교체. 이후 scripts/build.sh
set -e
source "$(dirname "$0")/env.sh"
L="$HVM_RE/work/apk_src/lib/arm64-v8a"; O="$HVM_RE/turnip/out"
for f in libhvmvk.so libhook_impl.so libmain_hook.so libfile_redirect_hook.so libgsl_alloc_hook.so; do [ -f "$O/$f" ] || { echo "없음: $O/$f (scripts/build_turnip.sh 먼저)"; exit 1; }; cp -v "$O/$f" "$L/"; done
# 번들 드라이버(선택): turnip/driver/libvulkan_freedreno.so 가 있으면 APK 에 포함 (HVM_VK_DRIVER=bundled 로 사용)
[ -f "$HVM_RE/turnip/driver/libvulkan_freedreno.so" ] && cp -v "$HVM_RE/turnip/driver/libvulkan_freedreno.so" "$L/"
# "libvulkan.so\0"(13B) -> "libhvmvk.so\0\0"(13B). Ghidra 주소 595eb7 (이미 패치된 경우 건너뜀)
python3 - "$L/libhvm_android.so" <<'P'
import sys
p=sys.argv[1]; d=bytearray(open(p,"rb").read())
old=b"libvulkan.so\0"; new=b"libhvmvk.so\0\0"
i=d.find(old); assert d.count(old)<=1
if i<0: print("이미 패치됨 또는 문자열 없음"); sys.exit(0)
# 'libvulkan.so.1' 는 별도 문자열이라 영향 없음 (\0 로 끝나는 것만 매치)
d[i:i+13]=new; open(p,"wb").write(d); print("patched libvulkan.so ->libhvmvk.so @file",hex(i))
P
echo "다음: scripts/build.sh ; 앱 '추가 환경변수'에 HVM_VK_DRIVER=bundled (번들 드라이버) 또는 HVM_VK_DRIVER=<files/drivers 아래 폴더>"
