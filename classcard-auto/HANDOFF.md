# 인계 문서 (다른 에이전트/개발자용)

> 작성 시점 버전: **0.6.26-dev** (versionCode 35). 사용자 응답 언어는 **한국어**, 군더더기 없이 결과물 우선.
> **기기에서 실행해 본 적이 없다.** 아래 "미검증" 항목은 전부 컴파일·JVM 테스트·렌더링 미리보기까지만 확인됨.

## 1. 이게 뭔가
`ClassCardAuto`(applicationId `net.classcardauto.mobile.nocert`, namespace `net.classcardauto.mobile`):
클래스카드(`classcard.net`) 단어 테스트를 안드로이드 **접근성 서비스**로 자동 풀이하는 앱.
원본 APK(`ClassCardAuto-0.6.11-no-cert`)를 jadx로 디컴파일해 Gradle 프로젝트로 재구성(Java)하고 기능을 추가했다.
사용자 맥락: **교내 경진대회("사람같은 봇 만들기")** 출품용. 평가 기준 ① 안정성(외부인 개입 테스트 포함) ② 실용성 ③ 모방.
사용자 설명상 클래스카드 약관이 금지하는 것은 CSV 리스트 외부 반출·수익 창출이며 자동화 자체 금지 조항은 없다고 함(미확인, 사용자 진술).
외부 서버로 데이터를 보내지 않으며 단어/문항 원문은 기록하지 않는다 — 이 원칙을 유지할 것(실패 기록도 시각·이유·진행만).

## 2. 빌드·검증
- JDK 17+, Android SDK 35 / build-tools 35.0.0, Gradle 8.14.3 (래퍼 없음), AGP 8.9.1, Kotlin 2.1.0, onnxruntime-android 1.31.0.
- `local.properties`에 `sdk.dir=` 필요(저장소에 없음). 빌드: `gradle :app:assembleDebug`.
- **JVM 테스트(에뮬레이터 불필요):** `tools/run_tests.sh <android.jar> [kotlin-stdlib.jar]` → Matcher 18, HumanTiming 5011, WrongAnswers 24, PauseState 20, FailureLog 17 전부 통과(마지막 확인 시점).
  - 주의: 진짜 `org.json`(`tools/lib/json.jar`)이 `android.jar`(스텁)보다 **앞**에 와야 한다.
- **UI 미리보기:** `preview-proj/`(Paparazzi). `gradle recordPaparazziDebug --tests "*UiPreviewTest*"` → `app/src/test/snapshots/images/*.png`. 앱 소스는 메인 소스를 참조. `android.useAndroidX=true` 필요.
- 서명: 디버그 키. **키가 다른 PC에서 빌드하면 기존 설치본 위에 덮어쓰기 불가**(삭제 후 재설치 → 인증키 파일 재투입 필요).
- 전달 방식: 사용자 업로드 한도 때문에 APK(≈165MB, OCR 모델 포함)를 **≤30MB로 분할**했다: `zip -s 28m -9 out.zip app.apk` → `.z01 .z02 .zip`. 검증은 SHA-256 비교(`zip -s 0` 병합은 CRC 오류가 나므로 쓰지 말 것; z01 앞 4바이트 시그니처 제거 후 첫 로컬 헤더에서 raw deflate 해제하는 파이썬 스크립트로 확인했음).

