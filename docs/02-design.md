# 02. 설계 — Ling-3.0-tiny 전용 S25 Ultra 앱

근거는 [01-research.md](01-research.md). 이 문서의 수치 기본값 중 [추정]인 것은 Phase 0 실측 후 갱신한다.

---

## 1. 목표 / 비목표

**목표**
1. Galaxy S25 Ultra(12 GB)에서 Ling-3.0-tiny를 완전 오프라인으로 실행하는 안드로이드 채팅 앱.
2. 이 모델·이 기기에 특화된 최적화: KDA/MLA 하이브리드 캐시 특성, MoE 대역폭 특성, Oryon CPU·Hexagon NPU 활용.
3. 정량 목표(초기안, Phase 0 후 확정):
   - 디코드 ≥ 25 tok/s (CPU, Q4_0, 4K 컨텍스트)
   - 첫 토큰 지연(TTFT) ≤ 1.5 s (짧은 프롬프트, 캐시 히트 시 ≤ 0.3 s)
   - 앱 피크 메모리 ≤ 6 GB, 32K 컨텍스트 지원
   - 10분 연속 생성 시 속도 하락 ≤ 30%

**비목표**
- 범용 다중 모델 런처(다른 아키텍처 지원). Ling-3.0 계열 외 모델은 고려하지 않는다.
- 클라우드/원격 추론.
- 다른 기기 최적화(동작은 하되 튜닝 대상 아님).

---

## 2. 핵심 설계 결정

| ID | 결정 | 이유 |
|---|---|---|
| D1 | 추론 엔진 = **llama.cpp**, git submodule로 커밋 고정 | BailingMoeV3(KDA+MLA) 지원이 확인된 유일한 안드로이드 런타임 |
| D2 | 기본 경로 = **CPU**, NPU/GPU는 opt-in 실험 기능 | 가속기 경로의 KDA 정확성·앱 권한이 미검증(R1, R2) |
| D3 | 기본 양자화 = **Q4_0**, 품질 옵션 Q4_K_M, 저메모리 옵션 IQ4_XS | 디코드 상한 차이 ±5%, Q4_0만 CPU repack/OpenCL/Hexagon 모두 호환. 최종 기본값은 Phase 0 KLD·속도 실측으로 결정 |
| D4 | 컨텍스트 기본 16K (최대 64K, 설정 가능), 캐시 f16 | MLA 캐시 6.9 KB/토큰 → 16K = 108 MiB. 메모리보다 prefill 시간이 실질 제약 |
| D5 | **턴 경계 상태 체크포인트** | KDA 상태는 롤백 불가. 재귀 부분 스냅샷 ~20 MB로 편집/재생성 시 재-prefill 회피 |
| D6 | thinking 모드는 **대화 단위 고정**, 중간 전환 시 재-prefill 경고 | 템플릿상 모드 플래그가 시스템 프롬프트 맨 앞에 위치 |
| D7 | 샘플러 체인 `top_k(20) → top_p(0.95) → temp(1.0) → dist` | 권장값. 157K 어휘를 top-k로 먼저 줄여 정렬 비용 최소화 |
| D8 | 스레드 수·코어 배치는 **기기 자동 튜닝**으로 결정 | Oryon 2+6 구성에서 메모리 바운드 디코드 최적 스레드 수는 실측 필요 |
| D9 | 추론은 **포그라운드 서비스**에서 실행 | 화면 전환/백그라운드 시 LMK로 인한 4.6 GB 모델 재로드 방지 |
| D10 | 모델 파일은 앱 전용 외부 저장소에 다운로드(이어받기 + SHA-256 검증), SAF 가져오기 지원 | 권한 불필요, 4–5 GB 파일 안정 다운로드 |

---

## 3. 아키텍처

