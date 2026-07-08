# SKEP v2 구현 작업 로그

> 마지막 갱신: 2026-05-11 (UI 검수 폴리시 + 헤더/알림/검토큐/사용자관리 등 9건)

## 2026-05-11 — Playwright UI 검수 + UI 폴리시

실제 Chromium 으로 4 역할 × 11~14 페이지 일주(총 44 페이지)하고 스크린샷을 검수한 결과 발견된 UI 갭들을 일괄 보강했다.

### 변경 — 헤더 / 글로벌

1. **TopBar 의 무동작 컨트롤 제거** (`TopBar.tsx`, `AppShell.tsx`)
   - `onChange`/`onSubmit`/`onKey` 없는 placeholder-only 검색 input + ⌘K chip 삭제. 페이지별 검색은 본문 카드에 자체 input 으로 유지.
   - 무동작 "도움말 (?)" 버튼 삭제.
   - 알림 배지 하드코딩 "3" → 실제 `/api/notifications/unread-count` 폴링(60초). 클릭 시 `/notifications` 이동.
   - `searchPlaceholder` prop을 사용하던 5개 페이지에서 제거(Equipment/Person/Site/SiteDetail/WorkPlan).

2. **알림 페이지 — 전체 읽음** (`NotificationService`, `NotificationRepository`, `NotificationController`, `NotificationsPage.tsx`)
   - 백엔드 `POST /api/notifications/read-all` + `NotificationRepository.findUnreadFor()` 추가.
   - 페이지 상단 우측 "전체 읽음 (N)" 버튼(미읽음 N건일 때만 노출).

### 변경 — ADMIN 페이지

3. **서류 검토 큐 — verify_result 사람 라벨** (`ReviewQueuePage.tsx`)
   - 컬럼명 "사유 / verify_result" → "검증 사유".
   - `NTS_INVALID` → "국세청 검증 실패 — 사업자번호가 휴/폐업 또는 미등록", `BIZNAME_MISMATCH` → "회사명 불일치 — ...", `UNKNOWN` → "판정 불가 — OCR 결과 부족" 등 사람 라벨 매핑.
   - `rejected_reason` 컬럼도 같은 매핑 적용.
   - 빈 경우 "—" 표시 통일.

4. **사용자 관리 — 부제 + 행 액션** (`AdminUsersPage.tsx`, `UserTable.tsx`)
   - 부제 추가: "회원가입 신청 사용자를 승인하거나, 역할/상태를 변경합니다."
   - 행 끝에 "액션" 컬럼 + 승인 대기 사용자에 한해 "승인" 버튼 (`PATCH /api/users/{id}/enable`).

5. **회사 관리 — 부제 + breadcrumb** (`AdminCompaniesPage.tsx`)
   - 부제: "전체 회사 목록입니다. 행을 클릭하면 상세 정보를 수정할 수 있는 패널이 열립니다."

6. **활동 로그 변경 컬럼** (`AuditLogPage.tsx`)
   - 잘리던 `before:`/`after:` 한 줄 truncate → `<details>` 접힘으로 변경. 클릭 시 monospace + whitespace-pre-wrap 으로 full JSON 표시.
   - 둘 다 비면 "—" 표시.

### 변경 — 목록/상세 빈 값 처리

7. **WP 목록 시간 컬럼** (`WorkPlanPage.tsx`) — `- ~ -` placeholder 제거. start/end 둘 다 비어있으면 단일 "—". 한쪽만 있으면 그쪽만.

8. **사이트 목록/상세 기간** (`SitePage.tsx`, `SiteDetailPage.tsx`) — start_date/end_date 둘 다 비어있으면 "기간 미정", 한쪽만 있으면 그쪽 + "—".

### 변경 — DOCX 템플릿 폼

9. **파일 input 한국어 드롭존** (`DocxTemplatesPage.tsx`)
   - 기본 `<input type="file">` 의 영어 "Choose File / No file chosen" 대신, 한국어 placeholder + 선택된 파일명 표시 + 우측 "찾아보기" 버튼 chip. 클릭 영역 전체로 확장.

### 변경 — 이모지 제거

10. **DocumentManagementPage** ProgressBanner / ResourceCard / ItemRow 의 ✓ ⚠️ 이모지 제거. "준비 완료" / "검증 완료" / "X / Y 항목 완료 (Z%)" 등 텍스트만 사용 (메모리 규칙: Material Icon name).

### 빌드 + 검증

- `docker compose build frontend backend && up -d` 두 차례.
- 진짜 Chromium (mcr.microsoft.com/playwright:v1.58.2-noble) 으로 44 페이지 일주 → console error 0 / 4xx 0 / redirect 0 / empty body 0 / FINDINGS 빈 배열 유지.
- 변경된 페이지 스크린샷으로 시각 확인:
  - 검색바/도움말 사라지고 알림 배지에 실제 unread (예: 28) 표시.
  - 알림 페이지 우측 상단 "전체 읽음 (22)".
  - 서류 검토 큐의 reason 한국어 라벨.
  - 사용자 관리 액션 컬럼에 "승인" 버튼.
  - 활동 로그 "변경 내용 보기 ▾" 토글.
  - 사이트 목록 "기간 미정" / WP 목록 시간 "—".

## 2026-05-11 — E2E 점검 + Hotfix

전체 워크플로우(BP 사업자등록증 자동검증 → 컴플라이언스 평가 → 보완요청 → 견적 → 작업계획서) API smoke 테스트를 진행하고 발견된 3건을 수정했다.

### 검증된 흐름

1. BP 사업자등록증 OCR 업로드 → NTS 검증 → `VERIFIED` (회사명 일치 시) / `OCR_REVIEW_REQUIRED` (불일치 시 사유 자동 기록)
2. `/api/sites/{id}/compliance` — 18 required 항목 중 1 OK = 6% 진척률, `ready_for_work_plan=false` 상태 노출
3. BP → 공급사 보완요청 발송 → 공급사 신규 업로드 시 매칭 type+owner 자동 `RESOLVED` 전환 (resolved_doc_id 저장)
4. 견적 요청(BP) → 응답(공급사 ACCEPTED) → 최종 수락(BP) → 동일 work_date DRAFT 작업계획서 자동 매칭 + `work_plan_equipment.daily_rate=800000`, `source_quotation_target_id=1` 저장
5. 알림 모든 단계 정상 발송 (`QUOTATION_RECEIVED/RESPONDED/FINALIZED`, `SUPPLEMENT_REQUESTED/RESOLVED`, `DOCUMENT_VERIFIED/OCR_REVIEW`)

### Hotfix 1 — `/document-management` 라우트 누락

**증상**: Sidebar 의 "서류관리" / "받은 보완요청" 클릭 시 catch-all `*` 에 잡혀 `/` 로 redirect.
**원인**: `frontend/src/App.tsx` 에 import + Route 미등록 (Sidebar 만 메뉴 추가됨).
**수정**: `import DocumentManagementPage` + `<Route path="/document-management">` 추가.

### Hotfix 2 — DocumentManagementPage 보완요청 payload camelCase

**증상**: BoardTab 에서 "보완 요청 보내기" 클릭 → submit 시 400 `targetOwnerType: must not be null`.
**원인**: 백엔드는 `spring.jackson.property-naming-strategy=SNAKE_CASE` 강제. 다른 페이지는 모두 snake_case 로 보냈으나 신규 `SupplementDialog.submit()` 만 camelCase 키 (`targetOwnerType`, `targetOwnerId`, `documentTypeId`, `contextSiteId`) 로 보냄.
**수정**: 4개 키 → `target_owner_type`, `target_owner_id`, `document_type_id`, `context_site_id` 로 교체.

### Hotfix 3 — WorkPlanEquipmentResponse 가격 필드 미노출

**증상**: 견적 finalize 후 `GET /api/work-plans/{id}` 응답의 equipment 요소에 `daily_rate / monthly_rate / source_quotation_target_id` 가 빠져있어 UI 가 가격을 표시할 수 없음.
**원인**: V23 마이그레이션 + 엔티티에는 추가됐으나 응답 DTO 가 누락.
**수정**: `WorkPlanEquipmentResponse` 에 3개 필드 + `from()` 매핑 추가.

### 변경 파일

- `frontend/src/App.tsx` — 라우트 추가
- `frontend/src/features/compliance/DocumentManagementPage.tsx` — payload snake_case
- `backend/src/main/java/com/skep/workplan/dto/WorkPlanEquipmentResponse.java` — 3 필드 노출
- 컨테이너 재빌드: `docker compose build frontend backend && docker compose up -d`

### 미해결 / 나중에 봐야 할 것

- cloud browser MCP (firecrawl) 토큰 거부 → 실제 브라우저 자동화는 불가, API + 빌드 검증으로 대체
- 동일 doc 에 verify 를 반복 호출하면 알림이 중복 적재됨 (UI 에서 verify 1회 제약 또는 최근 N 분 dedup 검토)
- 검증 성공 후에도 이전 호출의 `rejected_reason` 이 남아있는 케이스 발견 (status=VERIFIED + reason=BIZNAME_MISMATCH). 표시용일 뿐 게이트 영향 없으나 사용자 혼동 우려 → success 시 reason 비우는 게 자연스러움.



## 2026-05-11 — Phase S-11: 서류관리 (작업계획서 전 단계 통합 dashboard)

### 배경

워크플로우는 다음과 같지만 그 사이를 묶는 게이트가 없었음:

```
BP 발주 → 공급사 응답 → BP/ADMIN finalize  ← S-10
                                       ↓
        ───────────── 서류관리 (NEW S-11) ─────────────
        BP 회사 서류 검증 자동 확인 (게이트)
        자원 서류 컴플라이언스 점검 (BP 가 어떤 서류 필요/갖춤/만료/검증 모두 봄)
        부족 → 공급사에 서류 보완 요청 발송 (사유 포함)
        공급사가 갱신 서류 업로드 → 자동 RESOLVED
        모든 ✓ → [작업계획서 만들기] CTA 활성
        ───────────────────────────────────────────────
                                       ↓
                          작업계획서 작성 ← S-9 (BP 사업자등록증 VERIFIED 시점에만)
```

핵심 미싱: **"어떤게 필요한지" 시스템이 자동 판단** + **자원별 컴플라이언스 한눈에** + **보완 요청 도메인**.

### 데이터 모델

**V24 — `document_supplement_requests`**:
```
id, requester_user_id, requester_role,
target_supplier_company_id, target_owner_type, target_owner_id, document_type_id,
context_site_id, context_work_plan_id, reason,
status (OPEN/RESOLVED/CANCELLED),
resolved_doc_id (자동 매칭된 갱신 서류 ID), timestamps
```

**V25 — `document_types` 매핑 컬럼**:
```
applies_to_categories TEXT     -- CSV: 'CRANE,AERIAL_LIFT' 또는 NULL (= 모든 카테고리)
applies_to_person_roles TEXT   -- CSV: 'OPERATOR' 또는 NULL (= 모든 역할)
```
시드: 운전면허증→OPERATOR / 화물운송자격증→OPERATOR / 안전인증서→CRANE,AERIAL_LIFT.

### 컴플라이언스 평가 (`ComplianceService`)

자원 1건의 평가 절차:
1. `document_types.applies_to` + 카테고리/역할 매핑 일치 → "필요 서류 catalog" 산출
2. 자원의 chain head 서류 (각 type 별 최신) 평가
3. 각 항목: present / verified / rejected / OCR_REVIEW_REQUIRED / expired / expiring_soon / open_supplement
4. `requiredTotal` / `requiredOk` / `missing` / `rejected` / `expiring` / `openSup` 카운트
5. `readyForWorkPlan = (requiredTotal == requiredOk && rejected == 0)` 판정

사이트 통합 (`SiteCompliance`): BP 회사 + 사이트 ACTIVE EQUIPMENT_SUPPLIER 자원들 + 사이트 ACTIVE MANPOWER_SUPPLIER 인원들.

### API 7종

```
GET  /api/sites/{id}/compliance             사이트 통합 컴플라이언스
GET  /api/companies/{id}/compliance         회사 단독
GET  /api/equipment/{id}/compliance         장비 1건
GET  /api/persons/{id}/compliance           인원 1건

POST /api/document-supplements              BP/ADMIN 보완 요청 생성
GET  /api/document-supplements              role-aware list
GET  /api/document-supplements/{id}         상세
POST /api/document-supplements/{id}/cancel  요청자/ADMIN 취소

POST /api/admin/document-types              ADMIN catalog 추가
PATCH /api/admin/document-types/{id}        ADMIN catalog 수정 (정책/매핑)
DELETE /api/admin/document-types/{id}       ADMIN catalog 비활성화
```

### 게이트 (BP 사업자등록증 VERIFIED)

`WorkPlanService.create()` — 사이트의 `bp_company_id` 회사 사업자등록증 chain head VERIFIED 검사. 미검증 시 `BP_BIZ_CERT_REQUIRED` 거부.

### 자동 RESOLVED

`DocumentService.upload()` 후크에서 새 서류 업로드 시 같은 `(owner_type, owner_id, document_type_id)` 의 OPEN 보완 요청 자동 close + 요청자에게 알림 (`SUPPLEMENT_RESOLVED`).

### 알림

- `SUPPLEMENT_REQUESTED` — 발신 시 target 공급사 broadcast
- `SUPPLEMENT_RESOLVED` — 자동 resolve 시 요청자

### 프론트

새 페이지 `/document-management` (`DocumentManagementPage`) — 3 탭:
- 사이트별 보드: 사이트 dropdown + BP/장비/인원 컴플라이언스 카드 + 진행률 + [작업계획서 만들기 →] CTA
- 보낸 보완 요청 (BP/ADMIN)
- 받은 보완 요청 (공급사/ADMIN)

자원 항목별 [보완 요청] 버튼 → 다이얼로그 → 사유 입력 → 발송.

Sidebar nav: ADMIN/BP "서류관리" / 공급사 "받은 보완요청".

### 라이브 검증

- V24 + V25 마이그레이션 적용 (now at version v25)
- `/api/sites/1/compliance` 200 (BP)
- `/api/document-supplements` 200
- `/document-management` 200
- Sidebar 새 메뉴 노출

### 운영성

ADMIN 이 catalog 변경 가능:
- 새 서류 종류 추가 (예: KOSHA-MS) → 자동으로 컴플라이언스 평가에 포함
- 매핑 변경 (예: 비파괴검사를 PUMP_TRUCK 에도 적용)
- 정책 변경 (required/blocks_assignment/has_expiry/만료 개월)

`/api/admin/document-types/*` endpoints 로 PATCH 가능. 향후 운영 UI 추가.

## 2026-05-11 — Phase S-10: 장비 견적 요청 도메인

### 배경

BP 가 사이트 운영 시 작업용 장비를 미리 사용 가능 여부 + 단가로 공급사에 제안하고, 공급사 응답 후 최종 채택 → 작업계획서 자원으로 반영하는 흐름이 빠져있었음. 사용자 요구:

1. BP/ADMIN 가 사이트 + 카테고리 + 스펙 + 단가로 견적 발송
2. 사이트 ACTIVE 참여 EQUIPMENT_SUPPLIER 들의 가용 장비를 그룹 표시 (다른 현장 사용 중 자원 제외)
3. 공급사 별 yes/no 응답
4. BP/ADMIN 최종 채택 → WorkPlan 자동 생성/매칭 + 가격 저장
5. ADMIN 은 모든 BP/공급사 견적 read + 모든 BP 대행으로 작성 + 모든 응답 finalize 가능

### 데이터 모델 (V23)

```
quotation_requests
  id, site_id, requested_by_user_id, on_behalf_of_bp_company_id,
  work_period_start/end, equipment_category, spec_text,
  proposed_daily_rate, proposed_monthly_rate, count, notes,
  status (DRAFT/SENT/CLOSED/CANCELLED), timestamps

quotation_request_targets
  id, request_id, supplier_company_id, equipment_id,
  status (PENDING/ACCEPTED/REJECTED/FINAL_ACCEPTED/EXPIRED),
  responded_by/at, response_note,
  finalized_by/at, finalized_to_work_plan_id, finalized_to_wpe_id,
  UNIQUE(request_id, supplier_company_id, equipment_id)

work_plan_equipment 컬럼 추가
  daily_rate, monthly_rate, source_quotation_target_id
  → finalize 시 가격 + 견적 출처 트래킹
```

### API 엔드포인트

```
GET  /api/quotations/equipment-candidates?siteId=&category=
GET  /api/quotations                              role-aware list
POST /api/quotations                              BP self / ADMIN onBehalfOf
GET  /api/quotations/{id}                         상세 (공급사는 자기 target 만)
POST /api/quotations/{id}/cancel                  BP/ADMIN
POST /api/quotations/{id}/targets/{tid}/respond   공급사 (accept/reject + 메모)
POST /api/quotations/{id}/targets/{tid}/finalize  BP/ADMIN — WorkPlan 자동 생성 + WPE add (가격 저장)
```

### 권한

| 역할 | 견적 read | 작성 | 응답 | finalize | 취소 |
|---|---|---|---|---|---|
| ADMIN | 전체 | onBehalfOf 필수 | (대행) | ✓ | ✓ |
| BP | 자기 사이트 | 자기 사이트 | — | 자기 사이트 | 자기 사이트 |
| EQUIPMENT_SUPPLIER | 자기 target 있는 견적 (자기 row만) | — | 자기 target | — | — |
| 그 외 | 차단 | — | — | — | — |

### finalize → WorkPlan 자동 흐름

1. target.status == ACCEPTED 만 finalize 가능
2. 같은 (siteId, work_period_start, status=DRAFT) WorkPlan 검색 — 있으면 그것 사용, 없으면 새로 생성
3. 같은 (workPlanId, equipmentId) WPE 검색 — 없으면 새로 add
4. WPE 에 daily_rate/monthly_rate (request 의 proposed 가격) + source_quotation_target_id 저장
5. target.status → FINAL_ACCEPTED + finalized_to_work_plan_id, finalized_to_wpe_id 채움
6. 마지막 PENDING/ACCEPTED 가 모두 처리되면 request.status → CLOSED 자동
7. 공급사에게 알림 (`QUOTATION_FINALIZED`)

### 가용성 정책

후보 조회 시 다음 조건 만족 장비만:
- `equipment.supplier_id ∈ 사이트 ACTIVE EQUIPMENT_SUPPLIER 참여사`
- `equipment.category == 요청 카테고리`
- `equipment.current_site_id IS NULL` 또는 같은 사이트

### 알림 (NotificationType.QUOTATION_*)

- `QUOTATION_RECEIVED` — 발송 시 각 공급사로 broadcast
- `QUOTATION_RESPONDED` — 공급사 응답 시 BP 회사로
- `QUOTATION_FINALIZED` — finalize 시 공급사로

### 프론트

3 페이지 + Sidebar 메뉴:
- `/quotations` (`QuotationListPage`) — 통계 카드 + 목록 (역할별 view), 상태 필터
- `/quotations/new` (`QuotationCreatePage`, BP/ADMIN) — 사이트→메타→후보조회+다중선택→발송 4단계 단일 페이지
- `/quotations/{id}` (`QuotationDetailPage`) — BP/ADMIN 시점: 응답 표 + finalize/cancel · 공급사 시점: 자기 target 만 + 수락/거부

