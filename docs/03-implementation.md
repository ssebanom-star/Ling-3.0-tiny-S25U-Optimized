# 03. 구현 현황 · 검증 기록

작성일 2026-10-05. 표기: **[검증]** 이 저장소에서 실행해 확인, **[빌드만]** 컴파일/패키징까지 확인,
**[미검증]** 실기기(S25U) 필요.

---

## 1. Phase별 구현 상태

| Phase | 범위 | 상태 |
|---|---|---|
| 0 | 실기기 측정 | 스크립트 준비 완료(`tools/phase0/`), **측정은 미실행** — 기기 필요 |
| 1 MVP | Gradle, :engine JNI, 채팅 UI, 다운로드, 템플릿 빌더, 포그라운드 서비스 | 완료 [빌드만 / 엔진·JNI는 호스트 검증] |
| 2 캐시 최적화 | 증분 prefill, 턴 체크포인트, 편집/재생성, 성능 패널, 자동 튜닝 | 완료 [엔진 로직 호스트 검증, 튜닝은 미검증] |
| 3 안정화 | 열 거버너, ADPF, 메모리 정책, 대화 저장, 툴 호출 | 완료 [빌드만] |
| 4 가속기 | Hexagon 백엔드 동봉 + 정확성 검사, OpenCL | 완료 [빌드만 — NPU/GPU 동작·정확성 미검증] |

APK: `app-release.apk` ≈ 10.8 MB (arm64-v8a, CPU 변형 7종 + OpenCL + Hexagon v73~v81 skel).
Phase 0 도구(`tools/phase0/build_bench_tools.sh`): llama-bench/llama-completion + CPU·OpenCL·Hexagon 백엔드 빌드 [빌드만].

### 첫 실행 자동 설치 (2026-10-06 추가)

`SetupManager`: 기기 확인 → `SetupPlanner`(양자화 선택) → `ResumableDownloader`(이어받기·재시도·네트워크 대기)
→ SHA-256 → 설정 저장 → 로드 → 자동 튜닝 → `setupDone`. 앱/서비스 재시작 시 같은 지점부터 재개(멱등).

| 검증 | 결과 |
|---|---|
| `ResumableDownloaderTest` (로컬 HTTP 서버) | 302 리다이렉트 추적, 1 MiB 에서 끊김 → `Range: bytes=1048576-` 재개, 기존 `.part` 재개, 서버가 Range 무시 시 처음부터, SHA 불일치 시 삭제, 재시도 한도, 완성된 `.part` 는 검증만 — 7/7 [검증] |
| 실제 HF CDN | imatrix(44 MB, LFS): 10 MB `.part` → 서명 CDN 302 → 206 재개 → SHA-256 일치 [검증] |
| `SetupPlannerTest` | S25U → Q4_0/16K, 기존 파일 재사용, 받다 만 파일 이어받기, 공간 부족 시 하향, 저RAM → IQ4_XS — 6/6 [검증] |
| 앱 내 전체 흐름(4.6 GB) | [미검증 — 실기기] |

---

## 2. 호스트 검증 결과 (x86-64, 4 vCPU, 15 GB, 실모델 Q4_0)

> 수치는 개발용 x86 VM 기준이다. **S25U 성능 추정 근거로 쓰지 말 것.**

### 2.1 엔진 통합 테스트 — `tests/host/engine_test.cpp` [검증]

| 시나리오 | 결과 |
|---|---|
| 첫 sync (40 tok) | 40 토큰 prefill, 재귀 체크포인트 19.3 MiB 생성 |
| 재생성(같은 프롬프트) | 체크포인트 복원 + **1 토큰만 prefill** (50 ms), greedy 출력 동일 |
| 다음 턴 append | 이전 41 토큰 전부 재사용, 18 토큰만 prefill |
| 사용자 메시지 편집 | 턴 경계 체크포인트 복원, 18 토큰만 prefill (전체 재계산 없음) |
| 증분 vs 처음부터 | greedy 결과 일치 |
| thinking 모드 | `17*23` → 추론 후 `</think>391` 정상 |
| 취소 | 콜백 false 후 정확히 5토큰에서 정지, 컨텍스트와 출력 일치 |
| UTF-8 | 한글·이모지 분할 경계 보류 처리 |

### 2.2 JNI 계층 — `tools/run_host_jni_test.sh` [검증]

실제 Kotlin 컴파일 결과(`LingNative`, `NativeCallbacks`)를 호스트 JVM에서 로드해 전 JNI 함수를 호출.
로드 진행률 콜백, UTF-8(이모지) 왕복, sync 진행률 콜백, 스트리밍 텍스트 == 디토큰화 결과,
"한국의 수도" → 서울 응답, 2턴째 생성 토큰 재사용(32 토큰 재사용/18 prefill), 체크포인트 2개(38.5 MiB),
top-k log-prob, 벤치, cpumask 포함 스레드 변경 → **전 항목 통과**.
앱과 같은 동적 백엔드 구성(`-DLING_HOST_BACKEND_DL=ON`, CPU 변형 14종 중 점수 선택 + 미존재 백엔드 지연 로드 false)
에서도 동일하게 통과.

### 2.3 성능 관련 실측 (호스트)

