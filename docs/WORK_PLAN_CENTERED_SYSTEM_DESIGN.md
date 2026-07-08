# SKEP v2 작업계획서 중심 시스템 설계

## 목적

SKEP v2는 건설 현장에서 BP사가 현장을 구성하고, 장비공급사와 인력공급사를 선정한 뒤, 공급사들이 등록한 장비와 인원을 기반으로 작업계획서를 생성하고 관리하는 웹앱이다.

이 시스템의 중심은 단순 장비/인원 CRUD가 아니라 아래 흐름이다.

```text
BP사 현장 생성
→ 현장 참여 공급사 선정
→ 공급사별 장비/인원 등록
→ 장비/인원 서류 등록 및 검증
→ 작업계획서 생성
→ 장비/인원 투입
→ 서류 만료/검증/투입 이력 기반 대시보드 관리
```

## 핵심 설계 원칙

1. 현장은 독립 도메인으로 관리한다.
2. BP사는 자기 현장에 참여할 장비공급사와 인력공급사를 직접 선정한다.
3. 공급사는 자기 장비와 인원을 스스로 등록할 수 있다.
4. 작업계획서는 해당 현장에 참여 중인 공급사의 장비/인원만 사용할 수 있다.
5. 장비/인원 선택 시 이전 현장 투입 이력과 서류 구비 상태를 함께 보여준다.
6. 서류 관리는 이 서비스의 핵심 기능이다.
7. 서류 만료/미검증/누락은 작업계획서 생성과 대시보드에 직접 영향을 준다.
8. 가동률 퍼센트보다 "언제 어느 현장에 몇 시간 투입되었는가"가 더 중요하다.
9. QR/GPS 출결과 위치 추적은 추후 앱 개발 단계에서 확장한다.
10. 지금은 ADMIN, BP, 장비공급사, 인력공급사를 중심으로 설계하고, 작업자/현장소장은 확장 가능하게 둔다.

## 주요 사용자 역할

| 역할 | 설명 | 현재 우선순위 |
|---|---|---|
| ADMIN | 전체 회사, 현장, 장비, 인원, 서류, 작업계획서 관리 | 높음 |
| BP | 현장을 만들고 공급사를 선정하며 작업계획서를 생성 | 높음 |
| EQUIPMENT_SUPPLIER | 자기 장비를 등록하고 장비 서류를 관리 | 높음 |
| MANPOWER_SUPPLIER | 자기 인원을 등록하고 인원 서류를 관리 | 높음 |
| WORKER | 추후 앱에서 출결/안전관리/본인 일정 확인 | 낮음 |
| SITE_MANAGER | 추후 여러 현장 담당자로 추가 가능 | 보류 |

## 도메인 관계

```text
Company(BP)
  └─ Site
      ├─ SiteParticipant(EQUIPMENT_SUPPLIER)
      │   └─ Equipment
      │       └─ Equipment Documents
      ├─ SiteParticipant(MANPOWER_SUPPLIER)
      │   └─ Person
      │       └─ Person Documents
      └─ WorkPlan
          ├─ WorkPlanEquipment
          ├─ WorkPlanPerson
          └─ WorkPlanComplianceSnapshot
```

중요한 점은 "현장에 이미 공급사가 있어서 BP가 고르는 것"이 아니라, BP사가 현장 운영을 위해 장비공급사와 인력공급사를 선정해서 현장 참여 업체로 구성한다는 점이다.

## 현장 설계

### sites

현장은 독립 테이블로 관리한다. 현장을 단순 문자열로 저장하면 현장별 권한, 필터, 대시보드, 작업계획서, 투입 이력 관리가 어렵다.

