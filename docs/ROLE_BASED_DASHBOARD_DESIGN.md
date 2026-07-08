# 역할별 화면/권한/대시보드 설계

> 마지막 갱신: 2026-05-06
> 기준 상태: Phase S-1 현장/참여업체 완료, Phase S-2 자원 현장 배치/이력 완료

## 목적

SKEP v2는 하나의 공통 대시보드를 역할별 필터만 바꿔 보여주는 구조로 가면 안 된다. ADMIN, BP, 장비공급사, 인력공급사는 보는 데이터와 수행하는 업무가 다르다.

따라서 Phase S-3에서는 아래 두 가지를 먼저 고정한다.

```text
1. 역할별 기본 진입 화면, 메뉴, 대시보드 위젯 분리
2. 주요 업무 변경에 대한 audit log 기반 추가
```

작업계획서와 서류 적합성 API는 이 역할 구조 위에서 붙인다.

## 현재까지 확정된 역할 정의

| 역할 | 핵심 목적 |
|---|---|
| `ADMIN` | 전체 시스템 관리, 다른 회사 업무 대행, 강제 처리, 전체 위험 관제 |
| `BP` | 자기 현장 구성, 공급사 연결/해제, 장비/인원 배치, 작업계획서 생성 |
| `EQUIPMENT_SUPPLIER` | 자기 장비 등록/관리, 장비 서류 관리, 연결 현장/작업계획서 확인 |
| `MANPOWER_SUPPLIER` | 자기 인원 등록/관리, 인원 서류 관리, 연결 현장/작업계획서 확인 |
| `WORKER` | 추후 앱에서 본인 일정/출결/안전관리용. 현재 우선순위 낮음 |
| `SITE_MANAGER` | 추후 현장소장 계정으로 확장 가능. 현재 미정 |

## 계정 구조

회사 역할별로 관리자 계정과 일반 직원 계정을 둔다.

| 구분 | 설명 |
|---|---|
| 회사 관리자 | 해당 회사의 자원/서류/현장 참여 정보 전체 관리 |
| 일반 직원 | 추후 GPS, 출결, 통계, 담당 범위 제한을 위해 필요 |

현재 `users.is_company_admin` 을 활용한다. 추후 담당 현장/담당 자원 제한이 필요하면 별도 매핑 테이블을 추가한다.

```text
company_user_scopes
- user_id
- scope_type: SITE | EQUIPMENT | PERSON | ALL
- scope_id
```

한 사용자가 여러 회사에 속하는 경우는 거의 없다고 보고 우선 제외한다. 필요 시 `user_company_memberships` 로 확장한다.

## 확정 권한 정책

### ADMIN

ADMIN은 BP와 공급사가 할 수 있는 모든 업무를 대신 처리할 수 있다.

- 모든 회사/현장/장비/인원/서류/작업계획서 조회 가능
- 모든 회사/현장/장비/인원/서류/작업계획서 생성/수정 가능
- 공급사 대신 장비/인원 등록 가능
- 공급사 대신 서류 업로드/갱신 가능
- 만료/미검증/누락 서류에 대해 강제 처리 가능
- 전체 알림/로그 조회 가능
- 승인 대기 계정 처리 가능

ADMIN 대시보드 핵심은 전체 위험 관제와 업무 대행이다.

### BP

BP는 자기 회사가 소유한 현장을 운영한다.

- 자기 BP 회사의 현장만 조회/관리
- 자기 현장에 장비공급사/인력공급사 연결/해제
- 연결된 공급사의 장비/인원 조회
- 연결된 공급사의 장비/인원 배치/해제
- 작업계획서 생성/수정
- 공급사 장비/인원 자체 정보 수정 불가
- 공급사 서류 업로드/갱신 불가
- 공급사 서류 확인 가능
- 서류 미비 자원 강제 진행 불가

BP에게는 "장비 관리/인원 관리"보다 "배치 장비/배치 인원" 메뉴가 맞다.

### 장비공급사

장비공급사는 자기 회사 장비와 장비 서류를 관리한다.

- 자기 회사 장비 등록/수정/삭제
- 자기 장비 서류 업로드/갱신
- 자기 장비 상태 변경 가능 (`AVAILABLE/ASSIGNED/BROKEN` 계열)
- 직접 현장 배치 불가
- 연결된 BP 현장 정보 조회 가능
- 연결된 BP가 만든 관련 작업계획서 조회 가능
- 현장명, 주소, 기간, BP 담당자 연락처, 투입된 인력/장비 조회 가능

