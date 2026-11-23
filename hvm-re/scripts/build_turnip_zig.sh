#!/bin/bash
# NDK 없이 빌드: zig(clang 21)로 컴파일(-target aarch64-freestanding + bionic 헤더) + 호스트 ld.lld 로 링크.
# 준비물: /tmp/pp/zig_x/ziglang/zig (pip download ziglang 후 unzip), AOSP bionic 헤더(/home/user/aosp-mirror/platform_bionic),
#         libadrenotools(/home/user/bylaws/libadrenotools, --recurse-submodules)
set -e
source "$(dirname "$0")/env.sh"
Z=${ZIG:-/tmp/pp/zig_x/ziglang/zig}; BION=${BIONIC:-/home/user/aosp-mirror/platform_bionic/libc}; AT=${ADRENOTOOLS_DIR:-/home/user/bylaws/libadrenotools}
T="$HVM_RE/turnip"; O="$T/build"; OUT="$T/out"; mkdir -p "$O" "$OUT" "$O/stubs"
ZL="$(dirname $Z)/lib/libcxx/include"
DEFS="-D_LIBCPP_STDDEF_H -include $T/sysroot/hvm_pre.h -D__ANDROID__ -D__BIONIC__ -D__linux__ -Dlinux=1 -D__ANDROID_API__=30 -D__ANDROID_MIN_SDK_VERSION__=30 -D_LIBCPP_DISABLE_EXTERN_TEMPLATE -D_LIBCPP_ABI_NAMESPACE=__ndk1 -D_LIBCPP_HARDENING_MODE_DEFAULT=_LIBCPP_HARDENING_MODE_NONE -D_LIBCPP_ASSERTION_SEMANTIC_DEFAULT=_LIBCPP_ASSERTION_SEMANTIC_IGNORE"
INC="-nostdinc -isystem $ZL -isystem $T/sysroot -isystem $BION/include -isystem $BION/kernel/uapi -isystem $BION/kernel/uapi/asm-arm64 -isystem $BION/kernel/android/uapi -I$AT/include -I$AT/src -I$AT -I$AT/lib/linkernsbypass"
CF="-target aarch64-freestanding-none -fPIC -O2 -fvisibility=hidden -fno-stack-protector -fno-exceptions -fno-rtti -Wno-#warnings -w $DEFS $INC"
cc()  { $Z cc  $CF -std=gnu17 -c "$1" -o "$2"; }
cxx() { $Z c++ $CF -std=c++20 -c "$1" -o "$2"; }
cxx "$T/hvmvk_shim.cpp" "$O/shim.o"
cxx "$T/stubs.cpp" "$O/stubs.o"
# libc++ 바이너리 대신 string.cpp 만 직접 컴파일 (std::string 의 out-of-line 멤버 제공)
$Z c++ $CF -std=c++20 -D_LIBCPP_PSTL_BACKEND_SERIAL=1 -D_LIBCPP_BUILDING_LIBRARY -D_LIBCPP_HAS_LOCALIZATION=0 -I"$(dirname $Z)/lib/libcxx/src" -c "$(dirname $Z)/lib/libcxx/src/string.cpp" -o "$O/string.o"
cxx "$AT/src/driver.cpp" "$O/driver.o"
cxx "$AT/lib/linkernsbypass/android_linker_ns.cpp" "$O/ans.o"
cxx "$AT/lib/linkernsbypass/elf_soname_patcher.cpp" "$O/esp.o"
cxx "$AT/src/hook/hook_impl.cpp" "$O/hook_impl.o"
for h in main_hook file_redirect_hook gsl_alloc_hook; do cc "$AT/src/hook/$h.c" "$O/$h.o"; done
# 스텁 공유 라이브러리(링크 시 DT_NEEDED 생성용)
echo 'void __hvm_stub(void){}' > "$O/stub.c"; $Z cc -target aarch64-freestanding-none -fPIC -c "$O/stub.c" -o "$O/stub.o"
for l in c dl m log; do ld.lld -shared -soname lib$l.so -o "$O/stubs/lib$l.so" "$O/stub.o"; done
LD="ld.lld -shared -z noexecstack -z max-page-size=16384 -z common-page-size=16384 -z relro -z now --unresolved-symbols=ignore-all --hash-style=both -L$O/stubs --no-as-needed"
$LD -soname libhook_impl.so -o "$OUT/libhook_impl.so" "$O/hook_impl.o" "$O/ans.o" "$O/esp.o" "$O/stubs.o" "$O/string.o" -llog -ldl -lc
for h in main_hook file_redirect_hook gsl_alloc_hook; do
  $LD -z global -soname lib$h.so -o "$OUT/lib$h.so" "$O/$h.o" -L"$OUT" -l:libhook_impl.so -ldl -lc
done
$LD -soname libhvmvk.so -o "$OUT/libhvmvk.so" "$O/shim.o" "$O/driver.o" "$O/ans.o" "$O/esp.o" "$O/stubs.o" "$O/string.o" -llog -ldl -lc
ls -l "$OUT"
