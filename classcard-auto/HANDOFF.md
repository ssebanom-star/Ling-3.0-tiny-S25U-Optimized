# 인계 문서 (다른 에이전트/개발자용)

> 작성 시점 버전: **1.14** (versionCode 63, auth만 빌드) + 인증용 앱 MiMiC-Auth-V1.0. V1.5만 **auth(인증 있음)/noauth(인증 없음) 두 flavor**로 빌드했고, **V1.6부터는 auth만 빌드**한다 (`gradle :app:assembleAuthDebug` → MiMiC-V<버전>-auth.apk). noauth는 applicationId `net.classcardauto.mobile.noauth`, 인증 키 불필요(비밀번호는 동일). auth는 기존 applicationId `net.classcardauto.mobile.nocert`를 유지(덮어 설치·발급 키 유효). 둘 다 `BuildConfig.AUTH_REQUIRED`로 갈림. 사용자 응답 언어는 **한국어**, 군더더기 없이 결과물 우선.
> V1.0: 사용자가 기기에서 시험을 해 보고 "다 테스트 통과"라고 보고함(0.7 기준, 세부 결과는 사용자 진술이며 이 저장소에 기록 없음). 아래 "미검증" 항목은 그 이전 시점의 목록이니, 사용자 보고와 다르면 사용자에게 확인할 것.