장비공급사 메뉴에는 "현장 관리"를 보여주되, 의미는 현장 생성/수정이 아니라 "참여 현장 관리/조회"다.

### 인력공급사

인력공급사는 자기 회사 인원과 인원 서류를 관리한다.

- 자기 회사 인원 등록/수정/삭제
- 자기 인원 서류 업로드/갱신
- 직접 현장 배치 불가
- 연결된 BP 현장 정보 조회 가능
- 연결된 BP가 만든 관련 작업계획서 조회 가능
- 현장명, 주소, 기간, BP 담당자 연락처, 투입된 인력/장비 조회 가능
- 출근/미출근은 아직 고려하지 않는다.

인력공급사 메뉴에도 "현장 관리"를 보여주되, 참여 현장 중심으로 제한한다.

## 작업계획서 권한

| 역할 | 작업계획서 생성 | 조회 | 수정 | 승인 |
|---|---|---|---|---|
| `ADMIN` | 가능 | 전체 가능 | 가능 | 강제 처리 가능 |
| `BP` | 가능 | 자기 현장 가능 | 가능 | 기본 주체 |
| `EQUIPMENT_SUPPLIER` | 불가 | 연결된 BP가 만든 관련 계획 조회 | 불가 | 불필요 |
| `MANPOWER_SUPPLIER` | 불가 | 연결된 BP가 만든 관련 계획 조회 | 불가 | 불필요 |

기본 원칙은 작업계획서 자원이 해당 현장 참여 공급사 소속이어야 한다는 것이다. 단, 특수한 예외 가능성은 남겨두되 초기 구현에서는 예외 등록을 만들지 않는다.

## 기본 라우팅

로그인 후 역할별 기본 진입 경로를 분리한다.

| 역할 | 기본 라우트 |
|---|---|
| `ADMIN` | `/admin/dashboard` |
| `BP` | `/bp/dashboard` |
| `EQUIPMENT_SUPPLIER` | `/equipment-supplier/dashboard` |
| `MANPOWER_SUPPLIER` | `/manpower-supplier/dashboard` |
| `WORKER` | `/worker/dashboard` 추후 |

기존 `/` 또는 `/dashboard` 는 역할별 대시보드로 redirect 하는 위임 라우트로 둔다.

## 역할별 메뉴 구조

### ADMIN

- 대시보드
- 회사 관리
- 사용자 승인/관리
- 현장 관리
- 장비 관리
- 인원 관리
- 서류 관리
- 작업계획서
- 알림
- 로그
- 설정

### BP

- 대시보드
- 현장 관리
- 배치 장비
- 배치 인원
- 작업계획서
- DOCX 템플릿
- 알림
- 로그

> 2026-05-08 갱신 — `참여 공급사`/`서류 위험` 같은 같은-URL 중복 항목 제거. 참여 공급사는 사이트 상세에서 노출, 서류 위험은 미구현이라 disabled 였던 것 정리.

### 장비공급사

- 대시보드
- 내 장비
- 내 조종원
- 현장 관리
- 작업 일정
- 알림
- 로그

> 2026-05-08 갱신 — `장비 서류`/`장비 배치 현황`/`조종원 서류` 중복 항목 제거. EquipmentPage / PersonPage 안의 필터/탭으로 부분집합 보기 전환할 예정. `내 조종원` 항목 추가 (장비공급사도 OPERATOR 역할 인력 운영).

### 인력공급사

- 대시보드
- 내 인원
- 현장 관리
- 작업 일정
- 알림
- 로그

> 2026-05-08 갱신 — `인원 서류`/`인원 배치 현황` 중복 항목 제거. 동일.

## 역할별 대시보드 구성

### ADMIN 대시보드

ADMIN은 전체 시스템 위험과 대행 업무가 중심이다.

- 전체 회사 수
- 전체 현장 수
- 전체 장비/인원 수
- 서류 만료/임박/미검증/누락 요약
- 승인 대기 계정
- 회사별 위험 요약
- 현장별 위험 요약
- 최근 알림
- 최근 audit log
- 공지사항 등록/배포

### BP 대시보드

BP는 자기 현장 운영이 중심이다.

- 내 현장 현황
- 현장별 참여 공급사 수
- 현장별 배치 장비/인원
- 오늘/이번 주 작업계획서
- 관련 공급사 서류 만료/누락/미검증
- 배치 변경 알림
- 서류 보완 요청
- 최근 audit log

### 장비공급사 대시보드

