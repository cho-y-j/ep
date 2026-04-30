# SKEP v2 API 명세서

> 마지막 갱신: 2026-04-30 (Phase D-1 완료 시점)
> Base URL: `http://localhost:8081` (로컬), `/api` prefix 공통
> 관련 문서: [ERD](./ERD.md)

## 공통 규칙

### 인증
- **방식**: Bearer JWT (HS384, access 60분 + refresh 14일 rotation)
- **헤더**: `Authorization: Bearer <access_token>`
- **공개 엔드포인트**: `GET /api/health`, `POST /api/auth/{signup,login,refresh}`
- **그 외**: 모두 로그인 필요. ADMIN 전용 엔드포인트는 `[ADMIN]` 표시.

### 네이밍
- **Request/Response 모두 snake_case** (`company_id`, `business_number`)
- 자바 record 필드는 camelCase, Jackson이 자동 변환

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
  "created_at": "2026-04-30T13:24:17.888"
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
  "created_at": "2026-04-30T15:11:58.145482"
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
  "active": true
}
```

---

## Documents — `/api/documents`

장비/인원의 첨부 서류. 파일 + 메타데이터. 권한은 owner(equipment/person) 권한을 그대로 상속.

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
  "owner_type": "EQUIPMENT",
  "owner_id": 2,
  "file_name": "license.pdf",
  "file_size": 12345,
  "content_type": "application/pdf",
  "expiry_date": "2026-12-31",       // null 허용 (해당 type에 만료 없음)
  "verified": false,
  "created_at": "2026-04-30T15:41:49.504"
}
```

### Storage 동작
- 지금: `LocalDiskStorage` (`/app/uploads/{yyyy}/{mm}/{uuid}.bin`)
- Docker volume: `skep_v2_uploads` 마운트
- 나중에 S3로 갈아끼울 때: `FileStorage` 인터페이스 다른 구현체로 교체. DB의 `file_key`만 다른 형식이 되고 코드 다른 부분은 무관.

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
