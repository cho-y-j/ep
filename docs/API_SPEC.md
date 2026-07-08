# SKEP v2 API 명세서

> 마지막 갱신: 2026-06-01 (코드 기준 전수 점검 — 견적/배차/공개입찰/영업견적/원청기관/보완요청/컴플라이언스/compliance-orders/안전점검/작업확인서/전자서명/OnlyOffice/DOCX 도메인 보강)
> Base URL: `http://localhost:8081` (로컬), `/api` prefix 공통
> 관련 문서: [ERD](./ERD.md)

## 공통 규칙

### 인증
- **방식**: Bearer JWT (HMAC-SHA, 서명 알고리즘은 JWT_SECRET 길이에 따라 jjwt 가 자동 선택. access 60분 + refresh 14일 rotation)
- **헤더**: `Authorization: Bearer <access_token>`
- **공개 엔드포인트**: `GET /api/health`, `POST /api/auth/{signup,login,refresh}`
- **그 외**: 모두 로그인 필요. ADMIN 전용 엔드포인트는 `[ADMIN]` 표시.

### 네이밍
- **Request/Response 모두 snake_case** (`company_id`, `business_number`)
- 자바 record/DTO 필드는 camelCase, Jackson이 전역 `SNAKE_CASE` 전략으로 자동 변환
- **예외**: 일부 엔드포인트는 컨트롤러가 `@RequestBody Map` 에서 키를 직접 읽어 전역 변환을 거치지 않는다 — 서명 PNG 제출 `pngBase64`, 안전점검 완료 `resultNotes`(둘 다 camelCase), 작업확인서 발급 `person_id`(snake). OnlyOffice/Worksheet config 응답도 DocsAPI 규격 키(camelCase). 각 엔드포인트에 별도 표기.

### 시간대
- 모든 timestamp는 `Asia/Seoul` 기준 ISO-8601 (`2026-04-30T13:00:25.107581`)

### 에러 응답 형식
모든 에러는 동일한 JSON 구조:
```json
{
  "timestamp": "2026-04-30T13:00:00.000Z",
  "status": 400,
  "code": "ERROR_CODE_UPPER_SNAKE",
  "message": "사람이 읽을 수 있는 메시지"
}
```

### 공통 에러 코드
| code | status | 의미 |
|---|---|---|
| `VALIDATION_ERROR` | 400 | 요청 본문/파라미터 검증 실패 |
| `INVALID_CREDENTIALS` | 401 | 로그인 실패 / 토큰 무효 |
| `ACCESS_DENIED` | 403 | 권한 부족 |
| `INTERNAL_ERROR` | 500 | 서버 내부 오류 |

---

## Auth — `/api/auth`

### `POST /api/auth/signup` — 자기가입 (공개)
가입 후 `enabled=false` 상태로 생성됨. ADMIN 승인 후 로그인 가능.

**Request**
```json
{
  "email": "user@example.com",
  "password": "minimum8chars",
  "name": "홍길동",
  "phone": "010-1234-5678",
  "role": "BP",
  "company_name": "테스트 BP건설(주)",
  "business_number": "111-11-11111"
}
```

| 필드 | 타입 | 필수 | 비고 |
|---|---|---|---|
| email | string | ✓ | 이메일 형식, unique |
| password | string | ✓ | 8자 이상 |
| name | string | ✓ | 100자 이하 |
| phone | string |  | 32자 이하 |
| role | enum | ✓ | `BP` / `EQUIPMENT_SUPPLIER` / `MANPOWER_SUPPLIER` (`ADMIN` 가입 불가) |
| company_name | string | △ | role이 회사 필요 역할(BP/공급사)일 때 필수 |
| business_number | string | △ | 같음 |

**Response 201**
```json
{
  "user": { /* UserResponse */ },
  "message": "signup successful — awaiting admin approval"
}
```

**회사 처리 로직**
- 새 사업자번호 → 새 Company 생성, 가입자는 `is_company_admin=true`
- 기존 사업자번호 + 회사 type 일치 → 그 회사에 합류, `is_company_admin=false`
- 기존 사업자번호 + type 불일치 → 409

**에러**
| code | status | 조건 |
|---|---|---|
| `EMAIL_EXISTS` | 409 | 이미 등록된 이메일 |
| `ADMIN_SIGNUP_NOT_ALLOWED` | 403 | role=ADMIN으로 가입 시도 |
| `COMPANY_INFO_REQUIRED` | 400 | BP/공급사인데 회사명 또는 사업자번호 누락 |
| `COMPANY_TYPE_MISMATCH` | 409 | 사업자번호는 같으나 회사 type 다름 |

---

### `POST /api/auth/login` — 로그인 (공개)

**Request**
```json
{ "email": "admin@skep.local", "password": "change-me-now" }
```

