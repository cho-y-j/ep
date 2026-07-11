# 갤럭시워치 낙상감지 개선 — 구현계획 (planning/Fable 산출)

대상: `app/watch-app` (Wear OS, Kotlin, AGP 8.2.2 / Gradle 8.5 / compileSdk 34). 핵심: `app/watch-app/app/src/main/java/com/dainon/skep/service/SensorService.kt`(1521줄).

> **자동 실행 범위 = Phase 0~2(저위험)만.** Phase 3+ (센서융합·Enforce·임계확정·2단계)는 실기기 데이터 + 사용자 결정(Q1~Q9) 필요 → 지금 하지 않음.

## Phase 0 — 빌드 게이트 (실측 결과)
- JDK: 시스템 java=21. **AGP 8.2.2는 JDK 17 필요** → `JAVA_HOME=$(ls -d ~/.local/jdk-17* | tail -1)` 사용.
- **`google-services.json` 저장소에 없음 = 컴파일 블로커.** phone/watch 둘 다 `com.google.gms.google-services` 적용(`build.gradle.kts:4`), applicationId `com.dainon.skep`(Firebase `skep-30bc5`). → 실제 json 없으면 `processDebugGoogleServices` 실패로 `compileDebugKotlin`도 막힐 가능성 높음.
- SDK: `/home/cho/Android/Sdk`에 `platforms/android-35`만(34 없음 — AGP 자동설치 시도 가능). `local.properties` 없음 → `sdk.dir=/home/cho/Android/Sdk` 필요(**gitignore 미등재 → 커밋 금지**).
- 네트워크/gradle 동작함(이전 시도서 확인).

**대응:** local.properties 생성(커밋 금지) + JDK17 지정 후 `./gradlew :app:compileDebugKotlin` 시도. google-services로 막히면 → **컴파일 검증 불가**를 정직히 보고(실기기 검증은 사용자 몫). 컴파일-전용 더미 json은 gitignore·APK 미사용 전제로만, 막힐 때 최후수단.

## Phase 1 — #1 가속도 샘플링 50Hz (저위험)
- `SensorService.kt:398`: `SENSOR_DELAY_UI` → `SENSOR_DELAY_GAME`(~20ms, 50Hz). 주석 갱신. 안전근거: 낙상 판정 시간창 전부 ms 기반이라 레이트 독립, activityLevel은 평균이라 불변, WorkerState 무변경.
- `CalibrationActivity.kt:127`: 동일 `SENSOR_DELAY_UI` → `SENSOR_DELAY_GAME` (학습/적용 레이트 일치 — 임계 왜곡 방지).
- `CalibrationActivity.kt:153`: `if (accelSamples.size < 2000)` → 5샘플당 1개 추가(실효 10Hz), 상한 6000. `:152` 주석 실제화.

## Phase 2 — #3 HR 게이트 제거 (저위험)
원래 목적: `heartRate>0`("착용 확인"). 실효는 (a)시작~첫HR 사이 낙상 OFF(FN 데드존) + (b)WATCH_REMOVED 후 차단인데 (b)는 `:520-527` 상태스킵이 이미 담당 → **중복 게이트, FN만 남김.**
- `SensorService.kt:538-539`: 자유낙하 조건에서 `&& heartRate > 0` **제거**. 착용 게이트는 `:520-527` WATCH_REMOVED 스킵이 계속 담당.
- `SensorService.kt:546-549`: `heartRate<=0 && zeroCount>=5` 자유낙하 취소 분기 = 도달불가 사문(그 전에 WATCH_REMOVED로 `:521` 리턴) → 함께 삭제. 도달불가 논증 커밋 메시지에.
- `SensorService.kt:609-616`(HR연속0 시 FALL 취소) = **이번엔 현행 유지(옵션 A)**. 옵션 C(낙상 증거 연동)는 Phase 3에서. (Q4)
- **회귀 금지(무변경)**: 절전 진입(:592-597), EMERGENCY 유지(:600-604), WATCH_REMOVED 진입+10분 자동종료(:633-636,:700-704), HR복귀 10초 안정화(:886-899), FALL/EMERGENCY 타이머 HR=0 진행(:904-906).

## 검증 (이번 범위)
- 컴파일: 위 Phase 0 대응 후 `compileDebugKotlin` 통과 여부(실제 출력). 막히면 정직 보고.
- **실기기 검증은 사용자 몫**(여기 device 없음): 50Hz 실수신, 시작구간/HR접촉불량 낙상 모사, WATCH_REMOVED 회귀 등.

---

## 지연(사용자 결정 + 실기기 필요) — 자동 실행 안 함
- **Phase 3 (#2 자이로/기압 융합 + #4 충격후정지)** — Shadow 모드 설계됨(발보 무변경, 증거 로그만). 임계 Q1(G_th 2.5rad/s, P_th +0.06hPa, S_still 1.0m/s²)·윈도우 Q2(2.0s)·gyro레이트 Q6·FallDetector분리 Q7 결정 필요 + 실기기 데이터로 튜닝.
- **Phase 4 (#6 impactThreshold 하한 12→15)** — 50Hz 캘리브레이션 실데이터 확인 후.
- **Phase 5 (Enforce 전환)** — Shadow 현장데이터 검토 후 별도 승인.
- **#5 2단계(저전력→고출력)** — 후순위 권고(기압 상시필요 등 이유로 지금 부적절).

## 사용자 결정 필요 (Q1~Q9) — 요약
Q1 융합 임계 초기값 / Q2 확인윈도우 2초(알림 2초 지연 허용?) / Q3 Enforce 전환 시점 / Q4 HR=0 시 FALL 취소정책(A/B/**C권장**) / **Q5 [중대] 낙상→EMERGENCY 대기가 안내문구 "5초"인데 실제 `ACK_TIMEOUT_SEC=300`(5분)** — 무의식 낙상 5분 지연. 낙상전용 타임아웃(30~60초) 분리 권장 / Q6 자이로 레이트(UI 제안) / Q7 FallDetector 분리+유닛테스트(권장) / Q8 배터리 예산 / Q9 google-services.json 실파일 제공 가능?

## 발견한 버그/불일치 (참고)
- **Q5의 5분 지연**(안내 "5초" ↔ 코드 300초). 안전 직결.
- `SensorService.kt:552` 주석 "2초" ↔ 실제 `FALL_WINDOW_MS=1000L`.
- `CalibrationActivity.kt:152` 주석 "100개마다 1개" ↔ 실제 전부 추가.