## 1. 이게 뭔가
`ClassCardAuto`(applicationId `net.classcardauto.mobile.nocert`, namespace `net.classcardauto.mobile`):
클래스카드(`classcard.net`) 단어 테스트를 안드로이드 **접근성 서비스**로 자동 풀이하는 앱.
원본 APK(`ClassCardAuto-0.6.11-no-cert`)를 jadx로 디컴파일해 Gradle 프로젝트로 재구성(Java)하고 기능을 추가했다.
사용자 맥락: **교내 경진대회("사람같은 봇 만들기")** 출품용. 평가 기준 ① 안정성(외부인 개입 테스트 포함) ② 실용성 ③ 모방.
사용자 설명상 클래스카드 약관이 금지하는 것은 CSV 리스트 외부 반출·수익 창출이며 자동화 자체 금지 조항은 없다고 함(미확인, 사용자 진술).
외부 서버로 데이터를 보내지 않으며 단어/문항 원문은 기록하지 않는다 — 이 원칙을 유지할 것(실행 이벤트 로그·logcat·last-summary.json·prepared.json은 V1.5부터 아예 기록하지 않음. 마지막 요약은 메모리 전용. 실패 기록 failures.jsonl만 시각·이유·진행 번호로 남음 — 이것도 없애려면 BotEngine.writeFailureRecord 호출 제거).

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
- 시작 전 큰 패널: "끄기" 칩은 패널을 숨기고(앱 화면을 열면 복귀, BotService.showPanel) "작게" 칩으로 시작 버튼만 남기는 작은 막대(펼침 버튼 포함)로 줄일 수 있음. 큰 패널 안쪽 가장자리의 접기 탭(▶)을 누르면 패널이 가장자리의 얇은 탭(◀)만 남기고 접히고, 탭을 누르면 펼쳐짐(시작 전 큰 패널 전용, 실행 중 막대에는 없음).
- 시작 전 큰 패널은 기본으로 설정 전체(스위치 2개 + 슬라이더 8개, 스크롤 영역)를 펼쳐 보여줌. 실행 중에는 가장자리 세로 막대(시험·수집 모두).
- 사람모드: `human_like`, `human_mean_ms`(500–8000, 1000) — 로그정규 응답시간·피로·망설임·탭 위치/길이 지터
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
0.7 (=0.6.38) 이름 MiMiC·아이콘·시험/수집 중 가장자리 막대·설정 펼침 패널·응답 시간 0.5~8초(기본 1초) 등 반영.
1.0 = 0.7과 같은 코드(버전 표기만 변경). 사용자 기기 테스트 통과 보고 후 확정.
1.6 안정성: ① 클래스카드가 3초 이상 안 보이거나 화면이 잠기면 중지 대신 **자동 대기**(BotEngine.autoHold, 진동 알림) → 돌아와 1.2초 보이면 자동 재개(resumeAuto, 8초 미만 이탈이면 추측/개입 표시 없이 이어감), 자동 대기 최대 `limit_hold_seconds`(기본 180초) 초과 시 중지 ② 단어 수집 중 사용자 터치는 읽던 페이지 상태를 버리고 1.5초 대기(registerCollectionTouch) ③ 탭 직전 대상 노드 재확인(BotService.targetStillThere, 24px 이내, 2회까지 건너뛰고 3번째는 그대로 진행) ④ 워치독(BotEngine.watchdog: 시험 30초 재동기화/60초 중지, 수집 90초/180초) ⑤ 화면 회전 시 재동기화(onConfigurationChanged) ⑥ 진동 알림(`vibrate_alerts`, VIBRATE 권한; 대기=짧게 2번, 중지·오류=길게 1번, 완료=짧게 2번+길게). 전부 JVM 테스트 불가 영역이라 **기기 검증 필요**.
1.7: ① 내 속도 맞추기(BotEngine.startCalibration/observeCalibration/finishCalibration, 패널 설정의 버튼): 봇은 화면만 읽고 사용자가 직접 푼 8문항(최소 3)의 "보기 표시→답 입력됨" 시간을 문장 길이로 보정해 중앙값으로 `human_mean_ms`를 설정. 메모리에만 두고 저장하지 않음(로그 비저장 원칙). 클래스카드 화면 id(txt_current_num, txt_total_cnt, txt_opt, txt_bottom_msg)와 대기/피드백 메시지에 의존하므로 **기기 검증 필요** ② 남은 시간 예측: 시작 전에는 설정값으로 문항당 시간을 대략 추정해 표시(HumanTiming.estimatePerQuestionMs, 상수는 측정값이 아닌 추정), 시험 중에는 이번 실행의 실제 속도로 예상 완료 시간 표시(BotEngine.getEtaSeconds, 세로 막대의 "끝까지 m:ss"와 앱 홈 안내). `human_mean_ms` 슬라이더 단계를 500→250ms로 변경.
1.8 인증 방식 추가(기존 키 파일 방식 유지): "발급자 휴대폰". 컴퓨터가 발급자 인증서(RSA 서명, kind=issuer, 폰의 EC P-256 공개키+기기 코드)를 한 번 만들어 주면(`issue.py --issuer-request`), 그 폰(PhoneIssuer, 키는 AndroidKeyStore에 비내보내기)이 다른 폰용 활성화 코드 `MC1.`+base64url({issuer 인증서, payload, ECDSA 서명})를 만든다. 받은 폰은 CertificateVerifier.verify가 `MC1.` 접두사로 분기해 컴퓨터 공개키→발급자 인증서→활성화 서명 순으로 연쇄 검증(기존 RSA 키 파일·발급 키는 그대로 유효). 화면: 인증 화면의 방법 A/B, 설정>발급자 도구(IssuerScreen). `tools/auth_roundtrip.py`가 실제 검증기로 11개 경우를 검사(통과). **AndroidKeyStore 키 생성·서명과 클립보드 붙여넣기는 기기 검증 필요.**
1.9 QR·짧은 코드: ① 기기 코드 QR(`MCDEV:`+코드)을 인증 화면에 표시, 발급자 도구에서 사진으로 읽기 ② 코드 QR: 짧은 형식 `MC2.`(앱에 박힌 발급자 폰 공개키 `CertificationIssuer.PHONE_ROOTS`로 직접 검증, 인증서 불필요, 약 610자)만 QR 표시, 긴 `MC1.`은 복사 전용 ③ QR 읽기는 시스템 카메라 앱으로 사진 1장(MediaStore, Android 10+; 이하는 사진 선택) → ZXing core로 디코드 → 사진 삭제. 앱은 CAMERA 권한 없음(QrCapture, QrCodec). `PHONE_ROOTS`는 현재 **비어 있음**: 사용자가 발급자 도구의 '요청 복사' 텍스트(issuerPub)를 주면 넣어 재빌드. JVM 테스트: QrCodecTest(5), tools/auth_roundtrip.py(17). 카메라 촬영·MediaStore·사진 선택은 **기기 검증 필요**.
1.10 인증용 앱(MiMiC Auth, 별도 모듈 `authenticator/`, applicationId `net.classcardauto.mobile.authenticator`, 빌드 `gradle :authenticator:assembleDebug` → MiMiC-Auth-V1.0.apk): 새 폰의 인증 화면(방법 C)이 일회용 EC 공개키·nonce가 든 QR(`MCA1:<기기코드>:<nonce>:<공개키>`)을 보여주고, 인증용 앱이 그 QR을 사진으로 읽어 ECDH(인증용 앱 개인키, 일회용 공개키)로 비밀을 만든 뒤 HMAC-SHA256 앞 50비트를 Crockford base32 10자로 표시(앞에 유효기간 숫자 1자, 예 `3-ABCDE-FGH12`). 사람이 입력 → 새 폰이 ECDH(일회용 개인키, 앱에 박힌 `CertificationIssuer.AUTHENTICATOR_KEYS` 공개키)로 같은 비밀을 만들어 검증(CertificateVerifier.verifyChallengeRecord). **앱에 비밀을 넣지 않는다**(공개키만). 시도 5회 틀리면 5분 잠금+새 QR, 챌린지 30분 수명, 코드는 기기·패키지·nonce·발급일·유효기간에 묶여 기록 파일을 고치거나 다른 폰으로 옮기면 실패. `AUTHENTICATOR_KEYS`에는 V1.11부터 사용자의 인증용 앱 공개키(MFkwEwYH…Wmg==) 1개가 들어 있음(다른 인증용 앱을 쓰려면 이 목록에 공개키를 추가해 재빌드). 인증용 앱의 키는 Android 12+에서 키 저장소(ECDH, 비내보내기), 그 외·실패 시 앱 전용 파일. 테스트: tools/run_auth_tests.sh(AuthFlowTest 23, QrCodecTest 5). 인증용 앱 화면·카메라 촬영·키 저장소 ECDH는 **기기 검증 필요**(미리보기도 못 만듦).
1.11: 사용자의 인증용 앱 공개키를 CertificationIssuer.AUTHENTICATOR_KEYS 에 등록(방법 C 사용 가능). 그 외 코드는 1.10과 같음.
1.12 인증 방식 정리: **방법 A(컴퓨터 키 파일)와 방법 C(인증용 앱 MiMiC Auth 코드 입력)만 남김**. 1.8~1.9의 "발급자 폰"(활성화 코드 MC1/MC2, PhoneIssuer, IssuerScreen, 설정>발급자 도구, 붙여넣기·QR 촬영, `PHONE_ROOTS`, issue.py `--issuer-request`)은 전부 제거. 메인 앱은 카메라/사진 기능이 없고 QR은 표시만(QrCodec) 함. 위 1.8·1.9 설명은 더 이상 유효하지 않음. 테스트: tools/auth_roundtrip.py(키 파일 7), tools/run_auth_tests.sh(AuthFlowTest 24, QrCodecTest 5).
1.13 내 패턴 측정 확장(BotEngine.observeCalibration, PaceModel, HumanProfile): 측정 시작 버튼을 누른 직후부터 사용자가 클래스카드에서 직접 ‘테스트 시작’을 누르고 8문항(최소 3)을 풀 때 ① 시작 버튼 누르기까지 기다림(세트/미리보기 화면, `human_nav_ms`) ② 보기 열기 전 멈춤(`human_reveal_ms`) ③ 답 응답 시간(응답 입력 − 보기 표시 − 150ms 반응 지연, 글 길이 보정 → `human_mean_ms`) ④ 답 후 카드 넘김 전 멈춤(`human_advance_ms`) ⑤ 변동 폭(MAD 기반 σ, `human_sigma_pct`) ⑥ 망설임 비율(`human_hesitate_pct`, 기본 6%와 혼합) ⑦ 문항마다 느려지는 정도(`human_fatigue_permille`, 6문항 이상)를 잰다. 봇의 사람모드가 이 값들을 사용(act()의 open/start 전 대기 포함). 터치 좌표·누르는 길이는 접근성으로 읽을 수 없어 측정하지 않음(기본 흔들림 유지). 측정 데이터는 메모리에만 두고 결과 숫자만 prefs에 저장. 설정>‘고급 · 내 패턴 세부값’ 슬라이더로 수동 조절·초기화. 테스트: PaceModelTest 68. **기기 검증 필요**(화면 id·메시지 의존).
1.14 응답 방식 두 벌 분리(HumanProfile): "평균·랜덤"(손으로 정한 `human_*` 키: 평균 응답 시간+고급 세부값)과 "내 패턴"(측정한 `mine_*` 키)을 따로 저장하고 `human_source`("default"/"mine")로 **둘 중 하나만** 사용. 측정(finishCalibration→saveMine)은 mine_* 에만 쓰고 끝나면 내 패턴으로 전환, 손으로 정한 값은 절대 덮어쓰지 않음. 측정하지 않은 항목은 손으로 정한 값이 아니라 내장 규칙(응답의 1/4 등)을 씀. 측정이 없으면 내 패턴 선택은 거부. 패널·설정에 선택 칩(Screens.sourceChooser), 사용하지 않는 쪽 컨트롤은 흐리게(Screens.dim). 설정>‘측정한 내 패턴’ 카드에서 내 패턴 삭제. 봇은 HumanProfile.meanMs/sigma/…(사용 중인 세트)를 읽음. 테스트: HumanProfileTest 27.
1.15 UI 리워크. ① 플로팅 패널(FloatingPanel) 재작성: 한 번에 하나의 모드만 표시 — CARD(대기: 헤더[이동·상태·시작] + 툴바[작게·끄기·설정▴▾] + 설정 탭 4개 입력/시험/안전/수집, 탭은 `panel_tab`에 기억, 최대 높이 300dp), START(시작 버튼만), TAB(가장자리 탭, B2↔B1), MEASURE(내 패턴 측정 중 전용 카드: 진행 n/8 + 측정 끝내기), STRIP(실행 중 세로 막대). 중복이던 "UI 끄기"(overlayDismissed)는 패널에서 제거하고 "끄기"(panelOff) 하나만 둠(Host.onHide는 호환용으로만 남음). 설정 내용은 Screens.addPanelGroup(c, parent, prefs, group)이 탭별로 만듦(옛 addPanelSettings 제거). BotService는 refreshOverlay와 ticker에서 panel.setCalibrating(on, calibrationText())를 호출, 측정 종료 시 Toast. ② 앱 설정을 카테고리 페이지로 분리: Screens.settings(..., onOpen)는 메뉴(입력 방식/안전·잠금/단어 읽기·수집/정답 매칭 + 안내 및 기록), Screens.settingsSection(c, prefs, i, onBack)이 각 페이지. 메뉴 행 아래 현재 값 요약(sectionSummary). MainActivity.showSettingsSection, 뒤로가기는 페이지→메뉴→홈. ③ 홈의 스크롤 카드 대신 "다음 실행" 요약(입력 방식·안전) 카드, 누르면 설정. 동작 로직(봇·타이밍·인증)은 변경 없음. **기기 검증 필요**(패널 모드 전환·가장자리 정렬).
1.16 안정성: ① 문항당 최대 응답 시간(`limit_question_seconds`, 2~20초, 기본 5초; 패널 ‘시험’ 탭·설정>안전·잠금>실행 한도): 문항을 처음 본 시각(BotEngine.questionSeen)부터 이 시간−0.7초 안에 답하도록 사람모드 생각 시간을 자름, 보기 열기 전 멈춤은 이 시간의 1/3 이하. 클래스카드 자체 문항 제한시간(txt_time_over)에 걸리는 것 방지. 클래스카드 실제 제한시간 값은 **모름**(사용자가 맞춰야 함). ② ticker의 catch를 Throwable로 넓히고, 실패 처리(fail→setStatus→패널 갱신) 자체가 예외를 던져도 BotEngine.forceStop으로 끝내고 ticker는 finally에서 항상 재예약. ③ 처리되지 않은 예외는 BotService.recordCrash가 실패 기록에 `CRASH`(예외 종류·메시지 80자·앱 내부 스택 3줄, 클래스카드 내용 없음)로 남긴 뒤 기본 처리기로 넘김 → 다음 강제 종료 원인은 앱 ‘실패 기록’에서 확인 가능. V1.15 강제 종료의 실제 원인은 로그가 없어 **미확인**(미리보기에서 패널 실행→실패 전환은 재현 안 됨).
1.17 시간 초과 시 계속 진행: 테스트 화면에 `txt_time_over`(답 선택 제한시간 초과)가 보이면 예전처럼 실패로 멈추지 않고 그 문항을 `timedOut`에 넣고(정답 입력 대기 pending 제거) 피드백 후와 같은 방식으로 카드를 넘김(humanAdvanceReady(":timeover")+SwipeGate). 입력 확인 전에 문항 번호가 바뀌어도(시간 초과로 자동 넘어감 등) 실패 대신 그 문항을 timedOut으로 기록하고 계속. 결과 화면 검사는 answered+timedOut = 전체 문항 수. 완료 문구에 "시간 초과 n문항". 시간 초과 화면에서 클래스카드가 스와이프를 요구하는지 자동으로 넘어가는지는 **미확인**(둘 다 처리하도록 작성).
1.18 초고속 모드(`turbo`, 패널 ‘입력’ 탭 맨 위·설정>입력 방식): 실행 중 ticker 100→35ms, BotService.hold 대기 절반(최소 40ms), 카드 스와이프 250→150ms, 시작 카운트다운 8→3초, 단어 수집 스크롤 간격 절반(최소 300ms). 켜면 사람처럼 입력보다 우선(human=false). 실행 기록 pace "turbo". 클래스카드 자체 애니메이션·피드백 표시 시간은 못 줄임. 150ms 스와이프·35ms 폴링이 클래스카드에서 잘 먹히는지(빠른 스와이프 무시, 화면 읽기 부하) **기기 검증 필요**.
1.19 수집→테스트 사이 중단 대응(사용자 보고, 실패 메시지는 미확인): ① finish_collect에서 `set_name`이 잠깐 안 보이면(목록 정착 중) 실패 대신 대기(단계 제한시간은 그대로) ② 수집 누락 검사(양면 수집 누락 단어/목록 위치 누락)에 걸리면 바로 멈추지 않고 처음부터 **한 번 더 수집**(이때는 초고속이어도 스크롤 간격 원래대로), 그래도 누락이면 그냥 테스트 시작(collectionIncomplete) ③ 테스트 중 단어장으로 답을 못 정하는 문항(AnswerResolutionException)은 실패 대신 무작위 보기 선택(guessedAnswers, 로그 unresolved_guess), 완료 문구에 "단어장에 없어 찍은 문항 n개". 원래의 '확실하지 않으면 멈춤' 정책을 사용자 요청(계속 진행)에 따라 바꾼 것.
1.20 원인 확인(실패 기록 "양면 수집이 누락된 단어가 있습니다: mechanism, concern", pace turbo): 수집의 화면 안정 판정(StableCollectionRead)은 "연속 2회 같은 읽기"라 ticker 35ms에서는 카드 뒤집기 애니메이션 중에 읽어 누락. 초고속(BotEngine.isTurbo)은 이제 phase가 collect* 일 때 꺼짐(ticker·hold·스와이프 모두 원래 값), 수집 스크롤 간격 절반 단축도 제거. 1.19의 재수집/찍기 안전장치는 유지.
1.21 초고속 하위 스위치 "확인 없이 넘기기"(`turbo_skip_confirm`, 패널 ‘입력’ 탭·설정>입력 방식; BotEngine.skipConfirm = isTurbo && phase test && 스위치): 답 탭 콜백에서 pending을 만들지 않고 바로 answered에 넣어, 다음 tick에 피드백 문구 없이 카드를 넘김. 탭 미반영 재시도 없음(그 문항은 오답/미응답 가능). 클래스카드가 피드백 전 스와이프를 받는지 **기기 검증 필요**.
1.22 단어 수집 스크롤 끝 멈춤(`collect_end_hold`, 기본 켜짐; 패널 ‘수집’ 탭·설정>단어 읽기·수집): BotService.scrollCollection이 gesture(..., endHoldMs=150)을 호출 → 첫 스트로크를 willContinue=true로 보내고 완료 콜백에서 같은 끝점에 정지한 continueStroke(150ms, willContinue=false)를 이어 보내 손을 뗄 때 속도 0 → 목록 관성(fling) 없음. 이어 보내기 실패 시 기존 취소 처리. 관성이 없어져 한 번에 넘어가는 실제 거리가 줄 수 있음(범위 % 재조정 필요할 수 있음). **기기 검증 필요**.
1.23 스와이프 시각화(SwipeGuide: 전체 화면·터치 불가(FLAG_NOT_TOUCHABLE) 오버레이, 접근성 노드 없음): ① 수집 중 스와이프마다 범위 띠·시작점·끝 화살표·손가락 점·"n%"를 그리고, 제스처 완료 콜백에서 바로 지워 다음 화면 읽기/OCR 전에 사라짐(`collect_show_swipe`, 기본 켜짐) ② 패널 ‘수집’ 탭 "화면에서 미리 보기": 스와이프 없이 현재 범위·시간 설정으로 그림(BotService.previewCollectionSwipe). 마지막 수집 목록 위치를 쓰고, 아직 없으면 화면 기준 대략값("(대략)" 표시). 오버레이가 화면 읽기·OCR 캡처에 끼는지 **기기 검증 필요**.
1.24 초고속 하위 옵션 "구간 먼저 전부 펼치기"(`turbo_expand_first`, 초고속일 때만): beginCollection이 collect_top 대신 `expand_down` → `expand_up` → `collect` 순으로 진행. expand_down: 관성 있는 큰 스와이프(BotService.flingCollection, 88%→12%, 110ms, 끝 멈춤 없음)를 목록 signature가 450ms 멈출 때까지 기다렸다 반복, 직전과 같으면 맨 아래. expand_up: 목록이 300ms 멈추면 화면 안의 접힌 구간(`titleopenstatus` 미선택 + 같은 줄 `term`) 중 **가장 아래 것**부터 탭(act, 키 expand-<구간명>), 접힌 게 없으면 위로 큰 스와이프, 스와이프해도 화면이 같으면 맨 위 → 일반 수집(collect, 남은 접힌 구간은 기존 로직이 처리). 두 단계는 화면 전환 제한시간 예외 목록에 추가(워치독은 적용). 아래부터 누르면 위쪽 줄이 밀리지 않는다는 전제는 사용자 관찰. **기기 검증 필요**.
1.25 초고속 수집 가속: 안정 판정(StableCollectionRead/StableInlineRead)을 "연속 2회 같음"에서 "같은 값이 MIN_SPAN_MS=90ms 이상 유지"로 바꿔 폴링 주기와 무관하게(일반 100ms 폴링에서는 사실상 기존과 동일) → 1.20에서 뺀 수집 단계 초고속을 다시 적용(ticker 35ms·hold 절반). 초고속 수집: 읽기 사이 최소 간격 400→200ms, 스크롤 간격 최대 250ms, 끝 멈춤이 켜져 있으면 스와이프 최대 220ms·끝 멈춤 110ms. 시작 카운트다운 초고속 3→1초. 1.20의 누락 원인(애니메이션 중 35ms 간격 2회 일치)은 시간 기준으로 막았다고 보지만 **기기 검증 필요**. 누락 시 1.19 재수집(원래 속도 아님 — 재수집도 초고속) 후 찍기.
1.26: 수집 중 스와이프 표시(`collect_show_swipe`) 기본값 꺼짐(미리 보기 버튼은 그대로). 한 번에 넘기는 범위 최대 60→85%(ScrollSettings.RANGE_MAX; 85%면 목록 높이의 97.5%→12.5% 지점, 겹침 15%라 누락 위험 증가 → 누락 시 1.19 재수집).