**Response 200**
```json
{
  "access_token": "eyJ...",
  "refresh_token": "9cb6bd...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

| 필드 | 의미 |
|---|---|
| access_token | JWT (HS384). claims: sub(user_id), email, role, name, iss=skep, exp |
| refresh_token | 32바이트 hex (JWT 아님). DB에 SHA-256 해시로 저장 |
| expires_in | access 만료까지 초 |

**에러**
| code | status | 조건 |
|---|---|---|
| `INVALID_CREDENTIALS` | 401 | 이메일 없음 또는 비번 불일치 |
| `ACCOUNT_DISABLED` | 403 | enabled=false (승인 대기 또는 비활성화됨) |

---

### `POST /api/auth/refresh` — 토큰 갱신 (공개)

**Request**
```json
{ "refresh_token": "9cb6bd..." }
```

**Response 200** — `POST /login`과 동일 구조 (rotation: 옛 refresh는 즉시 revoke, 새 access + 새 refresh 발급)

**에러**
| code | status | 조건 |
|---|---|---|
| `INVALID_REFRESH_TOKEN` | 401 | refresh 토큰 DB에 없음 |
| `REFRESH_TOKEN_REUSED` | 401 | 이미 사용된/만료된 refresh. **이 사용자의 모든 refresh 토큰을 revoke** (탈취 의심) |
| `ACCOUNT_DISABLED` | 403 | 사용자가 그 사이 비활성화됨 |

---

### `POST /api/auth/logout` — 로그아웃 (로그인)
현재 refresh 토큰을 revoke. 이후 access는 자연 만료까지는 유효.

**Request**
```json
{ "refresh_token": "9cb6bd..." }
```

**Response 204** (no content)

---

### `GET /api/auth/me` — 내 정보 + 소속 회사

**Response 200**
```json
{
  "user": {
    "id": 2,
    "email": "bp1@example.com",
    "name": "테스트 BP",
    "phone": null,
    "role": "BP",
    "company_id": 1,
    "is_company_admin": true,
    "enabled": true,
    "created_at": "2026-04-30T13:00:25.086651"
  },
  "company": {
    "id": 1,
    "name": "테스트 BP건설(주)",
    "business_number": "111-11-11111",
    "type": "BP",
    "created_at": "2026-04-30T13:00:24.998793"
  }
}
```
- `company`는 `user.company_id`가 null이면 null

---

## Users — `/api/users` `[ADMIN]`

### `GET /api/users`
**Response 200**: `UserResponse[]` (하단 스키마)

### `POST /api/users` — 관리자가 직접 생성 (`enabled=true`)
**Request**
```json
{
  "email": "...", "password": "...", "name": "...",
  "phone": "...", "role": "BP",
  "company_id": 1, "is_company_admin": false
}
```

**Response 201**: `UserResponse`

**에러**: `EMAIL_EXISTS` (409)

### `PATCH /api/users/{id}/enable`
**Response 200**: `UserResponse`

### `PATCH /api/users/{id}/disable`
- 현재 사용자(actor)와 같은 ID면 차단
- 대상이 마지막 활성 ADMIN이면 차단
- 비활성화 시 그 사용자의 **모든 refresh 토큰 즉시 revoke**

**Response 200**: `UserResponse`

**에러**
| code | status | 조건 |
|---|---|---|
| `CANNOT_DISABLE_SELF` | 400 | 본인을 비활성화 시도 |
| `LAST_ADMIN` | 400 | 마지막 활성 ADMIN을 비활성화 시도 |

---

## Companies — `/api/companies`

### `GET /api/companies?type=BP` `[ADMIN]`
- `type` 파라미터(선택): `BP` / `EQUIPMENT` / `MANPOWER`

**Response 200**: `CompanyResponse[]`

### `GET /api/companies/{id}` (로그인)
- 권한 검증은 추후 강화 예정 (현재는 로그인만)

**Response 200**: `CompanyResponse`

### `POST /api/companies` `[ADMIN]`
**Request**
```json
{
  "name": "센코어테크(주)",
  "business_number": "555-55-55555",
  "type": "EQUIPMENT"
}
```

**Response 201**: `CompanyResponse`

**에러**: `BUSINESS_NUMBER_EXISTS` (409)

### `PATCH /api/companies/{id}` `[ADMIN]` — 회사명만 변경
**Request**: `{ "name": "새이름" }`

**Response 200**: `CompanyResponse`

> 사업자번호와 type은 변경 불가 (생성 시 고정)

---

## Sites — `/api/sites`

현장은 BP사가 생성하고 소유한다. BP사는 자기 현장에 장비공급사와 인력공급사를 참여업체로 연결한다. 공급사는 자기가 참여 중인 현장만 조회할 수 있다.

### 권한 규칙

| Role | 조회 | 생성 | 수정 | 참여업체 추가/해제 | 공급사 후보 조회 |
|---|---|---|---|---|---|
| `ADMIN` | 전체 현장 | 가능, `bp_company_id` 필수 | 전체 가능 | 전체 가능 | 가능 |
| `BP` | 자기 BP 회사 현장 | 가능, 자기 회사로 고정 | 자기 현장만 가능 | 자기 현장만 가능 | 가능 |
| `EQUIPMENT_SUPPLIER` | 참여 중인 현장만 | 불가 | 불가 | 불가 | 불가 |
| `MANPOWER_SUPPLIER` | 참여 중인 현장만 | 불가 | 불가 | 불가 | 불가 |
| `WORKER` | 현재 없음 | 불가 | 불가 | 불가 | 불가 |

### `GET /api/sites`

역할에 맞는 현장 목록을 반환한다. 목록 응답에서는 `participants`가 null이고 `participant_count`만 내려간다.

**Response 200**
```jsonc
[
  {
    "id": 1,
    "bp_company_id": 10,
    "bp_company_name": "테스트 BP건설(주)",
    "name": "서울 A현장",
    "code": "SITE-SEOUL-A",
    "address": "서울시 강남구 ...",
    "detail_address": "1공구",
    "start_date": "2026-05-01",
    "end_date": "2026-12-31",
    "status": "ACTIVE",
    "participant_count": 2,
    "created_at": "2026-05-06T15:30:00",
    "updated_at": "2026-05-06T15:30:00",
    "participants": null
  }
]
```

### `GET /api/sites/{id}`

현장 상세와 참여업체 목록을 반환한다.

**Response 200**: `SiteResponse`

**에러**
| code | status | 조건 |
|---|---|---|
| `SITE_NOT_FOUND` | 404 | 현장이 없음 |
| `SITE_ACCESS_DENIED` | 403 | 조회 권한 없음 |
| `NO_COMPANY` | 403 | 회사 소속이 필요한 역할인데 company_id가 없음 |

### `POST /api/sites`

현장을 생성한다.

**Request**
```json
{
  "bp_company_id": 10,
  "name": "서울 A현장",
  "code": "SITE-SEOUL-A",
  "address": "서울시 강남구 ...",
  "detail_address": "1공구",
  "start_date": "2026-05-01",
  "end_date": "2026-12-31"
}
```

| 필드 | 필수 | 비고 |
|---|---|---|
| `bp_company_id` | △ | ADMIN은 필수. BP는 생략 가능하며 자기 회사로 고정 |
| `name` | ✓ | 150자 이하 |
| `code` |  | 64자 이하, null이 아니면 unique |
| `address` |  | 255자 이하 |
| `detail_address` |  | 255자 이하 |
| `start_date` |  | YYYY-MM-DD |
| `end_date` |  | YYYY-MM-DD |

**Response 201**: `SiteResponse`

**에러**
| code | status | 조건 |
|---|---|---|
| `BP_COMPANY_REQUIRED` | 400 | ADMIN 생성 요청에 `bp_company_id` 없음 |
| `BP_COMPANY_NOT_FOUND` | 400 | BP 회사 없음 |
| `COMPANY_NOT_BP` | 400 | `bp_company_id`가 BP 회사가 아님 |
| `FORBIDDEN_OTHER_BP_COMPANY` | 403 | BP가 다른 BP 회사로 생성 시도 |
| `ROLE_NOT_ALLOWED` | 403 | 생성 권한 없는 역할 |

### `PATCH /api/sites/{id}`

현장 기본정보와 상태를 수정한다.

**Request**
```json
{
  "name": "서울 A현장",
  "code": "SITE-SEOUL-A",
  "address": "서울시 강남구 ...",
  "detail_address": "1공구",
  "start_date": "2026-05-01",
  "end_date": "2026-12-31",
  "status": "ACTIVE"
}
```

**Response 200**: `SiteResponse`

**에러**
| code | status | 조건 |
|---|---|---|
| `SITE_NOT_FOUND` | 404 | 현장이 없음 |
| `SITE_MANAGE_DENIED` | 403 | 수정 권한 없음 |

### `POST /api/sites/{id}/participants`

현장에 장비공급사 또는 인력공급사를 참여업체로 추가한다. 이미 연결된 회사가 있으면 새 row를 만들지 않고 `ACTIVE`로 복구한다.

**Request**
```json
{ "company_id": 22 }
```

**Response 201**: `SiteResponse`

**에러**
| code | status | 조건 |
|---|---|---|
| `PARTICIPANT_COMPANY_NOT_FOUND` | 400 | 참여 업체 회사 없음 |
| `PARTICIPANT_MUST_BE_SUPPLIER` | 400 | BP 회사를 참여업체로 추가하려 함 |
| `SITE_MANAGE_DENIED` | 403 | 관리 권한 없음 |

### `DELETE /api/sites/{siteId}/participants/{participantId}`

참여업체를 물리 삭제하지 않고 `INACTIVE` 상태로 변경한다.

**Response 200**: `SiteResponse`

**에러**
| code | status | 조건 |
|---|---|---|
| `PARTICIPANT_NOT_FOUND` | 404 | 참여업체 row 없음 |
| `PARTICIPANT_SITE_MISMATCH` | 400 | participant가 해당 site에 속하지 않음 |
| `SITE_MANAGE_DENIED` | 403 | 관리 권한 없음 |

### `GET /api/sites/supplier-companies?type=EQUIPMENT`

현장에 추가할 공급사 후보 회사를 반환한다.

**Query**
- `type`: `EQUIPMENT` 또는 `MANPOWER`

**Response 200**: `CompanyResponse[]`

**에러**
| code | status | 조건 |
|---|---|---|
| `SUPPLIER_LOOKUP_FORBIDDEN` | 403 | ADMIN/BP가 아닌 역할 |
| `SUPPLIER_TYPE_REQUIRED` | 400 | `type`이 EQUIPMENT/MANPOWER가 아님 |

### SiteResponse 스키마
```jsonc
{
  "id": 1,
  "bp_company_id": 10,
  "bp_company_name": "테스트 BP건설(주)",
  "name": "서울 A현장",
  "code": "SITE-SEOUL-A",
  "address": "서울시 강남구 ...",
  "detail_address": "1공구",
  "start_date": "2026-05-01",
  "end_date": "2026-12-31",
  "status": "ACTIVE",
  "participant_count": 2,
  "created_at": "2026-05-06T15:30:00",
  "updated_at": "2026-05-06T15:30:00",
  "participants": [
    {
      "id": 1,
      "site_id": 1,
      "company_id": 22,
      "company_name": "대한장비",
      "company_type": "EQUIPMENT",
      "participant_type": "EQUIPMENT_SUPPLIER",
      "status": "ACTIVE",
      "added_at": "2026-05-06T15:40:00"
    }
  ]
}
```

### Site enum

| enum | 값 |
|---|---|
| `SiteStatus` | `ACTIVE`, `PAUSED`, `COMPLETED`, `ARCHIVED` |
| `SiteParticipantType` | `EQUIPMENT_SUPPLIER`, `MANPOWER_SUPPLIER` |
| `SiteParticipantStatus` | `ACTIVE`, `INACTIVE`, `SUSPENDED` |

---

## Resource Assignments — 자원 현장 배치 (Phase S-2)

장비/인원을 BP사가 만든 현장에 배치/해제하고 이력을 관리한다. 자원은 한 번에 한 현장에만 배치되며, 배치 변경 시 기존 활성 배치는 자동으로 닫힌다 (단일 트랜잭션).

### 권한 규칙

| Role | 자원 배치/해제 | 자원 이력 조회 | 현장 배치 자원 조회 | 현장 후보 조회 |
|---|---|---|---|---|
| `ADMIN` | 가능 | 가능 | 가능 | 가능 |
| `BP` | 자기 BP 회사 소유 현장만 | 자원 보유 회사가 자기 현장 참여 시 가능 | 자기 현장만 | 자기 현장만 |
| `EQUIPMENT_SUPPLIER` | 불가 | 자기 회사 자원만 | 참여 중인 현장만 | 불가 |
| `MANPOWER_SUPPLIER` | 불가 | 자기 회사 자원만 | 참여 중인 현장만 | 불가 |
| `WORKER` | 불가 | 불가 | 불가 | 불가 |

배치 검증:
- 자원의 `supplier_id` 가 사이트의 ACTIVE `site_participants` 에 포함되어야 한다.
- 사이트는 `ACTIVE` 상태여야 한다.
- 장비 `BROKEN` / 인원 `INACTIVE` 는 배치 거부된다.

### `POST /api/equipment/{id}/assignment` — 장비 배치

같은 자원이 다른 현장에 활성 배치 중이면 자동으로 그 배치를 `released_at` 으로 닫고 새 배치를 생성한다. 같은 현장에 재배치는 거부된다.

**Request**
```json
{ "site_id": 1, "note": "토공 작업 투입", "override": false, "override_reason": null }
```

| 필드 | 필수 | 비고 |
|---|---|---|
| `site_id` | ✓ | 배치할 현장 |
| `note` |  | 메모 (255자 이하) |
| `override` |  | (S-4 단계 3) `true` 면 서류 미비 강제 진행. ADMIN 만 가능 |
| `override_reason` |  | override=true 시 필수. audit log 에 기록됨 |

**Response 200**: 갱신된 `EquipmentResponse` (current_site_id/assignment_status/last_assigned_at 반영)

**에러**
| code | status | 조건 |
|---|---|---|
| `EQUIPMENT_NOT_FOUND` | 404 | 자원 없음 |
| `SITE_NOT_FOUND` | 404 | 현장 없음 |
| `SITE_NOT_ACTIVE` | 400 | 현장이 ACTIVE 가 아님 |
| `EQUIPMENT_BROKEN` | 400 | 장비 상태가 BROKEN |
| `SUPPLIER_NOT_PARTICIPANT` | 400 | 자원 공급사가 현장 참여업체가 아님 |
| `ALREADY_ASSIGNED` | 400 | 이미 같은 현장에 배치됨 |
| `DOCUMENTS_BLOCKED` | 400 | (S-4 단계 2.1) `blocks_assignment=true` 인 필수 서류 중 verified+안만료 가 아닌 type 이 있음. override 미지정 시 거부 |
| `OVERRIDE_ADMIN_ONLY` | 403 | (S-4 단계 3) `override=true` 인데 ADMIN 이 아님 |
| `OVERRIDE_REASON_REQUIRED` | 400 | (S-4 단계 3) `override=true` 인데 `override_reason` 비어있음 |
| `ASSIGNMENT_DENIED` | 403 | 배치 권한 없음 (ADMIN/BP가 아니거나 다른 BP의 현장) |

### `DELETE /api/equipment/{id}/assignment` — 장비 해제

**Request** (선택)
```json
{ "release_reason": "작업 종료" }
```

**Response 200**: 갱신된 `EquipmentResponse` (current_site_id=null, assignment_status=AVAILABLE)

**에러**
| code | status | 조건 |
|---|---|---|
| `NOT_ASSIGNED` | 400 | 현재 배치된 현장이 없음 |
| `ASSIGNMENT_DENIED` | 403 | 해제 권한 없음 |

### `GET /api/equipment/{id}/assignments` — 장비 배치 이력

**Response 200**: `AssignmentResponse[]` (assigned_at 내림차순)

### `POST /api/persons/{id}/assignment` — 인원 배치

장비와 동일한 흐름. 인원이 `INACTIVE` 면 거부.

**Request / Response**: 장비와 동일한 모양 (응답은 `PersonResponse`).

**추가 에러**: `PERSON_INACTIVE` (400) — 인원 상태가 INACTIVE.

### `DELETE /api/persons/{id}/assignment` — 인원 해제

**Response 200**: `PersonResponse` (assignment_status=OFF_DUTY).

### `GET /api/persons/{id}/assignments` — 인원 배치 이력

**Response 200**: `AssignmentResponse[]`.

### `GET /api/sites/{id}/equipment` — 현장에 현재 배치된 장비

**Response 200**: `EquipmentResponse[]` (활성 배치만).

### `GET /api/sites/{id}/persons` — 현장에 현재 배치된 인원

**Response 200**: `PersonResponse[]` (활성 배치만).

### `GET /api/sites/{id}/equipment-assignments` — 현장 장비 전체 이력

**Response 200**: `AssignmentResponse[]`.

### `GET /api/sites/{id}/person-assignments` — 현장 인원 전체 이력

**Response 200**: `AssignmentResponse[]`.

### `GET /api/sites/{id}/equipment-candidates` — 장비 후보

현장에 ACTIVE 참여 중인 장비공급사의 장비 전체를 후보로 반환한다. 작업계획서/배치 추천에 사용한다.

**Response 200**: `EquipmentCandidateResponse[]`

```jsonc
{
  "id": 1,
  "supplier_id": 2,
  "supplier_name": "테스트 장비공급(주)",
  "name": "DX300LCA",
  "category": "EXCAVATOR",
  "code": "EQ-2024-001",
  "vehicle_no": "경기99사1234",
  "has_photo": true,
  "assignment_status": "AVAILABLE",
  "current_site_id": null,
  "current_site_name": null,
  "last_assigned_at": null,
  "previously_used_on_site": false,    // 이 현장에 과거 배치된 적 있는지 (이력 기준)
  "currently_assigned": false,          // 현재 다른 현장에 배치 중인지
  "expiring_documents": 1,              // 만료 임박(30일) 서류 수
  "missing_documents": 0,               // 필수 서류 누락 수 (서류 정책 강화 단계에서 채움)
  "blocked": false                      // 사용 제한 (BROKEN | missing_documents>0)
}
```

### `GET /api/sites/{id}/person-candidates` — 인원 후보

**Response 200**: `PersonCandidateResponse[]` (장비와 같은 모양, `roles`/`employee_no`/`job_title` 추가).

### AssignmentResponse 스키마

```jsonc
{
  "id": 1,
  "resource_id": 2,           // equipment_id 또는 person_id
  "site_id": 1,
  "site_name": "서울 A현장",
  "assigned_at": "2026-05-06T16:16:08",
  "released_at": null,
  "assigned_by": 1,
  "released_by": null,
  "note": "테스트 배치",
  "release_reason": null,
  "active": true              // released_at IS NULL
}
```

### Assignment enum

| enum | 값 | 의미 |
|---|---|---|
| `EquipmentAssignmentStatus` | `AVAILABLE` | 미배치, 사용 가능 |
| | `ASSIGNED` | 현장 배치 중 |
| | `BROKEN` | 고장, 사용 불가 |
| `PersonAssignmentStatus` | `ON_DUTY` | 현장 배치 중 |
| | `OFF_DUTY` | 미배치 |
| | `INACTIVE` | 비활성 (배치 후보 제외) |

> 응답에는 Jackson 의 `non_null` 정책으로 `current_site_id` / `current_site_name` 등이 null인 경우 키가 누락될 수 있다. 프론트는 옵셔널로 받는다.

---

## Equipment — `/api/equipment`

장비공급사가 자기 회사의 장비를 관리. ADMIN은 모든 회사 가능. JWT의 `company_id` claim으로 권한 자동 매핑.

### `GET /api/equipment?supplier_id=&category=`
**파라미터** (둘 다 선택)
- `supplier_id` (long): 특정 공급사 필터. `EQUIPMENT_SUPPLIER`는 무시되고 본인 회사만 반환
- `category` (enum): 분류 필터

**응답**: `EquipmentResponse[]` (id 내림차순)

**역할별 동작**
| Role | 동작 |
|---|---|
| ADMIN | supplier_id 지정 시 그 회사만, 미지정 시 전체 |
| EQUIPMENT_SUPPLIER | 본인 회사 강제, supplier_id 파라미터 무시 |
| BP / 그 외 | supplier_id 지정 시 그 회사 (장비 검색 용도, 추후 좁힐 수 있음) |

### `GET /api/equipment/{id}`
**응답**: `EquipmentResponse`
**에러**: `EQUIPMENT_NOT_FOUND` 404, `FORBIDDEN_OTHER_COMPANY` 403 (다른 회사 장비를 EQUIPMENT_SUPPLIER가 조회)

### `POST /api/equipment`
**Request**
```json
{
  "supplier_id": 2,
  "vehicle_no": "경기99사1234",
  "category": "EXCAVATOR",
  "model": "DX300LCA",
  "manufacturer": "두산인프라코어",
  "year": 2022
}
```

| 필드 | 필수 | 비고 |
|---|---|---|
| supplier_id | △ | ADMIN: 필수 / EQUIPMENT_SUPPLIER: 무시 (본인 회사 강제) |
| vehicle_no | | 어태치먼트 등은 비울 수 있음 |
| category | ✓ | enum |
| model, manufacturer, year | | 모두 선택 |

**응답 201**: `EquipmentResponse`

**에러**
| code | status | 조건 |
|---|---|---|
| `SUPPLIER_REQUIRED` | 400 | ADMIN인데 supplier_id 누락 |
| `SUPPLIER_NOT_FOUND` | 400 | 존재하지 않는 supplier_id |
| `SUPPLIER_NOT_EQUIPMENT` | 400 | supplier_id가 type=EQUIPMENT가 아님 |
| `FORBIDDEN_OTHER_COMPANY` | 403 | EQUIPMENT_SUPPLIER가 다른 회사 supplier_id 보냄 |
| `ROLE_NOT_ALLOWED` | 403 | BP/MANPOWER_SUPPLIER/WORKER 등 |

### `PATCH /api/equipment/{id}`
**Request** (모든 필드 선택)
```json
{ "vehicle_no": "...", "category": "...", "model": "...", "manufacturer": "...", "year": 2023 }
```
**응답**: `EquipmentResponse`
**에러**: `FORBIDDEN` (다른 회사 장비)

### `DELETE /api/equipment/{id}`
**응답 204**
**에러**: `FORBIDDEN`

### EquipmentResponse 스키마
```jsonc
{
  "id": 1,
  "supplier_id": 2,
  "vehicle_no": "경기99사1234",      // null 허용
  "category": "EXCAVATOR",
  "model": "DX300LCA",                // null 허용
  "manufacturer": "두산인프라코어",   // null 허용
  "year": 2022,                        // null 허용
  "has_photo": true,
  "expiring_count": 0,
  "code": "EQ-2024-001",              // V8
  "serial_number": "...",              // V8
  "usage_hours": 1490,                  // V8
  "weight_kg": 28000,                   // V8
  "bucket_capacity": 1.20,              // V8 nullable
  "insurance_expiry": "2025-12-31",    // V8 nullable
  "operating_hours": 980,               // V8
  "idle_hours": 220,                    // V8
  "downtime_hours": 56,                 // V8
  "utilization_pct": 78,                // V8 계산값
  "current_site_id": 1,                 // V11 nullable — 현재 활성 배치 현장
  "current_site_name": "서울 A현장",  // V11 nullable
  "assignment_status": "ASSIGNED",     // V11 — AVAILABLE | ASSIGNED | BROKEN
  "last_assigned_at": "...",           // V11 nullable
  "created_at": "2026-04-30T13:24:17.888",
  "updated_at": "2026-05-06T16:16:08"
}
```

### EquipmentCategory enum
| 코드 | 라벨 |
|---|---|
| `EXCAVATOR` | 굴삭기 |
| `WHEEL_LOADER` | 휠로더 |
| `CRANE` | 크레인 |
| `FORKLIFT` | 지게차 |
| `DOZER` | 도저 |
| `GRADER` | 그레이더 |
| `AERIAL_LIFT` | 고소작업차 |
| `PUMP_TRUCK` | 펌프카 |
| `ATTACHMENT` | 어태치먼트 |

---

## Persons — `/api/persons`

장비/인력 공급사가 자기 회사의 인원(작업자)을 관리. ADMIN은 모든 회사 가능.

### `GET /api/persons?supplier_id=&role=`
**파라미터** (둘 다 선택)
- `supplier_id` (long): 특정 공급사 필터. 공급사 본인은 무시되고 본인 회사만 반환
- `role` (enum): 역할 필터

**응답**: `PersonResponse[]` (id 내림차순)

### `GET /api/persons/{id}`
**응답**: `PersonResponse`
**에러**: `PERSON_NOT_FOUND` 404, `FORBIDDEN_OTHER_COMPANY` 403

### `POST /api/persons`
**Request**
```json
{
  "supplier_id": 3,
  "name": "김신호",
  "birth": "1985-03-15",
  "phone": "010-1234-5678",
  "roles": ["SIGNALER", "FIRE_WATCH"]
}
```

| 필드 | 필수 | 비고 |
|---|---|---|
| supplier_id | △ | ADMIN: 필수 / SUPPLIER: 무시 (본인 회사 강제) |
| name | ✓ | 100자 |
| birth | | YYYY-MM-DD |
| phone | | 32자 |
| roles | ✓ | 배열, 1개 이상. supplier 회사 type에 맞아야 함 |

**응답 201**: `PersonResponse`

**에러**
| code | status | 조건 |
|---|---|---|
| `SUPPLIER_REQUIRED` | 400 | ADMIN인데 supplier_id 누락 |
| `SUPPLIER_NOT_FOUND` | 400 | 존재하지 않는 supplier_id |
| `SUPPLIER_NOT_ALLOWED` | 400 | BP사는 인원 등록 불가 |
| `ROLE_COMPANY_TYPE_MISMATCH` | 400 | 역할이 supplier type에 안 맞음 (예: MANPOWER에 OPERATOR) |
| `ROLES_REQUIRED` | 400 | roles 비어있음 |
| `FORBIDDEN_OTHER_COMPANY` | 403 | SUPPLIER가 다른 회사 supplier_id |
| `ROLE_NOT_ALLOWED` | 403 | BP/WORKER 등 |

### `PATCH /api/persons/{id}`
**Request** (모든 필드 선택)
```json
{ "name": "...", "birth": "...", "phone": "...", "roles": ["SIGNALER"] }
```
roles 변경 시 supplier type 검증 재수행.
**응답**: `PersonResponse`

### `DELETE /api/persons/{id}`
**응답 204**

### PersonResponse 스키마
```jsonc
{
  "id": 1,
  "supplier_id": 3,
  "name": "김신호",
  "birth": "1985-03-15",            // null 허용
  "phone": "010-1234-5678",          // null 허용
  "roles": ["SIGNALER", "FIRE_WATCH"],
  "employee_no": "P2024-0001",       // V9
  "job_title": "신호수",              // V9
  "team": "안전관리팀",                // V9
  "qualification": "신호수 자격증",   // V9
  "address": "...",                    // V9 nullable
  "email": "...",                      // V9 nullable
  "hired_at": "2024-03-11",           // V9 nullable
  "status": "WORKING",                 // V9 — WORKING | VACATION | RETIRED (고용상태)
  "employment_type": "DIRECT",        // V9 — DIRECT | SUBCONTRACT
  "current_site_id": 1,                // V11 nullable
  "current_site_name": "서울 A현장",  // V11 nullable
  "assignment_status": "ON_DUTY",     // V11 — ON_DUTY | OFF_DUTY | INACTIVE (배치상태)
  "last_assigned_at": "...",           // V11 nullable
  "has_photo": true,
  "expiring_count": 0,
  "document_count": 5,
  "created_at": "2026-04-30T15:11:58.145482",
  "updated_at": "2026-05-06T..."
}
```

### PersonRole enum + 허용 supplier type
| 코드 | 라벨 | 허용 supplier type |
|---|---|---|
| `OPERATOR` | 조종원 | EQUIPMENT |
| `WORK_DIRECTOR` | 작업지휘자 | MANPOWER |
| `GUIDE` | 유도원 | MANPOWER |
| `FIRE_WATCH` | 화기감시자 | MANPOWER |
| `SIGNALER` | 신호수 | MANPOWER |
| `INSPECTOR` | 점검원 | MANPOWER (잠정) |
| `SITE_MANAGER` | 소장 | MANPOWER (잠정) |

---

## DocumentTypes — `/api/document-types`

서류 종류 마스터. ADMIN이 추가/비활성 가능. 시드된 12종 (PERSON 6 + EQUIPMENT 6).

### `GET /api/document-types?appliesTo=PERSON`
**파라미터** (선택): `appliesTo=PERSON|EQUIPMENT` — 미지정 시 전체

**응답 200**: `DocumentTypeResponse[]` (active만, sortOrder 오름차순)

### `POST /api/document-types` `[ADMIN]`
**Request**
```json
{
  "name": "추가서류",
  "appliesTo": "PERSON",
  "hasExpiry": true,
  "requiresVerification": false,
  "sortOrder": 60
}
```

### `PATCH /api/document-types/{id}/active?active=true|false` `[ADMIN]`
활성/비활성 토글. 시드된 type도 비활성 가능.

### DocumentTypeResponse 스키마
```jsonc
{
  "id": 1,
  "name": "운전면허증",
  "applies_to": "PERSON",
  "has_expiry": true,
  "requires_verification": true,
  "sort_order": 10,
  "active": true,
  // V14 정책 / 검증 필드
  "required": true,
  "blocks_assignment": true,
  "default_valid_months": 24,
  "ocr_enabled": true,
  "ocr_extract_type": "LICENSE",          // LICENSE | BUSINESS | CARGO | KOSHA | EQUIPMENT_REGISTRATION | null
  "ocr_expiry_field_key": "expiry_date",
  "verify_endpoint": "RIMS_LICENSE",       // RIMS_LICENSE | CARGO_LICENSE | KOSHA | NTS_BIZ | null
  "required_fields": "[\"license_no\",\"name\",\"license_condition_code\"]"  // JSON 배열 문자열
}
```

### 시드 정책 표 (V14)

| 서류 | applies_to | required | blocks_assignment | OCR | verify endpoint | required_fields |
|---|---|---|---|---|---|---|
| 운전면허증 | PERSON | ✓ | ✓ | LICENSE | RIMS_LICENSE | license_no, name, license_condition_code |
| 신분증 | PERSON | ✓ |   |   |   | (없음) |
| 안전교육 이수증 | PERSON | ✓ | ✓ | KOSHA | KOSHA | (이미지 자체 검증) |
| 건강진단서 | PERSON | ✓ | ✓ |   |   | expiry_date |
| 자격증 | PERSON |   |   |   |   | (없음) |
| **화물운송자격증** | PERSON |   |   | CARGO | CARGO_LICENSE | license_no, name, birth_date |
| 기타 | PERSON |   |   |   |   | (없음) |
| 자동차등록증 | EQUIPMENT | ✓ | ✓ | EQUIPMENT_REGISTRATION |   | vehicle_no |
| 정기검사증 | EQUIPMENT | ✓ | ✓ |   |   | expiry_date |
| 보험증권 | EQUIPMENT | ✓ | ✓ |   |   | expiry_date |
| 안전인증서 | EQUIPMENT | ✓ | ✓ |   |   | expiry_date |
| 점검표 | EQUIPMENT |   |   |   |   | expiry_date |
| 기타 | EQUIPMENT |   |   |   |   | (없음) |

---

## Documents — `/api/documents`

장비/인원의 첨부 서류. 파일 + 메타데이터.

### 권한 (S-3.1 패치)

| 역할 | read (list / file / metadata) | write (upload / expiry / delete) | verify |
|---|---|---|---|
| `ADMIN` | 전체 | 전체 | 전체 |
| `EQUIPMENT_SUPPLIER` / `MANPOWER_SUPPLIER` | 자기 회사 자원만 | 자기 회사 자원만 | 불가 |
| `BP` | 자기 BP 회사 소유 사이트의 ACTIVE 참여 공급사 자원만 | 불가 (확인만 가능) | 불가 |
| `WORKER` | 차단 (`DOCUMENT_ACCESS_DENIED` 403) | 차단 | 차단 |

> 검증 토글은 ADMIN 만 (`VERIFY_ADMIN_ONLY`).

### `GET /api/documents?ownerType=&ownerId=`
**파라미터** (둘 다 필수)
- `ownerType`: `PERSON` | `EQUIPMENT`
- `ownerId`: long

**응답**: `DocumentResponse[]`

### `POST /api/documents` (multipart/form-data)
**Query 파라미터**
- `ownerType`: `PERSON` | `EQUIPMENT`
- `ownerId`: long
- `documentTypeId`: long
- `expiryDate` (선택): YYYY-MM-DD. type.has_expiry=true면 필수

**Form 필드**
- `file`: 업로드 파일 (max 20MB)

**S-4 단계 3 부터**: upload 트랜잭션 commit 직후 비동기로 자동 검증이 트리거된다 (`document_type.verify_endpoint != null` 인 경우). 응답은 `verification_status: PENDING` 으로 즉시 반환되고, 별도 thread 에서 OCR + 정부 API 호출 후 status 가 갱신된다 (`VERIFIED` / `REJECTED` / `OCR_REVIEW_REQUIRED`). 또한 같은 (owner_type, owner_id, document_type_id) 의 가장 최신 문서가 있으면 자동으로 `previous_document_id` 로 묶인다.

**응답 201**: `DocumentResponse`

**에러**
| code | status | 조건 |
|---|---|---|
| `EMPTY_FILE` | 400 | 파일 비어있음 |
| `DOCUMENT_TYPE_NOT_FOUND` | 400 | documentTypeId 없음 |
| `DOCUMENT_TYPE_OWNER_MISMATCH` | 400 | type.appliesTo와 ownerType 불일치 |
| `EXPIRY_REQUIRED` | 400 | type.has_expiry=true인데 expiryDate 누락 |
| `EQUIPMENT_NOT_FOUND` / `PERSON_NOT_FOUND` | 404 | owner 없음 |
| `FORBIDDEN_OTHER_COMPANY` | 403 | 다른 회사 owner |
| `FORBIDDEN` | 403 | 수정 권한 없음 |

### `GET /api/documents/{id}/history` (S-4 단계 3)

같은 (owner_type, owner_id, document_type_id) 의 모든 버전을 최신순으로 반환. 권한은 owner read 권한과 동일.

**Response 200**: `DocumentResponse[]` — 갱신 체인 (head 가 첫 항목, 옛 버전이 뒤). 각 row 의 `previous_document_id` 로 chain 확인 가능.

### `GET /api/documents/{id}/file`
실제 파일 다운로드. `Content-Disposition: inline; filename*=UTF-8''<filename>` 헤더로 브라우저가 inline 표시 또는 다운로드. 권한은 owner 조회 권한과 동일.

**응답 200**: 파일 바이너리

### `PATCH /api/documents/{id}/expiry?expiryDate=2026-12-31`
만료일 갱신 (재업로드 없이 메타만 수정).

### `PATCH /api/documents/{id}/verified?verified=true|false` `[ADMIN]`
검증 표시 토글.

### `DELETE /api/documents/{id}`
DB row 삭제 + 파일 삭제.

### DocumentResponse 스키마
```jsonc
{
  "id": 1,
  "document_type_id": 7,
  "document_type_name": "자동차등록증",
  "document_type_has_expiry": false,
  "owner_type": "EQUIPMENT",
  "owner_id": 2,
  "file_name": "license.pdf",
  "file_size": 12345,
  "content_type": "application/pdf",
  "expiry_date": "2026-12-31",       // null 허용
  "verified": false,                  // 호환용 boolean
  // V14 검증 필드
  "verification_status": "PENDING",  // PENDING | VERIFIED | REJECTED | OCR_REVIEW_REQUIRED
  "verified_by": 1,                   // ADMIN 사용자 id, null 허용
  "verified_at": "2026-05-07T...",   // 검증 시각, null 허용
  "rejected_reason": null,
  "previous_document_id": null,       // 갱신(재업로드) 시 이전 문서 id
  "verification_result": null,        // verify-api 응답 원본 JSON 문자열 (단계 2 부터 채움)
  "extracted_data": null,             // OCR 추출 + 사용자 입력 합본 JSON 문자열
  "created_at": "2026-04-30T15:41:49.504"
}
```

> `verified` boolean 은 호환용으로 유지된다. 실제 상태 분기는 `verification_status` 를 사용해라.

### Storage 동작
- 지금: `LocalDiskStorage` (`/app/uploads/{yyyy}/{mm}/{uuid}.bin`)
- Docker volume: `skep_v2_uploads` 마운트
- 나중에 S3로 갈아끼울 때: `FileStorage` 인터페이스 다른 구현체로 교체. DB의 `file_key`만 다른 형식이 되고 코드 다른 부분은 무관.

---

## Document Verification — `/api/documents/{id}/{verify,reject}` (Phase S-4 단계 2)

`document_types.verify_endpoint` 라우팅을 통해 외부 verify-api / main-api 정부 API 를 호출한다. skep `LiftonVerifyClient` 흐름의 이식.

### 권한

| 역할 | verify | reject |
|---|---|---|
| `ADMIN` | 가능 | 가능 |
| `EQUIPMENT_SUPPLIER` / `MANPOWER_SUPPLIER` | 자기 회사 자원만 | 불가 |
| `BP` / `WORKER` | 불가 | 불가 |

### `POST /api/documents/{id}/verify`

자동 검증 트리거. 자원 owner 의 supplier_id 확인 → OCR 추출 (해당하는 경우) → 정부 API 호출 → 결과 저장.

**Request** (선택)
```json
{
  "user_inputs": {
    "license_no": "21-08-003005-16",
    "name": "박조종",
    "license_condition_code": "01"
  }
}
```

`user_inputs` 는 OCR 추출 결과를 덮어쓴다. OCR 실패/일부 누락 시 사용자가 보충하는 용도.

**Response 200**: 갱신된 `DocumentResponse`. `verification_status` / `verification_result` / `extracted_data` 채워져 있음.

**verify_endpoint 별 입력 매핑**:
| verify_endpoint | 사용 필드 (extracted_data + user_inputs 합본 기준) |
|---|---|
| `RIMS_LICENSE` | `license_no`, `name`, `license_condition_code` |
| `CARGO_LICENSE` | `name`, `birth_date`, `license_no` |
| `NTS_BIZ` | `biz_no`, `start_date`, `owner_name` |
| `KOSHA` | (이미지 자체 multipart 전송) |

**상태 전이**:
| 응답 | verification_status |
|---|---|
| `verified=true` | `VERIFIED` (+ verified_by/at 채움) |
| `reasonCode=UPSTREAM_ERROR` 또는 `UPSTREAM_DISABLED` | `OCR_REVIEW_REQUIRED` |
| 그 외 (verified=false) | `REJECTED` (rejected_reason = reasonCode) |

**에러**
| code | status | 조건 |
|---|---|---|
| `DOCUMENT_NOT_FOUND` | 404 | id 없음 |
| `VERIFY_NOT_SUPPORTED` | 400 | document_type.verify_endpoint == null |
| `VERIFY_DENIED` | 403 | 권한 없음 |
| `FILE_READ_ERROR` | 400 | KOSHA 등 multipart 검증인데 파일 읽기 실패 |
| `UNKNOWN_VERIFY_ENDPOINT` | 400 | document_type 의 verify_endpoint 값이 enum 외 |

### `POST /api/documents/{id}/reject` `[ADMIN]`

수동 반려. 사유 필수.

**Request**
```json
{ "reason": "면허번호 불일치" }
```

**Response 200**: 갱신된 `DocumentResponse`. `verification_status=REJECTED`, `rejected_reason` 채워짐. `audit_logs` 에 `DOCUMENT_VERIFIED` 기록 (after `{"status":"REJECTED","reason":"..."}`).

**에러**
| code | status | 조건 |
|---|---|---|
| `REJECT_ADMIN_ONLY` | 403 | ADMIN 외 호출 |
| `REASON_REQUIRED` | 400 | reason 비어있음 |
| `DOCUMENT_NOT_FOUND` | 404 | id 없음 |

### 외부 API 연동 환경변수

| 변수 | 기본값 | 의미 |
|---|---|---|
| `VERIFY_ENABLED` | `false` | 외부 호출 활성화. 개발 환경에서는 false 로 두고 graceful fail 으로 흐름만 검증 |
| `VERIFY_MAIN_API_URL` | `http://main-api:8080` | 정부 API 게이트웨이 |
| `VERIFY_INNER_API_URL` | `http://verify-api:8081` | OCR 엔진 |
| `VERIFY_API_KEY` | `` | X-API-KEY 헤더 |