```
┌──────────────────────────── :app (Kotlin, Jetpack Compose) ────────────────────────────┐
│ ui/        ChatScreen · ConversationList · ModelManager · Settings · BenchmarkScreen     │
│ vm/        ChatViewModel · ModelViewModel · BenchViewModel                               │
│ domain/    ChatSession · LingPromptBuilder · ThinkParser · ToolCallParser               │
│ data/      Room(Conversation, Message, Checkpoint) · ModelRepository(Downloader)        │
│ service/   InferenceService(Foreground) · ThermalGovernor · DeviceProfiler              │
└──────────────────────────────────────┬─────────────────────────────────────────────────┘
                                       │ Kotlin API (suspend / Flow<TokenEvent>)
┌──────────────────────────────────────▼──────────── :engine (Android library) ──────────┐
│ LingEngine.kt   load / prefill / generate / cancel / saveCheckpoint / restore / bench   │
│ EngineConfig.kt threads, cpumask, backend, ctx, batch, cache type, sampler              │
│ jni/ling_jni.cpp  ── 얇은 C++ 래퍼 ──▶ llama.h (libllama + ggml-cpu [+ opencl/hexagon])│
└─────────────────────────────────────────────────────────────────────────────────────────┘
third_party/llama.cpp  (submodule, 고정 커밋)
```

### 3.1 모듈 책임

**:engine (C++/JNI)**
- 단일 `llama_model` + 단일 `llama_context`(seq 1개). 앱 수명 동안 모델 유지.
- 토큰 스트리밍: 네이티브 디코드 루프가 토큰 조각(UTF-8 경계 보정 후)을 콜백 → Kotlin `Flow`.
- 취소: atomic flag + `llama_set_abort_callback`.
- 체크포인트: `llama_state_seq_get_data_ext(..., LLAMA_STATE_SEQ_FLAGS_PARTIAL_ONLY)` 로 재귀 상태만 직렬화,
  MLA 캐시는 `llama_memory_seq_rm(seq, pos, -1)` 로 잘라냄. 재귀 메모리의 `seq_rm` 은 최근 수 토큰
  (`n_rs_seq`) 이내 롤백만 허용하므로 그 이상은 반드시 체크포인트 복원 경로를 탄다.
  참조 구현: llama-server의 context checkpoint(`tools/server/server-context.cpp`, `--ctx-checkpoints`).
- 성능 카운터: `llama_perf_context` + 자체 타이머(prefill/decode 분리, tok/s, 단계별 ms).
- 벤치 모드: 고정 프롬프트 pp/tg 측정, 스레드/백엔드 조합 스윕.

**:app**
- `LingPromptBuilder`: chat_template.jinja를 Kotlin으로 이식(Jinja 런타임 불필요). 단위 테스트로 원본 템플릿과
  출력 바이트 일치 검증(호스트에서 Python jinja2로 생성한 골든 파일 사용).
  - 대안: llama.cpp `common_chat_templates`(minja) 사용. 이식 실수 위험은 없지만 증분 프롬프트 생성이 어렵다.
    → 증분 append 방식이 필요하므로 자체 빌더 채택, 골든 테스트로 정합성 보장.
- `ThinkParser`: 스트림에서 `<think>…</think>` 분리 → UI에서 접이식 "추론" 블록.
- `ToolCallParser`: `<tool_call>` XML 파싱(Phase 3).
- `ThermalGovernor`: `PowerManager.getThermalHeadroom()` / `addThermalStatusListener` 로 스레드 수·생성 일시정지 조절.
- `DeviceProfiler`: `/sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq` 로 클러스터(prime/perf) 식별,
  `/proc/cpuinfo` feature, 가용 메모리(`ActivityManager.MemoryInfo`), 가속기 탐지 결과 기록.

---

## 4. 추론 파이프라인 상세

### 4.1 증분 프롬프트 + 상태 재사용 (가장 큰 체감 최적화)

Ling 템플릿은 `preserved_thinking=true` 라 이전 턴이 재작성되지 않는다 → 대화는 순수 append.

```
턴 n 시작 시 컨텍스트에 이미 있는 것:  [SYSTEM ... ][HUMAN u1][ASSISTANT a1] ... [ASSISTANT a(n-1)]
새로 prefill 할 것:                    <|role_end|>? + <role>HUMAN</role>u_n<|role_end|><role>ASSISTANT</role>\n<think>
```

- 엔진은 "현재 컨텍스트에 들어있는 토큰 시퀀스"를 보관. 새 프롬프트 토큰열과 **최장 공통 접두부**를 비교,
  차이분만 prefill.
- 접두부가 짧아지는 경우(편집/재생성/thinking 전환): 해당 위치 이하의 가장 가까운 **체크포인트**로 복원 후 나머지 prefill.
  체크포인트 없으면 전체 재-prefill.