장비공급사는 자기 장비 운영이 중심이다.

- 내 장비 수
- 현재 배치 중 장비
- 미배치 장비
- 고장 장비
- 장비 서류 만료/누락/미검증
- 참여 현장 목록
- 내 장비가 포함된 작업계획서 일정
- 최근 audit log

### 인력공급사 대시보드

인력공급사는 자기 인원 운영이 중심이다.

- 내 인원 수
- 현재 배치 중 인원
- 미배치 인원
- 인원 서류 만료/누락/미검증
- 참여 현장 목록
- 내 인원이 포함된 작업계획서 일정
- 최근 audit log

출근/미출근은 아직 제외한다.

## 공통 위젯 전략

대시보드 페이지는 역할별로 분리하되, 위젯은 공통으로 재사용한다.

| 위젯 | 사용처 |
|---|---|
| `SiteSummaryWidget` | ADMIN, BP, 공급사 |
| `AssignmentStatusWidget` | BP, 장비공급사, 인력공급사 |
| `DocumentRiskWidget` | 전체 |
| `RecentAlertsWidget` | 전체 |
| `AuditLogWidget` | 전체 |
| `WorkPlanScheduleWidget` | BP, 공급사 |
| `ParticipantCompanyWidget` | ADMIN, BP |
| `ResourceHistoryWidget` | BP, 공급사 |

## 역할별 API 방향

대시보드 API는 role 기반 단일 응답보다 역할별 endpoint 분리를 권장한다.

```text
GET /api/dashboards/admin/summary
GET /api/dashboards/bp/summary
GET /api/dashboards/equipment-supplier/summary
GET /api/dashboards/manpower-supplier/summary
```

이유:

- 역할별 관심 데이터가 다르다.
- 응답 스키마가 달라질 가능성이 높다.
- 권한 실수를 줄일 수 있다.
- UI 컴포지션을 명확히 유지할 수 있다.

## Audit Log 설계

알림과 로그는 분리한다.

```text
audit_logs
= 누가 어떤 데이터를 생성/수정/삭제/상태변경/배치/서류갱신 했는지 추적

notifications
= 사용자에게 보여주는 알림, 읽음/안읽음, 리마인드

domain histories
= 장비 배치 이력, 인원 배치 이력, 서류 갱신 이력처럼 상세 화면에 노출되는 업무 이력
```

### audit_logs 후보 스키마

