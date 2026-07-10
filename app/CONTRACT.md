# skep-app — 현장 작업자 안전/출석 앱 계약서 (v0)

> 모든 조각(백엔드·폰·워치)이 이 계약을 기준으로 맞물린다. 변경 시 여기부터 고친다.

## 대전제
- 앱 패키지: **`com.dainon.skep`** (폰·워치 동일)
- 백엔드: **skep-v2 (Spring Boot, `verify/skep-v2/backend/`)** 만 사용. `safe/`의 Node 서버는 **안 씀**.
- 참조(템플릿): `verify/safe/` (SafePulse) — 폰/워치 구조·로직을 **복사·참조**. ⚠️ **`safe/`는 절대 수정·커밋 금지** (참조 전용).
- 새 코드 위치: `verify/skep-app/{phone-app, watch-app}`. 백엔드 변경은 `verify/skep-v2/backend/`.
- 도메인: **실외 공사현장** 작업자. (인천공항/실내 아님 — GPS 사용 OK)
- JSON 네이밍: skep-v2 컨벤션 = **snake_case** (전역 Jackson SNAKE_CASE). 단, 일부 raw Map 파싱 엔드포인트는 camelCase일 수 있음 — 신규 엔드포인트는 DTO+snake_case로 통일.

## 컴포넌트 책임
- **phone-app** (Android): ① 온보딩(작업자 등록) ② 출석(GPS 현장 게이트) ③ 공지 풀스크린 수신(크게) ④ 워치 안전알림 중계(Wearable Data Layer → 서버)
- **watch-app** (Wear OS): 바이탈 모니터(HR/SpO2/낙상) → 이상감지 → 폰으로 안전알림 발신. `safe/watch-app`(작동하는 Wear OS 앱) 템플릿.
- **backend** (skep-v2): 아래 `/api/field-auth/*`. Person·WorkPlan(현장 좌표)·기존 인프라 재사용, FCM은 신규.

## 작업자 신원 (실제 구현)
- 별도 `FieldWorker` 엔티티 없음. 작업자는 skep-v2 **`Person`**(공급사 소속) 을 그대로 재사용.
- 인증 = 아이디/비번 로그인(`POST /api/field-auth/login`) 또는 출입 코드(`POST /api/field-auth/auth`) → 발급 **`token`(= 출퇴근/출입 코드, attendance_code)** 을 헤더 `X-Field-Token` 으로 사용. skep-v2 기존 JWT(Bearer)와 분리.
- 현장·작업은 skep-v2 **`WorkPlan`**(site_lat/site_lng/site_radius_m 포함) 재사용. 지오펜스 반경은 WorkPlan 의 `site_radius_m`(기본 300m).
- 워치는 폰에서 `worker_id`/`token`/`server_url` 수신(Wearable Data Layer path `/skep/worker_id`).