## 3. 소스 지도 (`app/src/main/java/net/classcardauto/mobile/`)
| 파일 | 역할 |
|---|---|
| `BotEngine.java` | 실행 상태기계(수집→테스트), 일시정지/재개, 사람모드 타이밍, 고의 오답, 터치 잠금 상태, 요약/실패 기록 |
| `BotService.java` | 접근성 서비스: 화면 읽기, `dispatchGesture` 탭/스와이프, 오버레이 창(플로팅 패널·터치 차단막·1×1 터치 감시창), ticker |
| `Matcher.java` / `Solver.java` | 정답 매칭(OCR 보정 퍼지, 동점 판별, 부분 일치) / 어댑터. 원본 Solver는 `reference/`에 보존 |
| `HumanTiming.java` `WrongAnswers.java` `PauseState.java` `FailureLog.java` `ScrollSettings.java` `AdvancedSettings.java` | 순수 로직·설정 (JVM 테스트 있음) |
| `Certification*.java` `CertificateVerifier.java` | 기기 인증 키 파일(RSA SHA256withRSA) 잠금. 드롭 폴더 `Android/data/<pkg>/files/auth` |
| `AppStyle` `Screens` `FloatingPanel` `PillSwitch` `MainActivity` | UI. 디자인 토큰·화면 조립·플로팅 패널 |
| `OcrReader` `Ocr*` `Ctc*` | 내장 OCR(onnx, `assets/qota-ocr`) — 기본 꺼짐(`image_collection`) |
| `QuietJson` `Sneaky` `ResultCompat.kt` | 디컴파일 코드의 checked exception / Kotlin `Result` 인라인 클래스 호환 |

## 4. 추가한 기능과 설정 키 (SharedPreferences)
- 정확도: `match_fuzzy_percent`(50–100, 80) `match_tie_percent`(0–30, 8) `match_partial_min`(1–6, 2)
- 한계: `limit_run_seconds`(120–3600, 600) `limit_phase_seconds`(10–120, 35) `limit_scroll_steps`(40–400, 160) `limit_tap_retries`(1–5, 2) `limit_gate_ms`(300–1500, 650) `limit_touch_pause`(1–5, 2)
- 스크롤: `scroll_range_percent` `scroll_duration_ms` `scroll_interval_ms` (플로팅 패널 슬라이더)
- 사람모드: `human_like`, `human_mean_ms`(1000–8000, 3000) — 로그정규 응답시간·피로·망설임·탭 위치/길이 지터
- 고의 오답: `wrong_count`(0–20) — 시험 시작 시 무작위 문항 선택. 앱 설정과 **플로팅 패널 슬라이더** 양쪽에서 조절
- 일시정지/재개: 시험 중 사용자 터치 N회면 자동 일시정지(1×1 `FLAG_WATCH_OUTSIDE_TOUCH` 창의 `ACTION_OUTSIDE`로 감지), 재개 시 터치 수 0, **재개 후 첫 해석 불가 문항은 찍기**. 일시정지/재개는 플로팅 패널에서만(앱 화면으로 돌아가게 하지 말 것 — 사용자 요구)
- **터치 잠금** `touch_lock`(기본 켬): 실행 중 전체 화면 투명 차단막(TYPE 2032)이 플로팅 패널 외 터치를 삼킴. 패널보다 먼저 추가돼 아래에 깔림. 봇 제스처 동안에는 `FLAG_NOT_TOUCHABLE`로 통과. 일시정지 때 해제. 시작 전 경고(앱: 대화상자, 패널: 두 번 누르기 10초 이내)
- OCR 촬영 시 패널이 읽을 영역을 덮으면 0.12초 투명 처리 후 촬영(`BotService.panelCovers`)
- 실패 기록 화면(최대 100건, 시각·코드·진행 번호만), 남은 시간 표시, 초 단위 표기

## 5. 미검증 / 위험 (기기에서 먼저 확인할 것)
1. 투명 차단막 위에서 `dispatchGesture`가 통과하는지(안 되면 수집부터 멈춤). 차단막이 접근성 노드 읽기·OCR 캡처에 영향이 없는지.
2. `ACTION_OUTSIDE` 기반 터치 감지가 실제 기기(특히 삼성 One UI)에서 동작하는지, 오탐(봇 제스처)이 없는지.
3. 일시정지→재개 흐름, 재개 후 찍기 동작.
4. 접근성 오버레이를 숨겨도 `takeScreenshot`에 남는 기기가 있는지.
5. 사람모드 타이밍이 실제 시험 제한시간 안에 완주하는지.