Sidebar 추가: ADMIN "견적 관리" / BP "견적 요청" / EQUIPMENT_SUPPLIER "받은 견적".

### 라이브 검증

- V23 마이그레이션 적용 (Successfully applied 1 migration, now at version v23)
- `/api/quotations/equipment-candidates?siteId=1` BP1 호출 → 사이트 ACTIVE 참여사 (테스트 장비공급(주)) 의 가용 장비 그룹 응답 확인
- `/api/quotations` 목록 200
- `/quotations` 페이지 200

### v2 deviation (의도)

| 항목 | 결정 |
|---|---|
| 가격 협상 | 공급사 카운터 제안 X — 가격 수락/거부만. 가격 불만이면 거부 + 메모 |
| 가용성 검사 | `current_site_id` 만 — 작업기간 겹침 검사는 향후 확장 (Equipment 도메인에 일정 데이터 X) |
| WorkPlan 매칭 | 같은 사이트 + 같은 work_period_start + DRAFT 만 활용 — 그 외엔 새 WorkPlan 생성 |
| ADMIN 컨텍스트 스위처 | 평면 리스트 + 새 요청 시만 onBehalfOf — UI 단순성 |
| 만료 cron | 없음 — 명시적 cancel 만 |

## 2026-05-08 — Phase S-9-G.2: 사업자등록증 업로드 UX (사이드바이사이드 + OCR preview)

## 2026-05-08 — Phase S-9-G.2: 사업자등록증 업로드 UX (사이드바이사이드 + OCR preview)

### 변경

**백엔드**:
- 새 endpoint `POST /api/documents/ocr-preview` (`OcrPreviewController`)
  - multipart `file` + `ocrType` 받아 verify-api OCR 만 호출 (DB 저장 X)
  - 응답: `{ok, fields, reasonCode?, message?, fullText?}` — fields 는 BUSINESS_REGISTRATION 의 경우 6 키
- `POST /api/documents` 가 추가 form 필드 `manual*` 을 받아 `extracted_data` 에 JSON 으로 저장
  - `manualBusinessNumber`, `manualBusinessName`, `manualRepresentativeName`, `manualStartDate`, `manualAddress`, `manualBusinessType`
- `DocumentService.upload()` overload 추가 — `Map<String,String> manualFields` 파라미터

**프론트엔드**:
- 새 `BusinessRegUploadDialog` (사이드바이사이드):
  - 좌측: 이미지/PDF 미리보기 (`URL.createObjectURL`)
  - 우측: 사업자번호 / 상호 / 대표자 / 개업연월일 / 사업장 소재지 / 업태·종목 6필드
  - 파일 선택 시 자동 `/api/documents/ocr-preview` 호출 → 우측 폼 자동 채움
  - OCR 실패 (verify-api 미가동) 시 amber 배너 + 사용자 직접 입력
  - submit → `POST /api/documents` (manual_* 필드 동봉) + 자동 NTS 검증 트리거
- `MyCompanyPage` 의 사업자등록증 미등록 배너에 "사업자 등록증 업로드" 버튼 추가 → 다이얼로그 트리거
- 업로드 성공 시 `reloadKey++` 로 docs/배너 즉시 갱신

### 검증 결과

업로드 자체는 200 잘 동작 — 사용자가 본 "업로드 실패" 는 자동 검증의 `OCR_REVIEW_REQUIRED` 상태가 amber chip 으로 표시된 것. 새 다이얼로그는 OCR/manual 입력 결과를 사용자 검토 단계에서 명확히 보여줘 검증 흐름이 직관적임.

라이브:
- `POST /api/documents/ocr-preview` 정상 응답 (verify-api off 시 `UPSTREAM_DISABLED` 반환)
- `manualBusinessNumber=...` 등 form 필드 → `extracted_data` JSONB 저장 확인

## 2026-05-08 — Phase S-9-G.1: verify-server 컨테이너 통합 (Google Vision OCR + 정부 API)

### 배경

S-9-G 에서 `OwnerType.COMPANY` + `NtsBizClient` (companies.business_number 직접 사용) 로 NTS 자동검증 구조는 갖췄지만, **이미지 OCR** 은 외부 verify-api 의존 — skep-v2 docker-compose 에 verify-api 가 없어 업로드한 이미지에서 사업자번호/대표자명을 추출할 수 없었음.

사용자 요구: "사업자 이미지 꼭 받기 + OCR 또는 수동 입력 정보 DB 저장 + verify-server 의 Google Vision 도 docker 에서 같이 띄우기".

### 변경

**docker-compose**:
- `external/verify-server/` 에 `old-server-backup/verify-server` 통째 복사 (824K → .git 등 제외 484K). main-api + verify-api 두 Spring Boot 모듈.
- `skep-v2/docker-compose.yml` 에 `verify-api` (port 8081 expose) + `main-api` (port 8080 expose) 두 서비스 추가. `profiles: ["verify"]` 로 옵트인.
- 환경변수: `GOOGLE_VISION_API_KEY`, `NTS_API_SERVICE_KEY`, `PUBLIC_API_SERVICE_KEY`, `RIMS_API_URL/AUTH_KEY/SECRET_KEY`, `TS_CAR_API_KEY`, `VERIFY_API_KEY`.
- `.env.example` 에 verify-server 통합 안내 + 키 발급 URL 명시.

**사업자등록증 OCR 흐름** (verify-api 활성화 시):
1. 사용자 → `/my-company` → 사업자 등록증 업로드 (image/pdf)
2. `AutoVerifyTrigger` → `VerificationService.verifyDocument()`
3. `client.extractOcr("BUSINESS", file)` → `verify-api:/verify/ocr/extract/BUSINESS`
4. verify-api 의 `GoogleVisionOCRService` 가 Google Cloud Vision REST 호출 → `textAnnotations` + `fullTextAnnotation` 파싱
5. `OcrExtractController.parseBusiness()` 가 정규식으로 추출:
   - `businessNumber` (`등록 번호 NNN-NN-NNNNN`, 체크섬 검증 포함)
   - `representativeName` (`대표자` / `성명`)
   - `businessName` (`상호` / `법인명`)
   - `startDate` (`개업연월일` → YYYYMMDD)
   - `address` (`사업장 소재지`)
   - `businessType` (`업태` + `종목`)
6. `extracted_data` JSONB 에 위 6개 필드 저장
7. `NtsBizClient` 가 (OCR 결과 또는 companies.business_number) 로 NTS 호출 → `b_stt_cd` 분기
8. 결과 `verification_result` JSONB 저장 + status (VERIFIED/REJECTED/OCR_REVIEW_REQUIRED)

수동 입력 — 기존 `DocumentVerifyDialog` (S-4 단계 3) 가 OCR 보충/덮어쓰기 입력 받아 `userInputs` 로 `VerificationService.verifyDocument()` 에 전달, 같은 `extracted_data` 에 병합.

**프론트엔드**:
- `MyCompanyPage` 에 사업자 등록증 상태 배너 4단계:
  - 미등록 → rose ⚠️ "자원 등록/작업계획서 차단됨"
  - REJECTED → rose ❌ + `rejected_reason`
  - PENDING / OCR_REVIEW_REQUIRED → amber ⏳ "관리자 검토 대기"
  - VERIFIED → emerald ✓ "국세청 계속사업자 확인됨"
- chain head 만 검사 (재업로드 시 최신 버전만 평가).

### 운영 활성화 절차

```bash
# 1) .env 에 키 입력
GOOGLE_VISION_API_KEY=AIza...
NTS_API_SERVICE_KEY=...
NTS_SERVICE_KEY=...        # skep-v2 자체 NtsBizClient (verify-server 우회)
VERIFY_API_KEY=$(openssl rand -hex 32)
VERIFY_ENABLED=true

# 2) 컨테이너 띄우기 (verify 프로필 추가)
docker compose --profile verify --profile onlyoffice up -d

# 3) backend 재시작 (env 갱신 반영)
docker compose restart backend
```

### 운영 코드 갱신 (Lifton 서버 verify-server 가 더 최신일 경우)

`external/verify-server/` 폴더 통째 교체 → `docker compose --profile verify build verify-api main-api` 후 재시작. SSH 정책 차단으로 자동 동기화는 안 함, 사용자가 직접 받아 갈아끼움.

### 라이브 검증

- 7 컨테이너 모두 Up: `skep-v2-frontend`, `skep-v2-main-api`, `skep-v2-verify-api`, `skep-v2-backend`, `skep-v2-postgres`, `skep-v2-onlyoffice`, `skep-v2-redis`
- `Started VerifyApiApplication in 2.1 seconds`
- `Started MainApiApplication in 2.154 seconds`
- `/my-company` 200, JS 번들에 4단계 배너 문자열 포함

## 2026-05-08 — Phase S-9-G: 회사 단위 서류 (사업자 등록증) + NTS 자동 검증

### 배경

장비공급사/인력공급사/BP 의 회사 자체에 사업자 등록증, 통장 사본, 건설업 등록증 등의 서류가 붙을 자리가 없었음. 또 검증/만료/chain head/검토 큐 인프라 (V14) 가 PERSON / EQUIPMENT 만 지원.

### 변경

**백엔드**:
- `OwnerType` 에 `COMPANY` 추가 (PERSON/EQUIPMENT/COMPANY)
- `V22__company_documents.sql` — 4종 회사 서류 시드:
  - 사업자 등록증 (`required=true`, `blocks_assignment=true`, `verify_endpoint=NTS_BIZ`, `ocr_extract_type=BUSINESS`)
  - 통장 사본
  - 건설업 등록증 (`has_expiry=true`, 60개월)
  - 4대보험 가입증명원
- `DocumentService` — `ownerSupplierIdOrThrow` / review queue 분기에 COMPANY 추가, `CompanyRepository` 의존성 주입
- `VerificationService.ownerSupplierIdOrThrow` switch 에 COMPANY 케이스 (ownerId == supplierId)
- 새 `NtsBizClient` — 공공데이터포털 사업자등록상태조회 API 직접 호출
  - URL: `https://api.odcloud.kr/api/nts-businessman/v1/status?serviceKey=...`
  - 응답 `b_stt_cd` → `01` 계속사업자=VERIFIED, `02` 휴업자/`03` 폐업자=REJECTED
  - 환경변수 `NTS_SERVICE_KEY`. 미설정 시 verify-api fallback, 둘 다 없으면 OCR_REVIEW_REQUIRED
- `VerificationService` 의 `NTS_BIZ` 분기:
  - COMPANY 서류면 `companies.business_number` 직접 사용 (OCR 없이 검증 가능)
  - `ntsClient.isEnabled()` 면 자체 클라이언트 우선, 아니면 verify-api fallback

**프론트**:
- `OwnerType` 타입에 `'COMPANY'` 추가
- 새 페이지 `MyCompanyPage` (`/my-company`) — 회사 기본정보 + `<DocumentSection ownerType="COMPANY" ownerId={company.id}>` 재사용
- 라우트 `roles=['BP','EQUIPMENT_SUPPLIER','MANPOWER_SUPPLIER']` 으로 보호
- Sidebar 3 역할 메뉴 최상단에 "내 회사" 추가

### 자동 검증 흐름 (사업자등록증 업로드 시)

1. 사용자 → `/my-company` → 사업자 등록증 업로드 (이미지/PDF)
2. `DocumentService.upload()` → `DocumentUploadedEvent` 발행
3. `AutoVerifyTrigger` 가 `document_type.requires_verification=true` 보고 자동 트리거
4. `VerificationService.verifyDocument()`:
   - OCR (옵션) — `verify-api` enabled 시 이미지에서 biz_no 추출해 cross-check
   - NTS — `companies.business_number` 또는 OCR 결과 → `NtsBizClient.lookupStatus()`
5. 응답 분기:
   - 계속사업자 (`b_stt_cd=01`) → `VERIFIED` 자동 통과
   - 휴업자 (`b_stt_cd=02`) → `REJECTED` reason `NTS_SUSPENDED`
   - 폐업자 (`b_stt_cd=03`) → `REJECTED` reason `NTS_CLOSED`
   - 외부 API 실패 / 키 미설정 → `OCR_REVIEW_REQUIRED` → ADMIN 검토 큐
6. `verification_result` JSONB 에 NTS 응답 raw 저장
7. 회사 사용자에게 알림 (DOCUMENT_REJECTED / DOCUMENT_REVIEW_REQUIRED)

### 운영 환경 변수

| 변수 | 용도 | 미설정 시 |
|---|---|---|
| `NTS_SERVICE_KEY` | 공공데이터포털 사업자등록상태조회 API 키 | OCR_REVIEW_REQUIRED 로 떨어져 ADMIN 수동 검토 |
| `VERIFY_ENABLED`, `VERIFY_MAIN_API_URL`, `VERIFY_INNER_API_URL`, `VERIFY_API_KEY` | 외부 verify-api OCR (이미지에서 biz_no 추출) | NTS 직접 호출만 사용 |

### 라이브 검증

- V22 마이그레이션 적용 (Successfully applied 1 migration, now at version v22)
- `/api/document-types?appliesTo=COMPANY` → 4종 시드 응답
- `/api/documents?ownerType=COMPANY&ownerId=2` 자기 회사 → 200
- `/api/documents?ownerType=COMPANY&ownerId=99` 다른 회사 → 403
- `/my-company` 페이지 200, JS 번들에 "내 회사" / "사업자 등록증" 문자열 포함
- `NtsBizClient init: serviceKey=(empty)` — 키 설정 시 즉시 자체 검증 활성화

### 남은 작업 (필요 시)

- 이미지 OCR — Naver Clova OCR / Upstage Document AI 의 사업자등록증 전용 템플릿으로 cross-check 추가. 현재는 companies.business_number 직접 사용으로 OCR 없이도 검증 동작
- 회원가입 시점 사업자등록증 강제 업로드 — 현재는 가입 후 `/my-company` 에서 업로드. 가입 흐름에 step 추가 가능
- 작업계획서 컴플라이언스에 supplier 회사 사업자등록증 검사 추가 (자원 추가 시 BLOCKED 차단)
- 폐업 감지 cron — 월 1회 모든 활성 회사 사업자등록상태 재검증

## 2026-05-08 — Sidebar nav 중복 제거

### 배경

`Sidebar.tsx` 의 supplier/BP 메뉴 항목들이 같은 URL 을 여러 라벨로 나열하고 있었음:

- EQUIPMENT_SUPPLIER: `내 장비` / `장비 서류` / `장비 배치 현황` → 모두 `/equipment`
- EQUIPMENT_SUPPLIER: `내 조종원` / `조종원 서류` → 모두 `/persons`
- MANPOWER_SUPPLIER: `내 인원` / `인원 서류` / `인원 배치 현황` → 모두 `/persons`
- BP: `현장 관리` / `참여 공급사` → 모두 `/sites`

`NavLink` 의 `isActive` 가 URL 매칭 기반이라 클릭 한번에 같은 URL 항목 전체가 활성 표시됨. 또 서로 다른 화면을 약속하지만 실제로는 같은 페이지가 떠 라벨이 거짓말을 하고 있었음.

### 해결

각 라우트 = 단일 메뉴 항목으로 정리. 페이지 안에서 필터/탭으로 보기 전환 (만료 임박/배치 중 같은 부분집합).

| 역할 | 변경 후 메뉴 |
|---|---|
| EQUIPMENT_SUPPLIER | 내 장비 / 내 조종원 / 현장 관리 / 작업 일정 |
| MANPOWER_SUPPLIER | 내 인원 / 현장 관리 / 작업 일정 |
| BP | 현장 관리 / 배치 장비 / 배치 인원 / 작업계획서 / DOCX 템플릿 |

### 후속 (별도 페이지가 정말 필요해지면)

- `/equipment?filter=expiring`, `/equipment?filter=on-duty` 같은 query param 으로 필터 진입
- 또는 EquipmentPage 안에 상단 필터칩 추가
- 대시보드 카드 클릭 시 미리 필터 걸린 상태로 진입

## 2026-05-08 — Phase S-9: skep 원본 작업계획서 풀 이식

### 배경

기존 작업계획서 페이지가 평면 폼 (제목/일자/장비 add/persons add) 수준이라 skep 원본의 풍부한 UX (3-Step 흐름 + 워크시트 132 필드 + 16종 첨부 + DOCX 자동 생성 + 제원표 + PDF 메일) 가 빠져있었음. 사용자 요청: "그대로 docx에 넣으면 자동으로 들어가는거까지" 100% 이식 + 모듈 분할.

### S-9-A: 3-Step 골격 + 역할별 인원 배정

신규 페이지 `/work-plans/new` (`features/workPlan/create/`):

- Step 1: 사이트 → BP 자동 + 제목/일자/시간/위치/설명
- Step 2: 장비공급사 (사이트 ACTIVE 참여사) + 장비 + 조종원 다중 배정
- Step 3: 인력공급사 + 작업지휘자/유도원/화기감시자/신호수 다중 배정
- 단계별 첨부 서류 토글 (장비+조종원 / 4역할)
- 진행률 헤더 + 7개 필수 체크리스트 칩

원자 분할 (12 파일):

```
features/workPlan/create/
├─ WorkPlanCreatePage.tsx        composition
├─ types.ts                       RoleKey, REQUIRED_ROLES
├─ hooks/useWorkPlanCreate.ts     상태 + effect
├─ sections/
│  ├─ Step1SiteAndBp.tsx
│  ├─ Step2Equipment.tsx
│  ├─ Step3Manpower.tsx
│  └─ ActionBar.tsx
└─ components/
   ├─ ProgressHeader.tsx
   ├─ ChecklistItem.tsx
   ├─ RoleAssignToggle.tsx       (variant=blue|violet)
   ├─ DocPanel.tsx               (EquipDocsGroup + RoleDocsGroup)
   ├─ DocRow.tsx
   └─ DocLightbox.tsx
```

`WorkPlanPage` 에 "+ 새 작업계획서 (3-Step)" 버튼 추가, 기존 inline 폼은 "간편 등록" 으로 보존.

### S-9-B: 워크시트 schema 132 필드 + 자동 채움

skep 원본 그대로 이식:

- `lib/worksheet/schema.ts` (373줄, 20섹션 / 132필드 / p1~p5)
- `lib/worksheet/types.ts` (45줄)
- `lib/worksheet/engine.ts` (597줄, pizzip + docxtemplater 클라이언트 DOCX 렌더링)
- `public/worksheet/template.docx` (마스터 템플릿, 24KB)

DOCX 자동 채움 — `useWorkPlanCreate` 의 effect 가:

- 사이트 선택 → `siteName`, `submitCompany`, `workPeriodStart/End`
- 장비 선택 → `equipmentName`, `equipmentModel`, `vehicleNo`, `equipmentSerialNo`, `manufacturer`, `manufactureYear`
- 역할 인원 선택 → `operatorName`, `operatorLicenseNo`, `supervisor_name`, `supervisor_position`

