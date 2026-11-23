# HorizonVM 그래픽 경로 분석

주소는 Ghidra 주소(ELF VA+0x100000). 소스: decomp/native/src/{hvm__gfx,hvm__eden__*,hvm__Gm20b*,Vulkan__*,VideoCommon__*,Tegra__*}.c

## 파이프라인 (확인된 범위)
1. 게스트 HOS 의 nvdrv/GPU 드라이버가 **Tegra X1 GM20B GPU 를 MMIO/FIFO 로 구동** → 에뮬레이트: `hvm::Gm20bFifo`(채널/푸시버퍼), `hvm::Gm20bBar1`(BAR1 MMU, `translate`), `hvm::Host1x`, `hvm::Maxwell3D/Fermi2D/MaxwellDma`(자체 엔진 구현, 기존 렌더러용).
2. `HVM_GPU=eden` 이면 `hvm::gfx::install_eden_renderer`(00b88ed4) 가 **Eden(yuzu 계열) video_core 통째 이식본**(`Tegra::Engines::*`, `VideoCommon::*`, `Vulkan::RasterizerVulkan`, shader_recompiler/`Shader::`, `Sirit`)을 연결. `hvm::eden::Bridge::create`(0107f640)가 Eden `Settings` 를 채우고 환경변수를 읽음 (렌더 스케일 75%/원본, 비동기 셰이더, 펜스 방식 `HVM_EDEN_FENCE=immediate`, 디스크 캐시 `HVM_EDEN_NO_DISK_CACHE`, 셰이더 캐시 폴더 `HVM_SHADER_DIR`=<config폴더>/eden_shader).
   - `Gm20bFifo::eden_prepare/eden_call/eden_rec/run_channel` 가 게스트 푸시버퍼를 Eden 엔진에 흘려보냄.
   - 기본(미지정)은 자체 Vulkan 렌더러: `hvm::gfx::vk::Device`, `cmdrec`(커맨드 기록 스레드/링), `Presenter`.
3. 표시: 게스트 디스플레이 컨트롤러 `hvm::T210Dc`(scanout/vblank, `HVM_VBLANK_*`) → Eden 경로는 `hvm::eden::EdenPresenter`(Vulkan 스왑체인, `HVM_EDEN_PRESENT_FIFO`, `HVM_EDEN_PRESENT_ALL`), `HVM_EDEN_OUTPUT_SCALE=1080p` 이면 **Vulkan::SGSR**(Snapdragon Game Super Resolution) 로 확대(`prepare_upscale` 012c6ec8, `SGSR::Draw`). 1080p 선택 시 Java 쪽이 SurfaceHolder 를 1920x1080 고정 (`MainActivity.applyEnv`).
   - CPU 합성 표시 경로(`HVM_PRESENT=cpu`), 소프트 렌더러(`HVM_SOFTGPU`), SDL 프레임싱크(PC 빌드용 코드가 같이 들어 있음)도 존재.

## Adreno 관련
- `Vulkan::Device` 가 Adreno 감지 시 `adrenotools_get_bcn_type / adrenotools_patch_bcn` 로 **드라이버 메모리를 패치해 BCn 텍스처 지원**(Android API ≥ 28 에서만; `HVM_EDEN_NO_BCN_PATCH` 로 끔). 드라이버 코드 패치라 불안정 시 크래시 가능(로그: "BCn patch failed! Driver code may now crash").
- ASTC 는 네이티브 사용(`HVM_NO_ASTC` 로 끔), "Adreno rejected compute shader" 로그 존재, `HVM_FBL_GENERAL`(RT GENERAL 레이아웃 직접 샘플링; Adreno 기본 켬), `HVM_I64`(int64 셰이더; Adreno 링크 실패 위험 → 기본 꺼짐).
- Vulkan 파이프라인 캐시 디스크 저장/삭제 로직 포함 (`HVM_VK_NOPCACHE`).

## 주요 함수 주소
| 함수 | 주소 |
|---|---|
| gfx::install_renderer / install_eden_renderer / eden_requested | 00b7b9f8 / 00b88ed4 / 00b88e94 |
| eden::Bridge::create | 0107f640 |
| EdenPresenter::{ctor, create_swapchain, prepare_upscale, record_layers, draw, present} | 012c490c, 012c5eac, 012c6ec8, 012c749c, 012c789c, 012c7bdc |
| gfx::create_vk_renderer | 00a6b34c |

환경변수 전체 표: docs/env_vars.md
