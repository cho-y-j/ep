# skep-v2 검수 결과 — 수정 안내서 (2026-06-17)

> **성격**: 읽기 전용 정적 검수. **이 검수 과정에서 코드는 한 줄도 수정하지 않았습니다.**
> 아래 항목을 개발자가 직접 수정해야 합니다.
> **범위**: 백엔드(약 31.8k줄/395파일) + 프론트(약 34k줄/195파일) + DB(V1~V70) 전수.
> **검증 표기**: ✅ = 검수자가 원본 코드를 직접 읽어 확인 / 🔍 = 보조 분석 후 근거 일관(개별 전수 재독은 아님).
> 줄 번호는 검수 시점(2026-06-17) 기준. 수정하면서 달라질 수 있으니 심볼로 재확인 바람.

---

## 0. 정직성 고지 / 정정
- 정적 분석이라 **런타임 PoC는 실행하지 않았습니다.** ✅ 항목은 코드 흐름상 명백한 것, 🔍는 추가 확인 권장.
- **이전 보고 정정**: `POST /api/safety-inspections/{id}/complete`의 `resultNotes` camelCase 전송은 **버그 아님** — 컨트롤러가 `@RequestBody Map`에서 `body.getOrDefault("resultNotes", …)`로 **리터럴 키**를 읽으므로 정상. (typed record DTO만 Jackson SNAKE_CASE 영향. 아래 프론트 계약 버그와 구분.)

---

## 1. CRITICAL — 즉시 조치

### CR-1. WebSocket 안전알림: 무인증 전체 테넌트 작업자 PII·건강·위치 실시간 유출 ✅
- **위치**: `backend/.../safety/WebSocketConfig.java:15-27`, `safety/SafetyAlertBroadcaster.java:24-46`, `config/SecurityConfig.java:66`
- **증상**: STOMP 브로커에 **메시지 레벨 인가가 전무**(`ChannelInterceptor`/`configureClientInboundChannel`/`@EnableWebSocketSecurity` 없음, grep 확인). `/ws`·`/ws-raw`는 `permitAll` + `setAllowedOriginPatterns("*")`. 서버는 `/topic/safety-alerts/all`(전역 토픽)로 `person_name`·`person_phone`·`hr`·`spo2`·`lat`·`lng`를 발행.
- **재현**: 인증 없는 클라이언트가 `wss://<host>/ws-raw/websocket` 연결 → `SUBSCRIBE /topic/safety-alerts/all` → 모든 회사 모든 작업자의 이름·전화·심박·SpO2·실시간 GPS 수신. `site-{id}`도 정수 열거로 구독 가능.
- **영향**: PIPA 민감정보(건강)+개인위치정보 대량 유출.
- **수정 방향**: `configureClientInboundChannel`에 `ChannelInterceptor` 추가 → CONNECT 시 JWT 검증, SUBSCRIBE 시 목적지를 actor의 role/company로 인가(ADMIN 전체, BP는 자기 `bp_company_id` 현장만). 전역 `/all` 토픽 제거하고 회사/현장 단위로 라우팅. 미사용 `/app` prefix 제거.
- (부수) `SafetyAlertBroadcaster.java:50` danger FCM이 `persons.findByFcmTokenIsNotNull()` — 주석은 "같은 현장"이나 **site 필터 없음** → 전 회사 작업자에게 긴급 푸시. 같은 site/회사로 한정 필요.

---

## 2. 기능 파손 (Critical급) — 핵심 화면/흐름이 실제로 동작 안 함

### C-1. 지정견적(TARGETED) 수락/거부 100% 실패 ✅
- **위치**: `backend/.../quotation/QuotationService.java:584` (+ `create()`의 `.siteId(null)` `:304`)
- **증상**: 모든 `QuotationRequest`가 `siteId=null`로 저장되는데(create가 클라이언트 siteId 무시), `respond()`가 `sites.findById(qr.getSiteId())` 호출. Spring Data `findById(null)`은 `IllegalArgumentException`을 던짐 → `markAccepted/markRejected` 적용 후 롤백.
- **재현**: 공급사가 `POST /api/quotations/{id}/targets/{targetId}/respond` 호출 → 500.
- **수정 방향**: `respond()`에서 `sites.findById(qr.getSiteId())` 제거하고 `qr.getBpCompanyId()`를 직접 사용(클래스 내 다른 메서드는 모두 그렇게 함). `:600` 등의 `qr.getSiteId()` 알림 인자도 점검.