값을 워크시트 schema 의 키에 매핑. DOCX 생성 시 `renderWorksheet(values, diagramKey, attachments)` 가 template.docx 의 `{key}` placeholder 를 치환 + 첨부 이미지를 새 페이지로 추가.

신규 컴포넌트:

- `SectionCard.tsx` — 섹션 카드 (헤더 토글, AI 배지, 검색 필터 매칭)
- `FieldInput.tsx` — text/textarea/checkbox/date 자동 분기 + AI 재작성 버튼
- `SignatureTable.tsx` — p1_signatures 5행 (작성/담당/확인/검토/승인) × {인력 dropdown + 직접입력 | 직위 | 일자}
- `WorksheetSections.tsx` — 132 필드 통합 렌더 (필수섹션 / 세부 분리, 검색)

백엔드:

- 마이그레이션 `V21__work_plan_form_values.sql` — `work_plans.form_values JSONB` + `equipment_supplier_company_id` / `manpower_supplier_company_id` / `current_equipment_id`
- `WorkPlan` 엔티티 + `hibernate-types-60` 으로 Map<String, Object> 매핑
- `UpdateFormValuesRequest` DTO + `WorkPlanService.updateFormValues()`
- `PATCH /api/work-plans/{id}/form-values` (DRAFT 단계만)

### S-9-C: PDF 다운로드 + PDF 메일 발송

백엔드 `com.skep.worksheet`:

- `WorksheetMailService` — LibreOffice headless 로 DOCX → PDF (UserInstallation 격리, 90s timeout, ImageResolution 150 / Quality 80)
- `WorksheetMailController` — `/api/worksheet/to-pdf`, `/api/worksheet/send-pdf` (multipart)
- `JavaMailSender` ObjectProvider 옵셔널 (MAIL_USERNAME 미설정 시 PDF 변환만 가능)
- Dockerfile 에 `libreoffice-writer libreoffice-core fonts-noto-cjk fonts-nanum` 설치
- `pom.xml` 에 `spring-boot-starter-mail` 추가

프론트:

- `PdfMailDialog.tsx` — Reply-To/받는사람/제목/내용 폼, 발송 진행 메시지
- `ActionBar.tsx` — DOCX (클라이언트) / PDF / 편집기 새 탭 / 메일 / 작업계획서 생성 5 버튼

### S-9-D: 장비 제원표 사이드 패널

- `public/equipment-specs.json` (51KB, skep 원본 그대로)
- `public/specs/` 폴더 (PDF 는 사용자가 채우면 즉시 동작)
- `SpecsSidebar.tsx` — 검색 + 카테고리/제조사/서브카테고리 필터, 200개 제한, PDF 새 탭

### S-9-E: BroadcastChannel 폼-에디터 동기화

- `hooks/useEditorSync.ts` — `skep-worksheet-{key}` 채널, `reload` 메시지
- `WorkPlanCreatePage` 에 600ms 디바운스로 폼 변경 시 새 sessionId reload 송신

skep 원본 OnlyOffice editor session 모델은 v2 의 `/api/onlyoffice/work-plan/{id}/config` (계획서 단위) 와 다르므로 새 탭 편집은 작업계획서 생성 후 상세 페이지에서 진입하는 흐름으로 통합.

### S-9-F: AI 재작성 stub

- `AiRewriteController` — `/api/ai/rewrite` (text + prompt → 재작성)
- `ANTHROPIC_API_KEY` 미설정 시 503 반환, 설정 시 echo (실제 Claude 호출은 추후 통합 자리)
- 프론트 `WorksheetSections.defaultAiRewrite` — `field.aiPrompt` 있는 textarea 옆에 "AI 재작성" 버튼

### 검증

- `tsc --noEmit -p tsconfig.app.json` → exit 0
- `mvn package` → 성공 (V21 + JSONB + JavaMail + LibreOffice 의존성)
- 도커 재배포: `Started SkepApplication in 12.181 seconds`, V21 마이그레이션 적용
- 라이브 smoke (모두 200):
  - `/worksheet/template.docx` (24793B)
  - `/equipment-specs.json` (51688B)
  - `/work-plans/new`
  - `/api/worksheet/to-pdf` 403 (JWT 필요 — 라우팅 OK)
  - `/api/ai/rewrite` 403 (JWT 필요 — 라우팅 OK)

### 운영 환경 변수 (필요 시)

| 변수 | 용도 |
|---|---|
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | PDF 메일 발송 활성화 |
| `ANTHROPIC_API_KEY` | AI 재작성 활성화 |
| `/specs/` 폴더 | 제원표 PDF 채우기 (없으면 사이드바 비어있음) |

### 의도된 v2 deviation (skep 원본과 다름)

| 항목 | skep 원본 | skep-v2 |
|---|---|---|
| BP 선택 | dropdown | 사이트 선택 → BP 자동 (site-centric 모델) |
| 후보 공급사 | 전체 SUPPLIER | 사이트 ACTIVE 참여사로 좁힘 (S-3.1 권한정책) |
| OnlyOffice 새 탭 | 작성 화면에서 즉시 | 작업계획서 저장 후 상세 페이지에서 |

## 2026-05-07 — 전체 시스템 audit + P1 4건 차단

병렬 4-agent audit 진행. 결과:
- API ↔ 프론트 연동 (99 호출 사이트 검토): P1/P2 없음, P3 4건 (운영 단계 권장)
- 로직/보안 갭: P1 4건 (아래 차단), P2 5건, P3 11건
- 설계-코드 일치: 자동화 4종 / 작업계획서 lifecycle / chain-head 정책 모두 정합
- 런타임 smoke 18 시나리오: 14 PASS, 4 시드 한계, 1 minor

### P1-A. Equipment/Person base list 권한 누수
- `EquipmentService.list/ensureCanAccess` + `PersonService.search/ensureCanAccess` 가 BP/WORKER/MANPOWER 까지 read-only 로 모두 통과시켜 cross-company 데이터 노출.
- S-3.1 에서 Document 만 좁히고 Equipment/Person 누락된 갭.
- 수정: ADMIN 전체 / EQUIPMENT|MANPOWER_SUPPLIER 자기 회사 / **BP 자기 사이트의 ACTIVE 참여 공급사 자원만** / WORKER 차단 + Equipment 도메인은 MANPOWER_SUPPLIER 도 차단.
- `PersonRepository.searchInSuppliers(supplierIds, ...)` 추가.
- 라이브 검증: MANPOWER→/api/equipment 403 `EQUIPMENT_DENIED` ✅.

### P1-B. OnlyOffice dev-fallback token 우회
- `OnlyOfficeService.signFileAccessToken/verifyFileAccessToken` 가 `ONLYOFFICE_JWT_SECRET` 미설정 시 `String.valueOf(planId)` 토큰 사용 → `?token=42` 만 보내면 plan #42 DOCX 읽기/덮어쓰기.
- 수정: 시크릿 16자 미만이면 토큰 생성/검증 둘 다 거부 (`ONLYOFFICE_NOT_CONFIGURED`). 토큰 만료도 10분→60분 연장.
- 라이브 검증: `?token=1` → 401 `BAD_TOKEN` ✅.

### P1-C. OnlyOffice callback SSRF
- `OnlyOfficeService.handleCallback` 의 `body.get("url")` 을 검증 없이 webclient 로 fetch → 169.254.169.254 등 내부 메타데이터 endpoint 호출 위험.
- 수정: `isAllowedCallbackUrl()` 추가 — callback url 의 host 가 OnlyOffice server-url host 와 같아야 통과.

### P1-D. Company GET 권한 누락
- `CompanyController.get` 에 권한 검사 없음 → 인증된 누구나 임의 회사 정보 조회.
- 수정: ADMIN 전체, 그 외는 `actor.companyId() == id` 만 통과 (`COMPANY_VIEW_DENIED`).
- 라이브 검증: EQ_SUP→/companies/1(BP) 403, /companies/2(자기) 200 ✅.

### 검증
```
docker compose --profile onlyoffice up -d --build backend  # ✅ 모든 컨테이너 healthy
4건 라이브 curl 검증 모두 통과
```

### 다음 sprint (P2/P3 잔여)
- N+1 — clone/submit/start 자원별 컴플라이언스 평가 (3N 쿼리)
- audit 누락 — Equipment/Person/Company CRUD, DocxTemplate, User enable/disable
- start() 사전 conflict 검사 ↔ sync 사이 race (advisory lock 또는 SELECT FOR UPDATE)
- DocxTemplate content-type strict 검증
- DashboardService.summary 의 `findAll().filter` → `countByEnabled` 로 대체

---

> 마지막 갱신: 2026-05-07 (Phase S-8.5: start 충돌 차단 + complete/cancel 자동 해제)

이 문서는 코드 작업 후 어떤 기능을 추가했고, 어떤 파일을 건드렸고, 어떤 검증을 했는지 남기기 위한 기록이다.

## 2026-05-07 — Phase S-8.5: start 충돌 차단 + complete/cancel 자동 해제

S-8 검토에서 발견된 두 가지 P2 — 다른 현장 배치 충돌이 있어도 start 가 통과 + complete 가 배치를 안 닫음 — 둘 다 처리.

### 정책

- **start()**: 다른 현장에 배치된 자원이 있으면 기본 차단 (`RESOURCE_CONFLICTS` 400).
  ADMIN 만 `force=true` + `force_reason` 으로 강제 진행 가능. 강제 시 자원은 그 자리에 두고 (자동 이동 X) plan 만 IN_PROGRESS, audit `WORK_PLAN_FORCE_STARTED` + 자원별 `WORK_PLAN_RESOURCE_CONFLICT` 양쪽 기록.
- **start() 자동 배치**: 신규 배치 row 에 `triggered_by_work_plan_id = wp.id` 기록. 수동 배치(직접 assignEquipment) 는 NULL 유지.
- **complete()**: `triggered_by_work_plan_id = wp.id` 인 활성 배치만 자동 해제. 수동 배치는 건드리지 않음. audit `EQUIPMENT_UNASSIGNED` / `PERSON_UNASSIGNED` (trigger=work_plan_complete).
- **cancel()**: IN_PROGRESS 였다면 동일하게 자동 해제. 다른 상태 (DRAFT/SUBMITTED/APPROVED) 에서는 자동 배치가 없으므로 no-op.

### DB

V20 — `equipment_assignments` / `person_assignments` 양쪽에 `triggered_by_work_plan_id BIGINT` + ON DELETE SET NULL FK + partial index.

### 백엔드

- `EquipmentAssignment` / `PersonAssignment` 엔티티에 `triggeredByWorkPlanId` 필드 + Builder.
- 두 Repository 에 `findByTriggeredByWorkPlanIdAndReleasedAtIsNull(workPlanId)` 추가.
- `WorkPlanService.start(id, StartWorkPlanRequest, actor)` — 사전 conflict 검사 → force 미설정이면 throw, force=true 면 ADMIN 검사 + 사유 검사 → 진행.
- `WorkPlanService.releaseAutoAssignments(wp, actor, reason)` — complete/cancel 공용 헬퍼. 자원의 currentSiteId 도 같은 site 일 때만 NULL 처리.
- audit 액션 `WORK_PLAN_FORCE_STARTED` 추가.

### 프론트

- `WorkPlanDetailPage.transition()` 에 `RESOURCE_CONFLICTS` 핸들링 추가 — ADMIN 이면 confirm + force_reason prompt 후 재호출, 그 외엔 에러 표시.

### 테스트 도구 (P1 false positive 검증)

- `/api/onlyoffice/work-plan/{id}/config` 인증 없으면 403 (Spring Security 차단) — 권한 검사 정상.
- `/api/onlyoffice/work-plan/{id}/file` 잘못된 token → `BAD_TOKEN` 응답 (우리 service 까지 도달) — permitAll + 자체 token 검증 정상.

### 검증

```
docker compose --profile onlyoffice up -d --build backend frontend  # ✅ 모든 컨테이너 healthy
```

---

## 2026-05-07 — Phase S-8: skep 자동화 4종

skep 가 가지고 있던 자동화 기능 4가지를 v2 에 이식. 각 sub-phase 가 독립적으로 동작.

### S-8.1 — 작업 시작/완료 ↔ 자원 배치 자동 동기화

**`WorkPlanService.start()` 가 자원 배치를 자동으로 처리:**
- 자원이 미배치면 plan 사이트로 신규 `EquipmentAssignment` / `PersonAssignment` 생성 (audit `EQUIPMENT_ASSIGNED` / `PERSON_ASSIGNED`, trigger=work_plan_start).
- 이미 plan 사이트에 배치되어 있으면 no-op.
- 다른 사이트에 배치되어 있으면 자동 이동하지 않고 audit `WORK_PLAN_RESOURCE_CONFLICT` 만 남김 — 운영자가 수동 결정. 한 자원이 두 사이트에 동시에 있을 수 없는 물리 현실 보존.

`complete()` / `cancel()` 은 자동 해제 안 함 — 다음 plan 에서 재사용 + 물리 위치는 운영자 결정.

audit `after_json` 에 6가지 카운트 (`equipment_assigned`, `equipment_already_here`, `equipment_conflict`, `person_*` 동일).

### S-8.2 — 작업계획서 프리셋 9개 슬롯

V17 `work_plan_presets` 테이블 + `WorkPlanPreset` 엔티티 + `WorkPlanPresetController`. (user_id, slot 1~9) UNIQUE.

**Endpoints**: `GET /api/work-plan-presets`, `GET/PUT/DELETE /api/work-plan-presets/{slot}`.

**프론트**: `WorkPlanPage` 등록 폼 안에 9개 슬롯 toolbar 추가. 빈 슬롯 클릭 시 현재 폼 값으로 저장, 채워진 슬롯 클릭 시 폼에 시드. payload_json 은 시작/종료시간/위치/상세/default_site_id 보관.

### S-8.3 — DOCX 템플릿 + 자동 placeholder 채움

V18 `docx_templates` 테이블. Apache POI XWPF (5.2.5). 회사별 + 전역(NULL) 템플릿.

**컴포넌트**:
- `WorkPlanDocxExporter` — `{key}` placeholder 치환. 본문 paragraphs / 표 셀 / header / footer 모두 처리. 같은 placeholder 가 여러 run 으로 split 되어도 합쳐서 치환 (스타일 유지 트레이드오프).
- `DocxTemplateService` — CRUD + 권한 (전역은 ADMIN, 회사 templates 는 회사 사용자).
- `WorkPlanExportController` — `GET /api/work-plans/{id}/export/docx?templateId=N` 다운로드.

**지원 placeholder**: `{title}`, `{site_name}`, `{bp_company_name}`, `{work_date}`, `{start_time}`, `{end_time}`, `{work_location}`, `{description}`, `{status}`, `{equipment_list}`, `{person_list}`, `{equipment_count}`, `{person_count}`, `{printed_at}`.

**프론트**: `DocxTemplatesPage` (`/admin/docx-templates` — ADMIN/BP) 업로드/목록/이름변경/삭제. `WorkPlanDetailPage` 헤더에 "DOCX 출력" 드롭다운 → 템플릿 선택 → blob 다운로드.

**제약 (현 phase)**: 표 행 반복 (`{#equipment}...{/equipment}`) 미지원. 단순 텍스트 placeholder 만. 후속 phase 로 행 반복 가능.

### S-8.4 — OnlyOffice 인플레이스 편집 (env-gated)

V19 `work_plans.current_docx_key` + `current_docx_template_id` 컬럼. 첫 편집 시 템플릿에서 DOCX 생성 → storage 저장 → 키 기록. 이후 콜백에서 같은 키 덮어쓰기.

**환경 변수 (모두 미설정 시 비활성화)**:
- `ONLYOFFICE_ENABLED=true`
- `ONLYOFFICE_URL=https://office.example.com` — Document Server 주소
- `ONLYOFFICE_JWT_SECRET=...` — Document Server 와 동일한 HS256 시크릿
- `PUBLIC_BACKEND_URL=https://api.example.com` — OnlyOffice 가 callback/file fetch 할 우리 서버 공개 URL

**Endpoints**:
- `GET /api/onlyoffice/status` → `{enabled, server_url}`
- `GET /api/onlyoffice/work-plan/{id}/config?templateId=N` → DocsAPI 용 config + JWT token (사용자 권한 검사)
- `GET /api/onlyoffice/work-plan/{id}/file?token=...` (permitAll, 자체 token 검증) — Document Server 가 fetch 하는 DOCX
- `POST /api/onlyoffice/work-plan/{id}/callback?token=...` (permitAll) — status 2/6 시 url 의 DOCX 다운로드해 storage 덮어쓰기

**JWT 흐름**:
- 우리 서버가 plan-별 file-access token 발급 (HS256, 10분 유효, plan_id 클레임).
- OnlyOffice config 자체도 JWT-signed (Document Server 가 `JWT_ENABLED=true` 일 때 필수).
- 콜백 본문이 JWT-wrapped 면 verify 후 payload 추출.

**`FileStorage` 확장**: `storeBytes(byte[], extension)`, `overwrite(key, byte[])` 추가 — 서버 생성 콘텐츠 + 인-플레이스 갱신용. `LocalDiskStorage` 구현.

**SecurityConfig**: 파일 fetch + 콜백은 사용자 인증 패스 (Document Server 가 직접 접근하므로 자체 token 만 검증).

**프론트**: `WorkPlanEditPage` (`/work-plans/:id/edit`). status enabled 가 true 면 OnlyOffice DocsAPI JS 동적 로드 + `DocsAPI.DocEditor()` 렌더. false 면 안내 페이지. `WorkPlanDetailPage` 헤더 "OnlyOffice 편집" 버튼은 status enabled 일 때만 노출.

**개발 환경에서 사용하려면**: OnlyOffice Community Edition (Docker) 띄우고 위 4개 env 설정. 미설정 상태로도 시스템 정상 동작 (DOCX 다운로드만 가능).

### S-8.4.1 — OnlyOffice Document Server docker-compose 추가

`docker-compose.yml` 에 `onlyoffice` 서비스 추가 (compose profile `onlyoffice` 로 gating). Community Edition image `onlyoffice/documentserver:latest`. 기본 포트 8083 (host) → 80 (container). 데이터/로그/lib/db 4개 named volume.

`backend` 서비스에 4개 env 추가: `ONLYOFFICE_ENABLED`, `ONLYOFFICE_URL`, `ONLYOFFICE_JWT_SECRET`, `PUBLIC_BACKEND_URL`. 기본 disabled. compose 안의 same network 라 PUBLIC_BACKEND_URL 기본값은 `http://backend:8080`.

`.env.example` 에 활성화 절차 4단계 + 운영 주의사항 (HTTPS 필수, 도메인 설정) 명시.

**활성화 명령**:
```bash
# 1. .env 에 ONLYOFFICE_JWT_SECRET= (16자 이상 랜덤) + ONLYOFFICE_ENABLED=true 설정
# 2. profile 포함 기동
docker compose --profile onlyoffice up -d

# 미활성화 (기본):
docker compose up -d  # onlyoffice 컨테이너 생성 안 함
```

