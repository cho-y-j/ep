# SKEP v2 API 명세서

> 마지막 갱신: 2026-04-30 (Phase A 완료 시점)
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

| Phase | 추가 엔드포인트 |
|---|---|
| **B. Equipment** | `/api/equipment` CRUD |
| **C. Person** | `/api/persons` CRUD (역할 다중) |
| **D. Document** | `/api/documents/upload` (multipart), `/api/document-types`, `/api/documents/{id}/file` |
| **E. Wizard** | (UI만, 백엔드 추가 없음 — D의 API 활용) |
| **F. OCR** | `/api/documents/{id}/ocr` (verify-api 호출 wrapper) |

---

## 변경 이력

- 2026-04-30: 초안 작성. Auth + Users + Companies + Health 정리. Phase A 완료 기준.
