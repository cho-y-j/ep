# 협력사(하위공급사) 기능 — 마스터 구현계획 (development 실행용)

> planning(Fable) 산출. 오케스트레이터가 사용자 "자동 진행 + 합의된 기본값" 지시에 따라 아래 5개 결정을 **기본안으로 확정**함(veto 시 변경):
> 1. 부모의 자식 자원 **수정/삭제 = 현행 유지**(자식 로그인이 수정). 확장 안 함.
> 2. 부모의 자식 자원 **서류 직접 업로드 = 미허용 유지**(증분4 링크로 우회).
> 3. **월대 환산 = 30일=1개월 일할(pro-rata)**.
> 4. 정산 **기간 필터 = v1 없음(전체 조회)**.
> 5. 증분3 **진입점 = 자원 상세의 "보완 요청" 버튼만**(별도 심사 보드 후순위).

---

## 1. 목표 (성공 기준)

| 증분 | 완료 판정 기준 (라이브 동작) |
|---|---|
| 1. 대행등록 검증 | 부모가 자식 소유로 장비/인원 등록 → 자식 로그인에 보임 → 부모가 자식 자원을 자기 명의로 배차 → BP에는 부모 명의만, 자식에게는 자기 귀속분만. 형제/타사 차단 403 |
| 2. 정산뷰 | `GET /api/settlements/summary`가 소유자별(본인/협력사) 그룹으로 배차 단가×계약기간 금액 반환. 부모=본인+협력사, 자식=본인 것만, BP=403. 수수료 필드 없음 |
| 3. 심사/빠꾸 | 부모(EQUIPMENT_SUPPLIER)가 자식 자원 서류에 보완요청 생성 가능(`requester_role=EQUIPMENT_SUPPLIER` 저장) → 자식이 확인 → 업로드 시 자동 RESOLVED + 부모 알림. BP 기존 흐름 무회귀 |
| 4. 링크 업로드 | 부모가 자식 소유 자원에 수집링크 생성 → 무로그인 토큰 페이지에서 sort_order 순 업로드 → 서류가 자식 소유 자원에 귀속, OPEN 보완요청 자동 RESOLVED |

## 2. 확정된 가정
- BP 노출은 이미 요구사항대로: `DispatchedEquipmentResponse`/`DispatchedPersonResponse`에 `sub_supplier_company_id` 필드 없음(`dto/DispatchedEquipmentResponse.java:7-25`, `DispatchedPersonResponse.java:7-19`) → BP는 부모 명의만. 증분1은 검증만.
- JSON 전역 SNAKE_CASE(`application.yml:7`) — DTO는 camelCase record.
- 신규 엔드포인트는 `SecurityConfig.anyRequest().authenticated()`(`SecurityConfig.java:67`)로 자동 보호 — 역할 체크는 서비스/컨트롤러.
- 정산 원천 = 배차 행(`quotation_dispatched_equipments/persons`)만. `field_deployment_requests`는 스코프 제외.

## 3. 영향 범위 (재사용 = 무변경)
- `company/CompanyService.java:43-58` — `selfAndChildren`/`listChildren` (모든 증분 스코프 계산기)
- `company/CompanyController.java:91-106` — `POST/GET /api/companies/children`
- `quotation/dispatch/DispatchedEquipmentService.java`·`DispatchedPersonService.java` — 배차(증분1 검증 대상, 무변경)
- `collection/PublicCollectionController.java` — 무로그인 업로드(증분4)
- `frontend/src/features/company/useSubSuppliers.ts` — 자식 목록 훅(증분2·3·4 재사용)

---

## 증분1 — 대행등록 라이브 검증 (코드 변경 0)

이미 구현된 체인(V77 + 대행등록) 실데이터 end-to-end 확인. 실패 발견 시에만 수정.

검증 대상(구현 완료 확인됨): `EquipmentService.resolveCreateSupplier`(`:323-340`), `PersonService.resolveCreateSupplier`(`:304-321`), 목록/열람 확장(`EquipmentService.java:156-166,342-350`/`PersonService.java:119-128,323-344`), 배차 자식 허용+`subOwnerOrNull`(`DispatchedEquipmentService.java:111-119,126,193-196`/`DispatchedPersonService.java:75-84,92`), 가시성(`DispatchedEquipmentRepository.findVisibleForSupplier:19-23`), 프론트(`EquipmentCreateForm.tsx:75-90`·`PersonCreateForm.tsx:70-80`, `DispatchSendDialog.tsx:64`).

