# 01. 사전조사 — Ling-3.0-tiny on Galaxy S25 Ultra

조사일: 2026-10-05. 표기 규칙: **[확인]** = 1차 자료(모델 카드, config, 소스코드, GGUF 헤더)로 직접 확인,
**[추정]** = 계산/유추, **[미확인]** = 실기기 측정 또는 추가 확인 필요.

---

## 1. 모델: inclusionAI/Ling-3.0-tiny

### 1.1 기본 사양 [확인: HF 모델 카드, config.json, GGUF 헤더]

| 항목 | 값 |
|---|---|
| 공개 | 2026-08-06, MIT 라이선스 |
| 파라미터 | 총 7.9B / 토큰당 활성 1.3B |
| 아키텍처 | `BailingMoeV3ForCausalLM` (GGUF arch: `bailingmoe3`) |
| 레이어 | 24 = 4-레이어 블록 × 6, 블록마다 KDA 3 + MLA 1 → **KDA 18층, MLA 6층** |
| hidden | 1536, head 16 × 128 |
| MoE | routed 128개 중 top-8 + shared 1, expert FFN 512, 첫 1층은 dense(FFN 4608) |
| 라우팅 | sigmoid score, `noaux_tc`, group 8 중 4 선택, routed_scaling 2.5 |
| MLA | q_lora 256, kv_lora 512, rope dim 64 (partial rotary 0.5), rope_theta 6e6 |
| KDA | head_dim 128, short conv kernel 4, safe gate, gate lower bound −5 |
| vocab | 157,184 |
| 컨텍스트 | native 131,072, YaRN ×2 로 262,144 |
| MTP | `num_nextn_predict_layers = 0` → **tiny에는 MTP 헤드 없음** (bartowski GGUF도 "Speculative decoding: no") |
| 권장 샘플링 | temperature 1.0, top_p 0.95, top_k 20 (generation_config.json) |

### 1.2 채팅 템플릿 특성 [확인: chat_template.jinja]

```
<role>SYSTEM</role>{system}\ndetailed thinking on<|role_end|><role>HUMAN</role>{user}<|role_end|><role>ASSISTANT</role>\n<think>
```

- thinking on/off 가 **시스템 프롬프트 첫 줄(대화 맨 앞)** 에 들어간다. → 대화 도중 모드를 바꾸면
  프롬프트 접두부가 바뀌어 **캐시 전체 무효화(전체 재-prefill)**. 설계에 직접 영향.
- off 모드는 생성 프롬프트가 `\n<think></think>` 로 끝난다.
- `preserved_thinking = true` : 이전 턴의 reasoning을 지우지 않고 그대로 재삽입 →
  **히스토리가 append-only** 로 유지됨. 재귀(recurrent) 상태를 이어서 쓰기에 유리하지만 컨텍스트 소모는 큼.
- 툴 호출 포맷: `<tool_call>name\n<arg_key>k</arg_key>\n<arg_value>v</arg_value>...\n</tool_call>`,
  결과는 `<role>OBSERVATION</role>\n<tool_response>...</tool_response>`.

### 1.3 공식 검증 환경 [확인: 모델 카드]

DGX Spark, Apple Silicon(M4 Pro), Ollama(MLX, Apple 전용 PR). FP8 기준 M4 Pro 86–90 tok/s, 8K 컨텍스트에서
피크 8.34 GiB. **모바일/안드로이드 공식 검증 및 공개 벤치마크는 찾지 못함 [미확인]**.

---

## 2. 타깃 하드웨어: Galaxy S25 Ultra

| 항목 | 값 | 근거 |
|---|---|---|
| SoC | Snapdragon 8 Elite for Galaxy (SM8750-AC), 3nm | [확인] GSMArena 등 |
| CPU | Oryon v2: 2 × 4.47 GHz (prime) + 6 × 3.53 GHz (performance), little 코어 없음 | [확인] |
| GPU | Adreno 830 | [확인] |
| NPU | Hexagon, HTP arch **v79** | [추정] llama.cpp 문서의 8 Elite 실행 로그가 v79. 런타임에서 확인 예정 |
| RAM | 12 GB LPDDR5X (1TB 모델 16 GB) | [확인] |
| 메모리 대역폭 | LPDDR5X-5300, 4×16bit → **이론 84.8 GB/s** | [확인] Qualcomm 브리프 계열 자료 |
| OS | Android 15(One UI 7) 출시, 이후 업데이트 | [확인] |