## 버전 규칙 (사용자 지시, 이후 계속 적용)
- 표기 버전(versionName)은 **'1.0'으로 고정**. 사용자가 "버전 올려"라고 할 때만 올린다. 파일명 MiMiC-V1.0-auth.apk.
- versionCode는 75에 고정(안드로이드는 versionCode가 낮아지면 덮어쓰기 설치 거부, 같은 값은 허용). 사용자가 버전을 올리라고 하면 versionCode도 함께 올린다.
- 위 1.x 이력은 이 규칙 이전의 내부 번호.

(구 1.27 = 현 V1.0) 초고속 항목별 켜기(TurboParts): 마스터 `turbo` + 항목 `turbo_react`(화면 읽기 35ms·hold 절반), `turbo_collect`(수집 읽기 간격 200ms·스크롤 간격≤250ms·수집 스와이프≤220ms/끝 멈춤 110ms), `turbo_test`(시험 카드 스와이프 150ms), `turbo_countdown`(시작 1초) — 기본 켜짐; `turbo_skip_confirm`, `turbo_expand_first` — 기본 꺼짐. BotEngine.turboPart(key)/TurboParts.on(prefs,key). UI: 패널 5번째 탭 ‘속도’(Screens.addPanelGroup group 4), 설정 메뉴 ‘초고속 모드’(SECTIONS[4], 메뉴 순서 SECTION_ORDER). 입력 방식 페이지·입력 탭에서 초고속 항목 제거.