### C-2. 작업확인서 수정 시 정산시간(totalHours)이 0으로 소실 ✅
- **위치**: `backend/.../workconfirmation/WorkConfirmationService.java:366` (`update`) + `:479` (`recalcTotal`)
- **증상**: 자동 생성된 작업확인서는 슬롯(오전/오후/연장/철야)=null, `totalHours`=실근무시간으로 채워짐. `update()`가 **무조건** `recalcTotal()` 호출 → 4개 슬롯(전부 null=0) 합산으로 `totalHours`를 **0으로 덮어씀**. 작업내용/비고만 수정해도 청구시간 소실.
- **재현**: 퇴근 자동생성 WC(예: 8.0h) → 작업내용만 수정·저장 → totalHours=0.00.
- **수정 방향**: 슬롯이 하나라도 입력된 경우에만 `recalcTotal`을 적용하거나, 자동생성 시 슬롯도 채우거나, totalHours를 슬롯 합과 분리 관리. (필드 서명 경로 `FieldAuthController:284`는 totalHours 직접 세팅이라 별개 — 상한 검증 없음도 같이 보완.)

### C-3. 작업확인서 화면(프론트) 읽기 전체 무력화 — camelCase/snake_case ✅
- **위치**: `frontend/src/features/workConfirmation/WorkConfirmationSection.tsx:9-32, 94, 155, 234, 246`
- **증상**: 백엔드 `WorkConfirmationResponse`(record)는 Jackson `SNAKE_CASE`로 `person_id`·`work_content`·`supplier_signed`·`work_date`… 직렬화. 프론트는 `wc.personId`·`wc.supplierSigned` 등 **camelCase로 읽어 전부 `undefined`**. → 인원 그룹핑 깨짐, 서명완료가 항상 미서명 표시(서명버튼 항상 활성), 날짜/시간/내용 공란. (쓰기 경로는 snake_case라 정상 — **읽기만** 깨짐.)
- **수정 방향**: 인터페이스/읽기를 snake_case로 통일. **아래 §4의 프론트 계약 버그를 일괄 점검** 권장(같은 패턴 다수).

---

## 3. HIGH

| ID | 위치 | 증상 / 수정 |
|---|---|---|
| H-1 ✅ | `quotation/pdf/QuotationPdfService.java:65,130` | 견적서 PDF `render`가 actor 없이 **전 공급사 dispatched 단가** 출력 → 멀티공급사 견적에서 선정 공급사 A가 경쟁사 B 단가 획득. **수정**: actor 받아 자기 회사 행만 필터(형제 `listByRequest`처럼). |
| H-2 ✅ | `resourceCheck/ResourceCheckController.java:77` `ResourceCheckService.java:232` | `GET /api/resource-checks/work-plan/{id}`가 actor 미수신·스코프 전무 → 아무 인증유저가 임의 작업계획서 자원점검 전수 열람. **수정**: actor 받아 BP/공급사/현장 스코프 적용. |
| H-3 ✅ | `frontend/.../admin/DocumentTypeAdminPage.tsx:104-110,192,200` | 문서타입 생성·수정이 `appliesTo`/`hasExpiry`/`appliesToPersonRoles`(camel) 전송 → typed record(snake) 미바인딩. 생성 시 속성 누락, 수정은 낙관적 UI로 성공처럼 보이나 **미저장**. **수정**: snake_case 전송. |
| H-5 ✅ | `quotation/proposal/QuotationProposalService.java:215,229` | finalize 비관적 락이 *제안 행*만 잠금, `QuotationRequest`에 `@Version` 없음 → 서로 다른 제안 동시 finalize 시 count 검사 둘 다 통과 → **요청 수량 초과 선정**. **수정**: `QuotationRequest` 행 잠금(SELECT…FOR UPDATE) 또는 `@Version`. |
| H-6 🔍 | `verify/VerifyClient.java:126,157` + `OcrPreviewController.java:66` | OCR `.block()`가 **요청 스레드에서 최대 60s** 블로킹 → verify-api 지연 시 톰캣 스레드풀 고갈=전체 무응답. **수정**: 비동기화 또는 짧은 타임아웃+분리 스레드풀. |
| H-7 🔍 | `alimtalk/AlimTalkService.java:43` + `QuotationService.java:968`, `ResourceCheckService.java:285` | 알림톡/SMS가 **수신자별 동기 HTTP(최대 15s)를 요청 트랜잭션 안에서 루프** → 견적/점검 생성 시 스레드+DB커넥션 수십초 점유. **수정**: 발송을 트랜잭션 밖/비동기로. |
| H-8 ✅ | `frontend/.../workPlan/create/components/SignaturePanel.tsx:128,302-359` | `s.signerName/signerEmail`(camel) 읽기→항상 빈값. 게다가 **15초 폴링이 사용자가 입력 중인 이름/이메일을 매번 `''`로 덮어씀**(파괴적). **수정**: snake_case 읽기 + 입력 중 폴링 덮어쓰기 방지. |
| H-9 ✅ | `frontend/.../components/ClientOrgHistory.tsx:84-123`, `features/signature/SignaturePage.tsx:90` | `clientOrgId/periodStart`·`roleLabel/signerName` camel 읽기 → 원청이력·**외부 서명자 페이지 역할/이름** 공란. **수정**: snake_case. |
| H-PII ✅ | `document/PiiImageMasker.java:27-64` + `DocumentService.java:197-205` | PII 마스킹이 **fail-open**(비대상·PDF/HEIC·OCR미검출·예외 → null → 원본 저장) + **키워드 공백 불일치**: `"사업자등록"`⊅`"사업자 등록증"`, `"건강검진"`⊅`"건강진단서"`, `통장 사본`·`4대보험` 미커버 → 원본 RRN 저장. `verify.enabled=false`면 전 PII 원본 저장. **수정**: 키워드를 실제 타입명과 일치(정규화/포함관계 재설계), 마스킹 실패 시 정책 결정(거부 또는 명시 경고), 성공 시 RRN 뒷7자리뿐 아니라 필요한 영역 확대 검토. |
| M-2 ✅ | `safety/SafetyAlertController.java:144-172` | `person/{id}/vitals`가 역할게이트(ADMIN/BP)만, 회사 스코프 없음 → 어느 BP나 임의 작업자 **건강 바이탈** 열람. **수정**: alert의 `bpCompanyId`/현장 소유 검증 추가. |