CPU 확장: Oryon v2의 dotprod / i8mm / fp16 지원은 [추정], SVE 지원 여부는 [미확인] → 런타임 `/proc/cpuinfo`
feature 확인 + llama.cpp `GGML_CPU_ALL_VARIANTS` 로 안전하게 처리.

---

## 3. 런타임 후보 비교

| 런타임 | BailingMoeV3(KDA+MLA) 지원 | Android | 가속기 | 판단 |
|---|---|---|---|---|
| **llama.cpp** | **[확인]** PR #26608 머지(2026-08-17), b10470+ 필요, `src/models/bailingmoe3.cpp` | 공식 예제 `examples/llama.android` | CPU, OpenCL(Adreno), Hexagon(NPU), Vulkan | **채택** |
| MNN | [미확인] Ling-3.0 지원 근거 못 찾음 | 공식 앱 있음 | CPU/OpenCL | 보류 |
| ExecuTorch / QNN(Genie) | [미확인] KDA 커스텀 연산 export 필요 | 가능 | NPU | 비용 과다 |
| MLC-LLM | [미확인] | 가능 | GPU | 보류 |
| vLLM / SGLang | 지원 [확인] | 불가(서버용) | — | 제외 |
| Ollama(MLX) | 지원 [확인] | 불가(Apple 전용) | — | 제외 |

### 3.1 llama.cpp 백엔드별 Ling-3.0 연산 지원 [확인: master `806eee98` 소스]

Ling-3.0-tiny 그래프의 특수 연산: `GGML_OP_GATED_DELTA_NET`(KDA, per-channel gate), `GGML_OP_SSM_CONV`(short conv),
`MUL_MAT_ID`(MoE), MLA attention.

| 연산 | CPU | OpenCL (Adreno) | Hexagon (NPU) |
|---|---|---|---|
| GATED_DELTA_NET (S_v=128, KDA) | O | O (F32, S_v∈{16,32,64,128}, KDA 커널 분기 존재) | O (S_v≤128, HMX 경로는 S_v%64==0 → 128 OK) |
| SSM_CONV | O | O | O |
| MUL_MAT_ID 양자화 타입 | 전부 | Q4_0 / Q8_0 / MXFP4 기본, Q4_K·Q6_K 등은 Adreno MoE 커널 조건부 | Q4_0, Q4_1, Q8_0, IQ4_NL, MXFP4, Q2_K–Q6_K |
| CPU repack(가중치 재배열 고속경로) | Q4_0(4x4/4x8/8x8/16), Q4_K(8x8/16), Q6_K, IQ4_NL, MXFP4, Q8_0 | — | — |