**graceful fail**: `enabled=false` / 타임아웃 / 5xx 모두 `verification_status = OCR_REVIEW_REQUIRED` + `verification_result.reasonCode` 에 `UPSTREAM_DISABLED` 또는 `UPSTREAM_ERROR` 기록.

---

## Role Dashboards — `/api/dashboards` (Phase S-3)

역할별 관심 데이터와 응답 스키마가 다르므로 단일 `/api/dashboard/summary` 와 별도로 역할별 endpoint 를 분리해 제공한다.

기존 `/api/dashboard/summary` 는 호환용으로 유지된다 (Phase S-5 이후 제거 검토).

### 권한

| Endpoint | 허용 role | 거부 코드 |
|---|---|---|
| `/api/dashboards/admin/summary` | `ADMIN` | `ADMIN_ONLY` (403) |
| `/api/dashboards/bp/summary` | `BP` (소속 회사 필요) | `BP_ONLY` (403), `NO_COMPANY` (403) |
| `/api/dashboards/equipment-supplier/summary` | `EQUIPMENT_SUPPLIER` (소속 회사 필요) | `EQ_SUPPLIER_ONLY` (403), `NO_COMPANY` (403) |
| `/api/dashboards/manpower-supplier/summary` | `MANPOWER_SUPPLIER` (소속 회사 필요) | `MP_SUPPLIER_ONLY` (403), `NO_COMPANY` (403) |

### `GET /api/dashboards/admin/summary`

**Response 200**
```jsonc
{
  "counts": {
    "companies": 5,
    "sites": 2,
    "equipment": 2,
    "persons": 3,
    "documents_expiring30d": 3,
    "users_pending": 2,
    "work_plans_upcoming": 4
  },
  "recent_audit_logs": [ /* AuditLogResponse[] */ ],
  "today_work_plans": [ /* DashboardWorkPlanItem[] — 오늘 + 향후 7일, 최대 20건 */ ],
  "recent_notifications": []   // TODO Phase S-4 이후
}
```

### `GET /api/dashboards/bp/summary`

**Response 200**
```jsonc
{
  "counts": {
    "my_sites": 2,
    "active_participants": 3,
    "equipment_on_my_sites": 1,
    "persons_on_my_sites": 0,
    "work_plans_upcoming": 2
  },
  "sites": [
    { "id": 1, "name": "서울 A현장", "status": "ACTIVE",
      "participant_count": 2, "equipment_count": 1, "person_count": 0 }
  ],
  "recent_audit_logs": [ ... ],
  "today_work_plans": [ /* DashboardWorkPlanItem[] — 자기 회사 plan, 오늘 + 향후 7일, 최대 20건 */ ],
  "document_risks": [ /* ... */ ]
}
```

### `GET /api/dashboards/equipment-supplier/summary` / `manpower-supplier/summary`

```jsonc
{
  "counts": { "...": "...", "work_plans_upcoming": 1 },
  "sites": [ ... ],
  "recent_audit_logs": [ ... ],
  "upcoming_work_plans": [ /* 자기 회사 자원이 포함된 plan */ ],
  "document_risks": [ /* ... */ ]
}
```

### DashboardWorkPlanItem 스키마

```json
{
  "id": 1,
  "title": "5/8 토사 굴착",
  "site_id": 12,
  "site_name": "A현장",
  "bp_company_name": "BP사",
  "work_date": "2026-05-08",
  "start_time": "08:00:00",
  "end_time": "17:00:00",
  "status": "APPROVED",
  "equipment_count": 2,
  "person_count": 3
}
```

---

## Audit Logs — `/api/audit-logs` (Phase S-3)

도메인 서비스가 비즈니스 작업 직후 자동 기록한다. 알림(notifications) 도메인과 분리되어 권한 변경 추적 전용으로 사용된다.

### 권한별 조회 범위

| 역할 | 회사 관리자 (`is_company_admin=true`) | 일반 직원 |
|---|---|---|
| `ADMIN` | 전체 (그대로) | — |
| `BP` | `actor_company_id = 자기 회사` OR `target_company_id = 자기 회사` OR `site_id` 가 자기 BP 회사 소유 사이트 | 본인 행동 로그(`actor_user_id = self`) 만 |
| `EQUIPMENT_SUPPLIER` | 자기 회사가 actor/target 인 로그 + 자기 회사가 ACTIVE 참여 중인 사이트 로그 | 본인 행동 로그만 |
| `MANPOWER_SUPPLIER` | 동일 | 본인 행동 로그만 |
| `WORKER` | (사실상 빈 결과) | 본인 행동 로그만 |

> 회사 관리자/일반 직원은 JWT claim `is_company_admin` 으로 구분된다 (S-3.1 패치). 이 claim 은 로그인/refresh 시 발급되며, 변경된 후에는 사용자가 재로그인해야 반영된다.

### `GET /api/audit-logs?page=&size=`

**Query**: `page` (기본 0), `size` (기본 20, 최대 100)

**Response 200**: `PageResponse<AuditLogResponse>`

### `GET /api/audit-logs/recent?limit=`

**Query**: `limit` (기본 10, 최대 50)

**Response 200**: `AuditLogResponse[]`

### AuditLogResponse 스키마
```jsonc
{
  "id": 1,
  "actor_user_id": 1,
  "actor_role": "ADMIN",         // ADMIN | BP | EQUIPMENT_SUPPLIER | MANPOWER_SUPPLIER | WORKER
  "actor_company_id": null,
  "action": "SITE_CREATED",      // 표 참고
  "target_type": "SITE",         // SITE | SITE_PARTICIPANT | EQUIPMENT | PERSON | DOCUMENT | WORK_PLAN
  "target_id": 4,
  "target_company_id": 1,
  "site_id": 4,
  "before_json": null,           // 단순 JSON 문자열 (V13 에서 jsonb→text)
  "after_json": "{\"name\":\"S-3 audit live\",\"status\":\"ACTIVE\"}",
  "created_at": "2026-05-06T17:17:46.907213"
}
```

### 기록되는 action

| Action | 위치 | 의미 |
|---|---|---|
| `SITE_CREATED` | SiteService.create | 현장 생성 |
| `SITE_UPDATED` | SiteService.update | 현장 정보/상태 수정 |
| `PARTICIPANT_ADDED` | SiteService.addParticipant | 참여 공급사 추가 |
| `PARTICIPANT_REMOVED` | SiteService.removeParticipant | 참여 공급사 비활성 |
| `EQUIPMENT_ASSIGNED` | AssignmentService.assignEquipment | 장비 현장 배치 |
| `EQUIPMENT_UNASSIGNED` | AssignmentService.releaseEquipment + 자동 release(이동 시) | 장비 현장 해제 — 사용자 명시 해제 + 다른 현장 이동 시 자동 해제(`auto_release_on_move`) 모두 기록 |
| `PERSON_ASSIGNED` | AssignmentService.assignPerson | 인원 현장 배치 |
| `PERSON_UNASSIGNED` | AssignmentService.releasePerson + 자동 release(이동 시) | 인원 현장 해제 — 동일 |
| `DOCUMENT_UPLOADED` | DocumentService.upload | 서류 업로드 |
| `DOCUMENT_VERIFIED` | DocumentService.setVerified | 서류 검증 표시 토글 |
| `DOCUMENT_RENEWED` | DocumentService.updateExpiry | 서류 만료일 갱신. before/after expiry_date 기록 |

다음 단계에서 추가 예정 action: `EQUIPMENT_STATUS_CHANGED`, `WORK_PLAN_*`.

---

## Document Review Queue — `/api/documents/review-queue` (S-4 단계 4)

### `GET /api/documents/review-queue` `[ADMIN]`

`verification_status` 가 `OCR_REVIEW_REQUIRED` 또는 `REJECTED` 인 chain head 만 모음. ADMIN 검토 페이지가 사용한다.

**Response 200**: `ReviewItemResponse[]`

```jsonc
{
  "id": 3,
  "document_type_id": 1,
  "document_type_name": "운전면허증",
  "owner_type": "PERSON",
  "owner_id": 2,
  "owner_name": "박조종",
  "owner_supplier_id": 2,
  "owner_supplier_name": "회사 #2",
  "file_name": "license.jpg",
  "expiry_date": null,
  "verification_status": "OCR_REVIEW_REQUIRED",
  "rejected_reason": null,
  "verification_result": "{\"verified\":false,\"reasonCode\":\"UPSTREAM_DISABLED\"}",
  "extracted_data": null,
  "created_at": "2026-05-07T..."
}
```

**에러**: `REVIEW_ADMIN_ONLY` 403 — ADMIN 외 호출.

---

## Notifications — `/api/notifications` (S-4 단계 4)

audit_logs(시스템 감사) 와 분리된 사용자 알림. 발신은 도메인 서비스에서 자동 트리거.

### 권한별 가시성

| 역할 | 보이는 알림 |
|---|---|
| `ADMIN` | 전체 |
| `BP` / `EQUIPMENT_SUPPLIER` / `MANPOWER_SUPPLIER` | `target_user_id == self` OR `target_company_id == 자기 회사` |
| `WORKER` | 직접 알림만 (회사 broadcast 도 회사 소속이면 보임) |

### `GET /api/notifications?page=&size=`

**Response 200**: `PageResponse<NotificationResponse>`

```jsonc
{
  "id": 1,
  "target_user_id": null,
  "target_company_id": 2,
  "site_id": null,
  "type": "DOCUMENT_OCR_REVIEW",
  "title": "운전면허증 OCR 검토 필요",
  "message": "운전면허증 자동 검증이 어려워 OCR 검토 큐로 들어갔습니다. 보충 입력 후 재검증해 주세요.",
  "link_type": "DOCUMENT",
  "link_id": 3,
  "read_at": null,
  "created_at": "2026-05-07T..."
}
```

### `GET /api/notifications/unread-count`

**Response 200**: `{ "unread": 2 }`

### `POST /api/notifications/{id}/read`

본인이 볼 수 있는 알림만 읽음 처리. `read_at` 채움.

**에러**: `NOTIFICATION_NOT_FOUND` 404 / `NOTIFICATION_DENIED` 403.

### 알림 type

| type | 발신 시점 | target |
|---|---|---|
| `DOCUMENT_VERIFIED` | 자동/수동 verify 결과가 VERIFIED | owner supplier (회사 broadcast) |
| `DOCUMENT_REJECTED` | verify 결과 / 수동 reject | owner supplier |
| `DOCUMENT_OCR_REVIEW` | verify 결과 OCR_REVIEW_REQUIRED (UPSTREAM_*) | owner supplier |
| `DOCUMENT_EXPIRING` | (정의만 — 스케줄러 미구현) | owner supplier |
| `DOCUMENT_EXPIRED` | (정의만) | owner supplier |
| `ASSIGNMENT_OVERRIDDEN` | 서류 미비 강제 배치 시 | owner supplier |

---

## Work Plans — `/api/work-plans` (Phase S-5)

작업계획서 = 현장의 일자별 작업 계획 + 배치할 장비/인원 + 자원 추가 시점의 서류 컴플라이언스 스냅샷.

### 권한별 가시성

| 역할 | 조회 | 생성/수정 | 자원 편집 | 상태 전이 |
|---|---|---|---|---|
| ADMIN | 전체 | 모든 BP 사이트 | 모든 plan | 모든 plan |
| BP | 자기 회사 plan | 자기 회사 owned site | 자기 회사 plan (DRAFT) | 자기 회사 plan |
| EQUIPMENT_SUPPLIER / MANPOWER_SUPPLIER | 자기 회사 자원이 포함된 plan | (불가) | (불가) | (불가) |

### `GET /api/work-plans?page=&size=`

상태 무관, 작업일 desc + id desc 페이지네이션.

**Response 200** — `PageResponse<WorkPlanSummary>`

### `GET /api/work-plans/{id}`

상세. equipment / persons / compliance_checks 포함.

### `POST /api/work-plans` — 생성

ADMIN 또는 BP (자기 회사 owned site).

**Request**
```json
{
  "site_id": 12,
  "work_date": "2026-05-08",
  "start_time": "08:00:00",
  "end_time": "17:00:00",
  "title": "5/8 토사 굴착",
  "work_location": "A동 지하 1층",
  "description": "..."
}
```

**Response 201** — WorkPlanResponse (status=DRAFT). `WORK_PLAN_CREATED` audit.

### `PATCH /api/work-plans/{id}/form-values` — 워크시트 폼 상태 저장 (S-9-B)

skep 원본 워크시트 schema 의 132 필드 + role_assign + 첨부 선택 ID 일괄 저장. DRAFT 상태에서만.

**Request**
```json
{
  "formValues": {
    "values": { "siteName": "...", "vehicleNo": "...", ... },
    "roleAssign": { "operator": [12, 17], "supervisor": [...], ... },
    "workSiteDiagramKey": "...",
    "equipDocIds": [1, 2, 3],
    "personDocIds": [10, 11]
  },
  "equipmentSupplierCompanyId": 2,
  "manpowerSupplierCompanyId": 5,
  "currentEquipmentId": 17
}
```

**Response 200** — `WorkPlanResponse` 상세. `WORK_PLAN_UPDATED` audit (`formValuesUpdated:true`).

**에러**
- 400 `WORK_PLAN_NOT_EDITABLE` — DRAFT 가 아닌 상태에서 호출.

### `PATCH /api/work-plans/{id}` — 수정

DRAFT 상태에서만. 모든 필드 optional.

### `POST /api/work-plans/{id}/clone` — 복제 (S-7)

원본의 헤더 + 자원(장비/인원) 을 새 DRAFT 로 복사. ADMIN/BP self-site 만.

복사 정책 (S-7.1 강화):
- supplier 가 사이트의 ACTIVE 참여 공급사가 아니면 그 자원은 skip.
- 자원별 컴플라이언스를 재평가하고 BLOCKED 면 skip (snapshot 도 안 만듦) — 새 plan 에 포함되지 않아 submit 우회 차단.
- 인원의 `equipment_id` 가 가리키는 장비가 이번 복제에서 skip 됐으면 매칭을 끊고 (`equipment_id=null`) 인원만 그대로 복사.
- audit `after_json` 에 7가지 카운트 (`copied_equipment`, `skipped_inactive_equipment`, `skipped_blocked_equipment`, `copied_person`, `skipped_inactive_person`, `skipped_blocked_person`, `dropped_equipment_match`) 기록.

**Request** (body 선택, 모두 optional)
```json
{ "work_date": "2026-05-09", "title": "5/9 토사 굴착 (재실행)" }
```
- `work_date` 기본값: 원본 work_date + 1일
- `title` 기본값: `[복사] ` + 원본 제목

**Response 201** — WorkPlanResponse 상세 (status=DRAFT). `WORK_PLAN_CLONED` audit.

### `POST /api/work-plans/{id}/equipment` — 장비 추가

DRAFT 상태에서만. equipment supplier 가 사이트의 ACTIVE 참여 공급사여야 함.

**Request**
```json
{ "equipment_id": 12, "purpose": "토사 굴착", "note": "...", "override": false, "override_reason": null }
```

**Response 201** — WorkPlanResponse 상세 (compliance_checks 에 새 스냅샷 1건 추가).

**400 / DOCUMENTS_BLOCKED** — `blocks_assignment=true` 필수 서류가 chain head VERIFIED+안만료 가 아니면.
ADMIN + `override=true` + `override_reason` 시 `OVERRIDDEN` 으로 통과 (audit 기록).

`WORK_PLAN_EQUIPMENT_ADDED` audit. compliance status 별로 OK/WARNING/OVERRIDDEN 스냅샷 저장.

### `DELETE /api/work-plans/{id}/equipment/{equipmentId}` — 장비 제거

DRAFT 상태에서만. `WORK_PLAN_EQUIPMENT_REMOVED` audit.

### `POST /api/work-plans/{id}/persons` — 인원 추가

같은 정책. 매칭 장비를 지정하려면 `equipment_id` 가 같은 plan 에 미리 추가되어 있어야 함.

**Request**
```json
{ "person_id": 88, "equipment_id": 12, "role": "OPERATOR", "note": "...", "override": false }
```

### `DELETE /api/work-plans/{id}/persons/{personId}`

### `POST /api/work-plans/{id}/submit` — 제출 (DRAFT → SUBMITTED)

자원 1건 이상 필수. ADMIN/BP. 제출 시 모든 자원의 컴플라이언스를 **현재 시점**으로 재평가 — clone 우회와 add 이후 만료된 서류 모두 차단.

자원별 가장 최근 `work_plan_compliance_checks` snapshot 이 `OVERRIDDEN` 인 자원은 ADMIN 이 이미 명시 승인했으므로 통과. 그 외에 현재 BLOCKED 인 자원이 있으면 `DOCUMENTS_BLOCKED_AT_SUBMIT` 400. 자원 제거 또는 ADMIN 강제 진행 재추가 필요.

`WORK_PLAN_SUBMITTED` audit.

### `POST /api/work-plans/{id}/approve` — 승인 (SUBMITTED → APPROVED)

ADMIN/BP. `WORK_PLAN_APPROVED` audit.

### `POST /api/work-plans/{id}/start` — 작업 시작 (APPROVED → IN_PROGRESS)

ADMIN/BP. `WORK_PLAN_STARTED` audit.

### `POST /api/work-plans/{id}/complete` — 작업 완료 (IN_PROGRESS → DONE)

ADMIN/BP. `WORK_PLAN_COMPLETED` audit.

### `GET /api/work-plans/{id}/candidates/equipment` — 추가 가능한 장비 후보

ADMIN/BP. 해당 plan 사이트의 ACTIVE `EQUIPMENT_SUPPLIER` 참여 공급사가 보유한 장비만 반환.
응답은 일반 `EquipmentResponse[]`. 프론트는 자원 추가 시 이 endpoint 만 사용해야 함 (전체 `/api/equipment` 노출 우회 차단).

### `GET /api/work-plans/{id}/candidates/persons` — 추가 가능한 인원 후보

ADMIN/BP. 해당 plan 사이트의 ACTIVE `MANPOWER_SUPPLIER` 참여 공급사 소속 인원만. 응답 `PersonResponse[]`.

## Worksheet — `/api/worksheet` (Phase S-9-C)

작업계획서 DOCX 의 서버측 PDF 변환 + 이메일 발송. DOCX 자체는 클라이언트 (`lib/worksheet/engine.ts`, pizzip + docxtemplater) 에서 생성.

### `POST /api/worksheet/to-pdf` — DOCX → PDF 변환

**Request** (multipart/form-data)
- `file` — DOCX 파일 (필수)
- `name` — 출력 baseName (선택, 기본 `worksheet`)

**Response 200** — `application/pdf` 바이너리, `Content-Disposition: attachment; filename*=UTF-8''<name>.pdf`.

**처리 흐름**
1. 임시 디렉토리 생성 + DOCX 저장.
2. `libreoffice --headless --convert-to pdf:writer_pdf_Export:{ImageResolution=150,Quality=80}` 실행 (UserInstallation 격리, 90초 타임아웃).
3. PDF 바이트 반환 + 임시 디렉토리 정리.

**에러**
- 500 `LibreOffice 변환 실패 (code=...)` — exit code != 0.
- 500 `LibreOffice 변환 타임아웃` — 90초 초과.

### `POST /api/worksheet/send-pdf` — PDF 메일 발송

**Request** (multipart/form-data)
- `file` — DOCX 파일 (필수)
- `to` — 받는 사람 이메일, 쉼표/세미콜론/공백 구분 다중 (필수)
- `from` — 답장 이메일 (선택, Reply-To 로만 사용. From 은 항상 `MAIL_USERNAME`)
- `subject` — 제목 (선택, 기본 `[SKEP] <baseName>`)
- `body` — 본문 (선택, 기본 빈 문자열)
- `name` — 첨부 파일명 baseName (선택)

**Response 200**
```json
{ "ok": true, "to": "manager@site.com", "pdfSize": 254893, "message": "메일 발송 완료" }
```

**에러**
- 400 `ok=false` — 받는 사람 누락 / SMTP 발신 계정 미설정 / `JavaMailSender` 빈 없음.
- 500 `ok=false` — 변환/발송 실패.

**환경 변수**
| 변수 | 용도 |
|---|---|
| `MAIL_HOST`, `MAIL_PORT` | SMTP 서버 |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | 발신 계정 (From 으로도 사용) |

미설정 시 `to-pdf` 만 동작하고 `send-pdf` 는 400 반환.

## Company Documents (S-9-G)

회사 단위 서류 (사업자 등록증, 통장 사본, 건설업 등록증, 4대보험 가입증명원). 기존 `/api/documents` API 재사용 — `ownerType=COMPANY` 로 호출.