```sql
CREATE TABLE sites (
    id BIGSERIAL PRIMARY KEY,
    bp_company_id BIGINT NOT NULL REFERENCES companies(id),
    name VARCHAR(150) NOT NULL,
    code VARCHAR(64),
    address VARCHAR(255),
    detail_address VARCHAR(255),
    start_date DATE,
    end_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

상태 enum:

```text
ACTIVE
PAUSED
COMPLETED
ARCHIVED
```

### site_participants

BP사가 특정 현장에 참여시킨 공급사 목록이다.

```sql
CREATE TABLE site_participants (
    id BIGSERIAL PRIMARY KEY,
    site_id BIGINT NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    participant_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    added_by BIGINT REFERENCES users(id),
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (site_id, company_id)
);
```

participant_type:

```text
BP
EQUIPMENT_SUPPLIER
MANPOWER_SUPPLIER
```

status:

```text
ACTIVE
INACTIVE
SUSPENDED
```

## 장비 설계

장비는 장비공급사 소유다. BP가 소유하는 것이 아니라, BP가 만든 현장에 장비공급사를 참여시키고 그 공급사 장비를 작업계획서에 사용한다.

### equipment 추가 권장 필드

```sql
ALTER TABLE equipment
    ADD COLUMN name VARCHAR(100),
    ADD COLUMN code VARCHAR(64),
    ADD COLUMN current_site_id BIGINT REFERENCES sites(id),
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    ADD COLUMN manager_name VARCHAR(100),
    ADD COLUMN last_assigned_at TIMESTAMP;
```

장비코드는 시스템 내부 관리번호다. 차량번호와 다르다.

```text
차량번호: 실제 번호판
장비코드: EQ-2026-0001 같은 내부 식별번호
```

장비 상태:

```text
AVAILABLE   미사용/대기
ASSIGNED    현장 투입 중
BROKEN      고장
BLOCKED     서류 문제로 사용 제한
```

권장 정책:

- `AVAILABLE`, `ASSIGNED`, `BROKEN`은 저장 상태로 관리한다.
- `BLOCKED`는 서류 상태에서 계산할 수 있다.
- 실제 저장 상태와 계산 위험 상태를 대시보드에서 함께 보여준다.

## 인원 설계

인원은 인력공급사 또는 장비공급사 소속이다. 조종원은 장비공급사에 속할 수 있고, 신호수/유도원/안전관리 인력은 인력공급사에 속할 수 있다.

### persons 추가 권장 필드

```sql
ALTER TABLE persons
    ADD COLUMN current_site_id BIGINT REFERENCES sites(id),
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'OFF_DUTY',
    ADD COLUMN employment_type VARCHAR(32),
    ADD COLUMN last_assigned_at TIMESTAMP;
