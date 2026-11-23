# Turnip(사용자 Vulkan 드라이버) 로딩 설계

## 현황 (분석 결과)
- 앱 .so 에는 libadrenotools 중 `adrenotools_patch_bcn / get_bcn_type` 만 정적 링크되어 있고 **`adrenotools_open_libvulkan` 과 훅 라이브러리(libhook_impl 등)는 없음** → 지금은 커스텀 드라이버를 못 올림.
- Vulkan 로드 지점 (Ghidra 주소): `Global::FUN_008f12e8`(서피스 생성: dlopen("libvulkan.so")), `hvm::gfx::vk::Device::create` 00b11868(자체 렌더러), `Vulkan::OpenLibrary` 01180574 → `Common::DynamicLibrary::Open` 01180864(Eden). 모두 문자열 `"libvulkan.so"`(Ghidra 00595eb7)를 쓰고 dlsym 으로 `vkGetInstanceProcAddr`/`vkCreateInstance` 등만 가져옴.

## 방식 (코드 수정 최소)
1. `libhvmvk.so`(turnip/hvmvk_shim.cpp) = libadrenotools 정적 링크 shim. 로더 진입점 6개(vkGetInstanceProcAddr, vkGetDeviceProcAddr, vkCreateInstance, vkEnumerateInstance{ExtensionProperties,LayerProperties,Version})를 export 하고, 내부에서 `adrenotools_open_libvulkan(CUSTOM)` 으로 만든 핸들로 전달. 드라이버 미지정/실패 시 시스템 libvulkan 으로 폴백.
2. 앱의 `"libvulkan.so"` 문자열 → `"libhvmvk.so"` (같은 길이 내 in-place 패치, `scripts/apply_turnip.sh`). 호출 코드는 수정 불필요.
3. APK 에 5개 .so 추가: libhvmvk.so, libhook_impl.so, libmain_hook.so, libfile_redirect_hook.so, libgsl_alloc_hook.so (`scripts/build_turnip.sh` 로 NDK 빌드). 훅 경로는 shim 이 `dladdr` 로 자기 폴더(nativeLibraryDir) 사용 — APK 가 .so 를 추출 설치해야 함(현재 매니페스트는 기본값=추출 O).
4. 사용: 드라이버 zip(adrenotools 형식: meta.json + libvulkan_freedreno.so) 을 기기 `/data/data/io.horizonvm.app/files/drivers/turnip/` 에 풀고(앱 내부 저장소 필수; sdcard 불가), 앱 '추가 환경변수'에 `HVM_VK_DRIVER=turnip`. 

## 상태
- 작성됨: shim 소스, CMake, build/apply 스크립트, 문자열 패치(복사본 검증 전 단계).
- **미완료/미검증**: 이 컨테이너에는 Android NDK 가 없고(dl.google.com 차단) 대체 툴체인(PyPI ziglang) 실행은 권한 거부되어 **빌드·기기 테스트를 하지 못함**. shim 코드는 컴파일 확인 전.
- 위험: Adreno 전용(Snapdragon). Turnip 은 Mesa 버전에 따라 `VK_KHR_*`/BCn 지원이 달라 Eden 이 요구하는 확장이 빠지면 디바이스 생성 실패 → 폴백 없음(실패 로그 `HVMVK`/`HorizonVM` logcat 확인). 앱 내 `adrenotools_patch_bcn` 은 *시스템* 드라이버 대상 로직이라 Turnip 에서는 불필요/무해 여부 미확인.
