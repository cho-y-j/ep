# 장비 종류 마스터 데이터화 설계 (2026-07-14)

> 목표: 어드민에서 ①장비종류(차종) 추가/수정/삭제 ②종류 선택→전체 서류 나열→**종류별 필수/선택/해당없음** 지정 ③등록 시 그대로 반영. 서류는 이미 `document_types` 테이블로 동적.

## 핵심 결정 (사용자 확정 필요)
- **enum→String 필요 여부**: `Equipment.category`·`QuotationRequest.equipmentCategory`·Create DTO가 `EquipmentCategory` **enum**이라, Jackson이 enum에 없는 새 코드를 역직렬화하면 400 → **"새 종류 추가 후 즉시 장비 등록"은 enum→String 전환이 불가피**(backend ~30파일).
  - **Phase A(저위험)**: enum 유지 + 기존 25종 rename/순서/숨김 + **종류별 서류 junction 체크리스트**. 새 코드 추가는 안 됨(가끔 코드 한 줄로).
  - **Phase B(고위험)**: enum→String 전환까지 → 어드민에서 새 코드도 추가. 견적·배차 매칭(`!=`/`==` 비교) 회귀 위험 → **매칭 단위테스트 동반 필수**.

## 데이터 모델
- **equipment_type**(V90): `code VARCHAR(32) PK`(현 enum name), `name`, `grp`(건설기계/차량/기타), `sort_order`, `active`. 25종 시드. FK 하드로 안 검(앱검증+soft delete).
- **equipment_type_doc_requirement**(V91) junction: `(equipment_type_code, document_type_id, required)` PK. 행존재+required=필수, +false=선택, **행없음=해당없음**(3상태). → 현 글로벌 `required`의 한계(종류별 다름 불가) 해소.
  - 백필: 각 EQUIPMENT `document_types` t → applies_to_categories NULL이면 전종류, CSV면 각 코드에 `(code,t.id,t.required)`.

## 사용처 (enum→String 시 위험도)
- **최상위 위험 — 매칭 `!=`/`==` (테스트 0 커버)**: `QuotationProposalService.java:104`, `QuotationService.java:142/385`, `EquipmentService.java:148/165`, `ComplianceService.java:291`, `WorkPlanService.java:1157`. → `.equals`/`Objects.equals`, null 의미 보존.
- DTO 13개(타입만 String), `.name()` 10곳(제거), 라벨 3곳(`NotificationLabels:19`·`DashboardService:138`·`QuotationService:531` → DB name 조회로 이전), 시드 `DemoDataSeeder`.
- FE: `types/equipment.ts`(유니온·라벨맵·배열) 동적화, 라벨 미폴백 ~15곳, 드롭다운 5곳, `DocumentTypeAdminPage`는 "서류→종류"라 "종류→서류" 신규 화면 필요.

## 단계 순서 (위험 낮은 것부터)
① equipment_type 테이블+시드(V90) → ② 종류 CRUD 어드민 + `GET /api/equipment-types` → ③ junction(V91)+백필+체크리스트 어드민+컴플라이언스 배선(EQUIPMENT 분기, PERSON/COMPANY 불변) → ④ FE 동적 드롭다운·라벨(`useEquipmentTypes` 훅) → **⑤ enum→String 원자적 전환(단일 커밋, mvn compile 전량 수렴 + 매칭 단위테스트)** → ⑥ 등록·매칭 회귀 스모크.
- ①~④는 enum 무관 → 저위험. ⑤가 Phase B(고위험).

## 검증
`mvn -q compile` + `mvn test`(SkepApplicationTests=Flyway+JPA 부팅) + 매칭 단위테스트 신설, FE `typecheck/build/lint`. 백필 후 샘플 장비 컴플라이언스 = 전환 전과 동일 대조.