- 체크포인트 정책: 각 사용자 턴 prefill 직전(= 직전 assistant 응답 종료 지점)마다 1개, 최근 N=8개 메모리 유지(~160 MB),
  나머지는 버림. 대화 전환 시 현재 대화 체크포인트를 앱 캐시 디렉토리에 저장(선택, Phase 3).
- 토큰화 경계 문제: 텍스트 단위로 이어붙이면 BPE 경계가 달라질 수 있음 → **턴 단위로 토큰화해서 이어붙임**
  (assistant 생성 토큰은 생성된 토큰 그대로 보관).

### 4.2 CPU 실행 구성 (기본 경로)

| 파라미터 | 초기값 | 근거/비고 |
|---|---|---|
| `n_threads` (decode) | 자동 튜닝 {2,4,6,8} 중 최고 | 메모리 바운드라 코어 전부가 최적이 아닐 수 있음 |
| `n_threads_batch` (prefill) | 자동 튜닝 {6,8} | 연산 바운드 → 많을수록 유리할 가능성 |
| cpumask | prime 2 + perf 일부 조합 튜닝 | `ggml_threadpool_params.cpumask` + `strict_cpu` |
| poll | 50 (기본) vs 0 비교 | 폴링은 지연↓ 전력↑ |
| `n_batch` / `n_ubatch` | 512 / 512 | prefill 처리량 |
| mmap | on | repack 후 원본 페이지는 회수 가능 |
| mlock | off | 4.6 GB 잠금은 시스템 압박 |
| flash_attn | auto | MLA 6층만 해당 |
| cache type | f16 (옵션 q8_0) | 캐시가 작아 양자화 이득 작음 |

빌드: `GGML_CPU_ALL_VARIANTS=ON` + `GGML_BACKEND_DL=ON` (런타임 최적 변형 선택, upstream llama.android 예제와 동일 방식),
`GGML_OPENMP=OFF`, `-O3`, LTO. 기기 전용 고정 빌드(armv8.6-a+i8mm)는 Phase 0 비교 후 결정.

### 4.3 가속기 경로 (실험, opt-in)

**Hexagon NPU — 전체 오프로드 (구현 시 변경)**
- 원래 계획은 "prefill 만 NPU, 디코드는 CPU" 였으나, 고정 커밋의 llama.cpp 에서 Hexagon/OpenCL 백엔드 모두
  `offload_op` 가 NULL 이라(가중치를 CPU 에 둔 채 큰 배치 연산만 디바이스로 보내는 경로가 없음) **불가**함을 소스로 확인.
  → 구현은 모든 레이어를 NPU 에 올리는 전체 오프로드. prefill-only 는 상류 지원 또는 자체 패치가 필요(후속 과제).
- 근거(원안): 디코드는 토큰당 0.8 GB 읽기의 메모리 바운드 → CPU와 NPU가 같은 DRAM을 공유하므로 이득 제한적 [추정].
  prefill은 연산 바운드 → NPU 이득 가능(선례 7.8×, 다른 모델).
- 구성: `GGML_HEXAGON_DEVICES=HTP0:0,HTP0:1` (세션 2개 레이어 분할, 세션당 3.5 GB VA 한계 회피), Q4_0.
- 안전장치: 활성화 시 **정확성 검사** — 고정 프롬프트의 마지막 위치 상위 20 log-prob 을 CPU 기준값(파일 보관)과 비교,
  top-1 일치·top-10 중 7개 이상 겹침·공통 top-5 평균 |Δlogp| < 0.5 미달 시 자동으로 CPU 복귀(R1 대응).
- 매니페스트: `<uses-native-library android:name="libcdsprpc.so" android:required="false"/>`,
  `ADSP_LIBRARY_PATH` = 앱 nativeLibraryDir.
- 빌드: `tools/build_hexagon_backend.sh` (Qualcomm snapdragon-toolchain 이미지, NDK r29 + Hexagon SDK 6.6) →
  `libggml-hexagon.so` + `libggml-htp-v73/v75/v79/v81.so` 를 APK 에 동봉. 이미지가 없으면 CPU+GPU 만 포함.

**Adreno GPU (OpenCL)**
- Q4_0 MoE 커널 지원 확인됨. 단, CPU repack 버퍼와 GPU 버퍼 이중 상주 위험 → 측정 후 판단.
- 우선순위는 NPU보다 낮음(Phase 4).

