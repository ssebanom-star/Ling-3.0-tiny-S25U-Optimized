# hvm-re — HorizonVM APK 분석·수정 환경

원본 APK: `apk/original.apk` (sha256 aa7b8bf608f7d17cdfcd8ebddf721593589c52abf6d39622b83de75e2e94fd91, 패키지 io.horizonvm.app)

## 디렉터리
| 경로 | 내용 |
|---|---|
| `apk/original.apk` | 원본 |
| `work/apk_src/` | apktool 디코드 결과 — **여기를 수정** (smali, res, assets/profiles/*.toml, AndroidManifest.xml, lib/arm64-v8a/*.so) |
| `out/HorizonVM-patched.apk` | 빌드 산출물 (zipalign + 디버그키 서명) |
| `decomp/jadx/` | jadx Java 소스 (MainActivity, PadView, Native) — 읽기용 |
| `decomp/native/src/*.c` | Ghidra 디컴파일 (hvm::, Vulkan::, VideoCommon::, Tegra::, GPU::, VideoCore::, Core:: 4578개 함수, 클래스별 파일) |
| `decomp/native/function_index.tsv` | 전체 37k 함수 (주소·네임스페이스·이름·크기) |
| `ghidra/proj/` | Ghidra 프로젝트(분석 완료). GUI 로 열려면 hvm.gpr 열기 |
| `docs/` | NETWORK.md, GRAPHICS.md, env_vars.md (HVM_* 243개) |
| `scripts/` | 아래 참고 |
| `tools/` | apktool 2.10.0, uber-apk-signer 1.3.0, jadx 1.5.1, Ghidra 11.3.2 (git 제외) |

## 워크플로
```bash
source scripts/env.sh
scripts/unpack.sh                 # (이미 해둠) 원본 → work/apk_src, 처음부터 다시 하고 싶을 때
# 1) Java/UI 수정: work/apk_src/smali/io/horizonvm/app/*.smali  (jadx 소스 보고 smali 편집)
# 2) 프로파일/설정 수정: work/apk_src/assets/profiles/*.toml
# 3) 네이티브 수정:
python3 scripts/sopatch.py dis   work/apk_src/lib/arm64-v8a/libhvm_android.so 9e5250 12   # Ghidra 주소 기준
python3 scripts/sopatch.py asm   work/apk_src/lib/arm64-v8a/libhvm_android.so 9e4d5c "mov w26, #0x1010101"
python3 scripts/sopatch.py bytes|nop|ret0|ret1 ...
scripts/build.sh                  # → out/HorizonVM-patched.apk
adb install -r out/HorizonVM-patched.apk   # 서명키가 원본(HVMDEBUG)과 달라 기존 앱은 먼저 uninstall (앱 데이터 삭제됨)
```
- Turnip(커스텀 Vulkan 드라이버) 적용: docs/TURNIP.md  (scripts/build_turnip_zig.sh → apply_turnip.sh → build.sh)
- 함수 찾기: `scripts/fn.sh "UserNet.on_tcp"` , 소스 보기: `scripts/fn.sh -d 009e67b4`
- 내보내지 않은 함수(Global FUN_xxx 등): `scripts/ghidra_decomp.sh 00dd93ec`
- 네임스페이스 추가 내보내기: `HVM_OUT=... HVM_NS=Shader,Settings analyzeHeadless ... -postScript export_ns.py`
- 주의: sopatch 의 `dis` 출력에서 분기 목적지(b.eq #…)는 raw ELF VA(= Ghidra−0x100000)로 표시됨. 패치 주소 입력은 Ghidra 주소.
- 길이가 바뀌는 패치는 불가(in-place). 코드를 크게 바꾸려면 .so 의 빈 공간(NOP padding/기존 불필요 함수)에 후킹하거나, 소스가 있는 부분(smali)에서 해결할 것.
- 이 .so 는 Eden(GPL) 등을 포함하므로 수정본을 재배포하면 라이선스 의무가 생길 수 있음.
