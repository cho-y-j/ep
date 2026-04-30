# SKEP v2 ERD

> 마지막 갱신: 2026-04-30 (Phase D-1 완료)
> 다이어그램은 Mermaid (GitHub에서 자동 렌더). 마이그레이션 SQL: `backend/src/main/resources/db/migration/`

---

## 현재 스키마 (Phase D-1까지)

```mermaid
erDiagram
    users ||--o{ refresh_tokens : "has"
    companies ||--o{ users : "employs"
    companies ||--o{ equipment : "owns (type=EQUIPMENT)"
    companies ||--o{ persons : "employs (type=EQUIPMENT|MANPOWER)"
    persons ||--o{ person_roles : "has"
    document_types ||--o{ documents : "classifies"
    persons ||..o{ documents : "owner_type=PERSON"
    equipment ||..o{ documents : "owner_type=EQUIPMENT"

    companies {
        bigint id PK
        varchar(255) name
        varchar(32) business_number UK
        varchar(32) type "BP | EQUIPMENT | MANPOWER"
        timestamp created_at
        timestamp updated_at
    }

    users {
        bigint id PK
        varchar(255) email UK
        varchar(255) password "BCrypt"
        varchar(100) name
        varchar(32) phone
        varchar(32) role "ADMIN | BP | EQUIPMENT_SUPPLIER | MANPOWER_SUPPLIER | WORKER"
        bigint company_id FK "nullable"
        boolean is_company_admin
        boolean enabled "false=승인대기/비활성화"
        timestamp created_at
        timestamp updated_at
    }

    refresh_tokens {
        bigint id PK
        bigint user_id FK
        varchar(255) token_hash UK "SHA-256"
        timestamp expires_at
        boolean revoked
        timestamp created_at
    }

    equipment {
        bigint id PK
        bigint supplier_id FK "companies.id, type=EQUIPMENT 강제"
        varchar(32) vehicle_no "nullable (어태치먼트 등)"
        varchar(32) category "EXCAVATOR | WHEEL_LOADER | CRANE | FORKLIFT | DOZER | GRADER | AERIAL_LIFT | PUMP_TRUCK | ATTACHMENT"
        varchar(100) model
        varchar(100) manufacturer
        int year
        timestamp created_at
        timestamp updated_at
    }

    persons {
        bigint id PK
        bigint supplier_id FK "companies.id, type=EQUIPMENT or MANPOWER"
        varchar(100) name
        date birth "nullable"
        varchar(32) phone "nullable"
        timestamp created_at
        timestamp updated_at
    }

    person_roles {
        bigint person_id PK,FK
        varchar(32) role PK "OPERATOR | WORK_DIRECTOR | GUIDE | FIRE_WATCH | SIGNALER | INSPECTOR | SITE_MANAGER"
    }

    document_types {
        bigint id PK
        varchar(100) name "예: 운전면허증/자동차등록증"
        varchar(16) applies_to "PERSON | EQUIPMENT"
        boolean has_expiry "true면 업로드 시 만료일 필수"
        boolean requires_verification "ADMIN 검증 표시 필요 여부"
        int sort_order
        boolean active
        timestamp created_at
        timestamp updated_at
    }

    documents {
        bigint id PK
        bigint document_type_id FK
        varchar(16) owner_type "PERSON | EQUIPMENT — polymorphic"
        bigint owner_id "persons.id or equipment.id"
        varchar(255) file_key "Storage key (예: 2026/04/uuid.bin)"
        varchar(255) file_name "원본 파일명"
        bigint file_size
        varchar(100) content_type
        date expiry_date "nullable"
        boolean verified "ADMIN 검증 표시"
        bigint uploaded_by FK "users.id, nullable"
        timestamp created_at
        timestamp updated_at
    }
```

### Role / CompanyType 매핑 (Person)
| PersonRole | 라벨 | 허용 supplier type |
|---|---|---|
| `OPERATOR` | 조종원 | EQUIPMENT |
| `WORK_DIRECTOR` | 작업지휘자 | MANPOWER |
| `GUIDE` | 유도원 | MANPOWER |
| `FIRE_WATCH` | 화기감시자 | MANPOWER |
| `SIGNALER` | 신호수 | MANPOWER |
| `INSPECTOR` | 점검원 | MANPOWER (잠정) |
| `SITE_MANAGER` | 소장 | MANPOWER (잠정) |

> 한 인원이 여러 role 가능 (다대다). EQUIPMENT 공급사는 OPERATOR만, MANPOWER 공급사는 그 외 6개. BP사는 인원 등록 불가.