**검증 시나리오 (qa, dev-local):** `docker compose stop backend frontend` → `./dev-local/backend.sh`(8091)+`./dev-local/frontend.sh`(5185). 계정 전부 `test1234`.
1. equipment1 → `/sub-suppliers` → "협력사A"(type=EQUIPMENT, 관리자 `sub1@example.com`/`test1234`) 등록. `GET /api/companies/children`에 표시.
2. equipment1 → `/equipment` 신규 → "소유 공급사" select에 "협력사A" → 선택 등록. `GET /api/equipment` 응답 `supplier_id`=협력사A. 인원도 동일.
3. sub1 로그인 → `/equipment`에 그 장비 보임, 부모 회사 장비 안 보임.
4. (negative) equipment1이 자식 장비 PATCH → 403. sub1은 성공.
5. bp1 → equipment1 지정 TARGETED 견적 → equipment1 제안 → bp1 finalize → equipment1이 DispatchSendDialog에서 자식 장비 선택, 일대 500,000 send. 응답 `supplier_company_id`=부모.
6. bp1 `GET /api/quotations/{id}/dispatched` → 부모 명의, 응답에 `sub_supplier_company_id` 키 없음.
7. sub1 같은 GET → 자기 귀속 행 성공.
8. `psql -p 15433` → 해당 배차 행 `supplier_company_id`=부모, `sub_supplier_company_id`=협력사A.
9. (negative) manpower1이 `POST /api/equipment` `supplier_id`=협력사A → 403.

---

## 증분2 — 소유자별 투입 정산 요약 뷰 (신규, read-only)

**금액 공식 (확정):**
- 대상: `quotation_dispatched_equipments`+`quotation_dispatched_persons` 전체(FINAL_ACCEPTED 후에만 생성됨 → 상태필터 불필요). `quotation_requests` 조인으로 기간.
- `period_days = DAYS.between(work_period_start, work_period_end) + 1`.
- 행 금액: `monthly_price != null` → `amount = round(monthly_price * period_days / 30.0)`, basis=MONTHLY; 아니고 `daily_price != null` → `amount = daily_price * period_days`, basis=DAILY; 둘 다 null → amount=null; 둘 다 → 월대 우선.
- OT 단가(장비만): **표기만, 합계 미포함**.
- 행 소유자: `owner_company_id = coalesce(sub_supplier_company_id, supplier_company_id)`.

**만들 것:**
1. 신규 `backend/.../com/skep/settlement/dto/SettlementDtos.java`: records `SettlementItem`(resourceType, dispatchId, resourceId, resourceLabel, quotationRequestId, siteId, siteName, bpCompanyId, bpCompanyName, workPeriodStart/End, periodDays, dailyPrice/otDailyPrice/monthlyPrice/otMonthlyPrice, amountBasis, amount, supplierCompanyId, dispatchedByParent, sentAt), `OwnerSettlement`(ownerCompanyId, ownerCompanyName, isSelf, items, totalAmount, itemCount), `SettlementSummaryResponse`(owners, grandTotal).
2. 신규 `settlement/SettlementService.java`: `summary(actor, companyIdParam)` — ADMIN=param필수, EQUIPMENT/MANPOWER_SUPPLIER=본인, else 403. `scope=companyService.selfAndChildren(companyId)` → 신규 repo 쿼리 2개 → `QuotationRequestRepository.findAllById`로 기간/사이트/BP, 이름 라벨(라벨 규칙 `DispatchedEquipmentService.java:199-201` 복제) → 공식 → owner별 그룹. `dispatched_by_parent = (sub_supplier_company_id != null && owner==actor회사)`.
3. 신규 `settlement/SettlementController.java`: `@GetMapping("/api/settlements/summary")`, `@RequestParam(required=false) Long companyId`.
4. 수정 `DispatchedEquipmentRepository.java`+`DispatchedPersonRepository.java`: `findAllVisibleForSupplier(supplierIds, selfId)` = `where supplierCompanyId in :supplierIds or subSupplierCompanyId = :selfId`.
5. 신규 `frontend/src/features/settlement/SettlementPage.tsx`: 소유자별 접이식 섹션(본인 먼저)+행 테이블+합계. 고지문구 "금액은 계약 투입조건(단가×계약기간) 기준 참고치이며 수수료·실출역 미반영". AppShell/UI킷 재사용.
6. 수정 `frontend/src/App.tsx`: `/settlements` 라우트(roles EQUIPMENT_SUPPLIER,MANPOWER_SUPPLIER,ADMIN).
7. 수정 `frontend/src/components/layout/Sidebar.tsx`: 공급사 섹션(:133-143)에 `{label:'투입 정산', to:'/settlements'}`.

**검증:** 견적 07-01~07-31(period_days=31), 자식 장비 일대 500,000 → amount=15,500,000. 월대 9,000,000만 → round(9,000,000×31/30)=9,300,000. equipment1 `/settlements` owner 2블록(본인+협력사A). sub1 → 협력사A만, dispatched_by_parent=true. bp1 → 403. commission/fee 필드 부재. 단가 null → "단가 미입력"(500 아님).

---

## 증분3 — 서류 심사/빠꾸 계층화 (부모→자식)

