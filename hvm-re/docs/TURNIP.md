# Turnip(사용자 Vulkan 드라이버) 로딩 설계

## 현황 (분석 결과)
- 앱 .so 에는 libadrenotools 중 `adrenotools_patch_bcn / get_bcn_type` 만 정적 링크되어 있고 **`adrenotools_open_libvulkan` 과 훅 라이브러리(libhook_impl 등)는 없음** → 지금은 커스텀 드라이버를 못 올림.
- Vulkan 로드 지점 (Ghidra 주소): `Global::FUN_008f12e8`(서피스 생성: dlopen("libvulkan.so")), `hvm::gfx::vk::Device::create` 00b11868(자체 렌더러), `Vulkan::OpenLibrary` 01180574 → `Common::DynamicLibrary::Open` 01180864(Eden). 모두 문자열 `"libvulkan.so"`(Ghidra 00595eb7)를 쓰고 dlsym 으로 `vkGetInstanceProcAddr`/`vkCreateInstance` 등만 가져옴.

## 방식 (코드 수정 최소)
1. `libhvmvk.so`(turnip/hvmvk_shim.cpp) = libadrenotools 정적 링크 shim. 로더 진입점 6개(vkGetInstanceProcAddr, vkGetDeviceProcAddr, vkCreateInstance, vkEnumerateInstance{ExtensionProperties,LayerProperties,Version})를 export 하고, 내부에서 `adrenotools_open_libvulkan(CUSTOM)` 으로 만든 핸들로 전달. 드라이버 미지정/실패 시 시스템 libvulkan 으로 폴백.
2. 앱의 `"libvulkan.so"` 문자열 → `"libhvmvk.so"` (같은 길이 내 in-place 패치, `scripts/apply_turnip.sh`). 호출 코드는 수정 불필요.
3. APK 에 5개 .so 추가: libhvmvk.so, libhook_impl.so, libmain_hook.so, libfile_redirect_hook.so, libgsl_alloc_hook.so (`scripts/build_turnip.sh` 로 NDK 빌드). 훅 경로는 shim 이 `dladdr` 로 자기 폴더(nativeLibraryDir) 사용 — APK 가 .so 를 추출 설치해야 함(현재 매니페스트는 기본값=추출 O).
4. 사용: 드라이버 zip(adrenotools 형식: meta.json + libvulkan_freedreno.so) 을 기기 `/data/data/io.horizonvm.app/files/drivers/turnip/` 에 풀고(앱 내부 저장소 필수; sdcard 불가), 앱 '추가 환경변수'에 `HVM_VK_DRIVER=turnip`. 

## 빌드 (NDK 없이, 이 컨테이너에서 성공)
- `scripts/build_turnip_zig.sh`: zig(clang 21, `-target aarch64-freestanding-none`)로 컴파일 + AOSP bionic 헤더(aosp-mirror/platform_bionic) + zig 번들 libc++ 헤더, 호스트 `ld.lld` 링크. libc++ 바이너리는 링크하지 않고 `string.cpp`만 직접 컴파일, operator new/guard 는 `turnip/stubs.cpp`. 의존 라이브러리는 스텁 .so 로 DT_NEEDED(libc/libdl/liblog)만 생성.
- 검증(정적): 5개 .so 모두 aarch64 ELF, 16KB 페이지 정렬, TEXTREL 없음, 미정의 심볼 전부 bionic `libc.map.txt`/`libdl.map.txt` 에 존재(+liblog `__android_log_print`).
- 산출: turnip/out/{libhvmvk,libhook_impl,libmain_hook,libfile_redirect_hook,libgsl_alloc_hook}.so → `scripts/apply_turnip.sh`(+ 번들 드라이버 turnip/driver/libvulkan_freedreno.so) → `scripts/build.sh` → out/HorizonVM-patched.apk (확인: 앱 .so 에서 "libvulkan.so" 1곳 → "libhvmvk.so", ".so.1" 은 그대로).

## 사용
앱 '추가 환경변수'에 (줄마다 K=V):
```
HVM_VK_DRIVER=bundled
HVM_EDEN_NO_BCN_PATCH=1
```
- `bundled` = APK 에 포함된 Turnip(StevenMXZ 27.0.0-R2 Oneui, Vulkan 1.4.367, minApi 28, A7xx) 사용. `HVM_VK_DRIVER=<폴더명>` 이면 files/drivers/<폴더명>/ 의 다른 드라이버.
- `HVM_EDEN_NO_BCN_PATCH=1`: 앱 내장 BCn 패치(시스템 Adreno 드라이버 메모리 패치)가 Turnip 에는 맞지 않을 수 있어 끔 (Turnip 은 BCn 을 자체 지원). 이 조합이 안전한지는 기기에서 확인 필요.
- 확인: `adb logcat -s HVMVK HorizonVM` 에서 `custom driver: ... ` / `libvulkan handle=... (custom requested)` 와 Vulkan 디바이스 이름/드라이버 버전 로그.

## 상태 / 미검증
- **런타임 테스트 안 함** (기기·에뮬레이터 없음). 실패 시 shim 은 시스템 libvulkan 으로 폴백하지만, 훅 로드 실패 시 "0 devices" 가능(libadrenotools 문서).
- shim/훅은 NDK 정식 빌드가 아닌 자체 툴체인 산출물: libc++ 가 `__ndk1` 이름공간 + 헤더 인라인 구현이라 시스템 libc++ 와 ABI 충돌은 없지만, 예외/RTTI 없음(-fno-exceptions) 상태.
- 서명키가 원본(HVMDEBUG)과 달라 기존 앱 삭제 후 설치(앱 데이터 삭제됨).
- Adreno 전용. Turnip 이 Eden 이 요구하는 확장을 지원하지 않으면 디바이스 생성 실패.