### 관계
- `companies (1) ─── (0..N) users` — 한 회사에 여러 직원. ADMIN/WORKER는 company_id NULL 허용.
- `users (1) ─── (0..N) refresh_tokens` — 토큰 rotation 이력 + 활성 토큰. ON DELETE CASCADE.
- `companies (1) ─── (0..N) equipment` — type=EQUIPMENT 회사만 장비 보유 (서비스 레이어 검증). ON DELETE RESTRICT (장비 있는 회사 못 지움).

### Enum 값
- **Role**: `ADMIN`, `BP`, `EQUIPMENT_SUPPLIER`, `MANPOWER_SUPPLIER`, `WORKER`
- **CompanyType**: `BP`, `EQUIPMENT`, `MANPOWER`
- **매핑**: `BP↔BP`, `EQUIPMENT_SUPPLIER↔EQUIPMENT`, `MANPOWER_SUPPLIER↔MANPOWER`, `ADMIN/WORKER → 회사 없음`

### 마이그레이션
| 버전 | 파일 | 내용 |
|---|---|---|
| V1 | `V1__init_users.sql` | users + refresh_tokens |
| V2 | `V2__add_companies.sql` | companies + users.company_id FK |
| V3 | `V3__add_equipment.sql` | equipment + supplier_id FK to companies |
| V4 | `V4__add_persons.sql` | persons + person_roles (다대다) |
| V5 | `V5__add_documents.sql` | document_types(시드 12종) + documents (polymorphic owner) |

---

## Phase B+ 예정 스키마

> 점선은 아직 안 만든 테이블. 설계 의도 공유용.

```mermaid
erDiagram
    companies ||--o{ equipment : "owns"
    companies ||--o{ persons : "employs"
    equipment ||--o{ documents : "has"
    persons ||--o{ documents : "has"
    persons }o--o{ person_roles : "plays"
    document_types ||--o{ documents : "classifies"

    equipment {
        bigint id PK
        bigint supplier_id FK "company_id, type=EQUIPMENT"
        varchar vehicle_no
        varchar category "굴삭기|크레인|지게차..."
        varchar model
        varchar manufacturer
        int year
        timestamp created_at
    }

    persons {
        bigint id PK
        bigint supplier_id FK "company_id"
        varchar name
        date birth
        varchar phone
        bigint user_id FK "nullable, 본인 로그인 가능 시"
        timestamp created_at
    }

    person_roles {
        bigint person_id PK,FK
        varchar role PK "조종원|작업지휘자|유도원|화기감시자|신호수|점검원|소장"
    }

    document_types {
        bigint id PK
        varchar name "운전면허증|자동차등록증|보험증권..."
        varchar applies_to "PERSON | EQUIPMENT"
        boolean has_expiry
        boolean requires_ocr
    }

    documents {
        bigint id PK
        bigint owner_id "person_id or equipment_id"
        varchar owner_type "PERSON | EQUIPMENT"
        bigint document_type_id FK
        varchar file_url "Storage 추상화 (local→S3)"
        date expiry_date "nullable"
        boolean verified
        jsonb ocr_result "nullable"
        timestamp created_at
    }
```

### 설계 의도

**Person.role을 별도 테이블로 분리한 이유**
- 한 사람이 여러 역할 가능 (예: 조종원 + 신호수 둘 다)
- `persons.roles` 컬럼을 `varchar[]`로 둘 수도 있지만 마스터 테이블 + JOIN이 추후 통계/필터에 유리

**Document.owner를 polymorphic (owner_type + owner_id)으로 둔 이유**
- 사람 서류 / 장비 서류가 같은 흐름 (업로드 + 만료추적 + OCR)이라 테이블 통합이 효율적
- 단점: FK 제약 못 검. → 코드 레벨에서 검증 + DB CHECK 제약 추가 가능

**document_types를 마스터 테이블로**
- 새 서류 종류 추가 시 row 추가만으로 동작 (운영 중 추가 가능)
- has_expiry, requires_ocr 같은 메타로 UI/검증 분기

**file_url은 Storage 추상화 결과**
- 지금: `local:///app/uploads/{path}` 형태
- 나중: `s3://bucket/key`. URL만 갈아끼우면 끝

---

## 변경 이력

- 2026-04-30: 초안 작성. Phase A 스키마 (users, refresh_tokens, companies). Phase B+ 설계 윤곽.
- 2026-04-30: Phase B — equipment 테이블 추가.
- 2026-04-30: Phase C — persons + person_roles 테이블 추가, role-supplier type 매핑.
- 2026-04-30: Phase D-1 — document_types(seed 12종) + documents (polymorphic PERSON/EQUIPMENT). 파일은 LocalDiskStorage(/app/uploads, docker volume).