## API 계약 (skep-v2, prefix `/api/field-auth`, snake_case, 인증 `X-Field-Token`)
| 메서드 | 경로 | 요청 | 응답/비고 |
|---|---|---|---|
| POST | `/api/field-auth/login` | `{username, password}` | `{token, person_id, name, job_title?, supplier_id?, supplier_name?, has_photo}` |
| POST | `/api/field-auth/auth` | `{code}` | 위와 동일(코드 인증) |
| GET | `/api/field-auth/me` | — | 본인 정보 + `active_work_plans[]`(site_lat/lng/radius_m, check_in_at, ...) |
| POST | `/api/field-auth/register-token` | `{fcm_token}` | FCM 토큰 등록 |
| POST | `/api/field-auth/check-in` | `{work_plan_id, photo_key?, lat?, lng?}` | 200 `SessionResponse{check_in_at,...}` / 현장 밖 403 `{code:"OUT_OF_SITE", distance_m}` |
| POST | `/api/field-auth/check-out` | `{work_plan_id, photo_key?, lat?, lng?}` | `SessionResponse{check_out_at, hours,...}` |
| POST | `/api/field-auth/break/start` · `/break/end` | `{work_plan_id}` | 휴식 시작/종료 |
| GET | `/api/field-auth/my-attendance` | — | 본인 출/퇴근 기록 |
| GET | `/api/field-auth/work-confirmations` · POST `/{id}/sign` | 조회 / `{total_hours, remarks?, signature_png_base64}` | 작업확인서 조회/서명 |
| POST | `/api/field-auth/issue-report` | `{work_plan_id, category, message}` | 현장 문제 신고 → BP 알림 |
| GET | `/api/field-auth/nfc/{tagId}` · POST `/equipment-inspection` | — / 점검 체크리스트 | NFC 식별 / 장비 일상점검 |
| POST | `/api/field-auth/upload-photo` (multipart) · GET `/my-photo` · GET `/map` | — | 출석 사진 업로드 / 아바타 / 지도 WebView |
| POST | `/api/field-auth/emergency` | `{kind, level, hr?, spo2?, lat?, lng?}` | **워치 안전알림 중계**(폰) + 워치 직접 호출 |
| POST | `/api/field-auth/sensor` | `{hr, spo2, bodyTemp, stress, state, lat?, lng?}` | 워치 5분 주기 센서 |
| GET | `/api/field-auth/alerts/recent?limit=` | — | 워치 최근 알림 |
| POST | `/api/field-auth/register-watch-token` | `{fcm_token}` | 워치 FCM 토큰 등록 |
| POST · GET | `/api/field-auth/baseline/sync` · `/baseline/restore` | — | 워치 바이탈 베이스라인 |

- BP·공급사(발주사)는 skep-v2 기존 웹 계정 = **Bearer JWT**(`POST /api/auth/login`, `GET /api/auth/me`, `/api/notifications`, `/api/work-confirmations/*`, `/api/field-deployments/*`, `/api/resource-checks/*`, `/api/compliance-orders/*`, `/api/equipment/*`). 작업자용 `field-auth` 와 분리.
- 지오펜스 판정: haversine 거리 ≤ site_radius_m. **서버가 최종 판정**(클라가 보낸 lat/lng로). 클라 반경 폴백 기본 300m.

## 공지 수신 (폰) — "크게 뜨게"
- FCM `data.type=announcement` 수신 → **풀스크린 Activity**(제목+본문 크게, 확인 버튼) + 시스템 알림. SafePulse `FCMService` 참조하되 풀스크린 강화.

## 안전알림 (워치→폰→백엔드→웹)
- 워치 이상감지 → Wearable Data Layer(path `/skep/safety`)로 폰에 전달 → 폰이 `POST /api/field-auth/emergency`. 폰 미연결 대비 워치가 같은 엔드포인트로 직접 호출도 병행.
- 백엔드는 Alert 저장 + 어드민 웹 실시간(skep-v2에 SSE/WebSocket 없으면 polling GET로 시작). 어드민 웹 UI(지도·알림)는 **다음 단계**(이번엔 백엔드 저장+GET까지).
- 워치 로직은 `safe/watch-app`의 SensorService/HealthServiceManager/AlertService 템플릿.

## 빌드/환경
- Android Studio(Windows). compileSdk 34, minSdk: phone 28 / watch 30. Java 17, Kotlin.
- FCM: `google-services.json` (프로젝트 skep-30bc5, 패키지 com.dainon.skep) — `safe/phone-app/app/google-services.json` 복사해서 사용.
- 앱 설정에 백엔드 base URL 입력칸(기본 skep-v2 dev 주소).

## v0 목표
컴파일 가능한 **골격 + 핵심 흐름**(등록→출석 게이트, 공지 수신, 워치→안전알림). 미완성은 각자 보고서에 TODO로 명시. (빌드/실기기 테스트는 사용자가 Android Studio/skep-v2 기동으로 수행)
