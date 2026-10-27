# PC에서 이어서 작업하기

## 준비물
- JDK 17 이상, Android SDK (platform 35, build-tools 35.0.0), Gradle 8.14.3 (래퍼 없음 → 직접 설치하거나 Android Studio가 처리)
- `local.properties`에 `sdk.dir=<SDK 경로>` 작성 (저장소에 포함하지 않음)

## 빌드
```
gradle :app:assembleDebug
```
결과: `app/build/outputs/apk/debug/app-debug.apk` (디버그 키 서명. 같은 키로 서명해야 이전 버전 위에 설치됨 →
기존 `~/.android/debug.keystore`를 쓰는 PC가 다르면 앱을 삭제 후 재설치하고 인증키도 다시 넣어야 함)

## 구성
- `app/` : 앱 소스 (Java, 일부 Kotlin). 버전은 `app/build.gradle`
- `solver/` : Matcher·HumanTiming 등 순수 로직의 JVM 테스트 (`src/*Test.java`)
- `preview-proj/` : Paparazzi로 UI를 PNG 렌더링하는 별도 프로젝트 (`recordPaparazziDebug`)
- `issuer/` : 인증키 발급 스크립트. **`issuer_private.pem`(개인키)은 보안상 저장소에 없음.** 이전에 받은 issuer.zip 안의 파일을 이 폴더에 복사할 것.
- `reference/` : 원본 디컴파일 Solver 보존본

## 주의
- 기기에서의 실행은 아직 검증되지 않음 (README.md의 검증 상태 참고).
