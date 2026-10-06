# Ling-3.0-tiny · S25 Ultra Optimized

Galaxy S25 Ultra(Snapdragon 8 Elite for Galaxy)에서 [inclusionAI/Ling-3.0-tiny](https://huggingface.co/inclusionAI/Ling-3.0-tiny)
(7.9B 총 / 1.3B 활성, KDA+MLA 하이브리드 MoE)를 완전 오프라인으로 실행하는 안드로이드 앱.

현재 상태: **Phase 1~4 구현 완료, 실기기 검증 전.** 엔진·JNI는 호스트에서 실모델로 검증,
앱은 빌드만 확인했다. 상세: [docs/03-implementation.md](docs/03-implementation.md)

## 첫 실행 (자동 설치)

APK 를 설치하고 열면 아래가 자동으로 진행된다. 사용자는 기다리기만 하면 된다.

1. 기기 확인 — RAM·저장공간으로 양자화 선택 (S25U 12GB → Q4_0 4.6GB, 저장공간 부족 시 IQ4_XS 등으로 자동 하향)
2. 다운로드 — HF 에서 이어받기 지원. 끊기면 자동 재시도, 앱을 나가거나 종료돼도 다음 실행 때 이어서 받음
   - **모바일 데이터에서는 Wi-Fi 를 기다림** (화면의 "모바일 데이터로 받기"를 누르면 즉시 진행)
3. SHA-256 무결성 검증
4. 모델 로드 + 기본 설정(컨텍스트 16K, 권장 샘플링)
5. 스레드·코어 배치 자동 튜닝(20~40초)
6. 채팅 화면으로 전환

## 특징

- **llama.cpp(고정 커밋) 기반 전용 엔진** — 동적 백엔드: CPU 변형 7종(armv8.0~v9.2 자동 선택) + Adreno OpenCL + Hexagon NPU
- **캐시 재사용** — 이전 응답을 생성 토큰 그대로 재삽입해 접두부 캐시를 유지하고, 턴 경계마다 KDA 재귀 상태(~19 MiB)
  체크포인트를 남김 → 다음 턴·재생성·편집 시 차이분만 prefill
- **템플릿 정확성** — Ling-3.0 chat_template.jinja 를 Kotlin 으로 이식, jinja2 골든 13케이스 바이트 일치
- **Thinking / Instant 모드**(대화 단위), 추론 과정 접기, 온디바이스 툴(계산기·시간·기기 상태)
- **기기 최적화** — 스레드·코어 배치 자동 튜닝, 열 거버너, ADPF 성능 힌트, 포그라운드 서비스 + wake lock
- **가속기 안전장치** — NPU/GPU 사용 전 CPU 기준 logits 와 비교, 기준 미달 시 자동 CPU 복귀
- 모델 다운로드(이어받기 + SHA-256), 파일 가져오기, 대화 저장(SQLite)

## 빌드

```bash
git clone --recursive <repo>
# (선택) Hexagon NPU 백엔드 — docker 필요
tools/build_hexagon_backend.sh
./gradlew :app:assembleRelease          # app/build/outputs/apk/release/app-release.apk
```

요구: JDK 17+, Android SDK 36, NDK 29.0.14206865, CMake 3.31.6. CI: `.github/workflows/android.yml`.

## 테스트

```bash
./gradlew :app:testDebugUnitTest                       # 템플릿 골든·파서
cmake -S tests/host -B build-host -DCMAKE_BUILD_TYPE=Release && cmake --build build-host -j
./build-host/ling_engine_test Ling-3.0-tiny-Q4_0.gguf   # 실모델 엔진 테스트
tools/run_host_jni_test.sh Ling-3.0-tiny-Q4_0.gguf      # 실모델 JNI 테스트
```

## 실기기 측정 (Phase 0)

```bash
tools/phase0/build_bench_tools.sh                       # llama-bench (CPU+OpenCL+Hexagon)
tools/phase0/bench_s25u.sh Ling-3.0-tiny-Q4_0.gguf      # adb 연결 필요 → bench/results/
```

## 문서

- [01-research.md](docs/01-research.md) — 모델·하드웨어·런타임 조사, 메모리/대역폭 예산
- [02-design.md](docs/02-design.md) — 설계, 구현 중 변경 사항(§10)
- [03-implementation.md](docs/03-implementation.md) — 구현 현황, 검증 기록, 실기기 체크리스트
- [tools/gguf_budget.py](tools/gguf_budget.py) — GGUF 헤더만으로 quant별 메모리/디코드 상한 계산

## 라이선스 / 출처

모델: MIT (inclusionAI). GGUF: bartowski/Ling-3.0-tiny-GGUF. 엔진: llama.cpp (MIT).