| 변경 | 전 | 후 |
|---|---|---|
| 지속 스레드풀 항상 생성 | pp128 81.0 / tg32 20.4 tok/s | pp128 94.2 / tg32 22.8 tok/s |
| 로드 직후 워밍업 | 콜드 첫 prefill 31 tok = 22.3 s | 0.5 s |
| 참고: 상류 llama-bench (같은 커밋, repack 0) | tg32 19.0 tok/s | — |

### 2.4 앱 JVM 단위 테스트 [검증]

- `LingPromptBuilderTest`: chat_template.jinja (HF 환경: trim_blocks/lstrip_blocks/tojson) 골든 **13 케이스 바이트 일치**
  (thinking on/off, 시스템 플래그, 다중 턴 reasoning, 인라인 `<think>`, 중간 system, 툴 정의·호출·관찰 그룹)
- 세그먼트 분할 = 렌더 결과, rawTokens 경로에서 이전 턴 조각과 동일 분할
- ThinkParser(태그가 조각 경계에 걸린 경우), ToolCallParser 왕복, PyJson(Python json.dumps 형식), 수식 평가기

---

## 3. 발견한 문제와 처리

| # | 문제 | 처리 |
|---|---|---|
| 1 | x86 호스트에서 컨텍스트 생성 시 `GGML_ASSERT(*cur_backend_id != -1)` | 원인: AMX extra buffer 가 MLA `attn_k_b`(Q4_0, 3D)를 배치받았으나 3D matmul 미지원. 상류 llama-bench 도 동일 재현. `EngineParams.weight_repack` 추가, 호스트 테스트에서만 off. ARM repack 은 `ggml_n_dims==2` 만 받음 → 기기 영향 없을 것으로 [추정] |
| 2 | 동적 백엔드 .so 가 APK 에 빠짐 | ggml 이 MODULE 을 `bin/` 으로 출력 → CMake 에서 LIBRARY_OUTPUT_DIRECTORY 재지정 |
| 3 | release .so 미strip (libllama 34 MB) | app 모듈 `ndkVersion` 지정 → APK 27 MB → 8.6 MB |
| 4 | NPU prefill-only 불가 | 상류 `offload_op` NULL → 전체 오프로드로 변경 (02-design §4.3) |
| 5 | 생성 중 대화 전환 시 상태 덮어쓰기 / 연타 시 중복 턴 | 생성 중 전환 차단, 턴 시작 원자화 |
| 6 | 앱 시작 시 모든 백엔드 로드 → CPU 모드에서도 NPU 세션 생성 | CPU 변형 점수 선택만 로드, GPU/NPU 는 지연 로드 |
| 7 | AGP 가 Hexagon DSP skel(QDSP6 ELF)까지 strip | `keepDebugSymbols` 로 제외, APK 내 파일이 빌드 산출물과 바이트 동일함을 확인 |
| 8 | Phase 0 도구 실행 시 백엔드 미탐색 | ggml 은 실행 파일 디렉터리·cwd 만 탐색 → 스크립트에서 cwd 를 lib/ 로 |
| 9 | 실기기 첫 실행: `ACCESS_NETWORK_STATE` 미선언 → ConnectivityManager SecurityException 으로 다운로드 실패 | 권한 추가 + 상태 조회 실패 시 진행(0.2.1) |
| 10 | 실기기: GPU 기본값에서 입력을 무시하고 엉뚱한 내용 생성(첫 턴) | GPU(OpenCL) 수치 오류 의심 [미확정]. 가속기는 모델별 정확성 검사 통과 전엔 사용 안 함 — 미검증이면 로드 시 자동 검사, 실패 시 CPU 복귀(0.3.1) |

---

## 4. 실기기에서 확인해야 할 것 (체크리스트)

1. **기본 동작**: 모델 다운로드(4.6 GB, SHA-256 검증) → 대화 → 재생성/편집 시 하단 통계에 "캐시 N · 체크포인트" 표시 확인
2. **Phase 0 측정**: `tools/phase0/build_bench_tools.sh` → `tools/phase0/bench_s25u.sh model.gguf` → `bench/results/*.md`
   - CPU 스레드 2~8 스윕, repack on/off, NPU/GPU, 10회 지속 부하
3. **앱 자동 튜닝**: 성능 탭 → 자동 튜닝 → 결과 JSON(`Android/data/io.github.ssebanom.ling/files/bench/`)
4. **가속기**: 성능 탭 → NPU 검사 / GPU 검사 (정확성 기준 통과 여부, 실패 사유)
   - One UI 에서 `libcdsprpc.so` / `libOpenCL.so` 접근 허용 여부 [미검증]
   - Hexagon v79 에서 GATED_DELTA_NET 정확성 [미검증, v73 에서는 상류 버그 보고 있음]
5. **메모리**: 로드 후 PSS(≈ 5 GB 예상), 백그라운드 전환 시 LMK 여부
6. **열**: 10분 연속 생성 시 tok/s 하락폭, 열 거버너 개입 시점
7. **ADPF**: 힌트 on/off tok/s·전력 차이

측정 결과로 결정할 기본값: 기본 양자화(Q4_0 vs Q4_K_M), 디코드/prefill 스레드·cpumask, 기본 컨텍스트, KleidiAI 사용 여부.