---

## 4. MEDIUM

### 인가/보안
- **M-1** ✅ `attendance/AttendanceController.java:108` `by-work-plan/{wpId}` — actor 주입했으나 미사용, 무스코프 → 임의 WP 출퇴근 열람.
- **M-3** ✅ `equipment/EquipmentInspectionController.java:85` `daily-inspections` — `ensureCanView`가 BP 무조건 통과 → 타사 장비 점검이력 열람.
- **M-4** ✅ `equipment/EquipmentInspectionController.java:30` `field-auth/equipment-inspection` — 출석코드만으로 임의 `equipmentId`에 점검 위조 기록.
- **M-5** ✅ `quotation/bundle/DocumentBundleService.java:115` — 게이트 후 전 공급사 서류묶음 메타 반환(공급사 필터 없음).
- **M-6** ✅ `bootstrap/AdminBootstrap.java:25` — prod 약비번 블록리스트 `{change-me-now, admin, admin1234}`가 실제 `.env`값 `admin1234!`를 통과(접미사 `!`). 강도 규칙으로 교체.
- **M-7** ✅ 전역 — **레이트리밋/계정잠금 전무**. 로그인 무차별 대입 + 출석코드(6자 약 29비트, 영구·만료없음)=베어러 토큰. 레이트리밋/락아웃 필요.
- **MA-1** 🔍 `worksheet/WorksheetEditorService.java:243`, `onlyoffice/OnlyOfficeService.java:387` — 콜백 host 검증은 견고하나 비교대상이 `ONLYOFFICE_URL`(=`localhost`)이라 임의 `http://localhost:포트` SSRF 가능. 허용 호스트를 명시 화이트리스트로.
- **MA-2** 🔍 `worksheet/WorksheetEditorService.java:74`, `application.yml:109` — 워크시트 콜백 JWT 시크릿 기본값이 하드코딩 약값, OnlyOfficeProperties와 달리 **부팅 강도검증 없음**. 동일 가드 추가.
- **MA-3** 🔍 `verify/VerifyClient.java:59`, `NtsBizClient.java:57`, `KmaWeatherClient.java:51` — API 키 앞/뒤 4자를 INFO 로그에 기록. 제거.
- **알림톡 sender key 커밋** ✅ `docker-compose.yml:74` — `SKEP_ALIMTALK_SENDER_KEY` 기본값에 실값 `691d93…4cc8` 하드코딩(파일 git 추적됨). 라이브 키면 회수+env 전용.
- **공개 엔드포인트 정보유출** 🔍 `signature/SignatureController.java:84` 등 — `e.getMessage()` 원문 반환. 일반 메시지로.
- **전화번호 로깅** 🔍 `sms/SmsService.java:38` — 전화번호 평문 로깅(타 클라이언트는 마스킹). 마스킹 통일.

