# ClassCardAuto (재구성 소스)

`ClassCardAuto-0.6.11-no-cert` APK를 jadx로 디컴파일해 Gradle 프로젝트로 재구성한 것이다. 원본은 Kotlin이지만
디컴파일 결과가 Java이므로 소스는 Java이고, 새로 추가한 보조 코드만 Kotlin(`ResultCompat.kt`)이다.

빌드: `gradle :app:assembleDebug` (Android SDK 35, build-tools 35.0.0, JDK 17+)

## 원본과 다른 점
- `Solver.java`: `Matcher.java`를 호출하는 어댑터로 교체. 원본 디컴파일본은 `reference/`에 보존.
- `Matcher.java`(신규): 정확도 개선판 매칭 로직(OCR 오차 보정, 표기 변형, 동점 보조판별). 단위테스트는 `src/test` 아님,
  `MatcherTest.java`(main 기반)로 별도 실행.
- `QuietJson`, `Sneaky`, `ResultCompat`: Kotlin에는 없는 checked exception / 인라인 클래스 `Result` 때문에 필요한 호환 코드.
- `CertificationReceiver.onReceive`: 디컴파일 실패. manifest에서 `enabled=false`라 no-op 스텁으로 둠.
- 디컴파일이 실패했거나 분기가 유실된 메서드는 바이트코드(dexdump)와 대조해 손으로 복원:
  `BotEngine.readCollectionNode$lambda$105`, `readInlinePage`, `readInlinePage$lambda$99`, `writeSummary`, `test`(else 분기),
  `MainActivity.settings$lambda$47`, `UiProfile(Context)`, `CollectionCursor.observe`, `CertificateVerifier.verify$field`,
  `OcrReader.getSession`/`readDetailed`, `BotService.gesture$lambda$24`, `BotService$ocrNodes$2`.
- onnxruntime / kotlin-stdlib은 디컴파일본 대신 Maven 의존성을 사용.

## 검증 상태
- 컴파일 및 APK 패키징은 확인. 기기 실행·실제 클래스카드 화면 테스트는 하지 않았다.
- 복원한 메서드 외의 나머지 디컴파일 코드는 컴파일만 통과했을 뿐 원본과 동치임을 증명하지 못했다.