### `GET /api/document-types?appliesTo=COMPANY`
회사 서류 종류 목록.

### `GET /api/documents?ownerType=COMPANY&ownerId={companyId}`
- 권한: ADMIN 전체 / EQUIPMENT_SUPPLIER, MANPOWER_SUPPLIER 자기 회사만 / BP 자기 사이트의 ACTIVE 참여공급사 회사만 read 가능
- `ownerId` 가 자기 회사가 아니면 403 `FORBIDDEN_OTHER_COMPANY`

### `POST /api/documents` (multipart) — `ownerType=COMPANY`
사업자 등록증 업로드 시 자동 검증 흐름:
1. `AutoVerifyTrigger` 발동 (document_type.requires_verification=true)
2. `NtsBizClient.lookupStatus(companies.business_number)` 호출
3. 결과:
   - 계속사업자 → VERIFIED
   - 휴업자/폐업자 → REJECTED (`rejected_reason=NTS_SUSPENDED|NTS_CLOSED`)
   - 키 미설정/외부 실패 → OCR_REVIEW_REQUIRED
4. `verification_result` JSONB 에 NTS 응답 raw 저장
5. 회사 사용자에게 알림 발송

### 환경 변수
| 변수 | 용도 |
|---|---|
| `NTS_SERVICE_KEY` | 공공데이터포털 사업자등록상태조회 API 인증 키. 미설정 시 verify-api fallback, 둘 다 없으면 OCR_REVIEW_REQUIRED |

## AI Rewrite — `/api/ai` (Phase S-9-F, stub)

### `POST /api/ai/rewrite` — 텍스트 재작성

워크시트 textarea 의 AI 재작성 버튼 — `field.aiPrompt` 와 현재 값을 보내 한 줄 재작성 받음.

**Request**
```json
{ "text": "철골 작업 시 추락 위험", "prompt": "한 문장으로 자연스럽게 다시 써줘" }
```

**Response 200**
```json
{ "ok": true, "value": "..." }
```

**에러**
- 503 `ok=false` — `ANTHROPIC_API_KEY` 미설정.

`ANTHROPIC_API_KEY` 가 있어도 현재는 echo (실제 Claude SDK 통합은 추후).

### `POST /api/work-plans/{id}/cancel` — 취소

DRAFT/SUBMITTED/APPROVED/IN_PROGRESS 어느 상태에서나. DONE/CANCELLED 는 거부.

**Request** `{ "reason": "발주처 일정 변경" }` (필수)

**Response** WorkPlanResponse (status=CANCELLED + cancel_reason). `WORK_PLAN_CANCELLED` audit.

### WorkPlanResponse 스키마

```json
{
  "id": 1,
  "site_id": 12,
  "site_name": "A현장",
  "bp_company_id": 3,
  "bp_company_name": "BP사",
  "work_date": "2026-05-08",
  "start_time": "08:00:00",
  "end_time": "17:00:00",
  "title": "5/8 토사 굴착",
  "work_location": "A동",
  "description": "...",
  "status": "DRAFT",
  "created_by": 5,
  "submitted_at": null,
  "approved_at": null,
  "cancelled_at": null,
  "cancel_reason": null,
  "created_at": "...",
  "updated_at": "...",
  "equipment": [
    { "id": 1, "equipment_id": 12, "equipment_name": "01가1234", "category": "EXCAVATOR",
      "supplier_company_id": 4, "supplier_company_name": "장비사", "purpose": "토사 굴착", "note": null, "created_at": "..." }
  ],
  "persons": [
    { "id": 1, "person_id": 88, "person_name": "홍길동",
      "supplier_company_id": 6, "supplier_company_name": "인력사",
      "equipment_id": 12, "role": "OPERATOR", "note": null, "created_at": "..." }
  ],
  "compliance_checks": [
    { "id": 1, "target_type": "EQUIPMENT", "target_id": 12,
      "status": "WARNING", "reason": "만료 임박 또는 검토 필요 서류 1건",
      "checked_at": "...", "override_by": null, "override_reason": null }
  ]
}
```

### 에러

| code | status | 의미 |
|---|---|---|
| `WORK_PLAN_NOT_FOUND` | 404 | id 없음 |
| `WORK_PLAN_DENIED` | 403 | 권한 부족 |
| `WORK_PLAN_VIEW_DENIED` | 403 | 조회 권한 부족 |
| `WORK_PLAN_NOT_EDITABLE` | 400 | DRAFT 가 아님 |
| `INVALID_TRANSITION` | 400 | 상태 전이 불가 |
| `NO_RESOURCES` | 400 | 제출 시 자원 0건 |
| `SUPPLIER_NOT_PARTICIPANT` | 400 | 자원 supplier 가 ACTIVE 참여공급사 아님 |
| `DOCUMENTS_BLOCKED` | 400 | 필수 서류 미비 (override 가능) — 자원 추가 시 |
| `DOCUMENTS_BLOCKED_AT_SUBMIT` | 400 | 제출 시 자원의 현재 서류 상태가 BLOCKED. OVERRIDDEN 이력이 없으면 막힘. |
| `OVERRIDE_ADMIN_ONLY` | 403 | ADMIN 외 강제 진행 시도 |
| `OVERRIDE_REASON_REQUIRED` | 400 | override 사유 누락 |
| `EQUIPMENT_NOT_IN_PLAN` | 400 | 매칭 장비가 plan 에 없음 |
| `ALREADY_ADDED` | 400 | 이미 추가된 자원 |

---

## 추가 도메인 (S-10 ~ Phase Bid · compliance-orders V46)

> 2026-06-01 코드 기준 전수 점검으로 보강. 전역 네이밍은 snake_case 이나, 일부 엔드포인트는 컨트롤러가 raw `Map` 에서 키를 직접 읽어 camelCase(`pngBase64`, `resultNotes`)나 혼합(`person_id`)을 쓴다 — 각 엔드포인트에 표기. OnlyOffice/Worksheet config 응답도 DocsAPI 규격 키(camelCase)라 변환 대상이 아니다.

---

## 견적 (Quotation) — `/api/quotations`

장비/인력 견적 요청 도메인 (S-10, V33+). 한 견적은 **자원 종류**(`request_type`)와 **발송 방식**(`mode`)으로 분류된다.

- **`mode`**: `TARGETED` = 지정배차(특정 공급사·자원을 지목해 발송, target 행 생성) / `OPEN_BID` = 공개입찰(플랫폼 게시판에 게시, target 없이 공급사가 제안 제출). `POST /` 는 항상 `TARGETED`, `POST /open-bid` 는 항상 `OPEN_BID`.
- **`request_type`**: `EQUIPMENT`(장비) / `MANPOWER`(인력). `EQUIPMENT` 면 `equipment_category` 필수, `MANPOWER` 면 `manpower_role` 필수.
- **`bundle_id`** (UUID): 한 번의 발송에서 장비+인력 N역할을 묶을 때 공유하는 UUID. 사용자 화면은 같은 `bundle_id` 묶음을 "견적 1건"으로 본다(`/bundles` 계열). 단건 발송이면 `null`.
- **site-free**: 견적 단계에서는 현장(`site_id`)이 없어도 된다. 모든 생성 경로가 `site_id=null` 로 저장하며, 발신 BP 식별은 `bp_company_id` 직접 컬럼으로 한다(V35).

### 권한 규칙

컨트롤러에 `@PreAuthorize` 없음 — 모든 권한은 서비스 내부에서 검증한다.

- **후보 조회·생성·번들·취소·삭제·최종선정**: `ensureBpOrAdmin` / `ensureCanFinalize` 로 ADMIN 또는 BP만. BP는 회사 소속 필수, 견적의 `bp_company_id` 가 본인 `company_id` 와 일치해야 함.
- **ADMIN 대행**: ADMIN 이 생성하려면 `on_behalf_of_bp_company_id` 필수 (create). BP 본인 발송은 `null` 허용.
- **응답(respond)**: ADMIN 또는 해당 target 의 소속 공급사(`company_id` 일치)만.
- **조회(get/list/bundles)**: ADMIN 전체 / BP 본인 회사 견적 / 공급사는 TARGETED 면 자기 target 있는 견적만, OPEN_BID 면 게시판 전체. 공급사 시점 상세에서는 `targets` 가 자기 회사 행만 노출됨.

### `GET /api/quotations/equipment-candidates` — 장비 견적 후보 공급사·장비 풀 [BP/ADMIN]

현장 무관. 카테고리 매칭되는 전체 `EQUIPMENT` 공급사와 그 장비. 컴플라이언스 `readyForWorkPlan()` 통과 공급사/장비만(BLOCKED 제외) (`QuotationService.java:104`).

**Query 파라미터**: `category` (선택, `EquipmentCategory` enum — 미지정 시 전체)

**Response 200** (공급사별 그룹 배열) (`QuotationCandidateResponse.java:8`)
```json
[
  {
    "supplier_id": 12,
    "supplier_name": "대한중기(주)",
    "equipments": [
      {
        "id": 305,
        "vehicle_no": "12가3456",
        "model": "ZX210",
        "manufacturer": "현대건설기계",
        "year": 2021,
        "category": "EXCAVATOR",
        "serial_number": "HX-0099",
        "has_photo": true,
        "current_site_id": 7,
        "current_site_name": "강남현장"
      }
    ]
  }
]
```

### `GET /api/quotations/manpower-candidates` — 인력 견적 후보 공급사·인원 풀 [BP/ADMIN]

현장 무관. 역할 매칭되는 전체 `MANPOWER` 공급사와 그 인원. 컴플라이언스 통과 인원만 (`QuotationService.java:150`).

**Query 파라미터**: `role` (선택, `PersonRole` enum — 미지정 시 전체)

**Response 200** (`QuotationManpowerCandidateResponse.java:9`): `supplier_id`/`supplier_name` + `persons[]` (`id`, `name`, `job_title`, `phone`, `employee_no`, `roles`[`PersonRole` 집합], `has_photo`).

### `POST /api/quotations` — 견적 요청 생성 (TARGETED) [BP/ADMIN]

지정한 공급사·자원에게 견적 발송. target 다중 생성 + 공급사 알림. `mode` 는 항상 `TARGETED` 저장 (`QuotationService.java:269`). 같은 `supplier+resource` 중복 target 은 자동 제거.