알려진 문제:
- Hexagon v73(SM7750)에서 GATED_DELTA_NET 실패/출력 깨짐 이슈 존재(llama.cpp #29473). **v79에서의 정확성은 [미확인]** → 앱에서 CPU 결과와 비교 검증 필요.
- Hexagon 세션 1개당 가상주소 공간 ~3.5 GB → 4.6 GB 모델은 동적 매핑(오버헤드) 또는 세션 2개 레이어 분할 필요.
- NPU는 fp16 연산 → CPU와 출력이 비트 단위로 같지 않음(BigMoeOnEdge PR #200 보고).

### 3.2 안드로이드 앱에서의 가속기 접근

- Hexagon: 앱이 `libcdsprpc.so`(벤더 공개 라이브러리)를 `<uses-native-library>`로 선언해야 하고,
  DSP skel(`libggml-htp-v79.so`)을 찾도록 `ADSP_LIBRARY_PATH` 설정. 일반 앱(비루팅)에서 동작한 선례 있음
  (BigMoeOnEdge, HexaMesh). **S25U One UI에서 unsigned PD 허용 여부 [미확인]**.
- OpenCL: `libOpenCL.so` 를 `<uses-native-library>`로 선언. S25U 노출 여부 [미확인].
- 선례 수치(다른 기기/모델): v81 기기에서 Qwen 35B-A3B Q4_0 **prefill NPU 172 tok/s vs CPU 22 tok/s(7.8×)**,
  decode는 CPU 유지. 짧은 프롬프트(수백 토큰 미만)에서는 이득 없음.

---

## 4. 양자화 선택지 [확인: bartowski/Ling-3.0-tiny-GGUF 헤더 파싱, `tools/gguf_budget.py`]

GGUF 헤더만 range 요청으로 받아 텐서별 크기를 계산. 디코드 1토큰당 가중치 읽기량 =
출력 헤드 + 비-전문가 텐서 전체 + 라우티드 전문가 × 8/128 (token_embd는 1행만 읽으므로 제외).

| quant | 파일 | 전문가 | 비-전문가 | 토큰당 읽기 | 상한 @40 GB/s | 상한 @60 GB/s |
|---|---|---|---|---|---|---|
| IQ4_XS | 4.38 GB | 3.69 | 0.69 | 0.792 GB | 50 tok/s | 76 tok/s |
| **Q4_0** | 4.62 GB | 3.92 | 0.70 | 0.807 GB | 50 tok/s | 74 tok/s |
| Q4_K_M | 4.91 GB | 4.19 | 0.72 | 0.845 GB | 47 tok/s | 71 tok/s |
| Q6_K | 6.83 GB | 5.97 | 0.87 | 1.042 GB | 38 tok/s | 58 tok/s |

관찰:
- 활성 파라미터가 작아 **토큰당 읽기량이 ~0.8 GB** — 같은 크기 dense 4B 모델(~2.5 GB/토큰)보다 3배 가볍다.
- 출력 헤드(157K × 1536, 0.198 GB)가 토큰당 읽기의 약 25%를 차지 → 어휘가 크다는 점이 디코드 병목의 일부.
- 양자화 간 디코드 상한 차이는 작다(±5%). **품질(KLD)과 백엔드 호환성이 선택 기준**.
- Q4_0은 CPU repack + OpenCL MoE 기본 커널 + Hexagon HMX 모두와 호환 → 가속기 실험의 공통 분모.

---

## 5. 메모리 예산 [확인: 계산]

| 항목 | 크기 |
|---|---|
| 가중치 (Q4_0) | 4.62 GB (CPU repack 시 익명 메모리로 복사 → 상주) |
| MLA 레이턴트 캐시 (f16, 6층 × (512+64) × 2 B) | **6,912 B/토큰** → 8K 54 MiB / 32K 216 MiB / 128K 864 MiB |
| KDA 재귀 상태 (f32, 18층 × 16 head × 128² + conv) | **19.3 MiB/시퀀스 (컨텍스트 길이와 무관)** |
| 연산 버퍼 | 수백 MB [추정] |

핵심:
- 같은 크기 일반 Transformer(GQA)라면 32K 컨텍스트 KV가 GB 단위인데, Ling-3.0-tiny는 **216 MiB**.
  긴 컨텍스트가 모바일에서 현실적이다.
- 대신 KDA 상태는 **되감기(rollback)가 불가**: 메시지 편집/재생성 시 해당 지점 상태 스냅샷이 없으면 처음부터 재-prefill.
  스냅샷 1개 = 약 20 MB(재귀 부분만, `LLAMA_STATE_SEQ_FLAGS_PARTIAL_ONLY`) → 턴마다 저장해도 부담 적음.
- 12 GB 기기에서 앱 피크 목표 **≤ 6 GB** (가중치 4.6 + 캐시/버퍼 ~1 GB). Q6_K(6.8 GB)는 LMK(저메모리 킬러) 위험 높음.
- GPU/NPU 오프로드 시 CPU 매핑과 디바이스 버퍼가 **이중 상주**하지 않도록 로드 모드 주의(no-mmap 등) [미확인, 실측 필요].

---

## 6. 성능 추정 [추정 — 실측으로 대체해야 함]

- **디코드(CPU)**: 대역폭 상한 50–74 tok/s. 참고로 같은 SoC(OnePlus 13)에서 Qwen3-4B Q4_K_M 14.3 tok/s 보고
  (→ 유효 ~36 GB/s; 웹 검색 요약 기준, 원문 측정 조건 [미확인]). 같은 효율이면 Ling Q4_0 ≈ 44 tok/s. MoE 소형 GEMV/라우팅/KDA 상태 갱신 오버헤드를 감안해
  **25–45 tok/s 예상**.
- **Prefill**: 연산 바운드. 토큰당 ~2.3 GFLOP(행렬곱 기준). CPU 수치는 실측 필요. NPU prefill은 긴 프롬프트에서
  수 배 개선 가능성(선례 7.8×, 다른 모델/기기).
- **열(thermal)**: 지속 부하에서 스로틀링으로 디코드 속도 하락 예상, 폭은 [미확인].

---

## 7. 리스크 정리

| # | 리스크 | 영향 | 대응 |
|---|---|---|---|
| R1 | Hexagon v79에서 KDA/MoE 정확성 미검증 | NPU 경로 사용 불가 | CPU를 기준 경로로, NPU는 opt-in + 자동 정확성 검사(CPU 대비 logits 비교) |
| R2 | 앱에서 FastRPC/OpenCL 접근이 One UI에서 막힐 가능성 | 가속기 불가 | 런타임 탐지 후 CPU 폴백 |
| R3 | 메모리 4.6 GB+ 상주 → 백그라운드 전환 시 LMK | 모델 재로드(수 초) | 포그라운드 서비스, 재로드 빠르게(mmap 캐시) |
| R4 | thinking 토글이 캐시 무효화 | 지연 | 모드를 대화 단위로 고정, 전환 시 재-prefill 비용 표시 |
| R5 | 재귀 상태 롤백 불가 | 편집/재생성 시 재-prefill | 턴 경계 체크포인트(~20 MB) |
| R6 | llama.cpp 빠른 변경(아키텍처 지원 신규) | 빌드 깨짐/회귀 | 커밋 고정(submodule pin), 업데이트 시 회귀 벤치 |
| R7 | 공개 모바일 벤치 부재 | 설계 수치 불확실 | Phase 0에서 llama-bench 실측 후 기본값 확정 |

---

## 출처

- 모델 카드 / config / 템플릿: https://huggingface.co/inclusionAI/Ling-3.0-tiny
- GGUF: https://huggingface.co/bartowski/Ling-3.0-tiny-GGUF (llama.cpp b10472로 양자화)
- llama.cpp BailingMoE3 지원: https://github.com/ggml-org/llama.cpp/pull/26608
- llama.cpp 소스(조사 시점 master `806eee9841de`): `src/models/bailingmoe3.cpp`, `ggml/src/ggml-opencl`, `ggml/src/ggml-hexagon`, `docs/backend/OPENCL.md`, `docs/backend/snapdragon/README.md`
- Hexagon v73 GDN 이슈: https://github.com/ggml-org/llama.cpp/issues/29473
- 앱 내 NPU prefill 선례: https://github.com/Helldez/BigMoeOnEdge/pull/200 , https://github.com/LuMarans30/HexaMesh
- S25U 사양: https://www.gsmarena.com/samsung_galaxy_s25_ultra-13322.php
- 8 Elite 메모리: https://www.qualcomm.com/content/dam/qcomm-martech/dm-assets/documents/Snapdragon-8-Elite-SM8750-3-AB-Product-Brief.pdf
- 8 Elite LLM 참고 수치: https://grapeup.com/blog/running-llms-on-device-with-qualcomm-snapdragon-8-elite , https://www.buildmvpfast.com/blog/on-device-llm-mobile-llama-ios-android-2026
