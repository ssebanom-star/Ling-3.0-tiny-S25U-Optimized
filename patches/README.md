# llama.cpp 로컬 패치

`engine/src/main/cpp/CMakeLists.txt` 가 configure 시 `third_party/llama.cpp` 에 멱등 적용한다.

## llama-opencl-runtime-switches.patch

Adreno GPU 정확성 문제를 기기에서 자동 진단·우회하기 위한 런타임 스위치. 기본 동작은 상류와 동일.

| 환경변수 | 효과 | 읽는 시점 |
|---|---|---|
| `GGML_OPENCL_OPFILTER` | 정규식에 맞는 op 를 GPU 가 맡지 않음(CPU 폴백) | `supports_op` 호출마다 (상류는 디바이스 탐색 시 1회) |
| `GGML_OPENCL_DISABLE_FUSION` | op 융합 끔 | 그래프 실행마다 (상류는 초기화 시 1회) |
| `LING_OPENCL_NO_ADRENO_GEMM=1` | Adreno 전용 dense 가중치 재배열/GEMM 대신 일반 커널 | 가중치 업로드 시 |
| `LING_OPENCL_NO_ADRENO_MOE=1` | MoE 가중치 재배열(`*_trans4_ns`, 일부 컴파일러 오컴파일 보고) 끔 | 가중치 업로드 시 |

앱(`InferenceManager`)은 GPU 정확성 검사 실패 시 이 조합들을 차례로 시도해 통과하는 첫 구성을 저장한다.
