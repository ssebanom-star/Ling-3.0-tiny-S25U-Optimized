# llama.cpp 로컬 패치

`patches/apply.cmake` 가 configure 시 `third_party/llama.cpp` 에 멱등 적용한다(앱 엔진·호스트 테스트·Phase 0 도구 공통).
수동 적용: `cmake -P patches/apply.cmake`

## llama-opencl-runtime-switches.patch

Adreno GPU 정확성 문제를 기기에서 자동 진단·우회하기 위한 런타임 스위치. 기본 동작은 상류와 동일.

| 환경변수 | 효과 | 읽는 시점 |
|---|---|---|
| `GGML_OPENCL_OPFILTER` | 정규식에 맞는 op 를 GPU 가 맡지 않음(CPU 폴백) | `supports_op` 호출마다 (상류는 디바이스 탐색 시 1회) |
| `GGML_OPENCL_DISABLE_FUSION` | op 융합 끔 | 그래프 실행마다 (상류는 초기화 시 1회) |
| `LING_OPENCL_NO_ADRENO_GEMM=1` | Adreno 전용 dense 가중치 재배열/GEMM 대신 일반 커널 | 가중치 업로드 시 |
| `LING_OPENCL_NO_ADRENO_MOE=1` | MoE 가중치 재배열(`*_trans4_ns`, 일부 컴파일러 오컴파일 보고) 끔 | 가중치 업로드 시 |

앱(`InferenceManager`)은 GPU 정확성 검사 실패 시 이 조합들을 차례로 시도해 통과하는 첫 구성을 저장한다.

## llama-bailingmoe3-mla-cont.patch — GPU 출력이 입력을 무시하던 원인 수정

**원인 [코드로 확인]**: MLA 6개 층의 `q_nope = permute(view(q))` 는 비연속 텐서다(토큰 stride 128·16·4 B,
헤드 stride 128·4 B 인데 nope 부분은 64). 이를 Q4_0 `attn_k_b`(64×512×16) 와 곱할 때 OpenCL 백엔드는

1. `supports_op` 의 Q4_0 분기가 src1 연속성을 검사하지 않아 GPU 가 맡고,
2. 3D 가중치라 Adreno GEMM 이 아닌 `kernel_mul_mat_q4_0_f32_(1d_)8x_flat` 로 가는데,
3. 이 커널은 src1 stride(nb11/nb12)를 받지 않고 `src1 + r1*ne10 + im*ne00*ne1` 로 연속 배치를 가정한다.

→ 헤드 h≥1 이 다른 헤드의 nope/rope 값을 읽어 MLA query 가 전 층에서 틀어진다. KDA(18층)·FFN 은 정상이라
문장은 유창한데 프롬프트를 무시하는 증상이 된다. CPU/NPU 는 stride 를 처리하므로 정상.

**수정**: `ggml_cont` 로 연속 복사 후 곱한다(크기 64×16×n_tok, 비용 무시 가능, 모든 백엔드 수치 동일).
추가로 `llama-opencl-runtime-switches.patch` 에서 Q4_0 mul_mat 의 비연속 src1 을 GPU 가 거절하도록 막았다
(FA 사용 시 `v_mla` 입력 등 같은 형태의 다른 경로 방어).