### 검증

```
docker compose build backend           # ✅
docker compose build frontend          # ✅
docker compose --profile onlyoffice config --services   # 5개 서비스 인식 OK
```

---

## 2026-05-07 — Phase S-7.1: 복제 BLOCKED skip + 제출 컴플라이언스 재검사 + 인원-장비 매칭 정합

S-7 검토에서 발견된 두 가지 — (P1) 복제된 BLOCKED 자원이 제출/승인까지 정책 우회, (P2) 매칭 장비가 skip 됐는데 인원의 `equipment_id` 가 그대로 복사되어 깨진 참조 발생 — 둘 다 차단.

### 백엔드 변경

**`WorkPlanService.clone()` 정책 강화**:
- BLOCKED 자원은 새 plan 에 복사하지 않음 (snapshot 도 남기지 않음 — 안 들어왔으니 추적할 target 없음). 이전엔 BLOCKED 라도 row + snapshot 둘 다 만들어서 그대로 통과되던 문제 차단.
- 어떤 장비가 실제 복제됐는지 `copiedEquipmentIds` 로 추적 → 인원 복사 시 매칭 장비가 그 set 에 없으면 `equipment_id` 를 null 로 떨어뜨려 깨진 참조 방지 (`droppedMatch` 카운트로 audit 기록).
- audit `after_json` 에 7가지 카운트 분리 (`copied_*`, `skipped_inactive_*`, `skipped_blocked_*`, `dropped_equipment_match`).

**`WorkPlanService.submit()` 정책 강화**:
- 자원이 한 건이라도 있어야 한다는 기존 검사 + 모든 자원의 컴플라이언스를 **현재 시점**으로 재평가.
- 자원별 가장 최근 `work_plan_compliance_checks` snapshot 이 `OVERRIDDEN` 이면 ADMIN 이 이미 명시 승인했으므로 통과 (override 보존).
- 그 외에 현재 BLOCKED 인 자원이 있으면 `DOCUMENTS_BLOCKED_AT_SUBMIT` 400 throw — clone 우회 + add 이후 만료된 서류 둘 다 차단. 메시지에 자원/사유 명시. 사용자는 자원을 제거하거나 ADMIN 이 강제 진행으로 재추가해야 함.

### 검증

```
docker compose build backend  # ✅
```

### 정책 요약 (S-7.1 이후)

| 시점 | 정책 |
|---|---|
| addEquipment / addPerson | BLOCKED → throw (ADMIN override 가능, OVERRIDDEN 으로 통과) |
| clone | BLOCKED → silent skip (row 안 만듦), OVERRIDDEN 은 history 가 없으면 발생 안 함 |
| submit | 자원별 현재 BLOCKED → throw. 단 latest snapshot 이 OVERRIDDEN 이면 통과 |
| approve / start / complete | 컴플라이언스 검사 없음 (이미 submit 단계에서 닫힘) |

---

## 2026-05-07 — Phase S-7: 작업계획서 복제 + 인쇄

### 목적

skep 의 자동화에서 가장 큰 가치였던 "어제 작업계획서 → 오늘 작업계획서 복사" 워크플로우, 그리고 PDF 출력을 v2 에 이식. DOCX 템플릿 / OnlyOffice 인플레이스 편집은 외부 라이브러리·인프라 의존이 커서 별도 phase 로 남김.

### 백엔드

- `WorkPlanService.clone(sourceId, req, actor)` — 원본 plan 의 헤더 (title/시간/위치/설명) + 자원(WPE/WPP) 행을 새 DRAFT 로 복사. 새 work_date 기본값은 원본+1일, 새 title 기본값은 `[복사] ` prefix.
- 자원 복사 시 supplier 가 사이트의 ACTIVE 참여 공급사인지 재확인 — 아니면 그 행 skip (skipped 카운트는 audit `after_json` 에 기록).
- 자원별 컴플라이언스 재평가 (`evaluateForClone()`) — 기존 `evaluateCompliance()` 와 달리 BLOCKED 라도 throw 하지 않고 BLOCKED 결과를 그대로 반환. 결과를 `work_plan_compliance_checks` 에 스냅샷. DRAFT 라 사용자가 제출 전 수정 가능.
- `POST /api/work-plans/{id}/clone` 엔드포인트 (request body 선택 — 모든 필드 optional). `WORK_PLAN_CLONED` audit 액션.

### 프론트엔드

