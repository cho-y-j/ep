# 장비 및 인력 상세 페이지 API/UI 설계

## 현재 구현 확인

### 대표 사진 API

장비와 인력 모두 대표 사진 업로드, 조회, 삭제 API가 구현되어 있다.

| 대상 | 메서드 | 경로 | 용도 |
|---|---|---|---|
| 장비 | `POST` | `/api/equipment/{id}/photo` | 대표 이미지 업로드 |
| 장비 | `GET` | `/api/equipment/{id}/photo` | 대표 이미지 조회 |
| 장비 | `DELETE` | `/api/equipment/{id}/photo` | 대표 이미지 삭제 |
| 인력 | `POST` | `/api/persons/{id}/photo` | 대표 이미지 업로드 |
| 인력 | `GET` | `/api/persons/{id}/photo` | 대표 이미지 조회 |
| 인력 | `DELETE` | `/api/persons/{id}/photo` | 대표 이미지 삭제 |

업로드는 `multipart/form-data`이고 form field 이름은 `file`이다. 백엔드는 `content_type`이 `image/*`인 파일만 허용한다.

응답은 기존 상세 응답과 동일하며 `has_photo`가 갱신된다.

```json
{
  "id": 1,
  "supplier_id": 2,
  "model": "ZX350-7",
  "has_photo": true,
  "expiring_count": 0,
  "created_at": "2026-04-30T13:24:17.888"
}
```

저장 구조도 구현되어 있다.

| 테이블 | 컬럼 |
|---|---|
| `equipment` | `photo_key`, `photo_content_type` |
| `persons` | `photo_key`, `photo_content_type` |

### 첨부서류 API

첨부서류는 장비와 인력을 `owner_type`, `owner_id`로 공통 처리한다.

| 메서드 | 경로 | 용도 |
|---|---|---|
| `GET` | `/api/document-types?appliesTo=EQUIPMENT\|PERSON` | 서류 종류 조회 |
| `GET` | `/api/documents?ownerType=EQUIPMENT&ownerId=1` | 대상별 서류 목록 |
| `POST` | `/api/documents` | 서류 업로드 |
| `GET` | `/api/documents/{id}/file` | 파일 조회 |
| `PATCH` | `/api/documents/{id}/expiry` | 만료일 변경 |
| `PATCH` | `/api/documents/{id}/verified` | 관리자 검증 상태 변경 |
| `DELETE` | `/api/documents/{id}` | 서류 삭제 |

업로드 파라미터:

```text
ownerType=EQUIPMENT | PERSON
ownerId=1
documentTypeId=7
expiryDate=2026-12-31
file=<multipart file>
```

첨부서류 카드 UI에 필요한 값은 현재 `DocumentResponse`에 대부분 포함되어 있다.

| UI 요소 | 응답 필드 |
|---|---|
| 썸네일 | `content_type`, `/api/documents/{id}/file` |
| 서류명 | `document_type_name` |
| 만료일 | `expiry_date` |
| 상태 배지 | `verified`, `expiry_date`, `document_type_has_expiry` |

## 현재 부족한 부분

레퍼런스 상세 페이지의 아래 데이터는 현재 별도 API/DB가 없어서 프론트에서 임시 값으로 표현 중이다.

| 영역 | 현재 상태 | 필요한 백엔드 |
|---|---|---|
| 상태 배지 | `expiring_count`, 필드 존재 여부로 임시 산출 | 장비/인력 상태 컬럼 또는 상태 산출 API |
| 가동률/배정률 | 프론트 임시 계산 | 기간별 집계 API |
| 점검이력 | 더미 리스트 | 점검 이력 테이블/API |
| 가동이력 | 더미 리스트 | 장비 가동/인력 투입 이력 테이블/API |
| 위치이력 | 더미 리스트 | 위치 로그 테이블/API |
| 장비 썸네일 여러 장 | 대표 사진 1장만 지원 | 갤러리 이미지 테이블/API |
| 담당자 | 로그인 사용자 표시 | 장비/인력 담당자 매핑 |

## 제안 API

### 1. 상세 화면 통합 조회

상세 첫 화면 렌더링을 줄이기 위한 통합 API를 둔다. 기존 CRUD API는 유지하고, 대시보드형 상세 전용 API를 추가한다.

```text
GET /api/equipment/{id}/detail
GET /api/persons/{id}/detail
```

장비 응답 예시:

```json
{
  "id": 1,
  "code": "EQ-2026-0001",
  "name": "굴삭기 ZX350",
  "status": "RUNNING",
  "operation_rate": 78,
  "basic": {
    "model": "ZX350-7",
    "manufacturer": "현대건설기계",
    "year": 2023,
    "manager_name": "김민수",
    "supplier_name": "서울중장비"
  },
  "location": {
    "site_name": "서울 A현장",
    "place_name": "현장 내 장비 주차장",
    "gps_status": "NORMAL",
    "updated_at": "2026-05-04T09:20:00"
  },
  "summary": {
    "operating_hours": 980,
    "standby_hours": 220,
    "down_hours": 56,
    "expiring_document_count": 1
  }
}
```

인력 응답은 `operation_rate` 대신 `assignment_rate`를 사용한다.

### 2. 상태 관리

```text
PATCH /api/equipment/{id}/status
PATCH /api/persons/{id}/status
```

장비 상태 enum:

```text
RUNNING
NEEDS_INSPECTION
BROKEN
UNUSED
```

인력 상태 enum:

```text
WORKING
ON_LEAVE
INACTIVE
NEEDS_DOCUMENT_CHECK
```

### 3. 갤러리 이미지

대표 사진 1장만으로는 레퍼런스의 썸네일 리스트를 온전히 표현하기 어렵다.

```text
GET /api/equipment/{id}/photos
POST /api/equipment/{id}/photos
PATCH /api/equipment/{id}/photos/{photoId}/primary
DELETE /api/equipment/{id}/photos/{photoId}

GET /api/persons/{id}/photos
POST /api/persons/{id}/photos
PATCH /api/persons/{id}/photos/{photoId}/primary
DELETE /api/persons/{id}/photos/{photoId}
```

테이블 제안:

```sql
CREATE TABLE owner_photos (
    id BIGSERIAL PRIMARY KEY,
    owner_type VARCHAR(16) NOT NULL,
    owner_id BIGINT NOT NULL,
    file_key VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    caption VARCHAR(100),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    uploaded_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_owner_photos_owner ON owner_photos(owner_type, owner_id);
```

### 4. 점검이력

```text
GET /api/equipment/{id}/inspections
POST /api/equipment/{id}/inspections
GET /api/persons/{id}/inspections
POST /api/persons/{id}/inspections
```

응답 필드:

```json
{
  "id": 1,
  "inspection_type": "정기 점검",
  "inspected_at": "2026-04-10",
  "inspector_name": "김민수",
  "result": "NORMAL",
  "memo": "이상 없음"
}
```

### 5. 가동/투입 이력

```text
GET /api/equipment/{id}/operations?from=2026-05-01&to=2026-05-31
POST /api/equipment/{id}/operations
GET /api/persons/{id}/assignments?from=2026-05-01&to=2026-05-31
POST /api/persons/{id}/assignments
```

장비 가동 이력:

```json
{
  "id": 1,
  "site_name": "서울 A현장",
  "work_name": "토공 구간 작업",
  "started_at": "2026-05-03T08:00:00",
  "ended_at": "2026-05-03T17:00:00",
  "status": "COMPLETED"
}
```

### 6. 위치이력

```text
GET /api/equipment/{id}/locations
POST /api/equipment/{id}/locations
GET /api/persons/{id}/locations
POST /api/persons/{id}/locations
```

응답 필드:

```json
{
  "id": 1,
  "site_name": "서울 A현장",
  "place_name": "장비 주차장",
  "latitude": 37.5665,
  "longitude": 126.9780,
  "source": "GPS",
  "created_at": "2026-05-04T09:20:00"
}
```

## UI 설계

### 상세 상단

좌측은 `PhotoGallery` 컴포넌트로 구성한다.

- 대표 이미지
- 썸네일 리스트
- 이미지 추가/변경/삭제 버튼
- 현재는 대표 사진 API 사용
- 갤러리 API가 추가되면 `GET /photos` 기반으로 교체

우측은 기본 정보 영역이다.

- 제목: 모델명/차량번호 또는 인력명
- 상태 배지
- 장비: 모델명, 제조사, 연식, 담당자, 차량번호, 공급사, 등록일
- 인력: 이름, 직무, 소속, 담당자, 연락처, 생년월일, 등록일
- 장비: 가동률 progress bar
- 인력: 배정률 progress bar

### 탭 구조

공통 탭:

```text
개요
점검이력
가동이력
위치이력
첨부서류
```

인력 상세에서도 화면 용어는 요구사항에 맞춰 동일 탭을 유지하되, 내부 데이터는 `가동이력 = 투입/배정 이력`으로 해석한다.

### 첨부서류 카드

카드에는 아래 정보를 고정 노출한다.

- 썸네일: 이미지면 실제 미리보기, PDF/기타 파일은 파일 아이콘
- 서류명: `document_type_name`
- 만료일: `expiry_date` 또는 `-`
- 상태 배지:
  - `verified=true`: 검증완료
  - 만료일 지남: 만료됨
  - 30일 이내: 만료임박
  - 그 외: 확인대기

### 권한

현재 권한 모델을 그대로 따른다.

| 역할 | 대표 사진 | 첨부서류 | 검증 |
|---|---|---|---|
| ADMIN | 전체 수정 가능 | 전체 수정 가능 | 가능 |
| EQUIPMENT_SUPPLIER | 본인 회사 장비/조종원 수정 | 본인 회사 대상 수정 | 불가 |
| MANPOWER_SUPPLIER | 본인 회사 인력 수정 | 본인 회사 대상 수정 | 불가 |
| BP/WORKER | 조회 중심 | 조회 중심 | 불가 |

## 프론트 적용 상태

현재 프론트는 다음 API를 실제로 사용한다.

- 장비 사진: `/api/equipment/{id}/photo`
- 인력 사진: `/api/persons/{id}/photo`
- 첨부서류 목록/업로드/열람/검증/삭제: `/api/documents`
- 서류 종류: `/api/document-types`

아직 API가 없는 상태/가동률/이력 데이터는 화면 구조를 먼저 맞추기 위해 프론트 임시 모델로 처리되어 있다. 운영 데이터로 전환하려면 위 제안 API와 테이블을 추가한 뒤 상세 페이지의 더미 `HistoryList`와 rate 계산 함수를 API 응답으로 교체하면 된다.