```

인원 상태:

```text
ON_DUTY       출근/투입 중
OFF_DUTY      미출근/미투입
BLOCKED       서류 문제로 사용 제한
INACTIVE      비활성
```

QR/GPS 출결은 추후 앱에서 확장한다. 현재는 작업계획서 배정과 현장 투입 이력을 중심으로 본다.

## 현장 배치 및 투입 이력

장비/인원은 한 번에 하나의 현장에만 배치된다. 다만 이동은 언제든 가능해야 한다.

### equipment_site_assignments

```sql
CREATE TABLE equipment_site_assignments (
    id BIGSERIAL PRIMARY KEY,
    equipment_id BIGINT NOT NULL REFERENCES equipment(id) ON DELETE CASCADE,
    site_id BIGINT NOT NULL REFERENCES sites(id),
    assigned_at TIMESTAMP NOT NULL,
    released_at TIMESTAMP,
    assigned_by BIGINT REFERENCES users(id),
    release_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### person_site_assignments

```sql
CREATE TABLE person_site_assignments (
    id BIGSERIAL PRIMARY KEY,
    person_id BIGINT NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    site_id BIGINT NOT NULL REFERENCES sites(id),
    assigned_at TIMESTAMP NOT NULL,
    released_at TIMESTAMP,
    assigned_by BIGINT REFERENCES users(id),
    release_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

운영 정책:

- `current_site_id`는 현재 위치를 빠르게 보여주기 위한 캐시성 컬럼이다.
- `*_site_assignments`는 이동 이력을 보존한다.
- 작업계획서에 투입되었다고 해서 항상 장기 배치로 볼지는 별도 정책이다.
- 초기 구현에서는 작업계획서 투입 이력을 우선하고, 현재 현장 표시가 필요할 때 `current_site_id`를 함께 갱신한다.

## 서류 설계

서류는 이 서비스의 핵심이다. 장비/인원이 작업계획서에 들어갈 수 있는지 판단하는 기준이다.

### document_types 확장 권장 필드

```sql
ALTER TABLE document_types
    ADD COLUMN required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN blocks_assignment BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN default_valid_months INTEGER,
    ADD COLUMN ocr_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ocr_expiry_field_key VARCHAR(100);
```

필드 의미:

| 필드 | 설명 |
|---|---|
| `required` | 작업계획서 투입 시 필수 서류인지 |
| `blocks_assignment` | 만료/누락 시 배정을 막을지 |
| `default_valid_months` | 재등록 시 기본 만료일 추천 기준 |
| `ocr_enabled` | OCR 대상 서류인지 |
| `ocr_expiry_field_key` | OCR 결과에서 만료일로 사용할 필드 |

### documents 확장 권장 필드

```sql
ALTER TABLE documents
    ADD COLUMN verification_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN verified_by BIGINT REFERENCES users(id),
    ADD COLUMN verified_at TIMESTAMP,
    ADD COLUMN rejected_reason VARCHAR(255),
    ADD COLUMN previous_document_id BIGINT REFERENCES documents(id);
```

verification_status:

```text
PENDING
VERIFIED
REJECTED
OCR_REVIEW_REQUIRED
```

### 서류 상태 계산

서류 상태는 저장값과 계산값을 구분한다.

검증 상태:

```text
PENDING
VERIFIED
REJECTED
OCR_REVIEW_REQUIRED
```

만료 상태:

```text
OK
D30
D7
EXPIRED
MISSING
```

작업계획서 배정 상태:

```text
ELIGIBLE       배정 가능
WARNING        경고 후 배정 가능
BLOCKED        기본 차단
OVERRIDDEN     강제 승인으로 배정
```

## 작업계획서 설계

작업계획서는 이 서비스의 중심 도메인이다. BP사가 현장에 참여시킨 공급사의 장비/인원을 기반으로 생성한다.

### work_plans

```sql
CREATE TABLE work_plans (
    id BIGSERIAL PRIMARY KEY,
    site_id BIGINT NOT NULL REFERENCES sites(id),
    bp_company_id BIGINT NOT NULL REFERENCES companies(id),
    work_date DATE NOT NULL,
    start_time TIME,
    end_time TIME,
    title VARCHAR(150) NOT NULL,
    work_location VARCHAR(255),
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT REFERENCES users(id),
    submitted_at TIMESTAMP,
    approved_by BIGINT REFERENCES users(id),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

status:

```text
DRAFT
SUBMITTED
APPROVED
IN_PROGRESS
DONE
CANCELLED
```

초기 구현에서는 승인 단계를 단순화할 수 있다. 다만 상태 enum은 확장 가능하게 둔다.

### work_plan_equipment

```sql
CREATE TABLE work_plan_equipment (
    id BIGSERIAL PRIMARY KEY,
    work_plan_id BIGINT NOT NULL REFERENCES work_plans(id) ON DELETE CASCADE,
    equipment_id BIGINT NOT NULL REFERENCES equipment(id),
    supplier_company_id BIGINT NOT NULL REFERENCES companies(id),
    purpose VARCHAR(100),
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (work_plan_id, equipment_id)
);
```

### work_plan_persons

```sql
CREATE TABLE work_plan_persons (
    id BIGSERIAL PRIMARY KEY,
    work_plan_id BIGINT NOT NULL REFERENCES work_plans(id) ON DELETE CASCADE,
    person_id BIGINT NOT NULL REFERENCES persons(id),
    supplier_company_id BIGINT NOT NULL REFERENCES companies(id),
    equipment_id BIGINT REFERENCES equipment(id),
    role VARCHAR(32),
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (work_plan_id, person_id)
);
```

`equipment_id`는 nullable이다.

예:

```text
굴삭기 ZX350
- 조종원 김민수
- 신호수 박준호
- 유도원 정미라

현장 전체
- 안전관리자 최영석
- 화기감시자 이정훈
```

## 작업계획서 후보 추천

BP가 작업계획서에서 장비/인원을 선택할 때, 단순 목록이 아니라 추천순으로 보여줘야 한다.

후보 판단 기준:

1. 해당 현장에 참여 중인 공급사의 장비/인원인가
2. 해당 현장에 과거 투입된 적이 있는가
3. 필수 서류가 모두 구비되어 있는가
4. 서류가 검증 완료되었는가
5. 만료 또는 D-7/D-30 상태인가
6. 현재 고장/사용 제한 상태인가

### 후보 점수 예시

| 조건 | 점수 |
|---|---:|
| 해당 현장 이전 투입 이력 있음 | +40 |
| 필수 서류 완비 | +30 |
| 검증 완료 | +20 |
| 만료 임박 없음 | +10 |
| 필수 서류 누락 | -50 |
| 서류 만료 | -100 |
| 고장/사용 불가 | -100 |

점수는 고정 비즈니스 로직으로 박지 말고 추후 조정 가능하게 서비스 레이어에서 관리한다.

### 후보 응답 DTO 예시

```json
{
  "equipment_id": 1,
  "supplier_id": 3,
  "supplier_name": "대한중기",
  "name": "굴삭기 ZX350",
  "category": "EXCAVATOR",
  "vehicle_no": "서울12가3456",
  "previously_used_on_site": true,
  "last_used_at": "2026-05-02",
  "document_status": "COMPLETE",
  "verification_status": "VERIFIED",
  "risk_level": "OK",
  "priority_score": 95,
  "badges": ["이전 투입", "서류완비", "검증완료"]
}
```

## 작업계획서 서류 검증 스냅샷

서류 상태는 시간이 지나면 변한다. 따라서 작업계획서 작성 당시의 서류 상태를 남기는 것이 중요하다.

### work_plan_compliance_checks

```sql
CREATE TABLE work_plan_compliance_checks (
    id BIGSERIAL PRIMARY KEY,
    work_plan_id BIGINT NOT NULL REFERENCES work_plans(id) ON DELETE CASCADE,
    target_type VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(255),
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    override_by BIGINT REFERENCES users(id),
    override_reason VARCHAR(255)
);
```

target_type:

```text
EQUIPMENT
PERSON
```

status:

```text
OK
WARNING
BLOCKED
OVERRIDDEN
```

정책:

- 필수 서류 누락 또는 만료 시 기본 차단한다.
- D-7 또는 D-30은 경고로 표시한다.
- ADMIN은 강제 진행 가능하다.
- BP도 강제 진행 가능하게 할 수 있으나 사유를 반드시 남긴다.
- 강제 진행 기록은 감사 로그와 대시보드에 표시 가능해야 한다.

## 알림 설계

알림은 앱 내부 알림을 먼저 구현한다. 문자/카카오/이메일은 추후 확장한다.

### notifications

```sql
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    target_user_id BIGINT REFERENCES users(id),
    target_company_id BIGINT REFERENCES companies(id),
    site_id BIGINT REFERENCES sites(id),
    type VARCHAR(64) NOT NULL,
    title VARCHAR(150) NOT NULL,
    message TEXT NOT NULL,
    link_type VARCHAR(32),
    link_id BIGINT,
    read_at TIMESTAMP,
    remind_policy VARCHAR(32) NOT NULL DEFAULT 'ONCE',
    reminder_key VARCHAR(150),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

type 예시:

```text
DOCUMENT_D30
DOCUMENT_D7
DOCUMENT_EXPIRED
WORK_PLAN_CHANGED
EQUIPMENT_STATUS_CHANGED
SITE_PARTICIPANT_ADDED
```

remind_policy:

```text
ONCE
DAILY_UNTIL_RESOLVED
STEP_D30_D7_D0
```

알림 대상:

- ADMIN: 전체 회사/현장 주요 이벤트
- BP: 자기 현장과 연결된 공급사/작업계획서/서류 이벤트
- 장비공급사: 자기 장비, 자기 장비 서류, 자기 장비가 포함된 작업계획서
- 인력공급사: 자기 인원, 자기 인원 서류, 자기 인원이 포함된 작업계획서
- 작업자: 추후 본인 일정/출결/안전관리 이벤트

## 대시보드 설계

대시보드는 역할별로 다르게 보여야 한다.

### ADMIN 대시보드

- 전체 현장 수
- 전체 BP/장비공급사/인력공급사 수
- 전체 장비/인원 수
- 서류 만료/임박/미검증 현황
- 사용 제한 대상
- 오늘 작업계획서 수
- 최근 알림

### BP 대시보드

- 내 현장 수
- 현장별 참여 공급사 수
- 오늘/이번 주 작업계획서
- 현장별 투입 장비/인원
- 서류 문제 있는 장비/인원
- 작업계획서 작성/검토 필요 항목
- 최근 알림

### 장비공급사 대시보드

- 내 장비 수
- 현장 투입 중 장비
- 오늘 투입 장비
- 서류 만료 임박 장비
- 고장/미사용 장비
- 내 장비가 포함된 작업계획서
- 최근 알림

### 인력공급사 대시보드

- 내 인원 수
- 현장 투입 중 인원
- 오늘 투입 인원
- 자격/교육/면허 서류 만료 임박 인원
- 내 인원이 포함된 작업계획서
- 최근 알림

## 대시보드 API 제안

```text
GET /api/dashboard/summary
GET /api/dashboard/document-risks
GET /api/dashboard/today-work-plans
GET /api/dashboard/site-overview
GET /api/dashboard/recent-alerts
GET /api/dashboard/resource-risks
```

작업계획서 후보 조회:

```text
GET /api/sites/{siteId}/supplier-candidates
GET /api/sites/{siteId}/equipment-candidates
GET /api/sites/{siteId}/person-candidates
```

작업계획서 API:

```text
GET /api/work-plans
GET /api/work-plans/{id}
POST /api/work-plans
PATCH /api/work-plans/{id}
POST /api/work-plans/{id}/submit
POST /api/work-plans/{id}/approve
POST /api/work-plans/{id}/cancel
POST /api/work-plans/{id}/compliance-check
```

현장 API:

```text
GET /api/sites
GET /api/sites/{id}
POST /api/sites
PATCH /api/sites/{id}
GET /api/sites/{id}/participants
POST /api/sites/{id}/participants
DELETE /api/sites/{id}/participants/{participantId}
```

## 권한 정책

### ADMIN

- 전체 조회/수정 가능
- 서류 강제 승인 가능
- 작업계획서 강제 진행 가능
- 공급사/현장/회사 전체 관리 가능

### BP

- 자기 회사 현장 생성/수정
- 자기 현장에 공급사 연결
- 연결된 공급사 장비/인원 조회
- 자기 현장 작업계획서 생성/수정
- 작업계획서 강제 진행 가능 여부는 정책값으로 둔다.

### EQUIPMENT_SUPPLIER

- 자기 장비 등록/수정
- 자기 장비 서류 등록/수정
- 자기가 참여한 현장 조회
- 자기 장비가 포함된 작업계획서 조회
- 다른 공급사 장비/인원은 조회 불가 또는 제한 조회

### MANPOWER_SUPPLIER

- 자기 인원 등록/수정
- 자기 인원 서류 등록/수정
- 자기가 참여한 현장 조회
- 자기 인원이 포함된 작업계획서 조회
- 다른 공급사 인원/장비는 조회 불가 또는 제한 조회

## UI 흐름

### BP 현장 구성 흐름

```text
현장 생성
→ 현장 상세
→ 장비공급사 추가
→ 인력공급사 추가
→ 참여 업체별 등록 자원 확인
→ 작업계획서 생성
```

### 공급사 등록 흐름

```text
공급사 로그인
→ 자기 장비/인원 등록
→ 필수 서류 등록
→ BP가 연결한 현장 확인
→ 작업계획서 투입 여부 확인
```

### 작업계획서 생성 흐름

```text
현장 선택
→ 작업일/시간/내용 입력
→ 참여 공급사 기준 장비 후보 조회
→ 이전 투입/서류완비 기준 추천순 표시
→ 장비 선택
→ 장비별 인원 또는 현장 전체 인원 선택
→ 서류 적합성 검사
→ 문제 항목 표시
→ 저장/제출
→ 필요 시 출력/PDF 생성
```

### 자원 후보 UI

필터:

- 공급사
- 장비 종류
- 인원 역할
- 이전 투입 여부
- 서류 상태
- 만료 임박
- 사용 가능 여부

배지:

- 이전 투입
- 신규
- 서류완비
- 서류누락
- 만료임박
- 만료
- 검증완료
- 사용제한

정렬:

- 추천순
- 최근 투입순
- 이름순
- 서류 위험순

## 기존 SKEP v2와의 차이

현재 구현은 장비/인원/서류 기본 관리와 일부 상세 화면, 간단한 대시보드 중심이다.

보강이 필요한 핵심:

| 영역 | 현재 | 필요 |
|---|---|---|
| 현장 | 독립 모델 없음 | `sites` 필요 |
| 현장 참여 업체 | 없음 | `site_participants` 필요 |
| 작업계획서 | 없음 | 핵심 도메인으로 추가 |
| 후보 추천 | 없음 | 이전 투입/서류 상태 기준 필요 |
| 서류 검증 스냅샷 | 없음 | 작업계획서 당시 상태 보존 필요 |
| 알림 | 없음 | 역할별 내부 알림 필요 |
| 대시보드 | 서류 만료 중심 일부 | 역할별 현장/작업계획/서류 위험 중심 필요 |

## 구현 현황

| 단계 | 상태 | 반영 파일 |
|---|---|---|
| 1단계: 현장과 참여 업체 | 완료 | `V10__add_sites_and_participants.sql`, `com.skep.site/*`, `frontend/src/features/site/*` |
| 2단계: 자원 현장 표시 | 완료 | `V11__add_resource_assignments.sql`, `com.skep.assignment/*`, `Equipment/Person 엔티티/DTO 확장`, `frontend/src/features/assignment/*`, `Site/Equipment/Person DetailPage 확장` |
| 3단계: 역할별 UI/대시보드/메뉴 + Audit Log 기반 | 완료 | `V12__add_audit_logs.sql`, `V13__audit_logs_text_json.sql`, `com.skep.audit/*`, `com.skep.dashboard.RoleDashboardController`, `frontend/src/features/dashboard/*`, `Sidebar.tsx` 역할별 분기 |
| 4단계: 서류 정책 강화 | **완료** (1+2+2.1+3+4+4.1) | 단계 1: V14 마이그레이션. 단계 2: VerifyClient + verify/reject endpoint. 단계 2.1: API 단 배차 차단 + required/blocks_assignment 분리 + previous_document_id 자동 매핑 + dashboard document_risks. 단계 3: 자동 OCR 트리거 + ADMIN override + history endpoint + verify dialog. 단계 4: V15 notifications, ADMIN 검토 큐, 알림 페이지 + Sidebar 미읽음 뱃지, history dialog, 자동 알림 발신. **단계 4.1: chain head 기준 배차 차단 정책 누수 2건 차단** (`findValidVerifiedTypesByOwners` 쿼리 강화 + `markOcrReviewRequired` verified boolean 동기화). |
| 5단계: 작업계획서 후보 추천/생성 | 다음 단계 | `work_plans` + `work_plan_equipment` + `work_plan_persons` + `work_plan_compliance_checks` + 후보 추천 endpoint + 생성 흐름. 위 단계 1~4 가 받쳐줌. 기존 `skep` 작업계획서 자동화 분석 후 이식. |
| 6단계: 역할별 대시보드 API 고도화 | 미완료 | 작업계획서/서류 위험/알림 기준으로 실제 집계 API 구현 |
| 7단계: 작업계획서 출력/이식 | 미완료 | `docs/LEGACY_SKEP_WORKSHEET_REFERENCE.md` 참고 |

상세 구현 로그는 `docs/IMPLEMENTATION_LOG.md`에 정리한다. 역할별 화면/권한/대시보드 설계는 `docs/ROLE_BASED_DASHBOARD_DESIGN.md`를 기준으로 한다.

## 개발 순서 제안

### 1단계: 현장과 참여 업체

1. `sites` 추가
2. `site_participants` 추가
3. BP가 현장 생성/수정
4. BP가 현장에 장비공급사/인력공급사 추가
5. 공급사가 참여 현장 조회

### 2단계: 자원 현장 표시

1. 장비/인원에 `current_site_id`, `status` 추가
2. 현장 배치/해제 이력 추가
3. 장비/인원 목록에서 현재 현장 표시
4. 상세에서 현장 이력 표시

### 3단계: 역할별 UI/대시보드/메뉴 + Audit Log 기반

1. 로그인 후 role별 기본 대시보드로 이동
2. Sidebar/Menu를 ADMIN/BP/장비공급사/인력공급사별로 분리
3. 역할별 대시보드 페이지 생성
4. `audit_logs` 테이블/API/서비스 추가
5. 현장 생성/수정, 참여업체 연결/해제, 장비/인원 배치/해제 등 핵심 액션에 로그 기록
6. ADMIN과 회사 관리자별 로그 조회 범위 분리

### 4단계: 서류 정책 강화

1. `document_types`에 필수/차단/기본 유효기간/OCR 플래그 추가
2. `documents`에 검증 상태 확장
3. 장비/인원별 서류 적합성 API 추가
4. D-30/D-7/만료 상태 계산

### 5단계: 작업계획서

1. `work_plans` 추가
2. `work_plan_equipment` 추가
3. `work_plan_persons` 추가
4. 현장 참여 공급사 기반 후보 조회 API 추가
5. 이전 투입/서류완비 추천 정렬 추가
6. 서류 적합성 스냅샷 저장

### 6단계: 대시보드

1. 역할별 summary API 재설계
2. 서류 위험 목록 분리
3. 오늘/이번 주 작업계획서 추가
4. 현장별 참여 업체/투입 자원 요약 추가
5. 최근 알림 추가

### 7단계: 작업계획서 출력/이식

1. 기존 `skep` 폴더의 작업계획서 생성 기능 분석
2. SKEP v2 도메인 모델에 맞춰 이식
3. 작업계획서 PDF/문서 출력
4. 서류 검증 결과를 출력물에 포함

## 설정값으로 남겨둘 정책

아래는 지금 확정하지 않고 설정 가능하게 남긴다.

| 정책 | 기본값 제안 |
|---|---|
| 서류 D-30 알림 | 사용 |
| 서류 D-7 알림 | 사용 |
| 만료 서류 배정 차단 | 사용 |
| BP 강제 진행 | 사용하되 사유 필수 |
| 공급사 강제 진행 | 기본 불가 |
| 작업계획서 승인 단계 | 초기 단순화, 추후 활성 |
| 이전 투입 추천 가중치 | 서비스 레이어 상수로 시작 |
| OCR 적용 서류 | 문서 타입별 플래그 |
| QR/GPS 출결 | 추후 앱 단계 |

## 최종 방향

SKEP v2는 작업계획서를 중심으로 재구성해야 한다.

장비와 인원은 단순 등록 대상이 아니라, BP사가 구성한 현장과 작업계획서에 투입되는 자원이다. 서류는 그 자원이 투입 가능한지 판단하는 핵심 기준이다.

따라서 앞으로의 설계와 구현 우선순위는 아래 순서가 되어야 한다.

```text
현장
→ 현장 참여 공급사
→ 공급사 자원 등록
→ 서류 적합성
→ 작업계획서
→ 후보 추천
→ 알림
→ 대시보드
```