## 6. 알려진 함정
- 시작 직후 멈춤 버그 이력: 디컴파일된 ticker가 `stop("화면이 잠겨…")`를 무조건 호출 → `!interactive || keyguard locked`일 때만 호출하도록 수정. 디컴파일 코드는 분기 유실이 잦다 → **dexdump 대조**가 기준.
- 손으로 복원한 메서드 목록은 `README.md` 참고. 그 외 디컴파일 코드는 컴파일만 통과, 원본 동치 미증명.
- `rm -rf` + cd + glob 조합이 샌드박스 안전검사에 막힌다. `pkill -f` 쓰면 자기 셸이 죽는다.
- Gradle 의존성은 Maven Central 429가 날 수 있음(재시도).

## 7. 디컴파일 파이프라인 (원본 APK가 다시 필요할 때)
원본 APK는 저장소에 없다(사용자가 보유). 이 과정은 재구성 완료 후엔 거의 필요 없음.
- `jadx` 자동 모드가 기본, `-m simple`(goto) 모드는 MainActivity/BotEngine에서 쓸 수 없었음. 실패한 메서드 5개는 수작업 복원.
- `dexdump -d`(build-tools 35.0.0)로 바이트코드와 대조.
- `tools/fix.py`(`replace(path, sig, new)` 등 메서드 단위 치환 도우미), `tools/errs.py`(컴파일 오류 요약), `tools/dflt.py`(Kotlin `$default` 호출 변환), `tools/legacy-edit-scripts/apply*.py`(개발 중 실제로 소스에 적용한 일회성 수정 스크립트 기록. 재실행 금지 — 이미 소스에 반영됨. 경로 `D=`가 이전 샌드박스 경로로 하드코딩).

## 8. 인증키 발급기 (`issuer/`)
- `issue.py --device <기기코드> [--days N]` → `classcard-auth.key`. 자세한 사용법은 `issuer/README.md`.
- **`issuer_private.pem`(개인키)은 의도적으로 저장소에서 뺐다.** 저장소가 공개/공유될 수 있고, 키가 새면 잠금이 무의미해진다. 사용자가 별도로(압축파일로) 받았다. 이 폴더에 복사해서 사용.
- 공개키·MACHINE_ID는 `CertificationIssuer.java`에 박혀 있고 `issuer_config.json`과 같아야 함. 새 키를 만들면(`keygen.py`) 앱 재빌드 + 기존 발급분 전부 무효.
- 한계: APK 수정으로 검사를 제거하는 것은 못 막는다(문서에도 명시).

## 9. 남은 일 (사용자가 아직 선택하지 않음)
"외부인 개입" 안정성 대책 묶음 — 추천안:
- 앱 이탈/잠금/팝업 시 **중지 대신 자동 일시정지 → 복귀 시 자동 재개 + 화면 재동기화**
- 단어 수집 중 터치 감지 후 재동기화
- 터치 횟수를 시간 창 기준으로 계산
- 탭 직전 화면 재검증, 워치독, 중지 버튼 확인, 알림/진동, 웨이크락/포그라운드 서비스, 회전 처리
사용자는 "추천 묶음으로 바로 진행할까요?"에 아직 답하지 않았고, 이후 터치 잠금·경고·패널 슬라이더·OCR 가림 방지를 먼저 요청했다. 진행 전 한 번 확인할 것.

## 10. 버전 이력 (요약)
0.6.12~0.6.15 정확도·플로팅 UI·시작 직후 중지 수정 / 0.6.14 UI 전면 재디자인 / 0.6.15~16 사람모드·인증키 잠금 /
0.6.17~19 고의 오답·고급 설정·실패 기록·남은 시간 / 0.6.20~22 일시정지·터치 자동정지 / 0.6.23 터치 잠금 /
0.6.24 시작 전 잠금 경고 / 0.6.25 패널에서 오답 수 조절 / 0.6.26 OCR 촬영 시 패널 가림 방지.