**함정(선행):** `document_supplement_requests.requester_role` = `VARCHAR(16)`(`V24__document_supplement_requests.sql:8`)인데 `EQUIPMENT_SUPPLIER`=18자 → INSERT 실패. 마이그레이션 필수.

**만들 것:**
1. 신규 `backend/src/main/resources/db/migration/V78__supplement_requester_role_widen.sql`: `ALTER TABLE document_supplement_requests ALTER COLUMN requester_role TYPE VARCHAR(32);`
2. 수정 `supplement/DocumentSupplementRequest.java:27`: `length=16` → `length=32`.
3. 수정 `supplement/DocumentSupplementService.java`: 생성자에 `CompanyService` 주입. `createRow`(:132-135) role 게이트에 supplier 분기 추가 — `isSupplier=(EQUIPMENT_SUPPLIER||MANPOWER_SUPPLIER)`, supplier면 `companyService.listChildren(actor.companyId())`에 `supplierCompanyId` 포함 여부로 부모→직속자식만 허용(아니면 403 NOT_CHILD_SUPPLIER). **기존 BP 블록(:145) 무변경.** `list()`(:208-210) supplier 분기 = target행+요청자회사행 합집합(`findByRequesterCompanyIdOrderByIdDesc` 재사용, dedupe). `ensureCanView`(:290-296) supplier 허용. `cancel`(:230-233)의 BP한정을 "요청자 같은 회사"로 일반화(ADMIN 무변경).
4. 프론트: 신규 `frontend/src/features/document/SupplementRequestDialog.tsx`(타입 선택+사유 → `POST /api/document-supplements`). 수정 `EquipmentDetailPage.tsx`·`PersonDetailPage.tsx`(자원 supplier_id가 useSubSuppliers 목록에 있으면 "보완 요청(빠꾸)" 버튼). 수정 `compliance/DocumentManagementPage.tsx`(보낸요청 탭 노출조건 `!isSupplier||subSuppliers.length>0`, sent 필터 `target_supplier_company_id !== user.company_id`). 기존 BP 필터(:62-65) 무변경.

**재사용:** 상태기계(OPEN→RESOLVED/CANCELLED), 자동 resolve(`onDocumentUploaded:245-271`+`DocumentService.java:248-254,308-310`), 중복방지(:175-180), 알림, 자식 받은요청 화면(`SupplierReceivedPage.tsx:55`) — 전부 무변경 동작.

**검증:** equipment1 자식 장비 "보완 요청" → 201, DB `requester_role='EQUIPMENT_SUPPLIER'`(V78 검증). sub1 받은요청+알림. sub1 업로드 → RESOLVED+부모 알림+문서 VERIFIED. equipment1 보낸요청 취소. (neg) 타사 자원 요청 403, 자식이 부모 자원 요청 403. **회귀: bp1 기존 3종(DocumentManagementPage, MissingDocsDialog batch, DocumentBundleSection batch) 동작.**

---

## 증분4 — 링크 업로드 (부모가 자식 자원 대상 수집링크)

**만들 것:**
1. 수정 `collection/DocumentCollectionService.java` `ensureOwnsResource`(:237-250, ~:247): `!supplierId.equals(actor.companyId())` → `!companyService.selfAndChildren(actor.companyId()).contains(supplierId)`. (companyService 이미 주입:44. 생성 시 supplierCompanyId=부모명의(:88) 유지.)
2. 수정 `EquipmentDetailPage.tsx`: 서류 카드 상단에 조건부 수집버튼 `canEdit(본인)||isParentOfOwner(useSubSuppliers().some(c=>c.id===equipment.supplier_id))`. 기존 외부장비 버튼 유지.
3. 수정 `PersonDetailPage.tsx`: 버튼 gate(:375-380) `canEdit` → `canEdit||isParentOfOwner`.

**재사용:** `DocumentCollectionDialog.tsx` 무변경, 토큰 14일·sort_order, 공개업로드→보완요청 자동resolve, SMS, PDF병합, `/collect/:token`(permitAll).

**검증:** equipment1 자식 장비 "서류 수집 요청" → public_url. 시크릿창 무로그인 접속 → sort_order 순 업로드 → 제출. 서류가 자식 장비에 귀속, OPEN 보완요청 자동 RESOLVED. (neg) manpower1이 협력사A 장비 id로 생성 403.

---

## 순서/의존성
`1(검증) → 2(독립 신규) → 3(V78 선행 필수) → 4(백엔드 1줄)`. 2와 3·4 병행 가능.
**안전 위험 지점:** (a) 증분3의 `DocumentSupplementService.list/createRow/cancel` — BP/ADMIN 분기 무변경 엄수, 회귀 3종 필수. (b) V78 컬럼 확장(non-breaking, Flyway가 코드보다 먼저 적용 보장). (c) 증분2 read-only라 회귀 최소.