### 정합성/로직
- **만료큐 체인헤드 누락** ✅ `document/DocumentRepository.java:105,112,154,201` — 만료 카운트/ADMIN 만료큐 쿼리에 `NOT EXISTS(체인헤드)` 필터 없음 → 갱신된 옛 서류 중복 집계(재업로드해도 안 사라짐). 다른 쿼리처럼 NOT EXISTS 추가.
- **graceful-fail 오매핑** 🔍 `verify/VerificationService.java:282` — `BIZNO_MALFORMED` 등이 OCR_REVIEW가 아닌 REJECTED로 떨어짐(정책4 위반).
- **점심 1h 미차감** ✅ `field/FieldAuthController.java:212` — 주석은 "근무-1h 점심"인데 실제론 기록된 break만 차감 → 휴식 미기록 시 1h 과다 산정.
- **다중 세션 누락** ✅ `field/FieldAuthController.java:210` — 같은 날 2번째 출퇴근분이 작업확인서에 미반영(첫 세션만).
- **MA-4** ✅ `document/DocumentService.java:226-242` — 수동입력 필드 JSON 손수 조립이 제어문자(\n,\t) 미이스케이프 → `VerificationService:110` 파싱 실패 시 수동필드 전부 무음 폐기. `ObjectMapper`로 직렬화 권장.
- **MA-5** ✅ `frontend/.../document/AdminExpiringDocumentsPage.tsx:10` — `new Date("YYYY-MM-DD")`(UTC) vs 로컬자정 비교 → 만료 임박 하루 off-by-one(형제 위젯과 불일치).
- **DOCX 표 행 반복 미구현** 🔍 `docx/WorkPlanDocxExporter.java:164,74` — 장비/인원 N건이 한 셀에 `\n`으로 뭉쳐 한 줄로 렌더(Word가 개행 무시). 활성 경로는 XML escape 적용으로 인젝션은 안전.

### 성능(운영 시 부각)
- **N+1 핫스팟** 🔍 `QuotationService.candidates():115`(견적요청 화면=공급사·장비 전수×compliance), `toResponse():881`(요청당+타깃당 findById, ADMIN 무페이징), `DocumentSupplementService.toResponse():306`(행당 5~6 findById, ADMIN findAll), 서류 리뷰/만료큐, `GET /api/users`·`/api/companies` 무페이징. findAllById 배치 + 페이징으로.

### 프론트 계약 일괄(snake/camel) — 같은 패턴이라 함께 수정
| 파일:라인 | 키 | 방향 |
|---|---|---|
| WorkConfirmationSection.tsx:9-32,90-360 | personId, workContent, *Hours, supplierSigned, bpSigned, workDate … | 읽기 (C-3) |
| SignaturePanel.tsx:128,302-359 | signerName, signerEmail, signedAt | 읽기 (H-8) |
| ClientOrgHistory.tsx:84-123 | clientOrgId, clientOrgName, periodStart, periodEnd | 읽기 (H-9) |
| SignaturePage.tsx:90 | roleLabel, signerName | 읽기 (H-9) |
| DocumentTypeAdminPage.tsx:104-110,192,200 | appliesTo, hasExpiry, … | 전송 (H-3) |
| DocumentBundleSection.tsx:296 | includeEmail | 전송 |

> 규칙: typed `@RequestBody record`/반환 DTO·엔티티는 **snake_case**(Jackson). 단 `@RequestBody Map` + `body.get("키")`나 `Map.of("키", …)`는 **리터럴 키**(camel 가능). 이 구분으로 오탐 방지(resultNotes·pngBase64는 Map 리터럴 → 정상).

---

## 5. 안전 확인된 것 (좋은 부분)
인증 코어(JWT 서명/issuer·리프레시 회전 CAS·시더 prod 가드)·서류 다운로드 인가(`ensureCanAccess`+CSP+매직바이트)·OnlyOffice/전자서명 토큰 IDOR 안전·SQLi/XXE/zip-slip/경로순회/unsafe-deser **없음**·반사 XSS 없음·프론트 라우트가드 정상·DB 엔티티↔마이그레이션 정합(boot-break 없음)·CI deploy.yml 인젝션 없음·DB/Redis 127.0.0.1 바인딩.

---

## 6. 권고 수정 순서
1. **CR-1**(WebSocket) · **C-1/C-2/C-3**(기능 파손) — 최우선.
2. **H-1·H-2·M-2·H-PII**(데이터 유출) · **H-5**(초과선정).
3. **H-3·H-8·H-9 + §4 프론트 계약** 일괄 · **H-6·H-7**(가용성) · **MA-1/MA-2**(SSRF).
4. 만료큐 체인헤드 · graceful-fail · M-6/M-7 · 시간계산 · N+1 · MA-3/로그/키.