- `WorkPlanPrintPage.tsx` — `/work-plans/{id}/print` 라우트, AppShell 미사용. A4 1매 기준 레이아웃 + `@page { size: A4; margin: 14mm; }` + `@media print` 로 미리보기/툴바 자동 숨김. "인쇄 / PDF 저장" 버튼이 `window.print()` 호출, 브라우저가 PDF 저장 처리. 상단 헤더(작업계획서 # + 상태) → 작업 정보 표 → 투입 장비 테이블 → 투입 인원 테이블 → 컴플라이언스 이력 → 작성/승인 서명란 → 출력일 푸터.
- `WorkPlanDetailPage.tsx` 헤더에 두 버튼 추가:
  - "인쇄" — 새 탭으로 print 라우트 열기.
  - "복제" — 새 작업일 입력 prompt → `POST /api/work-plans/{id}/clone` → 새 plan 으로 navigate.
- `App.tsx` 에 `/work-plans/:id/print` 라우트 추가.

### 검증

```
docker compose build backend  # ✅
docker compose build frontend # ✅
```

### 남은 후속 작업 (별도 phase 후보)

- **DOCX 템플릿 + 자동 placeholder 채움** (skep `Docxtemplater` 흐름 이식). PDF 가 아닌 .docx 산출물을 원할 때.
- **OnlyOffice 인플레이스 편집** — 인프라(OnlyOffice 서버) 추가 필요.
- **작업 시작/완료 ↔ 자원 배치 동기화** — `equipment_assignments` / `person_assignments` 와 work plan status 연동.
- **프리셋 9개 슬롯** — 자주 쓰는 양식 저장.

---

## 2026-05-07 — Phase S-6: 대시보드에 작업계획서 슬롯 채우기

### 목적

S-3 에서 비워뒀던 역할별 대시보드의 `today_work_plans` / `upcoming_work_plans` 슬롯을 S-5 의 work plans 데이터로 채움. 카드형 UI 위젯을 도메인별 4개 대시보드 모두에 동일하게 노출.

### 백엔드

- `RoleDashboardController` 에 `WorkPlanRepository`, `WorkPlanEquipmentRepository`, `WorkPlanPersonRepository` 주입.
- ADMIN: 오늘 + 향후 7일 (`findByWorkDateBetweenOrderByWorkDateAscStartTimeAsc`) 의 work plans 를 `today_work_plans` 키로 응답. `counts.work_plans_upcoming` 도 추가.
- BP: 자기 회사 (`findByBpCompanyIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc`) 오늘 + 향후 7일.
- 공급사 (장비/인력): 자기 회사 자원이 포함된 plan (`findUpcomingForSupplier`) 오늘 + 향후 7일 → `upcoming_work_plans`.
- `toWorkPlanItems()` 헬퍼 — work_plan_id 들의 장비/인원 카운트를 batch query (`findByWorkPlanIdIn`) 로 묶어서 N+1 회피. 사이트명/BP명도 한 번에 캐시. 응답 스키마: `id, title, site_id, site_name, bp_company_name, work_date, start_time, end_time, status, equipment_count, person_count`.
- `WorkPlanEquipmentRepository` / `WorkPlanPersonRepository` 에 `findByWorkPlanIdIn` 추가.

### 프론트엔드

- 신규 `src/features/dashboard/WorkPlanListWidget.tsx` — 공통 카드 위젯. 작업계획서 행마다 제목/상태배지/날짜·시간/현장명/(BP명 옵션)/장비·인원 카운트 표시. 행 클릭 시 `/work-plans/{id}` 진입. 빈 상태 처리.
- `AdminDashboardPage` / `BpDashboardPage` / `EquipmentSupplierDashboardPage` / `ManpowerSupplierDashboardPage` 모두 위젯 사용. 기존 TodoBanner placeholder 제거.

### 검증

```
docker compose build backend  # ✅
docker compose build frontend # ✅
```

### 다음 구현 순서

- Phase S-7: 인쇄/이식 (작업계획서 PDF + skep 자동화 기능 이식). 그 이전에 작업 시작/완료 시점에 `equipment_assignments` / `person_assignments` 동기화 옵션을 검토.

---

## 2026-05-07 — Phase S-5.1: 후보 조회 서버 권한 좁히기 + 시작/완료 전이

S-5 검토에서 발견된 두 가지 — (1) 후보 조회가 `/api/equipment` / `/api/persons` 전체로 열려 BP/ADMIN 이 다른 공급사 자원까지 응답으로 받는 데이터 노출, (2) `IN_PROGRESS`/`DONE` 상태 컬럼은 있으나 전이 API 미구현 — 모두 닫음.

### 백엔드

- `WorkPlanService` 에 `equipmentCandidates(workPlanId, actor)` / `personCandidates(workPlanId, actor)` 추가. 사이트의 ACTIVE `EQUIPMENT_SUPPLIER`/`MANPOWER_SUPPLIER` 참여 공급사 자원만 반환. BP/ADMIN 권한 검사 (ensureCanManage). 응답은 기존 `EquipmentResponse`/`PersonResponse` 재사용.
- `WorkPlanService.start()` (APPROVED → IN_PROGRESS) / `complete()` (IN_PROGRESS → DONE) 추가. ADMIN 또는 BP self-site.
- `WorkPlan.start()` / `WorkPlan.complete()` 도메인 메서드 추가 (status 만 전환).
- `AuditAction` 에 `WORK_PLAN_STARTED`, `WORK_PLAN_COMPLETED` 상수 추가.
- `WorkPlanController` 에 4 endpoint 추가:
  - `GET /api/work-plans/{id}/candidates/equipment`
  - `GET /api/work-plans/{id}/candidates/persons`
  - `POST /api/work-plans/{id}/start`
  - `POST /api/work-plans/{id}/complete`

### 프론트

- `WorkPlanDetailPage.tsx` 의 `EquipmentBlock` / `PersonBlock` 후보 fetch 를 `/api/work-plans/{id}/candidates/{equipment|persons}` 로 교체. 클라이언트 측 필터링 제거 + 사이트 상세 fetch 제거 (불필요).
- 헤더에 "작업 시작" (APPROVED) / "작업 완료" (IN_PROGRESS) 버튼 추가. transition() 가 5종 액션 처리.

### 검증

```
docker compose build backend  # ✅
docker compose build frontend # ✅
```

### 남은 후속 작업

- 작업 시작/완료 시점에 자원 실제 배치(`equipment_assignments` / `person_assignments`) 와의 동기화는 별도 단계 (S-7 또는 그 이후) — 현재는 작업계획서 status 만 추적.

---

## 2026-05-07 — Phase S-5: 작업계획서 도메인 + 자원 컴플라이언스 스냅샷

### 목적

skep v2 의 핵심 워크플로우 — 작업일 단위로 BP사가 자기 현장에 대한 작업계획서를 만들고, 참여 공급사가 보유한 장비·인원을 배치한다. 자원 추가 시점에 서류 컴플라이언스를 평가해서 필수 서류가 결격이면 차단(또는 ADMIN 강제진행)하고, 결과를 스냅샷으로 남긴다.

### DB

`V16__add_work_plans.sql` — 4개 테이블 추가
- `work_plans` — 헤더 (status: DRAFT|SUBMITTED|APPROVED|IN_PROGRESS|DONE|CANCELLED + lifecycle 타임스탬프 + cancel_reason)
- `work_plan_equipment` — UNIQUE(work_plan_id, equipment_id), supplier_company_id 캐시
- `work_plan_persons` — UNIQUE(work_plan_id, person_id), equipment_id (조종원/신호수 매칭, nullable), role
- `work_plan_compliance_checks` — 자원 추가 시점의 서류 검증 스냅샷 (OK|WARNING|BLOCKED|OVERRIDDEN + override_by/reason)

### 백엔드

신규 패키지 `com.skep.workplan` —
- 엔티티 4종 (`WorkPlan`, `WorkPlanEquipment`, `WorkPlanPerson`, `WorkPlanComplianceCheck`)
- enum 2종 (`WorkPlanStatus`, `ComplianceStatus`)
- 레포지토리 4종 — 가시성별 쿼리 (ADMIN 전체 / BP 자기 회사 / 공급사 자기 자원 포함된 plan)
- DTO 9종 — 요청/응답
- `WorkPlanService` — 생성/수정/제출/승인/취소 + 자원 추가/제거 + 컴플라이언스 평가
- `WorkPlanController` — `GET /api/work-plans`, `GET/POST/PATCH /api/work-plans/{id}`, `POST /api/work-plans/{id}/{equipment|persons}`, `POST /api/work-plans/{id}/{submit|approve|cancel}`

`AuditAction` 에 9종 신규 액션 (`WORK_PLAN_*`), `AuditTargetType` 에 `WORK_PLAN`, `WORK_PLAN_EQUIPMENT`, `WORK_PLAN_PERSON` 추가.

### 컴플라이언스 정책

`WorkPlanService.evaluateCompliance()` — S-4.1 의 `findValidVerifiedTypesByOwners` (chain head + verified + 안만료) 결과를 그대로 재사용. 자원 추가 시:
1. 해당 owner_type 의 `blocks_assignment=true` 인 활성 서류 타입 조회
2. 자원이 보유한 유효 서류 type_id 셋 조회
3. 누락이 있으면 → BLOCKED → ADMIN + override_reason 있으면 OVERRIDDEN, 아니면 400 `DOCUMENTS_BLOCKED`
4. 누락 없음 + 만료 임박/검토 필요 chain-head 가 있으면 WARNING
5. 그 외 OK

결과를 `work_plan_compliance_checks` 에 항상 스냅샷.

### 권한

- 생성/수정/자원편집/상태전이: ADMIN 또는 BP (자기 회사 owned site).
- 자원 supplier 가 사이트의 ACTIVE 참여 공급사가 아니면 추가 거부.
- 조회: ADMIN 전체 / BP 자기 회사 / 공급사는 자기 회사 자원이 포함된 plan 만.
- 자원 편집은 DRAFT 상태에서만. 제출은 자원 1건 이상 필요.

### 프론트엔드

신규 `src/features/workPlan/`
- `WorkPlanPage.tsx` — 목록 + 통계 + 작성 폼 (제목/현장/작업일/시간/위치/상세) + 상태 배지
- `WorkPlanDetailPage.tsx` — 헤더(상태 + 제출/승인/취소 버튼) + 장비/인원 블록(추가·제거, 컴플라이언스 차단 시 ADMIN override 다이얼로그) + 컴플라이언스 이력

신규 `src/types/workPlan.ts`. `App.tsx` 라우팅 (`/work-plans`, `/work-plans/:id`). `Sidebar.tsx` 의 `disabled: true` 제거하여 ADMIN/BP/공급사 메뉴 활성화.

### 검증

```
docker compose build backend  # ✅ jar 패키징 성공
docker compose build frontend # ✅ tsc -b + vite build 성공 (154 modules, 460KB gzip 127KB)
```

### 다음 구현 순서

- Phase S-6: 대시보드 고도화 — 역할별 대시보드의 today/upcoming work plans 슬롯을 새 repo 메서드로 채움.
- Phase S-7: 인쇄/이식 — skep 의 작업계획서 PDF/인쇄 자동화 + 외부 시스템 연동 잔여 작업.

---

## 2026-05-07 — Phase S-4 단계 4.1: 배차 차단 정책 마지막 누수 차단

S-4 검토에서 발견된 2건의 정책 누수 — **chain head 미고려** + **markOcrReviewRequired 가 verified 동기화 안함** — 차단. 이게 닫혀야 S-4 완료.

### 시나리오 (수정 전 위험)

```text
doc#3 운전면허증 verified=true / VERIFIED  ← 기존
doc#16 운전면허증 verified=false / OCR_REVIEW_REQUIRED  ← 새로 갱신, previous_document_id=3
```

**수정 전**: `findValidVerifiedTypesByOwners` 쿼리가 chain head 필터 없이 `verified=true` 만 봐서 doc#3 을 "유효 서류" 로 잘못 계산. 배차 통과.

**수정 후**: chain head 필터로 doc#16 만 평가. OCR_REVIEW_REQUIRED 라 invalid. 운전면허증 누락 처리 → `DOCUMENTS_BLOCKED`.

### 적용된 변경

#### #1 `DocumentRepository.findValidVerifiedTypesByOwners`

```sql
WHERE d.ownerType = ... AND d.ownerId IN ...
  AND d.verificationStatus = VERIFIED       -- 신규: enum 명시 (이중 안전망)
  AND d.verified = true                      -- boolean 도 동시 검사
  AND (d.expiryDate IS NULL OR d.expiryDate >= today)
  AND NOT EXISTS (                           -- 신규: chain head 필터
    SELECT 1 FROM Document d2 WHERE d2.previousDocumentId = d.id
  )
```

이 쿼리는 `AssignmentService.missingDocCounts` (후보 표시용) + `enforceAssignmentDocs` (실제 차단) 모두에서 사용 — 두 곳 모두 동시에 강화됨.

#### #2 `Document.markOcrReviewRequired`

```java
public void markOcrReviewRequired() {
    this.verified = false;   // 신규: verification_status 가 VERIFIED 외로 바뀌면 boolean 도 동기화
    this.verificationStatus = VerificationStatus.OCR_REVIEW_REQUIRED;
}
```

이미 `markRejected`/`unmarkVerified` 는 `verified=false` 로 내리고 있었는데 `markOcrReviewRequired` 만 누락이었음. UPSTREAM_DISABLED/ERROR 시 자동으로 이 메서드가 호출되므로 직접적인 운영 영향 케이스.

### 검증 (실제 시나리오 재현)

PostgreSQL 에 직접 INSERT 로 chain 시뮬레이션:

```sql
-- doc#3 = 운전면허증 VERIFIED (기존)
-- doc#16 = OCR_REVIEW_REQUIRED, previous_document_id=3 (신규 갱신본)

INSERT INTO documents (..., verification_status, previous_document_id)
VALUES (..., 'OCR_REVIEW_REQUIRED', 3);
```

검증 결과:
- 수정 전: missing=3 (운전면허증이 VERIFIED 로 계산됨), blocked=true (다른 3건 누락 때문)
- 수정 후: missing=**4** (운전면허증도 누락으로 정정), blocked=true
- `POST /api/persons/2/assignment` → `DOCUMENTS_BLOCKED` 메시지에 "운전면허증" 포함 ✓

### 검증 명령

- `docker compose build backend` 통과
- `docker compose up -d backend` 정상 기동

### 의미

이 두 줄기는 시스템에서 가장 중요한 안전 정책의 하부 가드. UI/API 차단이 모두 이 쿼리/메서드에 의존하고 있어서 여기가 뚫리면 전체 운영 정책이 뚫림. 닫힘.

**Phase S-4 완료**. 다음은 S-5 작업계획서.

---

## 2026-05-07 — Phase S-4 단계 4: OCR 검토 큐 + 알림 도메인 + history UI

S-5 (작업계획서) 진입 전 마지막 정리. Phase S-4 의 운영 흐름을 마무리한다.

### 1. ADMIN 검토 큐 (OCR_REVIEW_REQUIRED + REJECTED)

| 파일 | 내용 |
|---|---|
| `DocumentRepository.findReviewQueue` | `verification_status IN (OCR_REVIEW_REQUIRED, REJECTED)` 인 chain head |
| `dto/ReviewItemResponse.java` | doc + owner_name + supplier 메타 합본 응답 |
| `DocumentService.reviewQueue(actor)` | ADMIN 만. owner/supplier 조회 + 응답 매핑 |
| `DocumentController.GET /api/documents/review-queue` | endpoint |
| `frontend/src/features/document/ReviewQueuePage.tsx` | 검토 페이지. 자동 재검증 / 수동 검증 / 반려 액션 |
| `App.tsx` `/admin/document-review` 라우트 (ADMIN 전용) | |
| `Sidebar` ADMIN 메뉴에 "서류 검토" 추가 | |

### 2. 알림(notifications) 도메인

| 파일 | 내용 |
|---|---|
| `V15__add_notifications.sql` | `notifications` 테이블. target_user_id / target_company_id / site_id / type / title / message / link_type / link_id / read_at / created_at + 인덱스 |
| `com.skep.notification.{Notification, NotificationType, NotificationRepository, NotificationService, NotificationController}` | 엔티티/Repo/서비스/컨트롤러 |
| `dto/NotificationResponse.java` | 응답 DTO |
| `GET /api/notifications` | 페이지. ADMIN 은 전체, 회사 사용자는 직접 + 회사 broadcast |
| `GET /api/notifications/unread-count` | 미읽음 카운트 |
| `POST /api/notifications/{id}/read` | 읽음 처리 |
| `frontend/src/types/notification.ts` | `NotificationResponse` + label + `ReviewItemResponse` |
| `frontend/src/features/notification/NotificationsPage.tsx` | `/notifications` 페이지 |
| `Sidebar` 알림 메뉴 활성화 + 미읽음 뱃지 (60초 polling) | |

발신 hook:

| 위치 | type | target |
|---|---|---|
| `VerificationService.verifyDocument` 결과 | `DOCUMENT_VERIFIED` / `DOCUMENT_REJECTED` / `DOCUMENT_OCR_REVIEW` | owner_supplier (회사 broadcast) |
| `VerificationService.rejectDocument` | `DOCUMENT_REJECTED` | owner_supplier |
| `AssignmentService.assignEquipment / assignPerson` (override 시) | `ASSIGNMENT_OVERRIDDEN` | owner_supplier |

알림 type 상수: `NotificationType.{DOCUMENT_REJECTED, DOCUMENT_OCR_REVIEW, DOCUMENT_VERIFIED, DOCUMENT_EXPIRING, DOCUMENT_EXPIRED, ASSIGNMENT_OVERRIDDEN}`. `DOCUMENT_EXPIRING/EXPIRED` 는 타입만 정의 — 스케줄러/배치 발신은 별도 phase.

### 3. 갱신 이력 UI

| 파일 | 내용 |
|---|---|
| `frontend/src/features/document/DocumentHistoryDialog.tsx` | 모달. `GET /api/documents/{id}/history` 호출. 각 버전별 status / 만료일 / 반려 사유 / `previous_document_id` 체인 표시. 가장 최신은 "현재" 뱃지 |
| `DocumentCard` 메뉴에 "이력 보기" 추가 + `onHistory` prop | |
| `DocumentSection` 에 dialog mounting + `historyOf` 상태 | |

### 검증

프론트엔드: `npx tsc --noEmit -p tsconfig.app.json` 통과.

백엔드: `docker compose build backend` 통과. V15 마이그레이션 자동 적용 확인.

API e2e:
- `POST /api/documents/3/verify {}` (VERIFY_ENABLED=false → UPSTREAM_DISABLED) → `verification_status=OCR_REVIEW_REQUIRED` + `notifications` 테이블에 `DOCUMENT_OCR_REVIEW` 알림 발신 ✓
- `GET /api/notifications` (ADMIN) → 전체 2건 (target_company_id=2) ✓
- `GET /api/notifications/unread-count` → `{"unread":2}` ✓
- `POST /api/notifications/1/read` → `read_at` 채워짐 → unread=1 로 감소 ✓
- `GET /api/documents/review-queue` (ADMIN) → 1건 (doc#3, OCR_REVIEW_REQUIRED) ✓
- `GET /api/documents/3/history` → 1건 (chain head) ✓

### 미구현 / 의도적으로 미뤄둔 것

- **만료 임박/만료 자동 알림 발신**: `DOCUMENT_EXPIRING` / `DOCUMENT_EXPIRED` type 만 상수로 정의. 스케줄러/배치 (Spring `@Scheduled` 같은) 는 별도 phase.
- **target_user_id 직접 알림**: 발신은 가능하지만 트리거 케이스가 아직 없음 (회사 broadcast 만 사용중).
- **알림 클릭 → 자동 read**: 현재는 명시적 "읽음" 버튼만. 페이지 진입 시 자동 read 같은 UX 는 추후.

---

## 2026-05-07 — Phase S-4 단계 3: 자동 OCR + ADMIN override + 갱신 이력 + 사용자 보충 모달

### 목적

S-4 단계 2.1 까지 도입된 정책/검증 기반 위에, 운영 흐름을 마무리한다.

```text
업로드 → 자동 OCR + 정부 API 호출 (비동기)
       → 사용자가 빈 칸 보충 후 재검증 (verify dialog)
       → 서류 미비 자원도 ADMIN 이 사유 남기고 강제 진행 가능 (override)
       → 갱신 이력은 previous_document_id 체인 + history endpoint 로 추적
```

### 적용된 변경

#### 1. upload 시 자동 OCR/검증 트리거 (백엔드)

| 파일 | 내용 |
|---|---|
| `backend/src/main/java/com/skep/SkepApplication.java` | `@EnableAsync` 추가 |
| `backend/src/main/java/com/skep/verify/DocumentUploadedEvent.java` | record 이벤트 (documentId + actor) |
| `backend/src/main/java/com/skep/verify/AutoVerifyTrigger.java` | `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` 로 자동 verify 실행. type.verify_endpoint 가 있는 경우만. user_inputs 빈값으로 시도 — OCR 실패 시 자연스럽게 OCR_REVIEW_REQUIRED 로 떨어짐. |
| `backend/src/main/java/com/skep/document/DocumentService.java` | `ApplicationEventPublisher` 주입. upload 끝에 `events.publishEvent(new DocumentUploadedEvent(...))`. |

흐름 요약:
- upload HTTP 응답은 즉시 반환됨 (사용자 흐름 안 막음).
- AFTER_COMMIT + 별도 thread → OCR/외부 API 호출 동안 caller 영향 없음.
- VERIFY_ENABLED=false 면 VerifyClient 가 UPSTREAM_DISABLED → OCR_REVIEW_REQUIRED.

#### 2. ADMIN override (서류 미비 강제 진행)

| 파일 | 내용 |
|---|---|
| `backend/src/main/java/com/skep/assignment/dto/AssignRequest.java` | `override` (Boolean) + `overrideReason` (String) 필드 추가 |
| `backend/src/main/java/com/skep/assignment/AssignmentService.java` | 기존 `ensureAssignmentDocumentsReady` → `enforceAssignmentDocs(ownerType, ownerId, req, actor)` 로 교체. 누락 발견 + `override=true` + `actor=ADMIN` + `overrideReason` 채워짐 → 통과. 그 외 `DOCUMENTS_BLOCKED` / `OVERRIDE_ADMIN_ONLY` / `OVERRIDE_REASON_REQUIRED`. assign 의 audit `after_json` 에 `override`/`override_reason`/`missing` 함께 기록 |

#### 3. 갱신 이력 history endpoint

| 파일 | 내용 |
|---|---|
| `backend/src/main/java/com/skep/document/DocumentRepository.java` | `findByOwnerTypeAndOwnerIdAndDocumentTypeIdOrderByIdDesc` 추가 |
| `backend/src/main/java/com/skep/document/DocumentService.java` | `history(documentId, actor)` — 같은 (owner_type, owner_id, type_id) 의 모든 row 를 최신순으로 반환. 권한은 owner read 권한 |
| `backend/src/main/java/com/skep/document/DocumentController.java` | `GET /api/documents/{id}/history` 추가 |

#### 4. 사용자 보충 입력 모달 (프론트)

| 파일 | 내용 |
|---|---|
| `frontend/src/features/document/DocumentVerifyDialog.tsx` | type.required_fields 기반 동적 입력 폼. `extracted_data` 가 있으면 prefill. POST `/verify { user_inputs: {...} }`. KOSHA 처럼 required_fields 가 빈 배열이면 안내 후 바로 호출 |
| `frontend/src/features/document/DocumentSection.tsx` | `autoVerify(doc)` 직접 호출 → `openVerifyDialog(doc)` 로 변경. document_types 도 동시 로드해서 type 매핑 캐시 |

#### 5. 프론트 override prompt

| 파일 | 내용 |
|---|---|
| `frontend/src/types/assignment.ts` | `AssignPayload` 에 `override` / `override_reason` 필드 |
| `frontend/src/features/assignment/SiteResourcesSection.tsx` | candidate picker 에서 `c.blocked` 자원이라도 ADMIN 이면 disable 안 함. 클릭 시 사유 prompt → override 함께 전송 |
| `frontend/src/features/assignment/ResourceAssignmentSection.tsx` | 자원 상세 → site 선택 후 배치 시 DOCUMENTS_BLOCKED 응답 받으면 사유 prompt → override 재시도 |

### 검증

프론트엔드: `npx tsc --noEmit -p tsconfig.app.json` 통과.

백엔드: `docker compose build backend` 통과. `Started SkepApplication` 정상.

API e2e:
- `POST /api/equipment/2/assignment {site_id:1}` → `DOCUMENTS_BLOCKED` 400 ✓
- `POST /api/equipment/2/assignment {site_id:1, override:true}` (사유 없음) → `OVERRIDE_REASON_REQUIRED` 400 ✓
- `POST /api/equipment/2/assignment {site_id:1, override:true, override_reason:"긴급 현장 투입"}` → `assignment_status=ASSIGNED`, `current_site_id=1` ✓
- audit `EQUIPMENT_ASSIGNED.after_json` 에 `"override":true,"override_reason":"긴급 현장 투입","missing":"자동차등록증, 정기검사증, 보험증권, 안전인증서"` 기록 ✓
- `GET /api/documents/3/history` → 같은 type 의 모든 버전 (현재 1건) ✓
- 새 upload 시 AFTER_COMMIT 비동기로 AutoVerifyTrigger 실행 (verify_endpoint 있는 type 한정).

### 아직 미구현 / 의도적으로 보류

- **OCR_REVIEW_REQUIRED 큐**: ADMIN 검토용 별도 페이지 미구현. 단계 4 후보.
- **알림(notifications) 도메인**: REJECTED / OCR_REVIEW_REQUIRED / 만료 임박 시 owner 회사 관리자에게 알림 — 별도 phase.
- **history view UI**: `GET /api/documents/{id}/history` endpoint 는 추가됐지만 프론트 페이지/다이얼로그는 미구현.
- **공급사가 ADMIN 외 override 요청**: 정책상 불가. 추후 "BP 승인 + 사유" 같은 별도 워크플로 필요할 수 있음.

---

## 2026-05-07 — Phase S-4 단계 2.1: 운영 안전성 패치 (S-4 검토 후속)

S-4 단계 2 검토에서 지적된 6건 정리. 핵심: **API 단에서도 서류 미비 자원 배차를 강제 차단**.

### 적용된 변경

| # | 위치 | 이슈 | 패치 |
|---|---|---|---|
| 1 | `AssignmentService.assignEquipment / assignPerson` | 후보 UI 만 막혀 있고 직접 API 호출은 통과 → 운영 우회 가능 | `ensureAssignmentDocumentsReady(ownerType, ownerId)` 헬퍼 추가. `blocks_assignment=true` 인 필수 서류 중 verified+안만료 가 아닌 type 이 있으면 `DOCUMENTS_BLOCKED` 400 으로 거부. `assignEquipment` / `assignPerson` 양쪽에 호출. |
| 2 | `AssignmentService.missingRequiredCounts` | `required` 와 `blocks_assignment` 가 분리 안 됨 | `missingDocCounts(ownerType, ids, REQUIRED|BLOCKING)` 로 분기. 후보 응답의 `missing_documents` 는 `REQUIRED` 기준 (표시용). 실제 차단은 `BLOCKING` 기준. `DocumentTypeRepository.findByAppliesToAndBlocksAssignmentTrueAndActiveTrueOrderByIdAsc` 추가. |
| 3 | `frontend/src/types/document.ts` | `verification_status` / `verified_by` / `rejected_reason` / `extracted_data` 등 V14 필드 미반영 | `VerificationStatus` enum + `VERIFICATION_STATUS_LABEL` 추가. `DocumentTypeResponse` 에 정책/검증 라우팅 필드 8종 노출. `DocumentResponse` 에 검증 필드 7종 노출. `parseRequiredFields(json)` 헬퍼 추가. |
| 4 | `DocumentSection` / `DocumentCard` | 기존 `PATCH /verified` 토글만 사용 → V14 의 verify/reject 운영 플로우 미반영 | `DocumentSection.autoVerify(doc)` (POST `/verify`) + `reject(doc)` (POST `/reject`) 추가. `DocumentCard` 메뉴에 "자동 검증" / "반려" 항목, 상태 배지를 `verification_status` 기준 (만료가 우선). `onAutoVerify`/`onReject` props 추가. |
| 5 | `DocumentService.upload` + `DocumentRenewDialog` | 재등록 시 옛 doc 삭제 → 갱신 이력 추적 약화 | upload 시 백엔드가 같은 (owner_type, owner_id, type_id) 의 가장 최신 문서를 자동으로 `previous_document_id` 로 묶음. `DocumentService.listForOwner` 가 `findActiveHeadByOwner` 로 chain head 만 노출. `DocumentRenewDialog` 의 옛 doc 삭제 호출 제거 (이력 보존). |
| 6 | `RoleDashboardController.bp/equipment-supplier/manpower-supplier summary` | `document_risks` 가 빈 배열 | `DocumentRepository.findRiskyForOwners(ownerType, ownerIds, maxDate)` 추가 — 만료 임박 OR REJECTED OR OCR_REVIEW_REQUIRED. supplier dashboard 는 자기 자원 owner_id 로, BP dashboard 는 자기 사이트 ACTIVE 참여공급사 자원으로 채움. `risk` 분류: `EXPIRED` / `EXPIRING_SOON` / `REJECTED` / `OCR_REVIEW_REQUIRED`. |

### 변경된 파일

백엔드:
- `backend/src/main/java/com/skep/document/DocumentRepository.java` — `findTopByOwnerTypeAndOwnerIdAndDocumentTypeIdOrderByIdDesc`, `findActiveHeadByOwner`, `findRiskyForOwners` 추가.
- `backend/src/main/java/com/skep/document/DocumentTypeRepository.java` — `findByAppliesToAndBlocksAssignmentTrueAndActiveTrueOrderByIdAsc` 추가.
- `backend/src/main/java/com/skep/document/DocumentService.java` — `listForOwner` 가 chain head 만, `upload` 가 `previous_document_id` 자동 매핑.
- `backend/src/main/java/com/skep/assignment/AssignmentService.java` — `ensureAssignmentDocumentsReady` + `MissingFilter` enum + `missingDocCounts(filter)` 분리.
- `backend/src/main/java/com/skep/dashboard/RoleDashboardController.java` — `document_risks` 채움 (BP + 공급사). `documentRisks` / `bpDocumentRisks` 헬퍼.

프론트:
- `frontend/src/types/document.ts` — V14 필드 + label + helper.
- `frontend/src/features/document/DocumentSection.tsx` — `autoVerify`/`reject` 액션 + `DocumentCard` 에 props 전달.
- `frontend/src/features/document/DocumentCard.tsx` — `verification_status` 배지 + 메뉴 (자동 검증 / 반려) + props 시그니처 확장.
- `frontend/src/features/document/DocumentRenewDialog.tsx` — 옛 doc 삭제 호출 제거 + 안내 문구 변경.

### 검증 결과

프론트엔드:
- `npx tsc --noEmit -p tsconfig.app.json` 통과.
- `docker compose build frontend` 통과.

백엔드:
- `docker compose build backend` 통과.
- 컨테이너 기동 정상.

API e2e:
- `POST /api/equipment/2/assignment` (서류 미비) → `DOCUMENTS_BLOCKED` 400 + 누락 type 명 명시 ✓
- `POST /api/equipment/1/assignment` 동일하게 차단 ✓
- `GET /api/documents?ownerType=PERSON&ownerId=2` → 3건 (chain head 만, REJECTED 1 + PENDING 2) ✓
- candidate 응답의 `missing_documents` 는 `required` 전체 (4건), 실제 차단 사유는 별도 검증 호출 시 `BLOCKING` 기준으로 계산.

### 남은 / 의도적으로 미처리

- **갱신 이력 history view**: `previous_document_id` 체인을 따라 옛 버전 조회하는 endpoint/UI 는 미구현. 필요 시 별도 단계.
- **사용자 보충 입력 폼**: `document_type.required_fields` 기반 동적 입력 폼은 다음 단계. 현재 `autoVerify` 는 빈 user_inputs 로 호출. OCR 실패 + 보충 필드 부족 시 `OCR_REVIEW_REQUIRED` 로 떨어짐.
- **upload 시 자동 OCR 트리거**: 여전히 미구현. 수동 endpoint 만.
- **ADMIN 의 강제 진행(override)**: `DOCUMENTS_BLOCKED` 도 ADMIN 우회 불가. 정책 토론 후 필요 시 별도 phase.

---

## 2026-05-07 — Phase S-4 단계 2: 외부 verify-api / main-api 연동

### 목적

skep `LiftonVerifyClient` / `VerificationService` 흐름을 skep-v2 에 이식. 단계 1 에서 만들어둔 `document_types.verify_endpoint` 라우팅을 실제 외부 호출로 연결한다.

### 추가된 백엔드 코드

| 파일 | 내용 |
|---|---|
| `backend/pom.xml` | `spring-boot-starter-webflux` 의존성 추가 (WebClient용) |
| `backend/src/main/java/com/skep/verify/VerifyClient.java` | main-api 정부 API + verify-api OCR 호출 클라이언트. WebClient + X-API-KEY. graceful fail (`UPSTREAM_ERROR` / `UPSTREAM_DISABLED`). skep `LiftonVerifyClient.java` 골자 그대로 |
| `backend/src/main/java/com/skep/verify/VerificationService.java` | 라우팅 + 결과 저장. `verifyDocument(id, userInputs, actor)` / `rejectDocument(id, reason, actor)` |
| `backend/src/main/java/com/skep/verify/VerifyController.java` | `POST /api/documents/{id}/verify` / `POST /api/documents/{id}/reject` |
| `backend/src/main/resources/application.yml` | `verify.enabled` / `verify.main-api.url` / `verify.inner-api.url` / `verify.api-key` 설정 |
| `docker-compose.yml` | `VERIFY_ENABLED` (기본 false) / `VERIFY_MAIN_API_URL` / `VERIFY_INNER_API_URL` / `VERIFY_API_KEY` 환경변수 |

### 검증 흐름

`POST /api/documents/{id}/verify` body:
```json
{ "user_inputs": { "license_no": "...", "name": "...", "license_condition_code": "01" } }
```

처리 단계:
1. owner 권한 검증 — ADMIN 또는 자기 회사 공급사만 트리거 가능 (`VERIFY_DENIED`).
2. `document_type.verify_endpoint == null` 이면 `VERIFY_NOT_SUPPORTED` 400.
3. `ocr_enabled=true` 이면 verify-api OCR 추출 호출. 결과를 `extracted_data` 에 JSON 저장.
4. **사용자 보충 필드(userInputs)가 OCR 결과보다 우선** — OCR 실패 / 일부 필드 누락 시 사람이 보충 가능.
5. `verify_endpoint` 별 호출:
   - `RIMS_LICENSE`: `f_license_no / f_resident_name / f_licn_con_code`
   - `CARGO_LICENSE`: `name / birth / lcnsNo`
   - `NTS_BIZ`: `bizNo / startDate / ownerName`
   - `KOSHA`: 이미지 multipart 직접
6. 응답 분기:
   - `result.verified == true` → `markVerifiedBy(actor.id())` → `verification_status = VERIFIED`
   - `reasonCode == UPSTREAM_ERROR | UPSTREAM_DISABLED` → `markOcrReviewRequired()` → `verification_status = OCR_REVIEW_REQUIRED`
   - 그 외 → `markRejected(actor.id(), reasonCode)` → `verification_status = REJECTED`
7. `verification_result` 에 응답 원본 JSON 저장 (감사용).
8. audit `DOCUMENT_VERIFIED` 기록 (before/after status).

### 반려 endpoint

`POST /api/documents/{id}/reject` body:
```json
{ "reason": "테스트 반려" }
```

ADMIN 만 가능 (`REJECT_ADMIN_ONLY`). `reason` 필수 (`REASON_REQUIRED`). audit 기록.

### Graceful fail 정책

운영 안전을 위해 다음 경우 모두 `UPSTREAM_*` 응답 + `OCR_REVIEW_REQUIRED` 상태로 떨어뜨려 시스템이 멈추지 않게 한다.

| 상황 | reasonCode |
|---|---|
| `verify.enabled=false` (개발/CI) | `UPSTREAM_DISABLED` |
| 타임아웃 / 5xx / 네트워크 에러 | `UPSTREAM_ERROR` |

기본 `verify.enabled` 는 false. 운영 환경에서만 true 로 켜고 `VERIFY_API_KEY` 함께 주입.

### 검증 결과

- `docker compose build backend` 통과.
- `VerifyClient` 부트스트랩 로그: `enabled=false main=http://main-api:8080 inner=http://verify-api:8081 apiKey=(empty)***`.
- e2e curl 검증:
  - `POST /api/documents/5/verify` (건강진단서, verify_endpoint=NULL) → `VERIFY_NOT_SUPPORTED` 400 ✓
  - `POST /api/documents/3/verify` (운전면허증, RIMS_LICENSE, VERIFY_ENABLED=false) → `verification_status=OCR_REVIEW_REQUIRED` + `verification_result={..."reasonCode":"UPSTREAM_DISABLED"...}` ✓
  - `POST /api/documents/3/reject` (사유 "테스트 반려") → `verification_status=REJECTED` + `rejected_reason="테스트 반려"` ✓
  - audit logs: `DOCUMENT_VERIFIED` 2건 (OCR_REVIEW_REQUIRED → REJECTED) 정상 기록.

### 아직 미구현

- **upload 시 자동 OCR 트리거** — 현재는 수동 endpoint 만. upload 직후 트랜잭션 커밋 후 비동기 트리거 추가 예정.
- **재등록 문서 체인** — upload 시 같은 (owner_type, owner_id, document_type_id) 기존 문서가 있으면 그 id 를 `previous_document_id` 로 묶기. 단계 2.5 또는 단계 3.
- **프론트 UI** — 검증/반려 버튼, OCR 결과 미리보기, 사용자 보충 입력 폼 (document_type.required_fields 기반 동적 필드). 다음 작업.
- **알림** — REJECTED / OCR_REVIEW_REQUIRED 발생 시 owner 회사 관리자에게 알림. notifications 도메인 미구현이라 보류.
- **OCR_REVIEW_REQUIRED 큐** — ADMIN 이 검토할 대상 목록 endpoint. 별도 phase.

---

## 2026-05-07 — Phase S-4 단계 1: 서류 정책 + 검증 필드 정리

### 목적

skep `LiftonVerifyClient` / `verify-api` 흐름을 분석한 뒤, skep-v2 에 옮기기 위해 먼저 DB 와 시드를 고정한다. 이 단계는 외부 API 호출 없이 정책/스키마/시드만 손본다. 외부 호출(OCR + 정부 API) 은 단계 2 로 분리.

### 적용된 변경

#### V14 마이그레이션 — `V14__document_policy.sql`

**document_types 추가 컬럼**:

| 컬럼 | 의미 |
|---|---|
| `required` | 작업계획서 투입 시 필수 서류인지 |
| `blocks_assignment` | 만료/REJECTED 시 배정 차단 |
| `default_valid_months` | 재등록 시 만료일 자동 제안 |
| `ocr_enabled` | verify-api OCR 추출 대상 |
| `ocr_extract_type` | `LICENSE` / `BUSINESS` / `CARGO` / `KOSHA` / `EQUIPMENT_REGISTRATION` |
| `ocr_expiry_field_key` | OCR 결과에서 만료일로 사용할 필드 키 |
| `verify_endpoint` | main-api 정부 API 라우팅: `RIMS_LICENSE` / `CARGO_LICENSE` / `KOSHA` / `NTS_BIZ` |
| `required_fields` | 검증/등록 시 채워야 하는 필드 목록 (JSON 배열 문자열) |

**documents 추가 컬럼**:

| 컬럼 | 의미 |
|---|---|
| `verification_status` | `PENDING` / `VERIFIED` / `REJECTED` / `OCR_REVIEW_REQUIRED` |
| `verified_by` / `verified_at` | 검증 주체와 시각 |
| `rejected_reason` | REJECTED 시 사유 |
| `previous_document_id` | 갱신(재업로드) 시 직전 문서 연결 |
| `verification_result` | verify-api 응답 원본 JSON 문자열 |
| `extracted_data` | OCR 추출 + 사용자 보충 입력 합본 JSON 문자열 |

기존 `verified=true` 였던 row 는 자동으로 `verification_status = 'VERIFIED'` 로 마이그레이션.

#### 시드 정책 (12종 → 13종)

| 서류 | applies_to | required | blocks | OCR | verify endpoint | required_fields |
|---|---|---|---|---|---|---|
| 운전면허증 | PERSON | ✓ | ✓ | LICENSE | RIMS_LICENSE | license_no, name, license_condition_code |
| 신분증 | PERSON | ✓ |   |   |   | (없음) |
| 안전교육 이수증 | PERSON | ✓ | ✓ | KOSHA | KOSHA | (이미지) |
| 건강진단서 | PERSON | ✓ | ✓ |   |   | expiry_date |
| 자격증 | PERSON |   |   |   |   | (없음) |
| **화물운송자격증** (V14 신규) | PERSON |   |   | CARGO | CARGO_LICENSE | license_no, name, birth_date |
| 기타 | PERSON |   |   |   |   | (없음) |
| 자동차등록증 | EQUIPMENT | ✓ | ✓ | EQUIPMENT_REGISTRATION |   | vehicle_no |
| 정기검사증 | EQUIPMENT | ✓ | ✓ |   |   | expiry_date |
| 보험증권 | EQUIPMENT | ✓ | ✓ |   |   | expiry_date |
| 안전인증서 | EQUIPMENT | ✓ | ✓ |   |   | expiry_date |
| 점검표 | EQUIPMENT |   |   |   |   | expiry_date |
| 기타 | EQUIPMENT |   |   |   |   | (없음) |

사업자등록증은 회사(Company) 검증용이라 document_types 에는 넣지 않음. 별도 phase 에서 처리.

#### 코드 변경

| 파일 | 변경 |
|---|---|
| `backend/src/main/java/com/skep/document/VerificationStatus.java` | 신규 enum (PENDING/VERIFIED/REJECTED/OCR_REVIEW_REQUIRED) |
| `backend/src/main/java/com/skep/document/DocumentType.java` | 8개 신규 컬럼 + builder 매핑 |
| `backend/src/main/java/com/skep/document/Document.java` | 7개 신규 컬럼 + `markVerifiedBy(userId)` / `markRejected(...)` / `markOcrReviewRequired()` 메서드 |
| `backend/src/main/java/com/skep/document/dto/DocumentTypeResponse.java` | 신규 필드 노출 |
| `backend/src/main/java/com/skep/document/dto/DocumentResponse.java` | 검증 필드 노출 |
| `backend/src/main/java/com/skep/document/dto/CreateDocumentTypeRequest.java` | 정책 필드 입력 받음 |
| `backend/src/main/java/com/skep/document/DocumentTypeService.java` | builder 매핑 |
| `backend/src/main/java/com/skep/document/DocumentTypeRepository.java` | `findByAppliesToAndRequiredTrueAndActiveTrueOrderByIdAsc` 추가 |

### 검토 후속 패치

#### #1 missing_documents 실제 계산 (AssignmentService)

기존: `missing_documents` 가 0L 하드코딩 → 후보 추천이 의미 없음.

수정:
- `DocumentRepository.findValidVerifiedTypesByOwners(ownerType, ownerIds, today)` 추가 — 자원이 보유한 "유효 서류" (verified=true, 만료 안 됨) 의 (ownerId, type_id) 페어.
- `AssignmentService.missingRequiredCounts(ownerType, ownerIds)` 신규 — 자원 owner 별 필수 서류 누락 수 계산.
- `equipmentCandidates` / `personCandidates` 가 이 헬퍼를 호출하여 `missing_documents` 실제 채움.
- 결과: `EquipmentCandidateResponse.blocked` 가 `BROKEN || missing_documents > 0` 정책으로 정상 동작.
- 검증: 시드 데이터 기준 모든 자원이 missing=4, blocked=true 로 응답 확인 (필수 서류 4종 미보유).

#### #2 markVerifiedBy 사용 (DocumentService.setVerified)

기존: `markVerified()` 만 호출 → V14 의 `verified_by`/`verified_at` 미반영.

수정: `markVerifiedBy(actor.id())` 호출. 토글 OFF 는 `unmarkVerified()` 가 verified_by/at 도 비움. audit log after_json 에 검증 주체 ID 함께 기록.

### API e2e 검증

- `POST /api/auth/login` → V14 적용 후 정상 응답.
- `GET /api/document-types?appliesTo=PERSON` → 운전면허증/안전교육 이수증/화물운송자격증/건강진단서/자격증/신분증/기타 + 신규 컬럼(`required`, `blocks_assignment`, `ocr_extract_type`, `verify_endpoint`, `required_fields`) 정상 노출.
- `GET /api/document-types?appliesTo=EQUIPMENT` → 자동차등록증/정기검사증/보험증권/안전인증서/점검표/기타 + 정책 정상.
- `GET /api/sites/1/equipment-candidates` → `missing_documents=4` (필수 4종 모두 누락), `blocked=true`.
- `GET /api/sites/1/person-candidates` → 동일.

마이그레이션 로그: `Successfully applied 1 migration to schema "public", now at version v14`.

### 아직 미구현 (단계 2 이후)

- **외부 API 연동**: `VerifyClient`, `VerificationService.verifyDocument(...)`, OCR 자동 트리거. skep `LiftonVerifyClient` 골자 그대로 이식 예정.
- **REJECTED / OCR_REVIEW_REQUIRED 운영 플로우**: 반려 endpoint, 재시도, OCR 검토 큐 등.
- **재등록 문서 체인**: `previous_document_id` 활용 — upload 시 동일 (owner_type, owner_id, document_type_id) 기존 문서가 있으면 그 id 를 `previous_document_id` 로 묶음.
- **`application.yml` + `docker-compose.yml`**: `VERIFY_MAIN_API_URL` / `VERIFY_INNER_API_URL` / `VERIFY_API_KEY` 환경변수.
- **수동 입력 폼**: 프론트 `DocumentUploadForm` 이 type 의 `required_fields` 를 동적 폼으로 렌더.

---

## 2026-05-06 — Phase S-3.1: 권한 스코프 패치 (S-3 검토 후속)

### 목적

Phase S-3 직후 검토에서 발견된 6개 권한 스코프 이슈를 정리한다. 새 기능 추가 없이 기존 코드 위에서 권한 경계만 좁힌다.

### 적용된 변경

| # | 위치 | 이슈 | 패치 |
|---|---|---|---|
| 1 | `DocumentService.ensureCanAccess` | BP/WORKER 가 모든 서류 read 가능했음 | BP 는 자기 BP 회사 소유 사이트의 ACTIVE 참여 공급사 자원 서류만 허용. WORKER 는 차단 (`DOCUMENT_ACCESS_DENIED`). DocumentService 에 `SiteRepository` + `SiteParticipantRepository` 주입. |
| 2 | `RoleDashboardController.supplierSummary` | 공급사 dashboard 의 `documents_expiring30d` 가 전체 시스템 만료 카운트를 노출 | 회사 자원 owner_id 모음을 모은 뒤 `DocumentRepository.countExpiringForOwners(ownerType, ownerIds, maxDate)` 로 좁힘. 자원이 0 건이면 0. |
| 3 | `AssignmentService.assignEquipment / assignPerson` | 다른 현장 이동 시 자동 해제(release)에 audit log 가 안 남았음 | 자동 release 직후 `EQUIPMENT_UNASSIGNED` / `PERSON_UNASSIGNED` 추가 기록. `before_json` 에 이전 site_id + 상태, `after_json` 에 `auto_release_on_move` + 새 site_id. |
| 4 | `DocumentService.updateExpiry` | 만료일 갱신에 audit log 가 없었음. `DOCUMENT_RENEWED` 액션은 정의만 있었음 | `updateExpiry` 끝에 `DOCUMENT_RENEWED` 기록 (before/after expiry_date). |
| 5 | `AuthenticatedUser` + `AuditLogService` | `is_company_admin` 정보가 인증 객체에 없음 → 회사 일반 직원도 회사 범위 로그를 볼 가능성 | (a) `User.isCompanyAdmin` 을 JWT claim `is_company_admin` 으로 발급 (`JwtService.issueAccessToken`), (b) `JwtAuthFilter.doFilterInternal` 에서 파싱하여 `AuthenticatedUser(... isCompanyAdmin)` 채움, (c) `AuditLogService.list` 에서 ADMIN 이 아니고 `isCompanyAdmin == false` 면 `findByActorUserIdOrderByCreatedAtDesc(actor.id())` 로 자기 행동 로그만 반환. |
| 6 | `App.tsx` + `DashboardRedirect` | `/worker/dashboard` 가 `DashboardRedirect` 를 다시 렌더 + WORKER 의 redirect target 도 `/worker/dashboard` → 무한 redirect 위험 | `WorkerDashboardPage` placeholder 신설. App.tsx 에서 `/worker/dashboard` 가 이 placeholder 를 렌더하도록 변경. |

### 변경된 파일

백엔드:

- `backend/src/main/java/com/skep/security/AuthenticatedUser.java` — `isCompanyAdmin` 필드 추가.
- `backend/src/main/java/com/skep/security/JwtService.java` — `is_company_admin` claim 발급.
- `backend/src/main/java/com/skep/security/JwtAuthFilter.java` — claim 파싱.
- `backend/src/main/java/com/skep/audit/AuditLogService.java` — 회사 관리자만 회사 범위 로그 조회. 일반 직원은 자기 행동만.
- `backend/src/main/java/com/skep/audit/AuditLogRepository.java` — `findByActorUserIdOrderByCreatedAtDesc` 추가.
- `backend/src/main/java/com/skep/document/DocumentService.java` — `ensureCanAccess` 강화. SiteRepository/SiteParticipantRepository 주입. `updateExpiry` 에 `DOCUMENT_RENEWED` 기록.
- `backend/src/main/java/com/skep/document/DocumentRepository.java` — `countExpiringForOwners` 추가.
- `backend/src/main/java/com/skep/dashboard/RoleDashboardController.java` — 공급사 만료 카운트를 owner_type + 회사 owner_ids 로 좁힘.
- `backend/src/main/java/com/skep/assignment/AssignmentService.java` — 자동 해제 audit log 2건 추가 (장비/인원).

프론트엔드:

- `frontend/src/features/dashboard/WorkerDashboardPage.tsx` — placeholder 신설.
- `frontend/src/App.tsx` — `/worker/dashboard` 가 placeholder 를 렌더하도록 변경.

### 권한 정책 정정 (Phase S-3 표 갱신)

| 영역 | 정책 |
|---|---|
| 서류 read (`GET /api/documents`, `GET /api/documents/{id}/file`) | ADMIN 전체 / 공급사 자기 회사 자원만 / BP 자기 사이트의 ACTIVE 참여 공급사 자원만 / WORKER 차단 |
| 서류 update / verify | 그대로 (write 는 ADMIN + 자기 회사 공급사) |
| audit log 조회 | ADMIN 전체 / 회사 관리자(`is_company_admin=true`) 자기 회사+소유/참여 사이트 / 일반 직원 자기 행동 로그만 / WORKER 사실상 빈 결과 |
| 공급사 dashboard 만료 카운트 | 자기 회사 자원 owner_type+owner_ids 만 카운트 (다른 회사 노출 차단) |
| 자원 배치/해제 audit | 사용자 명시 release + 자동(auto_release_on_move) 모두 기록 |
| 만료일 갱신 audit | `DOCUMENT_RENEWED` 기록 |

### 검증 결과

프론트엔드:

- `npx tsc --noEmit -p tsconfig.app.json` 통과 (오류 0).
- `docker compose build frontend` 통과.

백엔드:

- `docker compose build backend` 통과.
- 컨테이너 재기동 후 `Started SkepApplication` 확인.
- `POST /api/auth/login` 으로 받은 access token 의 payload 디코딩 결과에 `is_company_admin: false` (ADMIN 계정은 false) claim 포함 확인.
- ADMIN 계정 기준 `/api/dashboards/admin/summary` 정상 응답.

### 남은 / 의도적으로 미처리

- 회사 일반 직원 계정으로 동작 검증은 미실시. 일반 직원 계정 시드를 늘릴 때 같이 검증.
- BP 의 서류 read 정책에서 "참여가 INACTIVE 로 바뀐 사이트의 과거 서류" 는 현재 차단된다 (ACTIVE 만 허용). 정책 토론 후 필요 시 변경.
- audit log `ip_address` / `user_agent` 채우기는 여전히 미구현.
- 향후 권한 패치는 PolicyEvaluator / 권한 단위 테스트로 통합 예정 (Phase S-5 이후).

---

## 2026-05-06 — Phase S-3: 역할별 UI + Audit Log 기반

### 목적

작업계획서/서류 정책 강화로 들어가기 전에 "누가 무엇을 보고 무엇을 할 수 있는가" 를 먼저 고정한다. 두 가지가 핵심이다.

```text
1. 역할별 기본 진입 화면, 메뉴, 대시보드 분리
2. 주요 업무 변경의 감사 로그 기반 추가
```

이번 단계에서 작업계획서/서류 정책 본격 구현은 미루고, 그 자리에 TODO + 빈 데이터를 둔다.

### 추가된 DB 구조

| 테이블 / 컬럼 | 목적 |
|---|---|
| `audit_logs` | 누가 어떤 데이터에 어떤 액션을 했는지 추적. 알림(notifications) / 도메인 이력과 분리. |

마이그레이션:

- `V12__add_audit_logs.sql` — 테이블 + 인덱스 (actor_company_id / target_company_id / site_id / action 별)
- `V13__audit_logs_text_json.sql` — `before_json` / `after_json` 을 JSONB → TEXT 로 단순화. 이유: JPA String 매핑이 jsonb 컬럼에 직접 들어가면 PostgreSQL 이 타입 캐스팅 에러를 낸다. 이번 단계에서는 JSONB 의 검색/인덱싱 이점이 필요 없어 TEXT 로 시작하고, 향후 검색이 필요해지면 다시 JSONB + AttributeConverter 로 복귀한다.

### 추가된 백엔드 코드

| 파일 | 내용 |
|---|---|
| `backend/src/main/java/com/skep/audit/AuditLog.java` | 감사 로그 엔티티 |
| `backend/src/main/java/com/skep/audit/AuditAction.java` | action 상수 모음 (SITE_CREATED 등) |
| `backend/src/main/java/com/skep/audit/AuditTargetType.java` | target_type 상수 모음 |
| `backend/src/main/java/com/skep/audit/AuditLogRepository.java` | 전체 / 회사 범위 조회 |
| `backend/src/main/java/com/skep/audit/AuditLogService.java` | `record(...)` 진입점 + 권한별 list/recent |
| `backend/src/main/java/com/skep/audit/AuditLogController.java` | `GET /api/audit-logs`, `GET /api/audit-logs/recent` |
| `backend/src/main/java/com/skep/audit/dto/AuditLogResponse.java` | 응답 DTO |
| `backend/src/main/java/com/skep/dashboard/RoleDashboardController.java` | 역할별 dashboard summary 4종 — 기존 단일 `/api/dashboard/summary` 와 별개. |
| `backend/src/main/java/com/skep/user/UserRepository.java` | `countByEnabled(false)` 추가 (승인 대기 카운트) |
| `backend/src/main/java/com/skep/site/SiteService.java` | AuditLogService 주입 + create/update/addParticipant/removeParticipant 끝에 record 호출 + JSON escape 헬퍼 |
| `backend/src/main/java/com/skep/assignment/AssignmentService.java` | AuditLogService 주입 + assign/release (장비/인원 4메서드) 끝에 record 호출 |
| `backend/src/main/java/com/skep/document/DocumentService.java` | AuditLogService 주입 + upload/setVerified 후 record 호출 |

### 추가된 API

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/audit-logs` | 권한 범위 내 로그 페이지 조회 |
| `GET` | `/api/audit-logs/recent` | 권한 범위 내 최근 N건 |
| `GET` | `/api/dashboards/admin/summary` | ADMIN 대시보드 summary |
| `GET` | `/api/dashboards/bp/summary` | BP 대시보드 summary |
| `GET` | `/api/dashboards/equipment-supplier/summary` | 장비공급사 대시보드 summary |
| `GET` | `/api/dashboards/manpower-supplier/summary` | 인력공급사 대시보드 summary |

기존 `/api/dashboard/summary` 는 그대로 유지. 역할별 endpoint 가 우선이며, 단일 endpoint 는 호환용이다 (Phase S-5 이후 단계에서 제거 검토).

### 권한 정책

| 역할 | 로그 조회 범위 |
|---|---|
| `ADMIN` | 전체 로그 |
| `BP` | `actor_company_id = 자기 회사` OR `target_company_id = 자기 회사` OR `site_id` 가 자기 BP 회사 소유 사이트 |
| `EQUIPMENT_SUPPLIER` | 자기 회사가 actor/target 인 로그 + 자기 회사가 ACTIVE 참여 중인 사이트 로그 |
| `MANPOWER_SUPPLIER` | 동일 |
| `WORKER` | 사실상 빈 결과 (사이트 참여 없음) |

대시보드는 역할별 endpoint 단계에서 거부:

| Endpoint | 허용 role |
|---|---|
| `/api/dashboards/admin/summary` | `ADMIN` (`ADMIN_ONLY`) |
| `/api/dashboards/bp/summary` | `BP` (`BP_ONLY`) |
| `/api/dashboards/equipment-supplier/summary` | `EQUIPMENT_SUPPLIER` (`EQ_SUPPLIER_ONLY`) |
| `/api/dashboards/manpower-supplier/summary` | `MANPOWER_SUPPLIER` (`MP_SUPPLIER_ONLY`) |

### Audit log 가 붙은 액션

| Action | 위치 | before_json | after_json |
|---|---|---|---|
| `SITE_CREATED` | SiteService.create | - | name + status |
| `SITE_UPDATED` | SiteService.update | name + status (이전) | name + status (이후) |
| `PARTICIPANT_ADDED` | SiteService.addParticipant | - | site_id + company_id + type |
| `PARTICIPANT_REMOVED` | SiteService.removeParticipant | `{"status":"ACTIVE"}` | `{"status":"INACTIVE"}` |
| `EQUIPMENT_ASSIGNED` | AssignmentService.assignEquipment | - | site_id + status |
| `EQUIPMENT_UNASSIGNED` | AssignmentService.releaseEquipment | site_id + ASSIGNED | 새 status |
| `PERSON_ASSIGNED` | AssignmentService.assignPerson | - | site_id + status |
| `PERSON_UNASSIGNED` | AssignmentService.releasePerson | site_id + ON_DUTY | 새 status |
| `DOCUMENT_UPLOADED` | DocumentService.upload | - | owner_type + owner_id + type 이름 |
| `DOCUMENT_VERIFIED` | DocumentService.setVerified | `{"verified":<이전>}` | `{"verified":<이후>}` |

붙이지 않은 액션 (이번 단계 보류):

- `EQUIPMENT_STATUS_CHANGED` — equipment.assignment_status 별도 토글 API 가 아직 없음. 배치/해제 로그로 간접 추적 가능.
- `DOCUMENT_RENEWED` — 별도 renew endpoint 가 없고 새 upload + 옛 doc 삭제 흐름. Phase S-4 에서 정식 흐름이 정해지면 추가.
- `WORK_PLAN_*` — 작업계획서 도메인 자체가 미구현 (Phase S-5).

### 추가된 라우트 (Frontend)

| Path | Page | 권한 |
|---|---|---|
| `/` | DashboardRedirect | 인증된 사용자 |
| `/dashboard` | DashboardRedirect | 인증된 사용자 |
| `/admin/dashboard` | AdminDashboardPage | ADMIN |
| `/bp/dashboard` | BpDashboardPage | BP |
| `/equipment-supplier/dashboard` | EquipmentSupplierDashboardPage | EQUIPMENT_SUPPLIER |
| `/manpower-supplier/dashboard` | ManpowerSupplierDashboardPage | MANPOWER_SUPPLIER |
| `/worker/dashboard` | DashboardRedirect (placeholder) | WORKER |
| `/audit-logs` | AuditLogPage | 로그인 |

기존 `/sites`, `/equipment`, `/persons`, `/admin/users`, `/admin/companies` 그대로 유지. 권한은 ProtectedRoute + API 단에서 강제.

### 역할별 메뉴

`Sidebar.tsx` 가 `useAuth().user.role` 로 분기.

| 역할 | 메뉴 |
|---|---|
| `ADMIN` | 대시보드 / 회사 관리 / 사용자 관리 / 현장 관리 / 장비 관리 / 인원 관리 / 서류 관리(disabled) / 작업계획서(disabled) / 알림(disabled) / 로그 / 설정(disabled) |
| `BP` | 대시보드 / 현장 관리 / 참여 공급사 / 배치 장비 / 배치 인원 / 작업계획서(disabled) / 서류 위험(disabled) / 알림(disabled) / 로그 |
| `EQUIPMENT_SUPPLIER` | 대시보드 / 내 장비 / 장비 서류 / 현장 관리 / 장비 배치 현황 / 작업 일정(disabled) / 알림(disabled) / 로그 |
| `MANPOWER_SUPPLIER` | 대시보드 / 내 인원 / 인원 서류 / 현장 관리 / 인원 배치 현황 / 작업 일정(disabled) / 알림(disabled) / 로그 |

공급사의 "현장 관리" 메뉴는 현장 생성/수정이 아니라 참여 현장 조회를 의미한다. SiteService 의 권한 정책이 이미 그렇게 동작한다 (공급사는 list 시 ACTIVE 참여 사이트만 반환).

`primaryItems` 의 일부 항목은 같은 라우트 (`/sites`, `/equipment`, `/persons`) 를 가리킨다. 이번 단계는 메뉴 정보 구조를 고정하는 것이 우선이고, 실제 별도 화면 분리는 Phase S-4 이후 자연스럽게 분기된다 (예: BP 의 "배치 장비" 는 자기 현장 기준 필터된 화면으로).

### 추가된 프론트엔드 코드

| 파일 | 내용 |
|---|---|
| `frontend/src/features/dashboard/DashboardRedirect.tsx` | 역할별 dashboard 로 redirect |
| `frontend/src/features/dashboard/AdminDashboardPage.tsx` | ADMIN 대시보드 (전체 카운트 + 최근 audit log) |
| `frontend/src/features/dashboard/BpDashboardPage.tsx` | BP 대시보드 (내 현장 + 참여/배치 카운트 + audit log) |
| `frontend/src/features/dashboard/EquipmentSupplierDashboardPage.tsx` | 장비공급사 대시보드 |
| `frontend/src/features/dashboard/ManpowerSupplierDashboardPage.tsx` | 인력공급사 대시보드 |
| `frontend/src/features/dashboard/AuditLogPage.tsx` | `/audit-logs` 전체 페이지 (테이블 + 페이지네이션) |
| `frontend/src/features/dashboard/AuditLogWidget.tsx` | 대시보드용 최근 활동 위젯 |
| `frontend/src/features/dashboard/widgets.tsx` | 공통 StatCard / SectionCard / EmptyState / TodoBanner |
| `frontend/src/App.tsx` | 역할별 dashboard 라우트 + DashboardRedirect 추가 |
| `frontend/src/components/layout/Sidebar.tsx` | role 별 메뉴 분기 (ADMIN/BP/공급사 4 + 기본) |

### UI 범위

구현된 화면:

- 4개 역할별 dashboard 페이지. 각 페이지는 4–6개 카운트 카드 + 사이트 리스트(BP/공급사) + 최근 audit log 위젯.
- audit-logs 전체 페이지. 페이지네이션 포함.
- 역할별 사이드바 메뉴.
- 로그인 후 역할에 맞는 dashboard 로 자동 진입.

빈 상태 + TODO 만 둔 영역:

- 역할별 알림 위젯 (notifications 도메인 미구현)
- BP 의 "오늘/이번 주 작업계획서"
- 공급사의 "내 자원이 포함된 작업계획서"
- 회사·현장별 위험 요약 (서류 정책 강화 후 채움)
- 공급사 owner_type 별 만료 카운트 (현재는 전체 30일 임박 카운트만)

### 검증 결과

프론트엔드:

- `npx tsc --noEmit -p tsconfig.app.json` 통과 (오류 0)
- 호스트 `npm run build` 는 WSL 의 rollup native binary 누락으로 실행 불가. Docker 빌드(`docker compose build frontend`) 가 정상 통과.

백엔드:

- `docker compose build backend` 정상 통과 (Maven 컨테이너 내부에서 컴파일).
- 컨테이너 기동 시 Flyway 가 V12 / V13 마이그레이션 자동 적용 확인.
- 호스트에 Maven/JDK 가 잡혀있지 않아 `mvn -DskipTests compile` 직접 검증은 못 함. Docker 빌드가 동일한 검증 역할.

API e2e 동작 확인 (curl + admin 토큰):

- `POST /api/sites` (BP company id=1, name=S-3 audit live) → 사이트 id=4 생성 → `audit_logs` 에 SITE_CREATED 1건 기록 확인.
- `GET /api/audit-logs?size=5` → SITE_CREATED 로그 응답 확인 (action / target_type / target_id / site_id / actor_role / after_json 모두 존재).
- `GET /api/dashboards/admin/summary` → counts (companies/sites/equipment/persons/documents_expiring30d/users_pending) + recent_audit_logs(1건) 응답 확인.
- 마이그레이션 로그: V11 → V12 → V13 순으로 자동 적용 (`Successfully applied 1 migration to schema "public", now at version v13`).

V12 적용 직후 `INSERT ... after_json (jsonb 타입에 character varying 입력)` 에러로 audit log 기록이 실패했고, V13 으로 컬럼을 TEXT 로 변경한 후 정상 동작했다. 이 의사결정은 docs 에 명시.

### 다음 구현 순서

1. Phase S-4: 서류 정책 강화 — `document_types.required/blocks_assignment/default_valid_months/ocr_*` + `documents.verification_status` 등 추가하고, supplier owner_type 별 만료/누락 카운트를 정확히 채우고 candidate API 의 `missing_documents` 도 채운다.
2. Phase S-5: 작업계획서 도메인 추가. dashboard 의 `today_work_plans` / `upcoming_work_plans` 가 그때 채워진다.
3. 알림(notifications) 도메인 추가. dashboard 의 `recent_notifications` 가 그때 채워진다.
4. 기존 `skep` 폴더의 작업계획서 PDF 출력/자동화 이식.

### 남은 / 의도적으로 보류한 작업

- **Audit before_json/after_json 검색**: TEXT 로 단순화. JSON 검색이 필요해지면 V?? 에서 JSONB 로 복귀 + Hibernate AttributeConverter 도입.
- **WORKER dashboard**: placeholder. 정식 화면은 모바일 앱 단계에서 정의.
- **Sidebar 메뉴 항목 중 같은 라우트를 가리키는 일부 (BP "배치 장비" → `/equipment` 등)**: 별도 필터 화면이 정해질 때까지 같은 라우트로 묶어둠.
- **알림 도메인**: dashboard 와 audit_log 가 분리되었지만 notifications 자체는 미구현.
- **audit log 의 ip_address / user_agent**: 컬럼은 있으나 service 에서 채우지 않음. 필요해지면 RequestContextHolder 에서 주입.
- **다음 사이클 시작 시 JDK 환경에서 `mvn -DskipTests compile` 한 번 더 통과** 시키는 것을 권장.

---

## 2026-05-06 — Phase S-2: 자원 현장 배치 + 배치 이력

### 목적

BP사가 만든 현장에 장비공급사/인력공급사의 자원을 실제로 배치하고, 이동/해제 이력을 보존한다. 작업계획서(다음 단계)에서 후보 추천 기반이 된다.

이 단계에서 확정한 흐름:

```text
BP가 현장 + 참여공급사 구성 (Phase S-1)
→ BP/ADMIN 이 자원을 현장에 배치 (Phase S-2)
→ 자원이 다른 현장으로 이동하면 기존 배치는 자동 해제
→ 모든 배치/해제 이력 보존
→ 후보 API에서 이전 투입/만료/사용 제한 정보까지 합쳐 반환
→ Phase S-3에서 역할별 UI/대시보드/로그 기반을 먼저 고정
→ Phase S-4에서 서류 정책을 보강
```

### 추가된 DB 구조

| 테이블 / 컬럼 | 목적 |
|---|---|
| `equipment.current_site_id` (FK sites.id, ON DELETE SET NULL) | 빠른 조회용 현재 배치 현장 캐시 |
| `equipment.assignment_status` (`AVAILABLE/ASSIGNED/BROKEN`) | 자원 배치 상태 |
| `equipment.last_assigned_at` | 가장 최근 배치 시각 |
| `persons.current_site_id`, `persons.assignment_status` (`ON_DUTY/OFF_DUTY/INACTIVE`), `persons.last_assigned_at` | 동일 |
| `equipment_site_assignments` | 장비 ↔ 현장 배치 이력 (assigned_at/released_at/note/release_reason 등) |
| `person_site_assignments` | 인원 ↔ 현장 배치 이력 |

마이그레이션: `backend/src/main/resources/db/migration/V11__add_resource_assignments.sql`

핵심 제약:

- `UNIQUE INDEX ... WHERE released_at IS NULL` — 자원당 활성 배치는 항상 1건만.
- 자원 supplier 가 사이트의 ACTIVE `site_participants` 에 포함돼야 배치 가능 (서비스 레이어 검증).
- 같은 현장에 재배치는 거부.
- 다른 현장에 배치된 자원을 새 현장에 배치하면 기존 활성 배치를 자동으로 닫고 새 row 생성 (단일 트랜잭션).
- 사이트가 ACTIVE 가 아니면 배치 불가.
- 장비 `BROKEN` / 인원 `INACTIVE` 는 배치 불가.

### 추가된 백엔드 코드

| 파일 | 내용 |
|---|---|
| `backend/src/main/java/com/skep/equipment/EquipmentAssignmentStatus.java` | 장비 배치 상태 enum |
| `backend/src/main/java/com/skep/person/PersonAssignmentStatus.java` | 인원 배치 상태 enum |
| `backend/src/main/java/com/skep/equipment/Equipment.java` | `current_site_id/assignment_status/last_assigned_at` 컬럼 + `assignToSite/releaseFromSite` 메서드 |
| `backend/src/main/java/com/skep/person/Person.java` | 동일한 컬럼/메서드 |
| `backend/src/main/java/com/skep/assignment/EquipmentAssignment.java` | 장비 배치 이력 엔티티 |
| `backend/src/main/java/com/skep/assignment/PersonAssignment.java` | 인원 배치 이력 엔티티 |
| `backend/src/main/java/com/skep/assignment/EquipmentAssignmentRepository.java` | 활성 배치/이력/이전 투입 조회 |
| `backend/src/main/java/com/skep/assignment/PersonAssignmentRepository.java` | 동일 |
| `backend/src/main/java/com/skep/assignment/AssignmentService.java` | 배치/해제/이력/후보 비즈니스 로직 + 권한 정책 |
| `backend/src/main/java/com/skep/assignment/AssignmentController.java` | `/api/equipment/{id}/assignment[s]`, `/api/persons/{id}/assignment[s]`, `/api/sites/{id}/equipment[-candidates]`, `/api/sites/{id}/persons[-candidates]` |
| `backend/src/main/java/com/skep/assignment/dto/{AssignRequest,ReleaseRequest,AssignmentResponse,EquipmentCandidateResponse,PersonCandidateResponse}.java` | 요청/응답 DTO |
| `backend/src/main/java/com/skep/equipment/dto/EquipmentResponse.java` | `current_site_id/current_site_name/assignment_status/last_assigned_at` 필드 추가 + 오버로드 from(e, expiring, siteName) |
| `backend/src/main/java/com/skep/person/dto/PersonResponse.java` | 동일 |
| `backend/src/main/java/com/skep/equipment/EquipmentRepository.java` | `findBySupplierIdInOrderByIdDesc` 추가 (후보 조회) |
| `backend/src/main/java/com/skep/person/PersonRepository.java` | 동일 |
| `backend/src/main/java/com/skep/equipment/EquipmentController.java` | list/get 시 `AssignmentService.siteNamesByIds` 로 현재 현장 이름 함께 반환 |
| `backend/src/main/java/com/skep/person/PersonController.java` | 동일 |

### 추가된 API

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/api/equipment/{id}/assignment` | 장비를 현장에 배치 |
| `DELETE` | `/api/equipment/{id}/assignment` | 장비 현장 해제 |
| `GET` | `/api/equipment/{id}/assignments` | 장비 배치 이력 |
| `POST` | `/api/persons/{id}/assignment` | 인원 배치 |
| `DELETE` | `/api/persons/{id}/assignment` | 인원 해제 |
| `GET` | `/api/persons/{id}/assignments` | 인원 배치 이력 |
| `GET` | `/api/sites/{id}/equipment` | 현장 현재 배치 장비 |
| `GET` | `/api/sites/{id}/persons` | 현장 현재 배치 인원 |
| `GET` | `/api/sites/{id}/equipment-assignments` | 현장 장비 전체 이력 |
| `GET` | `/api/sites/{id}/person-assignments` | 현장 인원 전체 이력 |
| `GET` | `/api/sites/{id}/equipment-candidates` | 참여 공급사 장비 후보 (이전 투입/현재 배치/만료/차단 메타 포함) |
| `GET` | `/api/sites/{id}/person-candidates` | 참여 공급사 인원 후보 |

상세 명세는 `docs/API_SPEC.md` 의 "Resource Assignments" 섹션.

### 권한 정책

| 역할 | 정책 |
|---|---|
| `ADMIN` | 모든 자원/현장에 대해 배치/해제/이력/후보 가능 |
| `BP` | 자기 BP 회사가 소유한 현장에 한해 배치/해제/후보 조회. 자기 현장 배치 자원 조회 가능 |
| `EQUIPMENT_SUPPLIER` | 배치/해제 불가. 자기 회사 자원 이력 조회 + 참여 중인 현장 자원 조회 가능 |
| `MANPOWER_SUPPLIER` | 동일 |
| `WORKER` | 권한 없음 |

추가로:

- 자원의 `supplier_id` 가 사이트의 ACTIVE `site_participants` 에 포함되지 않으면 배치 거부 (`SUPPLIER_NOT_PARTICIPANT`).
- 사이트가 `ACTIVE` 가 아니면 배치 거부 (`SITE_NOT_ACTIVE`).
- 장비 `BROKEN` / 인원 `INACTIVE` 는 배치 거부 (`EQUIPMENT_BROKEN` / `PERSON_INACTIVE`).
- 같은 현장에 이미 활성 배치된 자원을 다시 배치하려 하면 `ALREADY_ASSIGNED`.
- 다른 현장에 배치된 자원을 다른 현장에 배치 요청하면 자동으로 기존 배치를 닫고 새 배치 생성.

### 추가된 프론트엔드 코드

| 파일 | 내용 |
|---|---|
| `frontend/src/types/assignment.ts` | 배치 상태/이력/후보 타입 + 한국어 라벨 |
| `frontend/src/types/equipment.ts` | `current_site_id/current_site_name/assignment_status/last_assigned_at` 필드 추가 |
| `frontend/src/types/person.ts` | 동일 |
| `frontend/src/features/assignment/AssignmentBadge.tsx` | 자원 배치 상태 배지 (배치중 / 미배치 / 고장 / 비활성) |
| `frontend/src/features/assignment/ResourceAssignmentSection.tsx` | 자원 상세 페이지에서 사용. 현재 배치 + 배치/해제 액션 + 이력 |
| `frontend/src/features/assignment/SiteResourcesSection.tsx` | 현장 상세 페이지에서 사용. "배치 장비" / "배치 인원" 섹션 + 후보 picker (이전 투입/만료/사용 제한 배지 포함, 추천 정렬) |
| `frontend/src/features/site/SiteDetailPage.tsx` | 참여 업체 위에 SiteResourcesSection 삽입 |
| `frontend/src/features/equipment/EquipmentDetailPage.tsx` | 헤더에 AssignmentBadge + 현재 배치 사이트 링크, 본문 끝에 ResourceAssignmentSection |
| `frontend/src/features/person/PersonDetailPage.tsx` | 동일 |
| `frontend/src/features/equipment/EquipmentTable.tsx` | "현장(위치)" 열에 실제 `current_site_name` / 미배치 표시 |
| `frontend/src/features/person/PersonTable.tsx` | "소속" 열에 현재 배치 사이트 라인 추가 |

### UI 범위

구현된 화면:

- 현장 상세에 "배치 장비" / "배치 인원" 섹션 (현재 배치 자원 목록 + "+ 배치 추가" 액션)
- 후보 picker — 참여 공급사 자원만 노출, 이미 다른 현장에 배치 중이면 표시, 이전 투입/만료 임박/사용 제한 배지, 추천 정렬
- 자원 상세에 "현장 배치" 카드 — 현재 배치 + 배치 변경/해제 + 배치 이력
- 자원 헤더에 배치 상태 배지 + 현재 사이트 링크
- 자원 목록 행에 현재 배치 사이트 표시

아직 구현하지 않은 화면:

- 작업계획서 화면 (Phase S-5 이후)
- 서류 정책 강화 (필수 서류 누락 표시 정확화) — 현재 `missing_documents` 는 0으로 고정
- 역할별 대시보드 재설계
- 자원 탭 내부 mock 위젯(가동 현황 도넛 등) 의 실제 데이터 연동

### 서류 상태와 후보 추천 기준

이번 단계에서 구조를 잡아둔 부분:

- `EquipmentCandidateResponse` / `PersonCandidateResponse` 에 다음 필드를 포함:
  - `previously_used_on_site` — `equipment_site_assignments / person_site_assignments` 에서 site_id + resource_id 기록 존재 여부.
  - `currently_assigned` + `current_site_id/name` — 자원의 캐시 + assignment_status.
  - `expiring_documents` — 30일 이내 만료 서류 수 (DocumentRepository 기존 쿼리 재사용).
  - `missing_documents` — Phase S-4 서류 정책 강화에서 `document_types.required` + 필수 미등록 계산 시 채움. 현재는 0.
  - `blocked` — `BROKEN` / `INACTIVE` 또는 `missing_documents > 0` 일 때 true.
- 후보 응답은 정렬되지 않은 상태로 내려간다. 프론트(`SiteResourcesSection`) 에서 추천 정렬:
  - 다른 현장에 배치된 자원은 뒤로
  - blocked 는 뒤로
  - 이전 투입은 앞으로
  - 만료 임박 적은 순

### 문서 반영

| 문서 | 반영 내용 |
|---|---|
| `docs/ERD.md` | V11 컬럼/이력 테이블, 관계, enum, 마이그레이션 추가, 자원 배치 정책 절 |
| `docs/API_SPEC.md` | "Resource Assignments" 섹션 전체. EquipmentResponse/PersonResponse 스키마에 V8/V9/V11 필드 명시 |
| `docs/WORK_PLAN_CENTERED_SYSTEM_DESIGN.md` | 구현 현황표 2단계 완료로 업데이트 |
| `docs/IMPLEMENTATION_LOG.md` | 이번 작업 로그 작성 |

### 검증 결과

프론트엔드:

- `npx tsc --noEmit -p tsconfig.app.json` 통과 (오류 0)
- 호스트 `npm run build` 는 WSL 환경에서 `@rollup/rollup-linux-x64-gnu` native binary 누락으로 실행 불가 (npm install 이 Windows 호스트에서 됐기 때문). 대안으로 `docker compose build frontend` 가 정상 통과 — 결과적으로 같은 production 번들이 만들어진다.

백엔드:

- `docker compose build backend` 정상 통과 (Maven 컨테이너 내부에서 컴파일).
- 컨테이너 기동 후 Flyway 가 V10/V11 마이그레이션 자동 적용 확인 (`Successfully applied 2 migrations to schema "public", now at version v11`).
- 호스트에 Maven/JDK 가 잡혀있지 않아 `mvn -DskipTests compile` 직접 검증은 못 함. Docker 빌드(컨테이너 내부의 Maven)가 동일한 컴파일 검증을 대신했다.

API e2e 동작 확인 (curl + admin 토큰):

- `POST /api/sites` (BP company id=1) → site id=1 생성
- `POST /api/sites/1/participants` (장비공급 id=2, 인력공급 id=3) → ACTIVE 참여 등록
- `GET /api/sites/1/equipment-candidates` → 후보 2건 (`previously_used_on_site=false`, `expiring_documents=1` 등 메타 포함)
- `POST /api/equipment/2/assignment` (site 1) → `assignment_status=ASSIGNED`, `current_site_id=1` 응답
- `GET /api/sites/1/equipment` → 1건 (위 자원)
- `GET /api/equipment/2/assignments` → 활성 이력 1건
- `DELETE /api/equipment/2/assignment` → `assignment_status=AVAILABLE`, `current_site_id` 제거
- 해제 후 `GET /api/equipment/2/assignments` → 동일 row 가 `released_at + release_reason` 기록된 채로 닫힘

### 다음 구현 순서

1. Phase S-3 역할별 UI/대시보드/메뉴 + Audit Log 기반 — `docs/ROLE_BASED_DASHBOARD_DESIGN.md` 와 `docs/CLAUDE_PHASE3_ROLE_AUDIT_PROMPT.md` 기준.
2. Phase S-4 서류 정책 강화 — `document_types.required/blocks_assignment/default_valid_months/ocr_*` + `documents.verification_status` 등 추가하고 candidate API 의 `missing_documents` 를 실제 값으로 채운다.
3. Phase S-5 작업계획서 도메인 추가 (`work_plans/work_plan_equipment/work_plan_persons/work_plan_compliance_checks`). 후보 API 결과를 그대로 사용.
4. 기존 `skep` 폴더의 작업계획서 출력/자동화 분석 후 이식.

### Phase S-3 역할 분리 결정사항

Phase S-2 이후 사용자와 논의하여 다음 정책을 확정했다.

| 역할 | 확정 정책 |
|---|---|
| `ADMIN` | 모든 회사/현장/장비/인원/서류/작업계획서 업무를 대신 처리 가능. 강제 처리 가능. 전체 알림/로그/서류 위험 관제 |
| `BP` | 자기 현장 관리, 공급사 연결/해제, 공급사 자원 조회 및 배치/해제, 작업계획서 생성. 공급사 자원 자체 수정과 서류 업로드/갱신은 불가 |
| `EQUIPMENT_SUPPLIER` | 자기 장비/장비 서류 관리. 직접 현장 배치 불가. 연결된 BP 현장과 관련 작업계획서 조회 |
| `MANPOWER_SUPPLIER` | 자기 인원/인원 서류 관리. 직접 현장 배치 불가. 연결된 BP 현장과 관련 작업계획서 조회 |
| `WORKER` / `SITE_MANAGER` | 현재 보류. 추후 앱/GPS/출결 단계에서 확장 |

추가로 모든 주요 생성/수정/삭제/상태변경/배치/해제/서류갱신은 `audit_logs` 에 남기는 방향으로 확정했다. 알림(`notifications`)과 로그(`audit_logs`)는 분리한다.

### 남은 / 의도적으로 보류한 작업

- `missing_documents` 계산은 Phase S-4 로 미룸 (필수 서류 정의가 아직 없음).
- 후보 점수화는 backend 가 아닌 frontend 정렬로 단순 시작 (점수 가중치는 정책값으로 옮겨야 한다).
- BP 가 강제로 차단된 자원도 배치할 수 있도록 하는 override 흐름은 미구현.
- 자원 상세의 mock 위젯(가동 현황 도넛 등)은 그대로. 실제 데이터 연동은 별도 작업.
- 다음 사이클 시작 시 JDK 환경에서 `mvn -DskipTests compile` 한 번 더 통과 시키는 것을 권장 (현재는 Docker 빌드만 검증됨).

---

## 2026-05-06 — Phase S-1: 현장/참여업체 기반

### 목적

작업계획서 중심 구조로 가기 전에, BP사가 현장을 만들고 그 현장에 장비공급사와 인력공급사를 연결할 수 있는 기반을 추가했다.

이 단계에서 확정한 흐름은 아래와 같다.

```text
BP사 현장 생성
→ 현장 참여 공급사 선정
→ 공급사별 장비/인원 등록 자원과 연결
→ 이후 작업계획서 후보 조회/서류 적합성 판단에 사용
```

### 추가된 DB 구조

| 테이블 | 목적 |
|---|---|
| `sites` | BP사가 소유하는 현장 |
| `site_participants` | 현장에 참여하는 장비공급사/인력공급사 |

관련 마이그레이션:

- `backend/src/main/resources/db/migration/V10__add_sites_and_participants.sql`

주요 제약:

- `sites.bp_company_id`는 `companies.id`를 참조한다.
- 현장은 BP 회사가 소유한다. 실제 BP 여부는 서비스 레이어에서 검증한다.
- `site_participants.company_id`는 장비공급사 또는 인력공급사만 가능하다.
- 같은 현장에 같은 회사는 한 번만 연결된다. `UNIQUE(site_id, company_id)`
- 참여업체 해제는 삭제가 아니라 `INACTIVE` 상태로 변경한다.

### 추가된 백엔드 코드

| 파일 | 내용 |
|---|---|
| `backend/src/main/java/com/skep/site/Site.java` | 현장 엔티티 |
| `backend/src/main/java/com/skep/site/SiteParticipant.java` | 현장 참여업체 엔티티 |
| `backend/src/main/java/com/skep/site/SiteStatus.java` | 현장 상태 enum |
| `backend/src/main/java/com/skep/site/SiteParticipantType.java` | 참여업체 타입 enum |
| `backend/src/main/java/com/skep/site/SiteParticipantStatus.java` | 참여업체 상태 enum |
| `backend/src/main/java/com/skep/site/SiteRepository.java` | 현장 조회 repository |
| `backend/src/main/java/com/skep/site/SiteParticipantRepository.java` | 참여업체 조회 repository |
| `backend/src/main/java/com/skep/site/SiteService.java` | 현장 권한/생성/수정/참여업체 연결 로직 |
| `backend/src/main/java/com/skep/site/SiteController.java` | `/api/sites` API |
| `backend/src/main/java/com/skep/site/dto/*` | 요청/응답 DTO |

### 추가된 API

상세 명세는 `docs/API_SPEC.md`의 `Sites — /api/sites` 섹션에 정리했다.

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/sites` | 역할별 현장 목록 조회 |
| `GET` | `/api/sites/{id}` | 현장 상세 조회 |
| `POST` | `/api/sites` | 현장 생성 |
| `PATCH` | `/api/sites/{id}` | 현장 수정 |
| `POST` | `/api/sites/{id}/participants` | 현장 참여업체 추가 |
| `DELETE` | `/api/sites/{siteId}/participants/{participantId}` | 현장 참여업체 비활성화 |
| `GET` | `/api/sites/supplier-companies?type=EQUIPMENT` | 현장에 추가할 공급사 후보 조회 |

### 권한 정책

| 역할 | 정책 |
|---|---|
| `ADMIN` | 모든 현장 조회/생성/수정/참여업체 관리 가능 |
| `BP` | 자기 회사가 소유한 현장만 생성/수정/참여업체 관리 가능 |
| `EQUIPMENT_SUPPLIER` | 자기가 참여 중인 현장만 조회 가능 |
| `MANPOWER_SUPPLIER` | 자기가 참여 중인 현장만 조회 가능 |
| `WORKER` | 이번 단계에서는 현장 조회 권한 없음 |

### 추가된 프론트엔드 코드

| 파일 | 내용 |
|---|---|
| `frontend/src/types/site.ts` | 현장/참여업체 타입, 라벨 |
| `frontend/src/features/site/SitePage.tsx` | 현장 목록/생성 화면 |
| `frontend/src/features/site/SiteDetailPage.tsx` | 현장 상세/수정/참여업체 관리 화면 |
| `frontend/src/App.tsx` | `/sites`, `/sites/:id` 라우팅 추가 |
| `frontend/src/components/layout/Sidebar.tsx` | 현장 관리 메뉴 활성화 |

### UI 범위

구현된 화면:

- 현장 목록
- 현장 생성
- 현장 상세
- 현장 기본정보 수정
- 장비공급사/인력공급사 후보 조회
- 현장 참여업체 추가
- 현장 참여업체 비활성화

아직 구현하지 않은 화면:

- 장비/인원별 현재 현장 표시
- 장비/인원 현장 배치/해제 이력
- 작업계획서 생성 화면
- 작업계획서 후보 추천
- 서류 적합성 판단 API
- 역할별 대시보드 재설계

### 문서 반영

| 문서 | 반영 내용 |
|---|---|
| `docs/ERD.md` | `sites`, `site_participants` ERD와 enum, 마이그레이션 추가 |
| `docs/API_SPEC.md` | `/api/sites` 전체 명세 추가 |
| `docs/WORK_PLAN_CENTERED_SYSTEM_DESIGN.md` | 개발 순서 중 1단계 구현 완료 상태 반영 |
| `docs/IMPLEMENTATION_LOG.md` | 이번 작업 로그 작성 |

### 검증 결과

프론트엔드:

- `npm.cmd run typecheck` 통과
- `npm.cmd run build` 통과

백엔드:

- 로컬 환경 PATH에 `mvn.cmd`와 `java`가 없어 `mvn -DskipTests compile` 검증은 실행하지 못했다.
- 백엔드 컴파일 검증은 Maven/JDK가 잡힌 환경에서 다시 수행해야 한다.

### 다음 구현 순서

1. 장비/인원에 현재 현장 표시 필드 추가
2. 장비/인원 현장 배치/해제 이력 테이블 추가
3. 특정 현장에 참여 중인 공급사의 장비/인원 후보 조회 API 추가
4. 후보 조회 시 이전 투입 여부와 서류 구비 상태 반환
5. 작업계획서 도메인 추가
6. 기존 `skep` 폴더의 작업계획서 자동화 기능을 SKEP v2 모델에 맞춰 이식