### 4.4 열·전력 관리

- `ThermalGovernor` 상태 머신:
  - headroom < 0.7 → 정상
  - 0.7–0.9 → decode 스레드 1단계 감소
  - ≥ 0.9 또는 THERMAL_STATUS_SEVERE → 토큰 간 짧은 sleep 삽입(목표 tok/s 캡)
- ADPF `PerformanceHintManager` 세션에 워커 스레드 TID 등록 + 토큰 단위 목표 시간 보고(Phase 3, 효과 실측 필요).
- 생성 중 화면 꺼짐 방지(`FLAG_KEEP_SCREEN_ON`), 배터리 저전력 모드 감지 시 스레드 상한 축소.

### 4.5 메모리 관리

- 모델 로드 전 가용 메모리 확인: `availMem` < 모델 크기 + 1.2 GB 이면 경고 및 더 작은 quant 권장.
- `onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)` 시 체크포인트 캐시부터 해제.
- 생성 중이 아닐 때 앱이 백그라운드로 가면 일정 시간(설정, 기본 10분) 후 모델 언로드.

---

## 5. UI 범위

- **채팅**: 스트리밍 출력, 추론(think) 블록 접기/펼치기, 정지/재생성/편집, 대화별 thinking 모드 표시.
- **대화 목록**: Room 저장, 검색, 삭제.
- **모델 관리**: quant 목록(크기·예상 메모리 표시), 다운로드(진행률/이어받기/검증), 파일 가져오기, 활성 모델 선택.
- **설정**: 시스템 프롬프트, 샘플링(기본값 = 권장값), 컨텍스트 길이, 백엔드(CPU/NPU 실험), 스레드(자동/수동).
- **성능 패널**: 실시간 tok/s, TTFT, prefill/decode 분리 시간, 메모리, 온도 상태. 벤치 실행 및 결과 내보내기(JSON).

---

## 6. 검증 계획

| 단계 | 내용 | 산출물 |
|---|---|---|
| 정합성 | `LingPromptBuilder` vs jinja2 골든(단일/다중 턴, thinking on/off, tool) | JVM 단위 테스트 |
| 정합성 | 체크포인트 복원 후 생성 == 처음부터 prefill 후 생성 (greedy, 동일 토큰) | 계측 테스트 |
| 품질 | quant별 KLD(Q8_0 기준) — 호스트 llama-perplexity로 측정 | 표 |
| 성능 | 기기 벤치: pp128/pp1024/tg128 × 스레드 × quant × 백엔드 | `bench/results/*.json` |
| 지속성 | 10분 연속 생성, tok/s·온도 로그 | 그래프 |
| 가속기 | NPU vs CPU logits 비교 | 자동 검사 로그 |

---

## 7. 단계별 계획

| Phase | 범위 | 완료 조건 |
|---|---|---|
| **0. 기반 측정** | llama.cpp 고정 커밋으로 Android CLI 빌드(CPU, +Hexagon), S25U에서 `llama-bench`/`llama-cli` 실측, `tools/` 스크립트 | 실측 표로 D3/D4/D8 기본값 확정 |
| **1. MVP** | Gradle 프로젝트, :engine JNI(로드/생성/취소), 채팅 UI, 모델 다운로드, 프롬프트 빌더+테스트, 포그라운드 서비스 | S25U에서 대화 가능, 디코드 목표 충족 |
| **2. 캐시 최적화** | 증분 prefill, 턴 체크포인트, 편집/재생성, 성능 패널, 자동 스레드 튜닝 | 후속 턴 TTFT ≤ 0.3 s |
| **3. 안정화** | ThermalGovernor, ADPF, 메모리 정책, 대화 저장/복원, 툴 호출 파서 | 10분 지속 테스트 통과 |
| **4. 가속기** | Hexagon prefill 플레이버 + 정확성 검사, OpenCL 실험 | 긴 프롬프트 prefill 개선 수치 확보 |

Phase 0은 실기기(S25U + adb)가 필요하다. 개발 환경(이 저장소의 클라우드 세션)에는 기기가 없으므로
**빌드 산출물과 측정 스크립트를 준비하고, 측정 실행은 사용자 기기에서 수행**하는 구조로 간다.