**Request** (`CreateQuotationRequest.java:22`, `@JsonProperty` 미부착 → 전역 SNAKE_CASE)
```json
{
  "site_id": null,
  "work_period_start": "2026-07-10",
  "work_period_end": "2026-07-15",
  "request_type": "EQUIPMENT",
  "equipment_category": "EXCAVATOR",
  "manpower_role": null,
  "spec_text": "0.8㎥급 이상",
  "proposed_daily_rate": 350000,
  "proposed_monthly_rate": null,
  "count": 2,
  "notes": "유압브레이커 포함",
  "on_behalf_of_bp_company_id": null,
  "bundle_id": null,
  "targets": [
    { "supplier_company_id": 12, "equipment_id": 305, "person_id": null }
  ]
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| site_id | Long | | 견적 단계 미정 — 항상 null 저장(서버가 무시) |
| work_period_start / work_period_end | LocalDate | O | start ≤ end |
| request_type | enum | | `EQUIPMENT`(기본)/`MANPOWER` |
| equipment_category | enum | 조건부 | `request_type=EQUIPMENT` 면 필수 |
| manpower_role | enum | 조건부 | `request_type=MANPOWER` 면 필수 |
| spec_text | string | | 4000자 이하 |
| proposed_daily_rate / proposed_monthly_rate | Integer | | BP 제안 단가 |
| count | Integer | | 수량 (null=1) |
| notes | string | | 4000자 이하 |
| on_behalf_of_bp_company_id | Long | 조건부 | ADMIN 대행 시 필수, BP 본인 null |
| bundle_id | UUID | | 묶음 키 (단건 null) |
| targets | array | O | 최소 1개. `supplier_company_id` 필수, `equipment_id`/`person_id` 종류별 선택 |

**Response 201**: `QuotationRequestResponse`. target 검증: 자원이 지정 공급사 소속 + 카테고리/역할 일치.

**에러**: `BP_ADMIN_ONLY`(403), `ON_BEHALF_REQUIRED`(400), `INVALID_PERIOD`(400), `TARGETS_REQUIRED`(400), `EQUIPMENT_CATEGORY_REQUIRED`/`MANPOWER_ROLE_REQUIRED`(400), `EQUIPMENT_NOT_FOUND`/`PERSON_NOT_FOUND`(400), `EQUIPMENT_SUPPLIER_MISMATCH`/`PERSON_SUPPLIER_MISMATCH`(400), `EQUIPMENT_CATEGORY_MISMATCH`/`PERSON_ROLE_MISMATCH`(400).

### `POST /api/quotations/open-bid` — 공개입찰 생성 [BP/ADMIN]

현장 없이 시작 가능. `mode=OPEN_BID` 저장, target 생성 안 함 — 공급사가 게시판에서 제안 제출 (`QuotationService.java:387`). `email_recipients` 지정 시 외부 이메일 안내 메일 발송(실패해도 견적은 생성).

**Request** (`CreateOpenBidRequest.java:19`, 모든 필드 `@JsonProperty` 부착 — 와이어 키 아래 그대로)
```json
{
  "request_type": "EQUIPMENT",
  "equipment_category": "CRANE",
  "manpower_role": null,
  "client_org_id": 3,
  "work_location_text": "삼성 평택캠퍼스 P3",
  "spec_text": "50톤 이상",
  "proposed_daily_rate": null,
  "proposed_monthly_rate": null,
  "work_period_start": "2026-08-01",
  "work_period_end": "2026-08-31",
  "count": 1,
  "notes": "야간 작업 포함",
  "on_behalf_of_bp_company_id": null,
  "email_recipients": "a@vendor.com, b@vendor.com"
}
```

`client_org_id`(원청기관, 없으면 "협의"), `work_location_text`(1000자), `email_recipients`(2000자, CSV/줄바꿈) 외 필드는 `POST /` 와 동일.

**Response 201**: `QuotationRequestResponse` (`mode="OPEN_BID"`, `targets=[]`).

**에러**: `REQUEST_DENIED`(403), `INVALID_PERIOD`(400), `EQUIPMENT_CATEGORY_REQUIRED`/`MANPOWER_ROLE_REQUIRED`(400).
<!-- 확인필요: open-bid 는 ADMIN 대행 시 on_behalf_of_bp_company_id 누락을 막지 않음(create 와 달리 검사 없음). null 이면 bp_company_id = actor.companyId() 로 저장 (QuotationService.java:403-405). -->

### `GET /api/quotations/open-bids` — 공개입찰 게시판 [로그인]

`mode=OPEN_BID` + `status=SENT` 견적만. 공급사 역할별 자동 필터(EQUIPMENT_SUPPLIER → EQUIPMENT, MANPOWER_SUPPLIER → MANPOWER). ADMIN/BP 전체 (`QuotationService.java:491`). **Response 200**: `QuotationRequestResponse[]` (summary).

### `POST /api/quotations/bundle` — 현장 묶음 생성 [BP/ADMIN]

장비 1건 + 인력 N역할을 한 트랜잭션·같은 `bundle_id` 로 발송 (`QuotationService.java:204`). 내부적으로 각 항목을 `create()` 로 저장. 인력/역할 중복 차단.

**Request** (`CreateQuotationBundleRequest.java:20`, 전역 SNAKE_CASE)
```json
{
  "work_period_start": "2026-07-10",
  "work_period_end": "2026-07-15",
  "notes": "강남현장 1차",
  "on_behalf_of_bp_company_id": null,
  "equipment": {
    "category": "EXCAVATOR",
    "spec_text": "0.8㎥급",
    "proposed_daily_rate": 350000,
    "count": 1,
    "targets": [ { "supplier_company_id": 12, "equipment_id": 305 } ]
  },
  "manpower": [
    {
      "role": "SIGNALER",
      "proposed_daily_rate": 180000,
      "count": 1,
      "targets": [ { "supplier_company_id": 22, "person_id": 88 } ]
    }
  ]
}
```

`equipment`(장비 항목)와 `manpower`(역할 항목 배열) 중 최소 1개. 각 `targets[]` 는 `supplier_company_id` + (`equipment_id`|`person_id`) 필수.

**Response 201**: `QuotationRequestResponse[]` (모두 같은 `bundle_id`).

**에러**: `BUNDLE_EMPTY`(400), `DUPLICATE_ROLE`(400), `DUPLICATE_PERSON`(400) + `create()` 에러 전부 상속.

### `GET /api/quotations` — 견적 목록 [로그인]

역할별 가시성 (`QuotationService.java:510`). summary(`targets=[]`). **Response 200**: `QuotationRequestResponse[]`.

### `GET /api/quotations/{id}` — 견적 상세 [로그인]

조회 권한 검증. 공급사 시점이면 `targets` 가 자기 회사 행만 노출 (`QuotationService.java:531, 889`). **Response 200**: `QuotationRequestResponse` (detail).

**에러**: `QUOTATION_NOT_FOUND`(404), `VIEW_DENIED`(403), `NO_COMPANY`(403).

#### `QuotationRequestResponse` 스키마 (`QuotationRequestResponse.java:15`)

핵심 필드: `id`, `site_id`/`site_name`(보통 null), `bp_company_id`/`bp_company_name`, `requested_by_user_id`/`_name`, `on_behalf_of_bp_company_id`, `work_period_start`/`_end`, `request_type`, `equipment_category`, `manpower_role`, `spec_text`, `proposed_daily_rate`/`_monthly_rate`, `count`, `notes`, `status`(`QuotationStatus`), `bundle_id`, `mode`, `client_org_id`/`_name`, `work_location_text`, `created_at`/`updated_at`, `targets[]`.

`targets[]` (detail 응답에서만 채워짐): `id`, `supplier_company_id`/`_name`, `equipment_id`/`equipment_label`, `person_id`/`person_label`, `status`(`QuotationTargetStatus`), `responded_by_user_id`/`responded_at`/`response_note`, `finalized_by_user_id`/`finalized_at`, `finalized_to_work_plan_id`/`finalized_to_wpe_id` — **항상 null (死 필드)** [^finalize].

### `POST /api/quotations/{id}/targets/{targetId}/respond` — 공급사 수락/거부 [공급사/ADMIN]

target 소속 공급사(또는 ADMIN)가 BP 제안에 응답. target `PENDING` + 견적 `SENT` 여야 함 (`QuotationService.java:543`). `accept=true`→`ACCEPTED`, `false`→`REJECTED`.

**Request** (`RespondQuotationTargetRequest.java:7`): `{ "accept": true, "note": "투입 가능합니다" }` (`accept` 필수, `note` 1000자)

**Response 200**: `QuotationRequestResponse` (detail).

**에러**: `QUOTATION_NOT_FOUND`/`TARGET_NOT_FOUND`(404), `TARGET_REQUEST_MISMATCH`(400), `RESPOND_DENIED`(403), `TARGET_NOT_PENDING`(400), `REQUEST_NOT_OPEN`(400).
<!-- 확인필요: 수락/거부 시 BP 알림이 site.getBpCompanyId() 로 발송되는데 site_id 가 항상 null → site=null → bpCompanyId=null 이 되어 BP 알림이 실질적으로 발송되지 않음 (QuotationService.java:575-588). audit 기록은 정상. -->

### `POST /api/quotations/{id}/targets/{targetId}/finalize` — BP/ADMIN 최종 선정 [BP/ADMIN]

`ACCEPTED` target 을 `FINAL_ACCEPTED` 로 (`QuotationService.java:602`). PENDING/ACCEPTED target 이 더 없으면 견적을 `CLOSED` 로.

**중요 — 작업계획서 자동 생성 없음**: finalize 는 target 만 `FINAL_ACCEPTED` 로 바꾸며 **작업계획서나 자원을 자동 생성·연결하지 않는다.** 선정 후 작업계획서는 BP가 별도로 직접 작성한다. 코드상 `markFinalAccepted(actor.id(), null, null)` 로 호출되어 `finalized_to_*` 는 항상 null, 알림 문구도 "작업계획서는 BP가 별도로 작성합니다." (`QuotationService.java:618, 630`). [^finalize]

**Request**: 본문 없음. **Response 200**: `QuotationRequestResponse` (detail).

**에러**: `QUOTATION_NOT_FOUND`/`TARGET_NOT_FOUND`(404), `TARGET_REQUEST_MISMATCH`(400), `FORBIDDEN_OTHER_BP`/`FINALIZE_DENIED`(403), `TARGET_NOT_ACCEPTED`(400).

### `GET /api/quotations/bundles` — 묶음 목록 [로그인]

같은 `bundle_id` 끼리 그룹핑. `bundle_id=null` 은 `solo-{id}` 단건 묶음 처리 (`QuotationService.java:646`). **Response 200**: `QuotationBundleResponse[]`.

### `GET /api/quotations/bundles/{bundleId}` — 묶음 상세 [로그인]

묶음 내 본인 권한 있는 row 만 필터 (`QuotationService.java:678`). **Response 200**: `QuotationBundleResponse`. **에러**: `BUNDLE_NOT_FOUND`(404), `VIEW_DENIED`(403).

#### `QuotationBundleResponse` 스키마 (`QuotationBundleResponse.java:17`)

핵심 필드: `bundle_id`, `site_id`/`site_name`(null), `bp_company_id`/`_name`, `requested_by_user_id`/`_name`, `on_behalf_of_bp_company_id`, `work_period_start`/`_end`, `notes`, `aggregate_status`(하나라도 SENT면 SENT/모두 CANCELLED면 CANCELLED/모두 CLOSED면 CLOSED), `total_targets`, `responded_count`, `accepted_count`, `finalized_count`, `proposal_count`, `pending_proposal_count`, `first_work_plan_id`(**항상 null** [^finalize]), `created_at`/`updated_at`, `items[]`(`QuotationRequestResponse` detail 배열).

### `POST /api/quotations/bundles/{bundleId}/cancel` — 묶음 취소 [BP/ADMIN]

묶음 내 `SENT` 견적을 모두 `CANCELLED` 로 (`QuotationService.java:708`). **Response 200**: `QuotationBundleResponse`. **에러**: `BUNDLE_NOT_FOUND`(404), `FORBIDDEN_OTHER_BP`/`FINALIZE_DENIED`(403).

### `DELETE /api/quotations/bundles/{bundleId}` — 묶음 삭제 [BP/ADMIN]

묶음 내 모든 견적+targets 제거 (`QuotationService.java:718`, FINAL_ACCEPTED 있으면 거부). **Response 204**. **에러**: `BUNDLE_NOT_FOUND`(404), `ALREADY_FINALIZED`(400), 권한 403.

### `POST /api/quotations/{id}/cancel` — 견적 취소 [BP/ADMIN]

`CANCELLED` 로(이력 유지). 이미 CLOSED/CANCELLED 면 그대로 반환 (`QuotationService.java:805`). **Response 200**: `QuotationRequestResponse`. **에러**: `QUOTATION_NOT_FOUND`(404), `FORBIDDEN_OTHER_BP`/`FINALIZE_DENIED`(403).

### `DELETE /api/quotations/{id}` — 견적 완전 삭제 [BP/ADMIN]

targets(+OPEN_BID면 proposals)와 함께 제거. FINAL_ACCEPTED target/proposal 있으면 거부 (`QuotationService.java:779`). **Response 204**. **에러**: `QUOTATION_NOT_FOUND`(404), 권한 403, `ALREADY_FINALIZED`(400), `PROPOSAL_FINALIZED`(400).

### enum

- `QuotationStatus`: `DRAFT`(미사용) / `SENT` / `CLOSED` / `CANCELLED`
- `QuotationTargetStatus`: `PENDING` / `ACCEPTED` / `REJECTED` / `FINAL_ACCEPTED` / `EXPIRED`(미사용)
- `QuotationRequestType`: `EQUIPMENT` / `MANPOWER`
- `QuotationMode`: `OPEN_BID` / `TARGETED`

[^finalize]: **선정 후 작업계획서 미연결 정책** — TARGETED finalize(`QuotationService.finalize`)와 OPEN_BID finalize(`QuotationProposalService.finalize`) 모두 `markFinalAccepted(..., null, ...)` 로 호출되어 `finalized_to_work_plan_id`/`finalized_to_wpe_id`/`finalized_to_wpp_id` 등 작업계획서 연결 필드는 현재 항상 `null` 인 死 필드다. 선정은 상태 전이만 수행하고, 작업계획서는 BP가 별도 단계에서 직접 작성한다.

---

## 공개입찰 제안 (Quotation Proposal) — `/api/quotations`

공개입찰(`mode=OPEN_BID`) 견적에 공급사가 자기 보유 자원으로 제출하는 응찰. TARGETED 견적에는 사용 불가(`NOT_OPEN_BID`).

### 권한 규칙

- **제출·수정·철회**: 공급사만, 본인 회사 자원·제안만 (`QuotationProposalService.java:309, 155`).
- **최종선정·close**: ADMIN 또는 해당 견적의 BP 회사(`bp_company_id` 일치). 같은 BP 회사 직원도 가능.
- **목록**: `GET /{requestId}/proposals` 는 ADMIN·같은 BP 회사 전체, 공급사는 자기 제안만. `GET /proposals/mine` 는 본인 회사 제안 전체.

### `POST /api/quotations/{requestId}/proposals` — 제안 제출 [공급사]

OPEN_BID + `SENT` 견적에 자기 장비 또는 인원 1개로 제안. 같은 공급사 활성 제안 보유 시 차단 (`QuotationProposalService.java:70`).

**Request** (`CreateProposalRequest.java:7`, `@JsonProperty` 부착)
```json
{ "equipment_id": 410, "person_id": null, "daily_rate": 320000, "monthly_rate": null, "note": "즉시 투입 가능" }
```
`equipment_id`/`person_id` 중 하나(견적 종류에 맞춰). 동시 지정 불가.

**Response 201**: `ProposalResponse`.

**에러**: `QUOTATION_NOT_FOUND`(404), `NOT_OPEN_BID`(400), `SUPPLIER_ONLY`/`NO_COMPANY`(403), `RESOURCE_REQUIRED`(400), `AMBIGUOUS_RESOURCE`(400), `REQUEST_NOT_OPEN`(400), `PROPOSAL_ALREADY_EXISTS`(400), `EQUIPMENT_REQUIRED`/`PERSON_REQUIRED`(400), `EQUIPMENT_NOT_FOUND`/`PERSON_NOT_FOUND`(400), `EQUIPMENT_NOT_OWNED`/`PERSON_NOT_OWNED`(403), `EQUIPMENT_CATEGORY_MISMATCH`/`PERSON_ROLE_MISMATCH`(400), `PROPOSAL_DUPLICATE`(400).

### `GET /api/quotations/{requestId}/proposals` — 견적의 제안 목록 [로그인]

ADMIN·같은 BP 회사 전체, 공급사는 자기 제안만 (`QuotationProposalService.java:184`). **Response 200**: `ProposalResponse[]`. **에러**: `QUOTATION_NOT_FOUND`(404).

### `GET /api/quotations/proposals/mine` — 내 제안 목록 [공급사]

본인 회사 제안 전체(`company_id` 없으면 빈 배열). **Response 200**: `ProposalResponse[]`.

### `PATCH /api/quotations/proposals/{proposalId}` — 제안 수정 [공급사]

본인 회사 제안만. FINAL_ACCEPTED/REJECTED/WITHDRAWN 수정 불가. null 아닌 필드만 갱신. `PENDING_REVIEW` → 수정 시 `SUBMITTED` 복귀 (`QuotationProposalService.java:153`).

**Request** (`UpdateProposalRequest.java:3`, 전역 SNAKE_CASE): `{ "daily_rate": 300000, "monthly_rate": null, "note": "단가 조정" }` (null=미변경)

**Response 200**: `ProposalResponse`. **에러**: `PROPOSAL_NOT_FOUND`(404), `UPDATE_DENIED`(403), `PROPOSAL_LOCKED`(400).

### `POST /api/quotations/proposals/{proposalId}/withdraw` — 제안 철회 [공급사]

본인 회사 제안을 `WITHDRAWN` 으로. FINAL_ACCEPTED 철회 불가 (`QuotationProposalService.java:168`). **Response 200**: `ProposalResponse`. **에러**: `PROPOSAL_NOT_FOUND`(404), `WITHDRAW_DENIED`(403), `PROPOSAL_FINALIZED`(400).

### `POST /api/quotations/proposals/{proposalId}/finalize` — 제안 최종 선정 [BP/ADMIN]

제안을 `FINAL_ACCEPTED` 로. 비관적 락으로 동시성 보호. 견적 `SENT` 필수, 이미 선정 수가 `count` 도달 시 거부. 수량 가득 차면 남은 제안 자동 거절. 선정 시점 비교증거 snapshot 자동 갱신 (`QuotationProposalService.java:211`).

**작업계획서 자동 생성 없음**: TARGETED finalize 와 동일하게 `markFinalAccepted(actor.id(), null, null, null)` — 작업계획서/자원 미연결 (`QuotationProposalService.java:234, 239`). [^finalize]

**Request**: 본문 없음. **Response 200**: `ProposalResponse`. **에러**: `PROPOSAL_NOT_FOUND`/`QUOTATION_NOT_FOUND`(404), `NOT_REQUEST_OWNER`(403), `PROPOSAL_NOT_FINALIZABLE`(400), `REQUEST_NOT_OPEN`(400), `COUNT_FULL`(400).

### `POST /api/quotations/{requestId}/close` — 견적 마감 [BP/ADMIN]

남은 활성 제안(SUBMITTED/PENDING_REVIEW) 모두 자동 거절 + 견적 `CLOSED` (`QuotationProposalService.java:255`). **Request**: 본문 없음. **Response 200**: 본문 없음. **에러**: `QUOTATION_NOT_FOUND`(404), `NOT_REQUEST_OWNER`(403).

#### `ProposalResponse` 스키마 (`ProposalResponse.java:15`)

핵심 필드: `id`, `request_id`, `supplier_company_id`/`_name`, `proposed_by_user_id`, `equipment_id`/`equipment_label`, `person_id`/`person_label`, `daily_rate`/`monthly_rate`, `note`, `status`(`QuotationProposalStatus`), `created_at`/`finalized_at`/`rejected_at`, 그리고 견적 요약(`request_bp_company_id`/`_name`, `request_requested_by_user_id`/`_name`, `request_type`, `request_equipment_category`/`request_manpower_role`, `request_work_period_start`/`_end`, `request_status`, `request_mode`).

- `QuotationProposalStatus`: `SUBMITTED`(제출·검토대기) / `PENDING_REVIEW`(BP가 spec 수정 → 재확인) / `FINAL_ACCEPTED` / `REJECTED` / `WITHDRAWN`

---

## 비교 증거 스냅샷 (Comparison Snapshot)

공개입찰 제안 최종 선정 시점에 모든 응찰(가격·노트·제출시점)을 JSON 으로 동결한 영구 보존 증거. 한 견적당 1개, finalize 마다 delete+insert 갱신 (`ComparisonSnapshotService.java:34`). 조회 전용(생성 엔드포인트 없음 — finalize 가 자동 호출).

### `GET /api/companies/{companyId}/comparison-snapshots` — 회사별 목록 [ADMIN/BP 본인]

ADMIN 전체, BP 는 `companyId == 본인 company_id` 일 때만 (`ComparisonSnapshotController.java:23`). **Response 200**: `ComparisonSnapshotResponse[]`. **에러**: `NOT_PERMITTED`(403).

### `GET /api/quotations/{requestId}/comparison-snapshot` — 견적별 단건 [ADMIN/소유 BP]

ADMIN 전체. BP 는 견적 `bp_company_id`/`on_behalf_of_bp_company_id` 가 본인 `company_id` 와 일치할 때 (`ComparisonSnapshotController.java:37`). **Response 200**: `ComparisonSnapshotResponse`. **에러**: `NO_SNAPSHOT`(404), `REQUEST_NOT_FOUND`(404), `NO_COMPANY`(403), `NOT_PERMITTED`(403).

#### `ComparisonSnapshotResponse` 스키마 (`ComparisonSnapshotResponse.java:7`)

| 필드 | 타입 | 설명 |
|---|---|---|
| id | Long | 스냅샷 ID |
| quotation_request_id | Long | 대상 견적 |
| selected_proposal_id | Long | 선정 제안 |
| selected_at | LocalDateTime | 선정(동결) 시각 |
| snapshot_json | string | 응찰 전체 직렬화 JSON. **내부 키는 camelCase**(`proposalId`/`supplierId`/`supplierName`/`status`/`dailyRate`/`monthlyRate`/`note`/`submittedAt`) — 서버가 직접 만든 raw JSON 이라 전역 SNAKE_CASE 미적용 |
| selection_reason | string | 현재 finalize 가 항상 null 전달 |

<!-- 확인필요: snapshot 주석은 "WITHDRAWN 제외"라고 적혀 있으나 captureForRequest 는 findByRequestIdOrderByIdDesc 전체를 직렬화(status 필터 없음) — WITHDRAWN/REJECTED 도 entries 에 포함됨 (ComparisonSnapshotService.java:35,42). -->

---

## 견적 배차·금액·문서묶음 — `/api/quotations/{requestId}/*`

> 선정(`FINAL_ACCEPTED`) 받은 공급사가 차량·인원·단가를 송부하고, 견적서(xlsx/pdf)·비교표를 생성하며, 서류 묶음을 BP에 보내는 흐름. 컨트롤러에 `@PreAuthorize` 없음 — 권한은 서비스 내부 actor 검증. 대상 DTO에 `@JsonProperty` 오버라이드 없음(전역 SNAKE_CASE).

### `POST /api/quotations/{requestId}/dispatched` — 차량/단가 송부 [공급사/ADMIN]

선정된 장비공급사가 차량 다중 선택 + 단가, 또는 차량 미지정 단가만 송부. 견적당 공급사 1회 멱등 (`DispatchedEquipmentService.java:41`).

**권한 규칙** (`DispatchedEquipmentService.java:51-77`): actor role `EQUIPMENT_SUPPLIER`/`ADMIN` (아니면 `SUPPLIER_ONLY` 403). 공급사 식별 — 본인은 `actor.companyId()`, ADMIN은 차량 지정 모드에서 `items[0].equipment_id` 의 장비 `supplier_id`(단가-only 대행 시 식별 불가 → `SUPPLIER_REQUIRED` 400). 해당 공급사가 `FINAL_ACCEPTED` 아니면 `NOT_SELECTED`(400). 멱등: 재송부 시 `ALREADY_DISPATCHED`(409), DB UNIQUE `(quotation_request_id, equipment_id)` 이중 보장.

**두 모드**: ① 단가 모드(기본) — `items` 비우고 top-level 단가, `equipment_id=null` 1행. ② 차량 지정 모드(구버전 호환) — `items` 에 차량별 단가, 각 장비는 본인 회사 소속이어야(`EQUIPMENT_NOT_OWNED` 403).

**Request — 단가 모드**
```json
{ "daily_price": 350000, "ot_daily_price": 45000, "monthly_price": 7000000, "ot_monthly_price": 40000, "notes": "유류비 별도" }
```
**Request — 차량 지정 모드**
```json
{ "items": [ { "equipment_id": 12, "daily_price": 350000, "ot_daily_price": 45000, "monthly_price": 7000000, "ot_monthly_price": 40000, "notes": "차량별 비고" } ], "notes": "공통 비고" }
```

| 필드 | 타입 | 설명 |
|---|---|---|
| items | object[] | 차량 지정 모드용. 비거나 null이면 단가 모드 |
| items[].equipment_id | long | 필수(@NotNull) |
| items[].daily_price / ot_daily_price / monthly_price / ot_monthly_price | long? | 일대/OT일대/월대/OT월대(원) |
| items[].notes | string? | 차량별 비고(없으면 top-level notes) |
| daily_price / ot_daily_price / monthly_price / ot_monthly_price | long? | 단가 모드 단가 |
| notes | string? | 공통 비고 |

단가 모드에서 단가 4개 모두 null이면 `EMPTY_RATES`(400).

**Response 200** — `DispatchedEquipmentResponse[]`. 성공 시 BP 회사에 `QUOTATION_DISPATCH` 알림.

**에러**: `EMPTY_RATES`(400), `SUPPLIER_ONLY`(403), `REQUEST_NOT_FOUND`(404), `EQUIPMENT_NOT_FOUND`(400), `SUPPLIER_REQUIRED`(400), `NOT_SELECTED`(400), `EQUIPMENT_NOT_OWNED`(403), `ALREADY_DISPATCHED`(409).

### `GET /api/quotations/{requestId}/dispatched` — 송부된 차량 조회 [ADMIN/BP/공급사]

권한 (`DispatchedEquipmentService.java:191-202`): ADMIN 전체 / BP 본인 회사 견적 / 공급사는 `FINAL_ACCEPTED` 본인 회사만(경쟁사 가격 노출 방지). 미해당 `NOT_PERMITTED`(403)/`NO_COMPANY`(403).

**Response 200** — `DispatchedEquipmentResponse[]` (`DispatchedEquipmentResponse.java:7`)
```json
[ { "id": 1, "quotation_request_id": 10, "supplier_company_id": 2, "supplier_company_name": "테스트 장비공급(주)", "equipment_id": 12, "equipment_label": "경기99사1234", "equipment_category": "EXCAVATOR", "daily_price": 350000, "ot_daily_price": 45000, "monthly_price": 7000000, "ot_monthly_price": 40000, "notes": "유류비 별도", "sent_at": "2026-05-12T13:00:25" } ]
```
`equipment_id` 는 단가 모드면 null, `equipment_label` 은 차량번호→모델→`단가 응답` 폴백, `equipment_category` 는 `EquipmentCategory`(장비 없으면 null).

### `POST /api/quotations/{requestId}/dispatched-persons` — 인원/단가 송부 [공급사/ADMIN]

인원 다중 선택 + 인당 단가. 견적당 공급사 1회 멱등 (`DispatchedPersonService.java:41`). 권한: role `EQUIPMENT_SUPPLIER`/`MANPOWER_SUPPLIER`/`ADMIN`. 각 인원 본인 회사 소속(`PERSON_NOT_OWNED` 403). DB UNIQUE `(quotation_request_id, person_id)`.

**Request**
```json
{ "items": [ { "person_id": 5, "daily_price": 200000, "monthly_price": 4500000, "notes": "야간 가능" } ], "notes": "공통 비고" }
```
`items` 필수(@NotEmpty). `items[].person_id` 필수. 인원 단가는 **일대/월대 2종만**(장비와 달리 OT 없음).

**Response 200** — `DispatchedPersonResponse[]` (`person_label`=인원명 또는 `#id`, `job_title`). 성공 시 BP에 `QUOTATION_DISPATCH` 알림.

**에러**: `EMPTY_ITEMS`(400), `SUPPLIER_ONLY`(403), `REQUEST_NOT_FOUND`(404), `PERSON_NOT_FOUND`(400), `NOT_SELECTED`(400), `PERSON_NOT_OWNED`(403), `ALREADY_DISPATCHED`(409).

### `GET /api/quotations/{requestId}/dispatched-persons` — 송부된 인원 조회 [ADMIN/BP/공급사]

권한은 차량 조회와 동일. **Response 200** — `DispatchedPersonResponse[]` (`id`, `quotation_request_id`, `supplier_company_id`/`_name`, `person_id`, `person_label`, `job_title`, `daily_price`, `monthly_price`, `notes`, `sent_at`).

### `POST /api/quotations/{requestId}/document-bundle` — 서류 묶음 송부 [공급사/ADMIN]

공급사가 dispatched 차량의 서류를 BP에 명시적으로 송부. 견적당 1회 멱등. `include_email=true` 면 BP 관리자 이메일로 견적서 PDF + 차량별 서류 원본 첨부(best-effort) (`DocumentBundleService.java:55`).

**권한** (`DocumentBundleService.java:56-75`): role `EQUIPMENT_SUPPLIER`/`ADMIN`. 본인 회사가 먼저 차량을 dispatched 했어야(`NO_DISPATCH` 400). 멱등 `ALREADY_SENT`(409).

**Request** (선택, 없으면 `include_email=false`): `{ "include_email": true, "notes": "확인 부탁드립니다" }`

> 이메일 best-effort: 미구성/수신자 없음/실패해도 묶음 송부 자체는 성공. 성공 시 `email_sent_at` 기록. 헤더용 이메일/제목/파일명은 CRLF·제어문자 제거 후 사용.

**Response 200** — `BundleResponse`(단건: `id`, `quotation_request_id`, `supplier_company_id`/`_name`, `sent_at`, `include_email`, `email_sent_at`, `notes`). 성공 시 BP에 `DOCUMENT_BUNDLE` 알림.

**에러**: `SUPPLIER_ONLY`(403), `REQUEST_NOT_FOUND`(404), `NO_DISPATCH`(400), `ALREADY_SENT`(409).

### `GET /api/quotations/{requestId}/document-bundle` — 받은 묶음 조회 [ADMIN/BP/공급사]

권한은 `ensureCanReadRequest`(ADMIN/해당 BP/FINAL_ACCEPTED 공급사). 전체 묶음 반환. **Response 200** — `BundleResponse[]`. **에러**: `REQUEST_NOT_FOUND`(404), `NOT_PERMITTED`(403), `NO_COMPANY`(403).

### `GET /api/quotations/{requestId}/quote.xlsx` — 공급사별 견적서 (xlsx) [ADMIN/BP/공급사]

공급사 1곳의 dispatched 차량을 카테고리별로 묶은 단일 시트 견적서 (`QuotationExcelController.java:32`). 권한 `ensureCanReadRequest`. 공급사 식별: 공급사 본인은 자기 회사, BP/ADMIN은 `supplier` 쿼리 필수(`SUPPLIER_REQUIRED` 400).

**쿼리**: `supplier`(long?, BP/ADMIN 필수), `disposition`(`attachment` 기본/`inline`).
**Response 200** — `Content-Type: ...spreadsheetml.sheet`, `Content-Disposition: attachment; filename*=UTF-8''quote-{requestId}-s{supplier}.xlsx`. xlsx 바이너리.
**에러**: `REQUEST_NOT_FOUND`(404), `SUPPLIER_NOT_FOUND`(404), `NO_LINES`(400), 권한 403.

### `GET /api/quotations/{requestId}/quote.pdf` — 공급사별 견적서 (pdf) [ADMIN/BP/공급사]

`quote.xlsx` 와 동일 데이터의 PDF. **쿼리**: `supplier`(BP/ADMIN 필수), `disposition`(`inline` 기본). **Response 200** — `application/pdf`, `inline; filename*=UTF-8''quote-{requestId}-s{supplier}.pdf`.

### `POST /api/quotations/{requestId}/quote-preview.xlsx` — 발송 전 미리보기 (xlsx) [공급사 주로]

DB write 없이 입력 단가로 즉시 xlsx 생성(phantom 라인). **Request 본문** `PreviewRates`: `{ "daily_price":350000, "ot_daily_price":45000, "monthly_price":7000000, "ot_monthly_price":40000, "notes":"미리보기" }`. **Response 200** — xlsx, `inline; filename*=UTF-8''preview-{requestId}.xlsx`. DB 변경 없음.
<!-- 확인필요: supplier 쿼리 파라미터가 없어 BP/ADMIN 호출 시 resolveSupplier(null,actor)가 SUPPLIER_REQUIRED(400). 공급사 본인 호출 전제. -->

### `POST /api/quotations/{requestId}/quote-preview.pdf` — 발송 전 미리보기 (pdf) [공급사 주로]

동일 본문(`PreviewRates`), PDF. **Response 200** — `application/pdf`, `inline; filename*=UTF-8''preview-{requestId}.pdf`. DB 변경 없음.

### `GET /api/quotations/{requestId}/compare.xlsx` — 견적 비교표 (xlsx) [BP/ADMIN]

여러 공급사 라인을 좌측 `(품명,규격)` 행 × 우측 공급사별 4열로 비교 (`QuotationExcelService.java:113`). 권한 `ensureBpOrAdmin`(아니면 `BP_ONLY` 403) + `ensureCanReadRequest`. **쿼리**: `disposition`(`attachment` 기본). **Response 200** — xlsx, `attachment; filename*=UTF-8''compare-{requestId}.xlsx`. **에러**: `BP_ONLY`(403), `REQUEST_NOT_FOUND`(404), `NO_LINES`(400).

### `GET /api/quotations/{requestId}/compare.pdf` — 견적 비교표 (pdf) [BP/ADMIN]

동일 데이터, 가로 A4 PDF. **쿼리**: `disposition`(`inline` 기본). **Response 200** — `application/pdf`, `inline; filename*=UTF-8''compare-{requestId}.pdf`.

### `GET /api/quotations/{requestId}/pdf` — 견적서 PDF (SINGLE/FULL) [ADMIN/BP/공급사]

iText 7 견적서 PDF. `dispatched` 전체 라인 기준, 첫 라인 공급사로 헤더 (`QuotationPdfService.java:61`). 권한 `ensureCanReadRequest`.

**쿼리**: `mode`(`single` 기본 / `full`), `disposition`(`inline` 기본). `mode="full"`(대소문자 무시)일 때만 FULL(제목 "견 적 서 (전체)", 파일명 `-full`). **현재 FULL은 SINGLE과 동일 표 렌더링(분기 미구현)**.
**Response 200** — `application/pdf`, `inline; filename*=UTF-8''quotation-{requestId}.pdf`(FULL이면 `-full`). **에러**: `REQUEST_NOT_FOUND`(404), `NO_DISPATCH`(400), 권한 403.

---

## 영업견적 — `/api/outgoing-quotations`

> 공급사가 BP에게 **장비 또는 인원 1건의 단가 견적서**를 보내는 흐름. 수신 방식 — 가입 BP면 시스템 알림 + 수신함, 외부 이메일이면 PDF 첨부 메일. 견적서 PDF는 항상 서버 생성. V37에서 **BP 수락 사인**(PNG) 추가.

### `POST /api/outgoing-quotations` — 영업 견적 발송 [공급사/ADMIN]

`equipment_id`/`person_id` 중 정확히 하나 필수. `mode`: `REGISTERED_BP`(→`recipient_user_id` 필수, 알림만) / `EMAIL`(→`recipient_email` 필수, PDF 첨부 메일). 자원 소유권: ADMIN 외에는 본인 회사 자원만 (`OutgoingQuotationService.java:75-108`).

**Request** (`CreateOutgoingRequest.java`, `@JsonProperty` 부착)
```json
{ "equipment_id": 12, "person_id": null, "daily_rate": 350000, "monthly_rate": 7000000, "note": "고소작업대 10.5m 가용", "period_start": "2026-06-10", "period_end": "2026-07-10", "recipient_user_id": 48, "recipient_email": null, "mode": "REGISTERED_BP" }
```

**Response 201** — `OutgoingResponse`. `mail_sent`/`mail_error` 발송 결과. PDF 실패해도 row 저장 + `mail_sent=false`.

**에러**: `SUPPLIER_ONLY`(403), `RESOURCE_REQUIRED`(400), `EQUIPMENT_NOT_FOUND`/`PERSON_NOT_FOUND`(400), `EQUIPMENT_NOT_OWNED`/`PERSON_NOT_OWNED`(403), `RECIPIENT_USER_REQUIRED`(400), `USER_NOT_FOUND`(400), `EMAIL_REQUIRED`(400), `INVALID_MODE`(400).

### `GET /api/outgoing-quotations/sent` — 공급사 발신함 [로그인]
`actor.company_id` 가 발송 공급사인 견적(id desc). **Response 200** — `OutgoingResponse[]`.

### `GET /api/outgoing-quotations/inbox` — BP 수신함 [로그인]
`actor` 가 수신자(`recipient_user_id`)이거나 `actor.company_id` 가 수신 회사인 견적(중복 제거, id desc). **Response 200** — `OutgoingResponse[]`.

### `GET /api/outgoing-quotations/{id}` — 단건 조회 [로그인]
권한: ADMIN · 발송 공급사 · 수신 회사 · 수신자 본인. **Response 200** — `OutgoingResponse` / `404 OUTGOING_NOT_FOUND` / `403 NOT_PERMITTED`.

### `POST /api/outgoing-quotations/{id}/sign-bp` — BP 수락 사인 (V37) [BP/ADMIN]
수신 BP 회사가 수락하며 서명 PNG 첨부. 성공 시 발송 공급사에 `QUOTATION_FINALIZED` 알림 (`OutgoingQuotationService.java:171`).
**Request**: `{ "png_base64": "data:image/png;base64,iVBORw0KGgo...", "signer_name": "홍길동" }` — data-URL prefix 자동 제거, `signer_name` 비면 actor 사용자명.
**Response 200** — `OutgoingResponse`(`bp_signed=true`). 권한: ADMIN 또는 수신 회사(role BP). **에러**: `OUTGOING_NOT_FOUND`(404), `SIGN_DENIED`(403), `PNG_REQUIRED`(400), `PNG_INVALID`(400).

### `GET /api/outgoing-quotations/{id}/bp-signature` — BP 사인 PNG (V37) [로그인]
권한: ADMIN · 발송 공급사 · 수신 회사 · 수신자 본인. **Response 200** — `image/png` (`Cache-Control: private, max-age=300`). 없으면 404, 권한 없으면 `403 SIGN_VIEW_DENIED`.

#### `OutgoingResponse` 핵심 필드 (`dto/OutgoingResponse.java`)
`id`, `supplier_company_id`/`_name`, `equipment_id`/`equipment_label`(차량번호>모델>`장비#{id}`), `person_id`/`person_label`, `daily_rate`/`monthly_rate`, `note`, `period_start`/`_end`, `recipient_type`(`REGISTERED_BP`|`EMAIL`), `recipient_user_id`, `recipient_company_id`/`_name`, `recipient_email`, `sent_at`, `mail_sent`, `mail_error`, `bp_signed`, `bp_signer_name`, `bp_signed_at`.

---

## 원청기관 — `/api/client-orgs`

> 삼성·SK 등 **원청기관(ClientOrg)** 마스터. 가입 없이 ADMIN이 관리. 자원이 어느 원청 현장에 투입됐는지 이력 추적 + 라벨용.

### `GET /api/client-orgs` — active 목록 [로그인]
`active=true` 이름순(BP 드롭다운 등). **Response 200** — `ClientOrgResponse[]`.

### `GET /api/client-orgs/all` — 전체(비활성 포함) [ADMIN]
**Response 200** — `ClientOrgResponse[]`.

### `POST /api/client-orgs` — 생성 [ADMIN]
**Request**: `{ "name": "삼성전자", "code": "SAMSUNG", "note": "반도체 부문" }` — `name`(≤100, 필수), `code`(≤32, 전역 unique, 필수), `note`(선택). **Response 201** — `ClientOrgResponse`. 코드 중복 `400 CLIENT_ORG_CODE_DUP`.

### `PATCH /api/client-orgs/{id}` — 수정 [ADMIN]
부분 수정(null 아닌 필드만). **Response 200** — `ClientOrgResponse`. `404 CLIENT_ORG_NOT_FOUND`, `400 CLIENT_ORG_CODE_DUP`.

### `POST /api/client-orgs/{id}/deactivate` · `/activate` — 비활성/활성 [ADMIN]
**Response 200** — 갱신된 `ClientOrgResponse`. `404 CLIENT_ORG_NOT_FOUND`.

### `DELETE /api/client-orgs/{id}` — 완전 삭제 [ADMIN]
자원 이력에서 참조 중이면 거절(이력 0건일 때만). **Response 204**. `400 CLIENT_ORG_IN_USE`(참조 건수 포함), `404 CLIENT_ORG_NOT_FOUND`.

#### `ClientOrgResponse`: `id`, `name`, `code`, `note`, `active`.

---

## 자원 원청 이력 — `/api/client-org-history`

> 장비/인원이 어떤 ClientOrg 현장에 투입됐는지 기간 이력. `source` = `ADMIN`(수동) 또는 `WORK_PLAN`(작업계획서 STARTED 시 자동). 조회는 해당 자원 view 권한 선통과(IDOR 차단).

### `GET /api/client-org-history/equipment/{id}` · `/person/{id}` — 이력 조회 [로그인]
각 자원 `get(id, actor)` 권한 가드 통과 후 `period_start` desc. **Response 200** — `HistoryDto[]`.

### `POST /api/client-org-history/equipment/{id}` · `/person/{id}` — 수동 추가 [ADMIN]
`source=ADMIN`. **Request**: `{ "client_org_id": 3, "period_start": "2026-05-01", "period_end": "2026-06-30" }` — `client_org_id`(필수, 존재), `period_start`(필수), `period_end`(선택, 비우면 진행 중). **Response 201** — `HistoryDto`. `400 CLIENT_ORG_NOT_FOUND`/`PERIOD_REQUIRED`/`INVALID_PERIOD`.

### `PATCH /api/client-org-history/{equipment|person}-history/{historyId}` — 기간 수정 [ADMIN]
기간만 수정(`client_org_id` 변경 안 됨). **Response 200** — `HistoryDto`. `404 HISTORY_NOT_FOUND`.

### `DELETE /api/client-org-history/{equipment|person}-history/{historyId}` — 삭제 [ADMIN]
**Response 204**.

#### `HistoryDto`: `id`, `client_org_id`(원청 삭제 시 null), `client_org_name`(삭제됐으면 `"(삭제됨)"`), `period_start`, `period_end`(진행 중이면 null), `source`(`ADMIN`|`WORK_PLAN`).

---

## 서류 보완 요청 — `/api/document-supplements`

> S-11: BP/ADMIN이 공급사에게 **특정 자원의 특정 서류 재제출** 요청. 발송 시 공급사에 `SUPPLEMENT_REQUESTED` 알림. 공급사가 해당 (owner,type) 서류 업로드 시 `DocumentService.upload` 후크가 OPEN→`RESOLVED` 자동 close + 요청자에 `SUPPLEMENT_RESOLVED` 알림.
> 상태: `OPEN` → `RESOLVED`(업로드 자동) | `CANCELLED`(요청자 취소).

### `POST /api/document-supplements` — 보완 요청 생성 [BP/ADMIN]
**Request**
```json
{ "target_owner_type": "EQUIPMENT", "target_owner_id": 12, "document_type_id": 5, "context_site_id": 7, "context_work_plan_id": null, "reason": "보험증서 만료, 갱신본 제출 요망" }
```
`target_owner_type`(`EQUIPMENT`|`PERSON`|`COMPANY`), `target_owner_id`, `document_type_id` 필수. `context_site_id`(BP 권한 경계 판정), `reason`(≤2000).

**권한** (`createRow`): BP/ADMIN. ADMIN은 경계 skip. BP는 (대상 공급사가 본인 회사) 또는 (BP 견적에 배차한 공급사) 또는 (`context_site_id` 가 본인 사이트 + 대상 공급사가 그 사이트 ACTIVE 참여자) 중 하나.

**Response 201** — `SupplementResponse`. **에러**: `REQUEST_DENIED`(403), `DOCUMENT_TYPE_NOT_FOUND`(400), `EQUIPMENT_NOT_FOUND`/`PERSON_NOT_FOUND`(400), `NO_COMPANY`(403), `SITE_NOT_FOUND`(400), `SITE_SCOPE_DENIED`(403), `RESOURCE_SCOPE_DENIED`(403), `SITE_CONTEXT_REQUIRED`(403).

### `POST /api/document-supplements/batch` — 다건 보완 요청 [BP/ADMIN]
배열을 한 트랜잭션 생성 + **공급사 회사별로 묶어 알림 1건씩**. **Request**: `CreateSupplementRequest[]`. **Response 201** — `SupplementResponse[]`. 빈 배열 `400 EMPTY_ITEMS`.

### `GET /api/document-supplements` — 목록 [로그인]
ADMIN 전체 / BP 같은 회사 발신분 전체 / 공급사 자기 회사가 target인 요청 / 그 외 빈 배열. **Response 200** — `SupplementResponse[]`.

### `GET /api/document-supplements/{id}` — 단건 [로그인]
권한: ADMIN · 같은 BP 회사 요청자 · target 공급사 회사(아니면 `403 VIEW_DENIED`). **Response 200** — `SupplementResponse`. `404 SUPPLEMENT_NOT_FOUND`.

### `POST /api/document-supplements/{id}/cancel` — 취소 [BP/ADMIN]
`OPEN` 만 → `CANCELLED`. 권한: ADMIN 또는 같은 BP 회사 요청자. **Response 200** — `SupplementResponse`. `404 SUPPLEMENT_NOT_FOUND`, `403 CANCEL_DENIED`, `400 ALREADY_CLOSED`.

#### `SupplementResponse` 핵심 필드 (`dto/SupplementResponse.java`)
`id`, `requester_user_id`/`_name`, `requester_role`, `target_supplier_company_id`/`_name`, `target_owner_type`(`EQUIPMENT`|`PERSON`|`COMPANY`), `target_owner_id`, `target_owner_name`, `document_type_id`/`_name`, `context_site_id`/`_name`, `context_work_plan_id`, `reason`, `status`(`OPEN`|`RESOLVED`|`CANCELLED`), `resolved_doc_id`, `resolved_at`, `cancelled_at`, `created_at`.

---

## 컴플라이언스 조회 — `/api` (집계)

> S-11: 자원(회사/장비/인원)의 **서류 구비 현황 집계**(엔티티 아닌 실시간 계산 DTO). 적용 catalog는 `document_types` 의 `applies_to` + 카테고리/역할 매칭으로 산출, 각 종류별 chain head 평가(VERIFIED/REJECTED/만료/누락 + 진행 중 보완요청). 모든 조회 대상 자원 view 권한 선통과. 만료 임박 30일.
> `ready_for_work_plan`: 필수(`required`) 서류 모두 OK(VERIFIED & 미만료 & OPEN 보완요청 없음) + REJECTED 0건일 때 true(필수 0건이면 true).

### `GET /api/equipment/{id}/compliance` · `/persons/{id}/compliance` — 자원 1건 [로그인]
각 자원 `get(id, actor)` 권한 가드 통과 후 평가. **Response 200** — `ResourceCompliance`. `404 EQUIPMENT_NOT_FOUND`/`PERSON_NOT_FOUND`.

### `GET /api/companies/{id}/compliance` — 회사 1건 [로그인]
권한: ADMIN 또는 본인 회사. **Response 200** — `ResourceCompliance`. `403 COMPLIANCE_VIEW_DENIED`, `404 COMPANY_NOT_FOUND`.

### `GET /api/sites/{id}/compliance` — 사이트 통합 [로그인]
BP 회사 서류 + 사이트 ACTIVE 참여 공급사들의 장비/인원 통합. 공급사 호출 시 자기 회사 자원만 노출. 권한: ADMIN · 소유 BP · ACTIVE 참여 공급사(아니면 `403 COMPLIANCE_DENIED`). **Response 200** — `SiteCompliance`. `404 SITE_NOT_FOUND`.

#### `SiteCompliance`: `site_id`/`site_name`, `bp_company_id`/`_name`, `bp_company`(ResourceCompliance), `equipments[]`/`persons[]`(ResourceCompliance[]), `total_required_items`, `total_ok_items`, `progress_pct`, `ready_for_work_plan`.
#### `ResourceCompliance`: `owner_type`(`COMPANY`|`EQUIPMENT`|`PERSON`), `owner_id`, `owner_name`, `owner_sub_label`, `supplier_company_id`/`_name`, `items[]`(ComplianceItem), `required_total`, `required_ok`, `missing_count`, `rejected_count`, `expiring_count`, `open_supplement_count`, `ready_for_work_plan`.
#### `ComplianceItem`: `document_type_id`/`_name`, `required`, `blocks_assignment`, `has_expiry`, `present`, `verified`, `rejected`, `ocr_review_required`, `expired`, `expiring_soon`, `document_id`, `expiry_date`, `open_supplement`.

---

## 이행지시 (Compliance Orders) — `/api/compliance-orders` (V46 신규)

> **BP/ADMIN이 공급사에게 특정 차량/인원의 이행 조치(안전점검·건강검진 등)를 지시 → 공급사가 증빙 파일 업로드·제출 → BP가 검토(승인/반려)** 하는 흐름. 작업계획서 시작(`WorkPlanService.start`) 게이트에 연동(미이행 시 차단).
> **상태 전이**: `REQUESTED`(발행) → (증빙 업로드 또는 submit) `SUBMITTED` → 검토 → `APPROVED` | `REJECTED`. `REJECTED` 는 재제출로 `SUBMITTED` 재전환. `APPROVED` 종결.
> 주의: **증빙 파일 업로드 자체가 `SUBMITTED` 로 전환**(`attachProof`→`submit()`). `/submit` 은 증빙이 이미 있을 때 제출 메모 갱신용.
> `overdue`: 상태 `REQUESTED`/`REJECTED` 이고 `due_date` 가 오늘 이전이면 true.
> 알림 type(문자열 리터럴, NotificationType 상수 아님): `COMPLIANCE_ORDER`(발행)/`COMPLIANCE_ORDER_SUBMITTED`/`COMPLIANCE_ORDER_APPROVED`/`COMPLIANCE_ORDER_REJECTED`.

### `POST /api/compliance-orders` — 이행지시 발행 [BP/ADMIN]
**Request**
```json
{ "supplier_company_id": 21, "target_type": "VEHICLE", "target_id": 12, "order_type": "SAFETY_INSPECTION", "order_subtype": "분기 정기점검", "due_date": "2026-06-30", "request_notes": "점검표 사진 첨부 요망" }
```
`target_type`(`VEHICLE`|`PERSON`), `order_type`(`SAFETY_INSPECTION`|`HEALTH_CHECK`|`OTHER`), `due_date` 필수. 대상은 지정 공급사 소유여야.

**Response 200** — `ComplianceOrderResponse`(status `REQUESTED`). 권한: BP(본인 회사가 `bp_company_id`) 또는 ADMIN. **에러**: `BP_ONLY`(403), `ADMIN_BP_CONTEXT_REQUIRED`(400, ADMIN 발행 미지원), `TARGET_NOT_FOUND`(400), `TARGET_NOT_OWNED`(400).

### `GET /api/compliance-orders?scope=supplier|bp` — 목록 [로그인]
`scope=supplier`(기본): 공급사 본인 회사 수신분(ADMIN 전체). `scope=bp`: BP 본인 회사 발행분(ADMIN 전체). **Response 200** — `ComplianceOrderResponse[]`. `403 SUPPLIER_ONLY`/`BP_ONLY`.

### `GET /api/compliance-orders/{id}` — 단건 [로그인]
권한: ADMIN · 발행 BP 회사 · 수신 공급사 회사. **Response 200** — `ComplianceOrderResponse`. `404 ORDER_NOT_FOUND`, `403 ORDER_VIEW_DENIED`.

### `POST /api/compliance-orders/{id}/proof` — 증빙 업로드 (multipart) [공급사/ADMIN]
`multipart/form-data`, 파트 `file`. 저장 후 status `SUBMITTED` 로 전환. 허용 type: PDF/JPEG/PNG/WEBP/.xlsx/.docx. 권한: ADMIN 또는 수신 공급사. **Response 200** — `ComplianceOrderResponse`. **에러**: `EMPTY_FILE`(400), `BAD_CONTENT_TYPE`(400), `ORDER_NOT_FOUND`(404), `NOT_OWNER`(403), `ALREADY_APPROVED`(409).

### `GET /api/compliance-orders/{id}/proof` — 증빙 다운로드 [로그인]
권한: 조회 권한과 동일. **Response 200** — 파일 바이너리(`inline`, 원본 content-type). `404 NO_PROOF`, `403 ORDER_VIEW_DENIED`.

### `POST /api/compliance-orders/{id}/submit` — 제출(메모 갱신) [공급사/ADMIN]
증빙이 이미 있는 지시를 `SUBMITTED` (재)전환 + 메모 갱신. **Request**: `{ "submission_notes": "5/20 점검 완료" }`(선택). **Response 200** — `ComplianceOrderResponse`. **에러**: `ORDER_NOT_FOUND`(404), `NOT_OWNER`(403), `ALREADY_APPROVED`(409), `PROOF_REQUIRED`(400).

### `POST /api/compliance-orders/{id}/review` — 검토(승인/반려) [BP/ADMIN]
`SUBMITTED` 만. **Request**: `{ "approve": false, "rejection_reason": "점검표 일자 미기재" }` — `approve` 필수, 반려 시 `rejection_reason` 필수. **Response 200** — `ComplianceOrderResponse`(`APPROVED`|`REJECTED`). 권한: ADMIN 또는 발행 BP. **에러**: `ORDER_NOT_FOUND`(404), `NOT_BP`(403), `NOT_SUBMITTED`(400), `REASON_REQUIRED`(400).

#### `ComplianceOrderResponse` 핵심 필드 (`dto/ComplianceOrderResponse.java`)
`id`, `bp_company_id`/`_name`, `supplier_company_id`/`_name`, `target_type`(`VEHICLE`|`PERSON`), `target_id`, `target_label`, `order_type`(`SAFETY_INSPECTION`|`HEALTH_CHECK`|`OTHER`), `order_subtype`, `due_date`, `request_notes`, `status`(`REQUESTED`|`SUBMITTED`|`APPROVED`|`REJECTED`), `overdue`, `submitted_at`, `submission_notes`, `proof_filename`, `proof_content_type`, `reviewed_at`, `reviewed_by`, `rejection_reason`, `created_at`.

<!-- 확인필요: ComplianceOrderController의 issue/submit/review/proof 응답은 @ResponseStatus 미지정 → 모두 200 OK (발행도 201 아님). 반면 OutgoingQuotation/DocumentSupplement POST 생성은 201. -->

---

## Safety Inspections — `/api/safety-inspections` (Phase S-3.1)

현장 진입 전 안전점검/검사 일정. `target_type`(`VEHICLE`=장비/`PERSON`=인원), `kind`(`VEHICLE_INSPECTION`=차량검사/`ENTRY_CHECK`=입소검사). **상태 전이**: `PENDING`(등록)→`SENT`(BP 통보)→`CONFIRMED`(공급사 확인)→`COMPLETED`(완료)|`CANCELLED`. 작업 시작 게이트에서 자원별 `COMPLETED` 확인.

### `POST /api/safety-inspections` — 일정 등록 [BP/ADMIN]
권한: ADMIN 또는 BP(본인 회사 현장). **Request**
```json
{ "site_id": 12, "target_type": "VEHICLE", "target_id": 88, "kind": "VEHICLE_INSPECTION", "scheduled_at": "2026-06-10T09:00:00", "duration_minutes": 60, "supplier_company_id": 5, "inspector_id": 3 }
```
`site_id`/`target_type`/`target_id`/`kind`/`scheduled_at` 필수. `supplier_company_id` 미지정 시 대상 자원의 `supplier_id` 자동 추출. **Response** — `InspectionResponse`(`status=PENDING`). **에러**: `SITE_NOT_FOUND`(400), `NOT_YOUR_SITE`(403), `TARGET_NOT_FOUND`(400), `BP_ADMIN_ONLY`(403).

### `POST /api/safety-inspections/{id}/send` — 공급사 통보 [BP/ADMIN]
`PENDING` 일 때만(멱등, 이미 SENT면 400). 공급사 마스터에 알림 + SMS. 권한 `ensureCanManage`. **본문 없음.** **Response** — `InspectionResponse`(`SENT`).

### `POST /api/safety-inspections/{id}/confirm` — 공급사 확인 [공급사/ADMIN]
권한: ADMIN 또는 `supplier_company_id == actor.company_id`. `SENT` 에서만. **본문 없음.** **Response** — `InspectionResponse`(`CONFIRMED`).

### `POST /api/safety-inspections/{id}/complete` — 검사 완료 [BP/ADMIN]
권한 `ensureCanManage`. 상태 검증 없이 `COMPLETED`. **Request** (선택)
```json
{ "resultNotes": "이상 없음" }
```
> **주의 (camelCase):** 컨트롤러가 본문 `Map` 에서 `resultNotes` 키를 직접 읽는다 (`SafetyInspectionController.java:43`). 전역 SNAKE_CASE는 DTO에만 적용되며 여기는 raw Map이라 **`resultNotes`(camelCase) 그대로** 보내야 한다. `result_notes` 로 보내면 무시되고 null 저장.

**Response** — `InspectionResponse`(`COMPLETED`, `result_notes` 반영).

### `GET /api/safety-inspections/site/{siteId}` — 현장별 [BP/ADMIN]
ADMIN 전체 / BP 본인 현장 / 공급사는 403(`USE_MINE_ENDPOINT`). `scheduled_at` asc. **Response** — `InspectionResponse[]`.

### `GET /api/safety-inspections/mine` — 공급사 수신 목록 [공급사]
`actor.company_id == supplier_company_id` 만(없으면 빈 배열). **Response** — `InspectionResponse[]`.

#### `InspectionResponse` (`dto/InspectionResponse.java`)
`id`, `site_id`/`site_name`, `supplier_company_id`/`_name`, `target_type`(`VEHICLE`|`PERSON`), `target_id`, `target_label`(장비=차량번호/모델/`#id`, 인원=이름), `kind`(`VEHICLE_INSPECTION`|`ENTRY_CHECK`), `scheduled_at`, `duration_minutes`, `status`(`PENDING`/`SENT`/`CONFIRMED`/`COMPLETED`/`CANCELLED`), `sent_at`/`confirmed_at`/`completed_at`, `result_notes`, `created_at`.

---

## Work Confirmations — 작업확인서 (일별)

인원(Person) 단위 일별 작업 확인서. 공급사측·BP측 **양쪽 사인** 완료 시 `COMPLETED`. 동일 `(work_plan, person)` 1건만. **상태**: `PENDING`→`COMPLETED`|`CANCELLED`|`INVALIDATED`(내용 수정으로 사인 무효화). 사인 PNG 본문은 `pngBase64`(camelCase) 키를 컨트롤러가 Map에서 직접 읽음(`data:` prefix 자동 제거).

### `GET /api/work-plans/{workPlanId}/work-confirmations` — 작업계획서별 목록 [로그인]
ADMIN/현장 BP 전체, 공급사는 본인 회사 발급분만. PNG 미포함, `work_date` desc. **Response** — `WorkConfirmationResponse[]`.

### `POST /api/work-plans/{workPlanId}/work-confirmations/request` — 인원 단위 발급 [공급사/ADMIN]
권한: 공급사/ADMIN. 인원이 작업계획서에 배정 + (공급사는 자기 소속) + 작업계획서 `IN_PROGRESS`/`DONE`. 중복 발급 시 기존 건 반환(취소건은 PENDING 재오픈). **Request**: `{ "person_id": 41 }` — 컨트롤러가 Map에서 `person_id` 직접 읽음(snake), 누락 시 400. **Response** — `WorkConfirmationResponse`(`PENDING`). **에러**: `WORK_CONFIRMATION_DENIED`(403), `WP_NOT_IN_PROGRESS`(400), `PERSON_NOT_IN_WP`(403), `NOT_OWN_PERSON`(403), `SUPPLIER_TYPE_INVALID`(400).

### `GET /api/work-confirmations/{id}` — 단건 [로그인]
쿼리 `withPng=true` 면 PNG 포함(기본 false). 권한: ADMIN/현장 BP/발급 공급사. **Response** — `WorkConfirmationResponse`.

### `PATCH /api/work-confirmations/{id}` — 작업내용/시간 수정 [로그인]
쿼리 `invalidate=true` 면 양측 사인 제거 + `INVALIDATED`. 시간 슬롯 0~24, 합계 24 초과 시 400. 권한: `get()` 조회 권한. `CANCELLED` 수정 불가. **Request** (모두 선택, null=미변경)
```json
{ "work_content": "○○ 양중 작업", "remarks": "특이사항 없음", "morning_time": "08:00~12:00", "morning_hours": 4, "afternoon_time": "13:00~17:00", "afternoon_hours": 4, "overtime_time": "17:00~19:00", "overtime_hours": 2, "night_time": null, "night_hours": null }
```
`*_time`(64자), `*_hours`(decimal 0~24, `total_hours` 자동 합산). `UpdateRequest` 는 public-field 클래스지만 camelCase 필드 → 전역 SNAKE_CASE 로 위 snake 키 매핑. **Response** — `WorkConfirmationResponse`. **에러**: `HOUR_OUT_OF_RANGE`(400), `HOURS_OVER_24`(400), `CANCELLED`(400).

### `POST /api/work-confirmations/{id}/sign-supplier` · `/sign-bp` — 사인 [공급사/ADMIN · BP/ADMIN]
**Request**: `{ "pngBase64": "iVBORw0KGg..." }` (camelCase Map 키). sign-supplier 권한: ADMIN 또는 `actor.company_id == issuing_supplier_company_id`. sign-bp 권한: ADMIN 또는 `role=BP && company_id == bp_company_id`. **Response** — `WorkConfirmationResponse`. 양측 사인 시 `COMPLETED`.

### `POST /api/work-confirmations/{id}/cancel` — 취소 [공급사/ADMIN]
발급 공급사 본인 또는 ADMIN. **본문 없음.** **Response** — `WorkConfirmationResponse`(`CANCELLED`).

### `GET /api/work-confirmations/{id}/pdf` — PDF [로그인]
HTML→LibreOffice, 사인 PNG 임베드. **응답**: `application/pdf`, `attachment; filename*=UTF-8''작업확인서_{id}.pdf`.

#### `WorkConfirmationResponse` 핵심 필드 (`dto/WorkConfirmationResponse.java`)
`id`/`work_plan_id`/`person_id`, `work_date`, `issuing_supplier_company_id`, `issuing_supplier_type`(`EQUIPMENT`|`MANPOWER`), `bp_company_id`, `work_content`/`remarks`, `morning_time`~`night_hours`(시간 슬롯), `total_hours`, `supplier_signer_name`/`_person_id`/`_user_id`, `supplier_signed`/`supplier_signed_at`, `supplier_signature_png_base64`(withPng 시), `bp_signer_name`/`_user_id`, `bp_signed`/`bp_signed_at`, `bp_signature_png_base64`(withPng 시), `status`(`PENDING`/`COMPLETED`/`CANCELLED`/`INVALIDATED`), `created_at`/`updated_at`.

---

## Public Signature — `/api/sign` [공개·토큰]

작업계획서 5개 사인란 중 외부 4명(담당자/확인자/검토자/승인자)이 **비로그인** 토큰 링크로 서명. `GET/POST /api/sign/*` permitAll. 토큰 TTL 7일.

### `GET /api/sign/{token}` — 사인 요청 조회 [공개]
토큰 검증 + 작업계획서 메타. 만료 토큰은 GET 단계 차단(410). **Response 200**
```json
{ "signature": { /* SignatureResponse, png 미포함 */ }, "work_plan": { "id": 7, "title": "○○현장 작업계획서" } }
```
> 응답 Map 키는 `signature`/`workPlan` 이며 전역 SNAKE_CASE 로 `work_plan` 출력.

**에러(비표준 `{error}` 형)**: 404(유효하지 않은 링크), 410(만료).

### `POST /api/sign/{token}` — PNG 사인 제출 [공개]
PNG magic byte 검증 + 8B~2MB 제한. 만료/무효화 거부. 제출 시 `SIGNED`. **Request**: `{ "pngBase64": "iVBORw0KGg..." }`. **Response 200**: `{ "signature": { ... } }`. **에러(`{error}` 형, 400)**: PNG 비어있음/디코딩 실패/크기 유효하지 않음/PNG 아님/무효화됨/만료.

---

## Work Plan Signatures — `/api/work-plans/{workPlanId}/signatures` [로그인]

작업계획서 첫 페이지 5개 사인 슬롯. 역할: `AUTHOR`(작성자, BP 본인)/`SUPERVISOR`/`CONFIRMER`/`REVIEWER`/`APPROVER`. AUTHOR 외 4명은 이메일 사인 요청.

### `GET /api/work-plans/{workPlanId}/signatures` — 슬롯 목록 [로그인]
`WorkPlanService.get()` 조회 권한 선통과. 쿼리 `withPng=true` 면 SIGNED 슬롯 PNG 포함. **Response** — `SignatureResponse[]`.

### `POST /api/work-plans/{workPlanId}/signatures/author` — 작성자 사인 [BP/ADMIN]
권한: ADMIN 또는 `company_id == work_plan.bp_company_id`. **Request**: `{ "pngBase64": "iVBORw0KGg..." }`. **Response** — `SignatureResponse`(role=`AUTHOR`).

### `POST /api/work-plans/{workPlanId}/signatures/request` — 외부 사인 요청 발송 [BP/ADMIN]
권한: ADMIN 또는 소유 BP. 역할별 토큰 생성 + 이메일. 쿼리 `attachPdf`(기본 true), `templateId`(미지정 시 시스템 첫 템플릿). `AUTHOR` 키 무시. **Request** (역할별 객체, `email` 없으면 skip; 키는 `SignatureRole` enum 대문자, `name`/`email` 은 raw Map)
```json
{ "SUPERVISOR": { "name": "김감독", "email": "sv@ex.com" }, "CONFIRMER": { "name": "이확인", "email": "cf@ex.com" }, "REVIEWER": { "name": "박검토", "email": "rv@ex.com" }, "APPROVER": { "name": "최승인", "email": "ap@ex.com" } }
```
**Response** — `SignatureResponse[]`(`PENDING`).

### `POST /api/work-plans/{workPlanId}/signatures/invalidate` — 사인 전체 무효화 [BP/ADMIN]
SIGNED 슬롯 모두 `INVALIDATED`. **본문 없음.** **Response**: `{ "invalidated": <int> }`.
> 예외 매핑: `SecurityException`→403, `IllegalArgument/IllegalState`→400 (모두 `{error}` 형).

#### `SignatureResponse` (`dto/SignatureResponse.java`)
`id`/`work_plan_id`, `role`(`AUTHOR`/`SUPERVISOR`/`CONFIRMER`/`REVIEWER`/`APPROVER`), `role_label`(한글), `signer_name`/`signer_email`, `status`(`PENDING`/`SIGNED`/`EXPIRED`/`INVALIDATED`), `signed_at`, `token_expires_at`, `has_signature`, `signature_png_base64`(withPng 시).

---

## DOCX Templates — `/api/docx-templates`

작업계획서 DOCX 출력용 placeholder 치환 템플릿. `target_type` 기본 `WORK_PLAN`. `company_id` NULL=전역. 권한: ADMIN 전역+회사 전체 / BP·공급사는 전역+자기 회사 read, 자기 회사 템플릿만 업로드·수정·삭제(전역은 ADMIN만).

### `GET /api/docx-templates?targetType=WORK_PLAN` — 목록 [로그인] → `TemplateResponse[]`
### `POST /api/docx-templates` — 업로드 [로그인] (multipart/form-data)
DOCX 검증(ZIP magic + `[Content_Types].xml` & `word/document.xml`, ≤10MB). 파트: `file`(.docx, 필수), `name`(≤120, 필수), `targetType`(기본 WORK_PLAN), `companyId`(NULL=전역=ADMIN만). **Response** — `TemplateResponse`.
### `PATCH /api/docx-templates/{id}` — 이름 변경 [로그인] `{ "name": "새 이름" }`(≤120, @NotBlank) → `TemplateResponse`
### `DELETE /api/docx-templates/{id}` — 삭제 [로그인] (스토리지 파일 동반 삭제) → 200
### `GET /api/docx-templates/{id}/file` — 원본 다운로드 [로그인] → `...wordprocessingml.document`, `attachment; filename*=UTF-8''{name}.docx`

#### `TemplateResponse`: `id`, `target_type`, `company_id`(NULL=전역), `name`, `file_size`, `created_at`/`updated_at`.

---

## Work Plan Export (DOCX) — `/api/work-plans/{id}/export/docx`

### `GET /api/work-plans/{id}/export/docx?templateId={tid}` — DOCX 다운로드 [로그인]
작업계획서 데이터를 템플릿 placeholder 에 채워 DOCX 생성. 권한: `WorkPlanService.get()` + `DocxTemplateService.getForExport()` 둘 다 통과. `templateId` 필수. **응답**: `...wordprocessingml.document`, `attachment; filename*=UTF-8''{title}.docx`. **에러**: `WORK_PLAN_NOT_FOUND`(404), `DOCX_EXPORT_FAILED`(400).

---

## OnlyOffice — `/api/onlyoffice`

작업계획서를 OnlyOffice Document Server 로 협업 편집. `ONLYOFFICE_URL` 없으면 `enabled=false`.

### `GET /api/onlyoffice/status` — 상태 [로그인]
`{ "enabled": true, "server_url": "https://office.example.com" }`

### `GET /api/onlyoffice/work-plan/{id}/config?templateId={tid}` — 편집 config [로그인]
권한: `WorkPlanService.get()` 으로 plan 조회 권한 선검증(실패 시 file 토큰도 미발급). 첫 진입/템플릿 변경 시 DOCX 생성·저장 후 `current_docx_key` 기록. 전제: `ONLYOFFICE_JWT_SECRET` 16자 이상(없으면 `ONLYOFFICE_NOT_CONFIGURED` 400). **Response**: OnlyOffice DocsAPI config — `documentType`, `document.{fileType,key,title,url}`, `editorConfig.{mode,lang,callbackUrl,user}`, 선택 `token`(HS256 JWT, 1h). `document.url`/`callbackUrl` 에 `?token=<file-access JWT>` 부착. (DocsAPI 규격 키라 SNAKE_CASE 미적용)

### `GET /api/onlyoffice/work-plan/{id}/file` (+`/file.docx`) — DOCX 제공 [공개+file토큰]
쿼리 `token` 필수. OnlyOffice 서버가 직접 GET. permitAll 이나 file-access JWT `plan_id` 가 path id 와 일치해야 함. **응답**: docx 바이너리, `Cache-Control: no-cache, no-store, must-revalidate`.

### `POST /api/onlyoffice/work-plan/{id}/callback?token={...}` — 저장 콜백 [공개+JWT토큰]
permitAll. **콜백 보안**: ① 쿼리 `token`(file-access JWT)의 `plan_id == path id`, ② Document Server JWT mode 면 본문 `token` 을 동일 secret(HS256) verify, ③ `status` 2(저장준비)·6(강제저장)만 저장, ④ **SSRF 차단** — 본문 `url` host 가 `ONLYOFFICE_URL` host 와 동일할 때만 fetch. **Request**: OnlyOffice 표준 콜백(`{status,url,key,token,...}`). **Response**: `{ "error": 0 }`(성공)/`{ "error": 1 }`(실패) — 규격상 필수.

---

## Worksheet Editor (임시 세션) — `/api/worksheet` [ADMIN/BP]

작업계획서 **생성 중**(plan id 없이) 클라이언트 생성 DOCX 를 OnlyOffice 새 탭으로 편집. 세션 파일 24h TTL. `sessionId` UUID v4 형식만 허용(path traversal 차단).

### `POST /api/worksheet/editor-session` — 임시 세션 생성 [ADMIN/BP] (multipart/form-data)
파트: `file`(.docx, 필수), `name`(기본 `worksheet`), `userName`(에디터 표시명). **Response**: `{ "sessionId": "<uuid>", "fileName": "<name>.docx", "config": { /* OnlyOffice config + token(HS256, 6h) */ } }`.

### `GET /api/worksheet/editor-file/{sessionId}` (+`.docx`) — 세션 파일 제공 [공개]
> **경로 정정:** 실제 경로는 `/editor-file/{sessionId}` (GET/HEAD). OnlyOffice 컨테이너가 인증 없이 호출 — permitAll. **응답**: docx(`inline`).

### `POST /api/worksheet/onlyoffice-callback/{sessionId}` — 콜백 [공개]
permitAll. 보안: `sessionId` UUID 검증 + `SESSION_DIR` escape 차단, 본문 `token` HS256 verify, `status` 2·6 만 저장, SSRF host 일치 검증. **Response**: `{ "error": 0 }`/`{ "error": 1 }`.

### `GET /api/worksheet/editor-session/{sessionId}/download` — DOCX 다운로드 [ADMIN/BP]
쿼리 `name` 선택. **응답**: docx, `attachment; filename*=UTF-8''{name}.docx`.

### `GET /api/worksheet/editor-session/{sessionId}/pdf` — PDF 다운로드 [ADMIN/BP]
세션 DOCX → LibreOffice PDF. 쿼리 `name` 선택. **응답**: `application/pdf` 첨부.

---

> **기존 `Companies — /api/companies` 섹션에 추가** — 직원 관리 하위 리소스.

## Company Users — `/api/companies/me/users` [회사 master]

회사 master(`is_company_admin=true`)가 같은 회사 직원 관리. 모든 메서드 `ensureMaster` 검증. 등록 시 role/companyId/isCompanyAdmin 서버 강제(권한 상승 차단). 응답은 `auth.dto.UserResponse`(createdAt 포함).

### `GET /api/companies/me/users` — 직원 목록 → `UserResponse[]`
### `POST /api/companies/me/users` — 직원 생성 (`enabled=true`)
`{ "email": "staff@ex.com", "password": "minimum8c", "name": "직원", "phone": "010-0000-0000" }` — email(unique)/password(8~72)/name(≤100) 필수, phone(≤32). role=actor.role, company_id=actor.company_id, is_company_admin=false 고정. **Response 201** — `UserResponse`. `409 EMAIL_EXISTS`.
### `POST /api/companies/me/users/{id}/approve` — 승인(활성화) → `UserResponse`
### `POST /api/companies/me/users/{id}/disable` — 비활성화 → `UserResponse`
본인(`CANNOT_DISABLE_SELF`)·다른 master(`CANNOT_DISABLE_MASTER`) 불가. refresh 토큰 폐기.
### `PATCH /api/companies/me/users/{id}` — 프로필 수정 → `UserResponse`
다른 master 수정 불가(`CANNOT_EDIT_OTHER_MASTER`). 본인 비번은 self-service(`USE_SELF_SERVICE_PASSWORD` 400). **Request** (모두 선택): `{ "name","phone","new_password"(8~72, 타인 한정),"show_in_quote"(bool),"quote_display_order"(int) }`.

---

> **기존 `Documents — /api/documents` 섹션에 추가** — 업로드 전 OCR 미리보기.

### `POST /api/documents/ocr-preview` — OCR 미리보기 [ADMIN/BP/공급사] (multipart/form-data)
업로드 전 verify-api OCR 만 돌려 추출 결과 미리받기. ≤10MB, ContentType 화이트리스트(PDF/JPEG/JPG/PNG/WEBP/GIF — SVG·HEIC 차단). 파트: `file`(필수), `ocrType`(필수, 예 `BUSINESS_REGISTRATION`). **Response 200**
```json
{ "ok": true, "fields": { "businessNumber": "111-11-11111", "representativeName": "홍길동", "businessName": "..." } }
```
> `fields` 는 verify-api 응답을 flatten 한 string map(키는 verify-api 원본 그대로 camelCase). 실패/미가동 시 `ok=false` + `reasonCode`/`message`. 파일 문제 시 `{ "ok": false, "message": ... }`(400).

---

> **기존 `Equipment — /api/equipment` 섹션에 추가** — 사진 + 기본 조종원.

### `POST /api/equipment/{id}/photo` — 사진 업로드 [ADMIN/장비공급사] (multipart, 파트 `file`)
권한 `ensureCanModify`(ADMIN 또는 소유 EQUIPMENT_SUPPLIER). → `EquipmentResponse`.
### `DELETE /api/equipment/{id}/photo` — 삭제 [ADMIN/장비공급사] → `EquipmentResponse`
### `GET /api/equipment/{id}/photo` — 조회 [로그인]
권한 `ensureCanAccess`(ADMIN/소유/참여 BP). **응답**: 이미지 바이너리, `Cache-Control: private, max-age=300`.
### `GET /api/equipment/{id}/default-operators` — 기본 조종원 목록 [로그인]
우선순위 asc. 권한: `EquipmentService.get()`. **Response**: `[ { "id":9, "person_id":41, "person_name":"홍길동", "priority":1 } ]`.
### `PUT /api/equipment/{id}/default-operators` — 일괄 갱신 [ADMIN/BP]
`person_ids` 순서대로 priority 1,2,3… 전체 교체. 권한 ADMIN/BP. **Request**: `{ "person_ids": [41, 42, 43] }`(`@JsonProperty("person_ids")`). **Response**: `DefaultOperatorItem[]`(위와 동일).

---

> **기존 `Persons — /api/persons` 섹션에 추가** — 일괄 삭제 + 사진.

### `POST /api/persons/bulk-delete` — 일괄 삭제 [로그인]
`ids` 각각 단건 delete 권한 검사 후 삭제. **Request**: `{ "ids": [11, 12, 13] }`. 빈 배열 `400 EMPTY_IDS`. **Response**: 204.
### `POST /api/persons/{id}/photo` (multipart, `file`) · `DELETE /api/persons/{id}/photo` — [로그인] → `PersonResponse`
### `GET /api/persons/{id}/photo` — 조회 [로그인] → 이미지 바이너리, `Cache-Control: private, max-age=300`.

---

> **기존 `Companies — /api/companies` 섹션에 추가** — 조회/연동/프로필.

### `GET /api/companies/bp-list` — BP 회사 목록 [ADMIN/BP/공급사] → `CompanyResponse[]` (`type=BP` 전체)
### `GET /api/companies/suppliers?type=EQUIPMENT` — 공급사 목록 [로그인] → `CompanyResponse[]` (`type`=EQUIPMENT/MANPOWER만 유효)
### `GET /api/companies/connected-suppliers?type=EQUIPMENT` — 연동 공급사 [ADMIN/BP] → `CompanyResponse[]` (BP가 견적으로 연동한 공급사; 아니면 `BP_ADMIN_ONLY` 403)
### `GET /api/companies/connected-resources?supplierId={id}` — 연동 자원 id [ADMIN/BP] → `{ "<key>": [<long>...] }` <!-- 확인필요: Map 키 이름은 service 반환에 따름 -->
### `GET /api/companies/{id}/bp-users` — BP사 담당자 [ADMIN/BP/공급사] → `UserResponse[]` (role=BP만, `user.dto.UserResponse`)
### `GET /api/companies/{id}/users` — 회사 직원 [ADMIN] → `UserResponse[]`
### `GET /api/companies/{id}/partners` — 거래 이력 공급사 [ADMIN] → `CompanyResponse[]`
### `PATCH /api/companies/{id}/profile` — 회사 프로필 편집 [ADMIN/회사 master]
권한: ADMIN 또는 `is_company_admin && company_id == id`(아니면 `PROFILE_EDIT_DENIED` 403). **Request** (모두 선택): `{ "business_address"(≤255), "business_category"(≤100, 업태), "business_subcategory"(≤200, 종목), "ceo_name"(≤100), "phone"(≤32), "fax"(≤32) }`. **Response** — `CompanyResponse`.

#### `CompanyResponse` (보강, `dto/CompanyResponse.java`)
`id`/`name`/`business_number`, `type`(`BP`/`EQUIPMENT`/`MANPOWER`), `business_address`/`business_category`/`business_subcategory`, `ceo_name`/`phone`/`fax`, `created_at`.

---

> **기존 `DocumentTypes — /api/document-types` 섹션에 추가** — ADMIN 전용 전체 CRUD (별개 경로).

## Document Type Admin — `/api/admin/document-types` [ADMIN] (Phase S-11)

`@PreAuthorize("hasRole('ADMIN')")` (클래스 전체). document_types catalog 운영.

### `GET /api/admin/document-types` — 전체 목록 (`appliesTo, sort_order, id` 정렬) → `DocumentType[]`
### `POST /api/admin/document-types` — 생성
```json
{ "name": "보험증권", "applies_to": "EQUIPMENT", "has_expiry": true, "requires_verification": false, "sort_order": 10, "required": true, "blocks_assignment": false, "default_valid_months": 12, "applies_to_categories": "CRANE,AERIAL_LIFT", "applies_to_person_roles": null }
```
`name`/`applies_to`/`has_expiry`/`requires_verification`/`required`/`blocks_assignment` 필수. 생성 시 `active=true`, `ocr_enabled=false` 고정. **Response 201** — `DocumentType`.
### `PATCH /api/admin/document-types/{id}` — 수정 (null=미변경; `applies_to_*` 빈 문자열이면 매핑 해제; `active` true→activate/false→deactivate) → `DocumentType`. `404 DOCUMENT_TYPE_NOT_FOUND`.
### `DELETE /api/admin/document-types/{id}` — 비활성화(soft, deactivate만) → 204

#### `DocumentType` 핵심 필드: `id`/`name`, `applies_to`, `has_expiry`/`requires_verification`/`required`/`blocks_assignment`/`active`/`ocr_enabled`, `sort_order`, `default_valid_months`, `ocr_extract_type`/`ocr_expiry_field_key`/`verify_endpoint`, `required_fields`(JSON), `applies_to_categories`/`applies_to_person_roles`(CSV), `created_at`/`updated_at`.

---

> **기존 `Notifications — /api/notifications` 섹션에 추가.**

### `POST /api/notifications/read-all` — 전체 읽음 [로그인]
actor 미읽음 알림 모두 읽음 처리. **본문 없음.** **Response**: `{ "count": <int> }`.

---

> **기존 `Role Dashboards — /api/dashboards` 섹션에 추가.**

### `GET /api/dashboard/summary` — 레거시 대시보드 요약
<!-- 레거시: RoleDashboardController(/api/dashboards/*, Phase S-3)로 대체됨. deprecated. -->
로그인(actor 역할에 따라 분기). **Response** — `DashboardSummary`. 신규 클라이언트는 `/api/dashboards/{role}/summary` 사용.

---

## Equipment Timeline — `/api/equipment/{id}/timeline`

### `GET /api/equipment/{id}/timeline` — 장비 이력 (읽기전용) [로그인]
<!-- 주의: 쓰기 경로 없음 — inspections/operations/locations/maintenances/notes 는 현재 시드 데이터만 존재. -->
권한: `EquipmentService.get()`. 5개 이력 컬렉션을 한 번에 반환.
**Response** (`dto/EquipmentTimelineResponse.java`)
```json
{ "inspections": [...], "operations": [...], "locations": [...], "maintenances": [...], "notes": [...] }
```
- inspections: `id`, `inspected_at`, `inspector`, `title`, `result`, `note`, `next_inspection_at`
- operations: `id`, `started_at`, `ended_at`, `site_name`, `description`, `utilization_pct`, `status`
- locations: `id`, `recorded_at`, `location_name`, `note`
- maintenances: `id`, `maintained_at`, `maintainer`, `title`, `description`, `cost`
- notes: `id`, `author_id`, `content`, `created_at`

---

## Health

### `GET /api/health` (공개)
**Response 200**
```json
{ "status": "UP", "service": "skep-backend", "timestamp": "2026-04-30T..." }
```

### `GET /actuator/health` (공개) — Spring Boot 기본
### `GET /actuator/info` (공개)

---

## 스키마

### UserResponse
```jsonc
{
  "id": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "phone": "010-1234-5678",     // null 허용
  "role": "BP",                  // ADMIN | BP | EQUIPMENT_SUPPLIER | MANPOWER_SUPPLIER | WORKER
  "company_id": 1,               // null 허용 (ADMIN, WORKER는 보통 null)
  "is_company_admin": false,
  "enabled": true,
  "created_at": "2026-04-30T13:00:25.086651"
}
```

### CompanyResponse
```jsonc
{
  "id": 1,
  "name": "테스트 BP건설(주)",
  "business_number": "111-11-11111",
  "type": "BP",                  // BP | EQUIPMENT | MANPOWER
  "created_at": "2026-04-30T13:00:24.998793"
}
```

### Role / CompanyType 매핑
| User Role | Company Type | 의미 |
|---|---|---|
| `ADMIN` | (없음) | 시스템 관리자 |
| `BP` | `BP` | 발주사 직원 |
| `EQUIPMENT_SUPPLIER` | `EQUIPMENT` | 장비공급사 직원 |
| `MANPOWER_SUPPLIER` | `MANPOWER` | 인력공급사 직원 |
| `WORKER` | (보통 없음) | 작업자 (조종원/유도원 등) — Phase B+에서 정리 |

---

## Phase별 추가 예정

| Phase | 추가 엔드포인트 | 상태 |
|---|---|---|
| **B. Equipment** | `/api/equipment` CRUD | ✓ |
| **C. Person** | `/api/persons` CRUD (역할 다중) | ✓ |
| **D-1. Document 기본** | `/api/documents` (multipart 업로드) + `/api/document-types` | ✓ |
| **S-1. Site/Participant** | `/api/sites` + `/api/sites/{id}/participants` | ✓ |
| **S-2. Resource Assignment** | `/api/equipment/{id}/assignment` + `/api/persons/{id}/assignment` + `/api/sites/{id}/equipment[-candidates]` + `/api/sites/{id}/persons[-candidates]` + 이력 | ✓ |
| **S-3. Role Dashboard + Audit Log** | `/api/dashboards/{admin,bp,equipment-supplier,manpower-supplier}/summary` + `/api/audit-logs[/recent]` | ✓ |
| **S-4.1. Document Policy** | document_types 정책 컬럼 + documents 검증 컬럼 + 화물운송자격증 시드 + missing_documents 실제 계산 | ✓ |
| **S-4.2. External Verify** | `VerifyClient` (main-api/verify-api 호출) + `POST /api/documents/{id}/verify` + `POST /api/documents/{id}/reject` + graceful fail | ✓ |
| **S-4.3. Auto OCR + Override + History** | upload 시 자동 OCR 트리거 (AFTER_COMMIT @Async), ADMIN override (`DOCUMENTS_BLOCKED` 우회 + 사유 audit), `GET /api/documents/{id}/history`, 프론트 verify dialog (required_fields 동적 입력) | ✓ |
| **S-4.4. Review Queue + Notifications + History UI** | `GET /api/documents/review-queue`, `/api/notifications`, `DocumentHistoryDialog`, V15 notifications 테이블, verify/reject/override 시 자동 알림 발신 | ✓ |
| **S-5. Work Plans** | 작업계획서 도메인 + 자원 컴플라이언스 스냅샷 + 상태 전이 | ✓ |
| **D-2. Expiry 추적** | `/api/documents/expiring` (만료 임박 대시보드) | |
| **D-3. Verification flow** | ADMIN 검증 워크플로 (지금은 단순 toggle만) | |
| **E. Wizard** | (UI만, 백엔드 추가 없음 — D의 API 활용) | |
| **F. OCR** | `/api/documents/{id}/ocr` (verify-api 호출 wrapper) | |

---

## 변경 이력

- 2026-04-30: 초안 작성. Auth + Users + Companies + Health 정리. Phase A 완료 기준.
- 2026-04-30: Phase B — Equipment CRUD 추가. JWT에 `company_id` claim 포함하여 권한 자동 매핑.
- 2026-04-30: Phase C — Person CRUD 추가. 다중 role + supplier type 매핑 검증.
- 2026-04-30: Phase D-1 — Document/DocumentType API 추가. multipart 업로드, polymorphic owner, LocalDiskStorage.
- 2026-05-06: Phase S-1 — Site/Participant API 추가. BP 현장 생성, 공급사 연결/해제, 참여 현장 조회 권한 반영.
- 2026-05-06: Phase S-2 — 자원 현장 배치/해제/이력/후보 API 추가. 자원당 활성 배치 1건 unique 정책. EquipmentResponse/PersonResponse 에 current_site_id/assignment_status 등 추가. 후보 응답에 이전 투입/만료 임박/사용 제한 메타데이터 포함 (작업계획서 추천 기반).
- 2026-05-06: Phase S-3 — 역할별 대시보드 summary API 4종 추가 (`/api/dashboards/admin|bp|equipment-supplier|manpower-supplier/summary`). audit log API 2종 추가 (`/api/audit-logs[/recent]`). 도메인 서비스에 SITE_*/PARTICIPANT_*/EQUIPMENT_*/PERSON_*/DOCUMENT_UPLOADED/DOCUMENT_VERIFIED 액션 자동 기록. before_json/after_json 컬럼은 단순화를 위해 V13 에서 jsonb→text 로 변경.
- 2026-05-06: Phase S-3.1 — 권한 스코프 패치. (1) Document read 정책: BP 는 자기 사이트 ACTIVE 참여 공급사 자원만, WORKER 차단. (2) 공급사 dashboard 만료 카운트를 owner_type+owner_ids 로 좁힘 — 다른 회사 노출 차단. (3) 자원 자동 해제(이동) 도 audit 기록. (4) `DOCUMENT_RENEWED` audit 기록 추가 (updateExpiry). (5) JWT claim 에 `is_company_admin` 추가, AuditLogService 에서 회사 관리자만 회사 범위 로그, 일반 직원은 본인 행동만. (6) `/worker/dashboard` placeholder 페이지 (무한 redirect 차단).
- 2026-05-07: Phase S-4 단계 1 — V14 마이그레이션. document_types 에 정책/검증 라우팅 컬럼 8종(`required`/`blocks_assignment`/`default_valid_months`/`ocr_enabled`/`ocr_extract_type`/`ocr_expiry_field_key`/`verify_endpoint`/`required_fields`) 추가. documents 에 검증 컬럼 7종(`verification_status`/`verified_by`/`verified_at`/`rejected_reason`/`previous_document_id`/`verification_result`/`extracted_data`) 추가. 화물운송자격증 신규 시드. AssignmentService 후보 응답의 `missing_documents` 가 실제 누락 수로 계산됨 (이전엔 0L 하드코딩). DocumentService.setVerified 가 `markVerifiedBy(actor.id())` 호출하여 `verified_by`/`verified_at` 채움. 외부 verify-api/main-api 호출은 단계 2 로 분리.
- 2026-05-07: Phase S-4 단계 2 — 외부 verify-api/main-api 연동. `com.skep.verify.{VerifyClient,VerificationService,VerifyController}` 추가 (skep `LiftonVerifyClient` 흐름 이식). `POST /api/documents/{id}/verify` (자동 OCR + 정부 API 검증) / `POST /api/documents/{id}/reject` (ADMIN 수동 반려). graceful fail (`UPSTREAM_DISABLED` / `UPSTREAM_ERROR` → `OCR_REVIEW_REQUIRED`). `verify.enabled` / `VERIFY_*` 환경변수 추가, 기본 false (개발 환경 보호). `spring-boot-starter-webflux` 의존성 추가.
- 2026-05-07: Phase S-4 단계 2.1 — 운영 안전성 패치. (1) `AssignmentService.ensureAssignmentDocumentsReady` 추가 — `blocks_assignment=true` 필수 서류 미보유/만료/REJECTED 시 `DOCUMENTS_BLOCKED` 400. UI 만 막혔던 우회 차단. (2) candidate 응답의 `missing_documents` 는 `required` 전체, 실제 차단 정책은 `blocks_assignment` 기준으로 분리. (3) Document upload 가 `previous_document_id` 자동 매핑 + `listForOwner` 가 chain head 만 노출 (옛 doc 보존). DocumentRenewDialog 의 옛 doc 삭제 호출 제거. (4) Role dashboard 의 `document_risks` 채움 (만료 임박 + REJECTED + OCR_REVIEW_REQUIRED, 회사 자원 owner_id 로 좁힘, BP 는 자기 사이트 ACTIVE 참여공급사 자원). (5) 프론트 types/document.ts 에 V14 필드, DocumentCard/DocumentSection 에 자동 검증/반려 액션 + `verification_status` 배지.
- 2026-05-07: Phase S-4 단계 3 — 운영 흐름 마무리. (1) Upload AFTER_COMMIT 비동기 자동 검증 트리거 (`AutoVerifyTrigger` + `@EnableAsync` + `DocumentUploadedEvent`). (2) ADMIN override — `AssignRequest` 에 `override`/`override_reason` 추가, `DOCUMENTS_BLOCKED` 우회 시 `OVERRIDE_ADMIN_ONLY`/`OVERRIDE_REASON_REQUIRED` 검사 + audit `after_json` 에 override+사유+missing 기록. (3) `GET /api/documents/{id}/history` — 같은 (owner_type, owner_id, type_id) 모든 버전. (4) 프론트 `DocumentVerifyDialog` — type.required_fields 기반 동적 입력 폼, `extracted_data` prefill. (5) candidate picker 에서 ADMIN 은 blocked 자원도 사유 prompt 후 강제 진행 가능.
- 2026-05-07: Phase S-4 단계 4 — 검토/알림/이력 UI 완성. (1) `GET /api/documents/review-queue` — ADMIN 검토 큐 (OCR_REVIEW_REQUIRED + REJECTED chain head + owner/supplier 메타). (2) V15 `notifications` 테이블 + `com.skep.notification.*` + `GET /api/notifications` + `unread-count` + `POST /{id}/read`. ADMIN 은 전체, 회사 사용자는 직접+회사 broadcast. (3) verify 결과 + reject + override 발생 시 owner_supplier 회사 broadcast 알림 자동 발신 (`DOCUMENT_VERIFIED` / `DOCUMENT_REJECTED` / `DOCUMENT_OCR_REVIEW` / `ASSIGNMENT_OVERRIDDEN`). (4) 프론트 `ReviewQueuePage` (`/admin/document-review`), `NotificationsPage` (`/notifications`), `DocumentHistoryDialog`. Sidebar 알림 메뉴 활성화 + 미읽음 뱃지 (60초 polling) + ADMIN 메뉴에 "서류 검토" 추가.
- 2026-05-07: Phase S-4 단계 4.1 — 배차 차단 정책 마지막 누수 차단 (S-4 완료). (1) `DocumentRepository.findValidVerifiedTypesByOwners` 쿼리에 chain head 필터 (`NOT EXISTS 후속 row`) + `verification_status=VERIFIED` 명시 추가. 옛 VERIFIED 가 최신 REJECTED/OCR_REVIEW_REQUIRED 갱신본에 가려지지 않고 잘못 valid 로 계산되던 우회 차단. (2) `Document.markOcrReviewRequired` 가 `verified=false` 도 함께 설정하도록 수정. UPSTREAM_*시 verified boolean 이 true 로 남아 valid 로 잘못 계산되던 문제 차단.
- 2026-05-06: Phase S-3 방향 확정 — 역할별 대시보드 endpoint 분리와 audit log API 를 다음 구현 대상으로 지정. 상세 설계는 `ROLE_BASED_DASHBOARD_DESIGN.md`.
- 2026-05-13: Phase Bid — 견적/이력/영업 도메인 API. (1) ClientOrg: `GET /api/client-orgs` (active list, 모든 사용자) / `GET /all` / `POST` / `PATCH /{id}` / `DELETE /{id}` (hard delete, FK RESTRICT — 이력 참조시 거절) / `POST /{id}/activate` / `POST /{id}/deactivate` — write 는 모두 ADMIN-only. (2) 자원 ClientOrg 이력: `GET /api/client-org-history/equipment/{id}` / `/person/{id}` (chip + 펼침 용). `POST /equipment/{id}` / `/person/{id}` (수동 등록) / `PATCH /equipment-history/{hid}` / `/person-history/{hid}` / `DELETE` — write 는 ADMIN-only. 작업계획서 STARTED 시 자동 추가 hook (`ResourceHistoryService.recordWorkPlanStart`). (3) 공개입찰: `POST /api/quotations/open-bid` (BP/ADMIN, site 없이 ClientOrg/workLocationText/spec 만으로 발송, mode=OPEN_BID) / `GET /api/quotations/open-bids` (공급사 자기 카테고리 자동 매칭 게시판). (4) 공급사 제안: `POST /api/quotations/{rid}/proposals` (자기 보유 자원 드롭다운 + 일대/월대), `GET /{rid}/proposals` (BP/ADMIN 전체, 공급사 자기만), `GET /proposals/mine`, `PATCH /proposals/{pid}`, `POST /proposals/{pid}/withdraw`, `POST /proposals/{pid}/finalize` (BP 선정 → 작업계획서 자원 자동 생성, count 다 차면 나머지 자동 REJECTED), `POST /{rid}/close` (BP 수동 종료). (5) 영업견적 (공급사→BP): `POST /api/outgoing-quotations` (mode=REGISTERED_BP|EMAIL, PDF 첨부 메일 + 등록 BP 알림), `GET /sent` (공급사 발신함), `GET /inbox` (BP 수신함, 조회 전용).
- 2026-06-01: 코드 기준 전수 점검 — 누락 도메인 일괄 보강. 견적(`/api/quotations` 전체)·공개입찰 제안·비교 스냅샷·배차(차량/인원)·서류묶음·견적서 xlsx/pdf·영업견적·원청기관+이력·보완요청·컴플라이언스 조회·**이행지시(compliance-orders, V46 신규)**·안전점검·작업확인서·전자서명(공개 토큰 + 작업계획서 5슬롯)·DOCX 템플릿/내보내기·OnlyOffice·Worksheet 임시세션·Company Users·Document Type Admin(`/api/admin/document-types`)·Equipment Timeline 추가. 네이밍 예외(raw Map 파싱 `pngBase64`/`resultNotes`/`person_id`) 명시.
- 2026-06-01: **정정** — 위 2026-05-13 항목의 'proposal finalize → 작업계획서 자원 자동 생성'은 이후 정책 변경으로 더 이상 사실이 아니다. TARGETED finalize(`QuotationService.finalize`)·OPEN_BID finalize(`QuotationProposalService.finalize`) 모두 target/proposal 을 `FINAL_ACCEPTED` 로 전이할 뿐 작업계획서/자원을 자동 생성·연결하지 않으며, 작업계획서는 BP가 별도로 작성한다. `finalized_to_work_plan_id` 등은 현재 미사용(死 필드).