```sql
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    actor_role VARCHAR(32),
    actor_company_id BIGINT REFERENCES companies(id) ON DELETE SET NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id BIGINT,
    target_company_id BIGINT REFERENCES companies(id) ON DELETE SET NULL,
    site_id BIGINT REFERENCES sites(id) ON DELETE SET NULL,
    before_json JSONB,
    after_json JSONB,
    ip_address VARCHAR(64),
    user_agent VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### action 예시

```text
SITE_CREATED
SITE_UPDATED
PARTICIPANT_ADDED
PARTICIPANT_REMOVED
EQUIPMENT_CREATED
EQUIPMENT_UPDATED
EQUIPMENT_ASSIGNED
EQUIPMENT_UNASSIGNED
EQUIPMENT_STATUS_CHANGED
PERSON_CREATED
PERSON_UPDATED
PERSON_ASSIGNED
PERSON_UNASSIGNED
DOCUMENT_UPLOADED
DOCUMENT_RENEWED
DOCUMENT_VERIFIED
WORK_PLAN_CREATED
WORK_PLAN_UPDATED
```

### 로그 조회 권한

| 역할 | 로그 조회 범위 |
|---|---|
| `ADMIN` | 전체 |
| `BP` 회사 관리자 | 자기 회사 현장 관련 로그 |
| `EQUIPMENT_SUPPLIER` 회사 관리자 | 자기 회사 장비/서류/참여 현장 관련 로그 |
| `MANPOWER_SUPPLIER` 회사 관리자 | 자기 회사 인원/서류/참여 현장 관련 로그 |
| 일반 직원 | 본인 행동 로그 또는 추후 제한 |

## 알림 대상 정책

서류 만료/누락/미검증 알림은 아래 대상으로 간다.

- ADMIN
- 해당 BP 회사 관리자
- 해당 공급사 관리자
- 해당 작업자 또는 인원 계정, 추후
- 해당 작업자의 관리자, 추후

작업계획서 생성/변경 자체는 공급사 전체 알림으로 보내지 않는다. 다만 서류 보완 요청, 배치 변경, 서류 위험은 알림으로 보낸다.

ADMIN은 모든 알림을 받을 수 있다.

## Phase 순서 재정렬

Phase S-2 완료 이후에는 바로 서류 정책으로 들어가기 전에 역할별 정보 구조를 고정한다.

```text
Phase S-1: 현장/참여업체                          ✓ 완료
Phase S-2: 장비/인원 현장 배치 + 이력             ✓ 완료
Phase S-3: 역할별 UI/대시보드/메뉴 + Audit Log    ✓ 완료
Phase S-4: 서류 적합성/필수서류/검증 상태 API
Phase S-5: 작업계획서 후보 추천
Phase S-6: 작업계획서 생성
Phase S-7: 기존 skep 작업계획서 자동화 이식
```

## Phase S-3 구현 결과 (2026-05-06)

### 구현된 라우트

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

### 구현된 API

- `GET /api/dashboards/admin/summary`
- `GET /api/dashboards/bp/summary`
- `GET /api/dashboards/equipment-supplier/summary`
- `GET /api/dashboards/manpower-supplier/summary`
- `GET /api/audit-logs?page=&size=`
- `GET /api/audit-logs/recent?limit=`

### 구현된 audit action

`SITE_CREATED`, `SITE_UPDATED`, `PARTICIPANT_ADDED`, `PARTICIPANT_REMOVED`,
`EQUIPMENT_ASSIGNED`, `EQUIPMENT_UNASSIGNED`, `PERSON_ASSIGNED`, `PERSON_UNASSIGNED`,
`DOCUMENT_UPLOADED`, `DOCUMENT_VERIFIED`.

다음 단계로 미룬 action: `EQUIPMENT_STATUS_CHANGED`, `DOCUMENT_RENEWED`, `WORK_PLAN_*`.

### 한계와 다음 단계

- 알림(notifications) 도메인 미구현 → 모든 dashboard 의 `recent_notifications` 가 빈 배열.
- 작업계획서(work_plans) 도메인 미구현 → BP/공급사 dashboard 의 `today_work_plans`/`upcoming_work_plans` 가 빈 배열.
- audit log `ip_address`/`user_agent` 컬럼은 있으나 service 에서 채우지 않음.
- audit log `before_json`/`after_json` 은 V13 에서 jsonb → text 로 단순화. 검색 필요해지면 다시 jsonb 로 복귀.

### Phase S-3.1 권한 스코프 패치 (2026-05-06)

S-3 검토에서 발견된 권한 누수 6건을 정리했다. 자세한 변경은 `docs/IMPLEMENTATION_LOG.md` 의 S-3.1 항목.

핵심 결론:

- **서류 read** — BP 는 자기 사이트 ACTIVE 참여 공급사 자원만, WORKER 는 차단. 모든 BP/WORKER 서류 read-through 는 닫힘.
- **공급사 dashboard 만료 카운트** — 회사 자원 owner_id 로 좁힘. 다른 회사 서류 위험 노출 없음.
- **자원 자동 해제 audit** — 다른 현장 이동 시 자동 해제도 `EQUIPMENT_UNASSIGNED` / `PERSON_UNASSIGNED` 로 기록 (`auto_release_on_move`).
- **DOCUMENT_RENEWED** — 만료일 갱신에도 audit 기록.
- **회사 관리자 분리** — JWT 에 `is_company_admin` claim 발급. 일반 직원은 audit log 에서 본인 행동만 조회.
- **WORKER dashboard 무한 redirect** 차단 — placeholder 페이지로.

### Phase S-4 단계 1 후속 정정 (2026-05-07)

- **공급사 dashboard 만료 카운트** — V14 정책 도입 후에도 owner_type+owner_ids 기준 (S-3.1 정정 그대로 유효). 이번 단계에서 `documents` 에 `verification_status` 가 추가되었지만 카운트 식은 여전히 만료일 기준 (`expiry_date <= today + 30d`).
- **후보 추천의 missing_documents** — V14 의 `document_types.required + active` 기준으로 자원별 누락 수 실제 계산 (`AssignmentService.missingRequiredCounts`). REJECTED 는 `verified=false` 라 자동 누락 처리. blocked 정책 = `BROKEN/INACTIVE || missing_documents > 0`.
- **다음 단계로 미룬 표시 데이터** — 공급사 dashboard 의 "서류 위험" 카드는 만료 임박만 보여주고, REJECTED / OCR_REVIEW_REQUIRED 분리는 단계 2 (외부 verify-api 연동) 후 추가.
