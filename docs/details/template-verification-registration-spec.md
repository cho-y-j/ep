# 서류 템플릿 · 검증 · 등록 — 확정 정의 (spec)

> 사용자 확정(2026-07-13). 이 문서가 기준. 재확인 없이 이 정의대로 구현한다.

## 1. 수퍼 어드민 (전권)
- 전체 **회원 관리 · BP사 관리 · 모든 것** 관리.
- **장비공급사별 + 그 하위 협력사(하청)별 모든 등록 작업을 대행** 가능(전권).
- **서류 템플릿(서류 종류) 관리** = 수퍼 어드민 (`DocumentTypeAdminPage`, `GET/POST/PATCH /api/admin/document-types`).

## 2. 서류 템플릿 = DocumentType (이미 존재하는 데이터 모델)
`document_types` 한 행 = 템플릿 항목. 실제 필드:
- `applies_to`: **장비(EQUIPMENT) / 인력(PERSON) / 회사(COMPANY)**
- 적용 범위: **장비 카테고리별**(굴삭기·크레인·덤프차·고소작업차 …) / **인력 역할별**(굴삭기기사·유도원·비계 … = `PersonRole`) — 카테고리/역할 다중선택으로 매칭
- `required`: **필수 / 선택**
- `has_expiry` + `default_valid_months`: **만료일 관리**(만료 있는 서류는 만료일 입력 → 만료 알림). OCR 자동추출 또는 수기.
- `blocks_assignment`: 필수 미충족 시 **투입 차단**
- `ocr_enabled` + `ocr_extract_type` + `ocr_expiry_field_key`: **OCR 자동추출**(만료일 등)
- `requires_verification` + verify_endpoint: **정부 API 검증** — RIMS(운전면허) / CARGO(화물운송자격) / NTS(사업자번호 진위)

→ **장비 타입별·인력 타입별 필수/선택/만료 템플릿 + OCR + 진위검증이 이미 데이터모델에 있다.** 갭은 "등록 흐름에 안 엮인 것"과 "관리 UI 완성도".

### 유연성 — "나중에 안전 추가 서류" 대응 (핵심)
서류 템플릿은 **코드가 아니라 데이터**(`document_types` 행). 새 안전서류가 필요하면 수퍼 어드민이 **서류종류 1개 추가**(applies_to + 카테고리/역할 + required + 만료 + 검증) → **즉시 그 타입 전체 적용, 기존 자원은 "미충족"으로 표시되어 업로드 유도**. 코드/배포 0. 하드코딩이 아니라 템플릿이므로 추가 서류는 개발 건이 아닌 **데이터 추가**다. 템플릿 밖 임시/선택 서류도 언제든 업로드 가능(특수상황 대응).

## 3. 등록 흐름 (장비·인력 공통 정의)
### 소유자 = 단일 선택
- **소유자 = [우리 회사] 또는 [협력사 ○○]** — 이 하나로 끝. **우리 것 아니면 곧 협력사(=조달).**
- "장비 출처 체크박스 · 차주 free-text · 별도 소유공급사 select" 잡동사니 **전부 제거**. 협력사 선택 = 조달(is_external 파생), supplier_id=협력사, 사업자정보는 그 회사에서 자동.
- 수퍼 어드민/BP 대행 시: 장비공급사 → 그 하위 협력사까지 소유자로 선택 가능(전권).

### 필수 서류 = 템플릿 기준 업로드 + 즉시 검증
- 등록 시(또는 등록 직후 같은 흐름) 그 **타입(카테고리/역할)의 필수 서류**를 템플릿 기준으로 업로드.
- 업로드하면 **즉시 검증**: OCR(면허·화물자격 추출) + 정부 API(면허 RIMS / 화물 CARGO / 사업자 NTS 진위) — `AutoVerifyTrigger`(비동기) 자동 + 수동 보충(`OcrUploadDialog`/`DocumentVerifyDialog`). **검증 가능한 건 즉시, 자동화 검증 계속 확장.**
- 만료 있는 서류: 만료일(OCR 자동 or 수기) → 만료 알림 관리.

### 등록 vs 투입
- **등록은 됨.** 필수 서류 미완/미검증이면 **투입 불가**(`blocks_assignment`/컴플라이언스 `readyForWorkPlan`). 등록 자체는 막지 않는다(업로드가 자원 id를 요구하는 구조 + 데이터모델의 blocks_assignment가 투입 게이트).

## 3-B. 전체 흐름 — Phase 1(우리 프로그램) / Phase 2(BP)
### Phase 1 — 서류 등록·진위·만료 관리 (우리 프로그램의 1차 가치)
1. **수퍼어드민이 장비/인력 타입별 서류 템플릿** 정의(필수/선택/만료기간 예 6개월).
2. **장비 임대사업자가 장비 등록 + 서류 업로드.** 업로드 3경로:
   - **알림톡/SMS 링크** 발송 → 협력사 등 **무로그인 직접 업로드**(`document-collections` `/collect/{token}`)
   - 협력사 **로그인** 업로드(자가가입+부모승인)
   - 장비업자 **대행** 등록/업로드
3. **진위여부 체크** = **자동**(OCR + 정부API RIMS면허/CARGO화물자격/NTS사업자진위, `AutoVerifyTrigger`) **또는 담당자 육안**(OCR_REVIEW_REQUIRED → 수동 검증 `DocumentVerifyDialog`).
4. **만료일 관리** = 만료일 입력(OCR 자동 or 수기) + 템플릿 만료기간(`default_valid_months`, 예 6개월). **만료 1달 전(D-30)부터 알림 푸시**(+D-7). → `ExpiringDocumentScheduler`(구현됨).
### Phase 2 — BP 최종심사·작업계획서·검사 (그 다음)
5. **BP사 서류 최종심사** + **작업계획서** 작성 + 장비/인력 **검사(검증) 날짜 알림**. → `deployment-pipeline-spec.md` 3~5단계.

## 4. 현재 상태 (있음 / 구현 갭)
**있음(재사용)**: DocumentType 템플릿(타입별 필수/선택/만료/OCR/verify) · DocumentTypeAdminPage(수퍼어드민) · verify(OCR·RIMS·CARGO·NTS·AutoVerifyTrigger) · ComplianceService(readyForWorkPlan/required/expiring) · OcrUploadDialog(업로드+OCR+검증).

**구현 갭(대상)**:
1. **장비/인력 등록 소유자 단일화** — 겹친 4개 컨트롤 → [우리/협력사] 단일 select. (장비 우선, 인력 동일 적용)
2. **등록 흐름에 템플릿 필수서류 업로드+검증 통합** — 타입별 필수서류를 OcrUploadDialog(OCR+자동검증)로. (장비 change3 부분구현됨 → 검증 노출·인력까지 확장)
3. **수퍼어드민 전권 등록** — 공급사·그 하위 협력사 대행 등록 범위 확인/구현.
4. **만료 알림 · 자동화 검증 확장** — 추후.

## 5. 구현 순서
① 장비 등록 소유자 단일화 + 템플릿 필수서류+검증 → ② 인력 등록 동일 → ③ 수퍼어드민 전권 등록 → ④ 만료알림·자동검증 확장. 각 단계 dev→독립 QA.