---

## 8. 저장소 구조

```
.
├── app/                         # Compose UI, 서비스, 저장소, 런타임 (io.github.ssebanom.ling)
│   ├── src/main/java/.../domain   LingPromptBuilder(템플릿 이식), ThinkParser, ToolCallParser, PyJson
│   ├── src/main/java/.../runtime  ChatController, InferenceManager, ThermalGovernor, PerfHints, DeviceProfiler
│   ├── src/main/java/.../data     ModelCatalog/ModelStore(다운로드·검증), ConversationStore(SQLite), Settings(DataStore)
│   ├── src/main/java/.../ui       Chat / Models / Perf / Settings 화면
│   └── src/test/                  템플릿 골든·파서·수식 단위 테스트
├── engine/                      # Android library: LingEngine(Kotlin) + JNI + ling_engine(C++)
│   └── src/main/cpp/              ling_engine.{h,cpp}, ling_jni.cpp, CMakeLists.txt, cmake/opencl.cmake
├── third_party/llama.cpp        # submodule, 806eee98 고정
├── tests/host/                  # 실모델 호스트 테스트(C++ 엔진, JNI)
├── tools/
│   ├── gguf_budget.py             GGUF 헤더 기반 메모리/대역폭 예산
│   ├── template/                  chat_template.jinja + 골든 생성기
│   ├── build_hexagon_backend.sh   NPU 백엔드 빌드(docker)
│   ├── run_host_jni_test.sh       JNI 실모델 검증
│   └── phase0/                    실기기 측정(build_bench_tools.sh, bench_s25u.sh)
├── .github/workflows/android.yml
└── docs/
```

## 9. 열린 질문 (사용자 결정 필요 시 기본값으로 진행)

| 질문 | 기본값 |
|---|---|
| S25U RAM 12 GB 모델 기준? (1TB = 16 GB) | 12 GB 기준으로 설계 |
| 기본 thinking 모드 | on (모델 기본값), 대화 생성 시 선택 |
| 앱 언어 | 한국어 UI + 영어 리소스 |
| 최소 SDK | 33 (upstream 예제와 동일). S25U 전용이므로 문제 없음 |
| 배포 | GitHub Release APK(사이드로드). 스토어 배포는 범위 외 |

---

## 10. 구현 중 변경·추가 사항 (2026-10-05)

| 항목 | 내용 | 근거 |
|---|---|---|
| NPU prefill-only → 전체 오프로드 | 상류 Hexagon/OpenCL `offload_op` 미구현 | §4.3 |
| 턴 경계 체크포인트를 sync 에서 자동 생성 | `PromptSegment.boundaryAfter` → prefill 도중 경계마다 스냅샷, 재생성 지점(last-1)은 항상 저장 | 호스트 테스트: 편집 시 전체 재계산 → 경계 복원 18토큰 |
| assistant 이력은 **생성 토큰 그대로** 재삽입 | 템플릿은 reasoning 의 개행을 strip 하므로 텍스트 재렌더링 시 토큰이 달라져 캐시가 끊김 | `ChatMessage.rawTokens` |
| 지속 스레드풀 항상 생성 | 미부착 시 ggml 이 compute 마다 임시 스레드 생성 | 호스트: pp128 81→94, tg32 20.4→22.8 tok/s |
| 로드 직후 워밍업 디코드 | 콜드 mmap 페이지 폴트를 로드 단계로 이동 | 호스트(JVM): 첫 prefill 31토큰 22 s → 0.5 s |
| x86 호스트에서 weight repack 끔 | AMX extra buffer 가 MLA `attn_k_b`(3D)를 받아놓고 연산은 거부 → 스케줄러 assert | ARM repack 은 2D 만 받으므로 해당 없음 [추정, 실기기 확인 필요] |
| 앱 툴 호출 | 계산기·현재 시각·기기 상태 3종, 최대 4라운드 | Ling-3.0 네이티브 툴 포맷 |
| 백엔드 지연 로드 | 시작 시 CPU 변형 중 최고 점수 1개만 로드, OpenCL/Hexagon 은 해당 모드 선택 시에만 로드 | Hexagon 은 등록 시점에 FastRPC 세션을 열어 CPU 모드에서도 자원 점유 |
