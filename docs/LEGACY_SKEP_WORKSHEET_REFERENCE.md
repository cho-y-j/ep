# legacy `skep` 작업계획서/서류 자동화 참고 문서

`skep-v2`에 작업계획서 기능을 구현할 때 기존 `C:\Users\dksej\Desktop\verify\skep` 폴더를 반드시 참고한다.

## 참고해야 하는 이유

기존 `skep`에는 작업계획서 생성, 임시저장, DOCX 템플릿 렌더링, 첨부서류 이미지 삽입, OnlyOffice 편집, PDF 변환/메일 발송 흐름이 이미 구현되어 있다.

`skep-v2`에서는 도메인 구조가 바뀐다.

```text
BP 현장
→ 현장 참여 공급사
→ 공급사 장비/인원
→ 서류 적합성 검증
→ 작업계획서 생성
```

따라서 legacy 코드를 그대로 복사하는 것이 아니라, 작업계획서 자동화 엔진과 UI 아이디어를 `skep-v2` 도메인 모델에 맞게 이식해야 한다.

## legacy `skep` 핵심 참고 파일

### 프론트 작업계획서 생성 화면

```text
C:\Users\dksej\Desktop\verify\skep\frontend\skep-web\src\pages\shared\WorkPlanCreate.tsx
```

확인할 내용:

- BP사 선택
- 장비공급사 선택
- 인력공급사 선택
- 공급사별 장비/인원 로딩
- 장비 1대에 여러 인원을 배정하는 UI
- 조종원, 작업지휘자, 유도원, 신호수, 화기감시자 역할 배정
- 장비 서류 선택
- 인원 서류 선택
- 제원표 검색/첨부
- 작업계획서 임시저장
- DOCX 생성
- OnlyOffice 편집기로 열기

`skep-v2` 이식 시 주의:

- legacy는 BP/장비공급사/인력공급사를 화면에서 직접 고르는 방식이다.
- `skep-v2`에서는 먼저 `site_participants` 기준으로 현장에 참여 중인 공급사만 후보로 보여야 한다.
- 전체 공급사/전체 장비/전체 인원에서 고르면 안 된다.

### 프론트 작업계획서 편집 화면

```text
C:\Users\dksej\Desktop\verify\skep\frontend\skep-web\src\pages\shared\WorkPlanEditor.tsx
```

확인할 내용:

- OnlyOffice iframe 편집
- 편집 세션 config 저장
- DOCX 다운로드
- PDF 다운로드
- PDF 메일 발송
- BroadcastChannel 기반 자동 갱신

`skep-v2` 이식 시 주의:

- 처음부터 OnlyOffice까지 모두 붙이지 않아도 된다.
- 1차 구현은 DOCX/PDF 다운로드까지, 2차 구현에서 OnlyOffice 편집을 붙이는 순서가 안전하다.

### 작업계획서 데이터 어댑터

```text
C:\Users\dksej\Desktop\verify\skep\frontend\skep-web\src\lib\worksheet\data-source.ts
```

확인할 내용:

- 기존 API의 장비/인원/서류 응답을 작업계획서 전용 모델로 변환
- 장비별 서류 로딩
- 인원별 서류 로딩
- `DocumentRef`, `WorksheetEquipment`, `WorksheetPerson` 형태로 매핑

`skep-v2` 이식 방향:

- 이 파일의 "어댑터 레이어" 개념은 그대로 가져간다.
- 다만 API는 아래처럼 바뀌어야 한다.

```text
GET /api/sites/{siteId}/equipment-candidates
GET /api/sites/{siteId}/person-candidates
GET /api/sites/{siteId}/supplier-candidates
```

후보 응답에는 이전 투입 여부, 서류 상태, 검증 상태, 추천 점수, 배지가 포함되어야 한다.

### 작업계획서 타입

```text
C:\Users\dksej\Desktop\verify\skep\frontend\skep-web\src\lib\worksheet\types.ts
```

중요 타입:

```ts
DocumentRef
WorksheetPerson
WorksheetEquipment
```

확인할 내용:

- 작업계획서 렌더링에 필요한 최소 장비/인원/서류 필드
- 문서 첨부를 위한 `storageKey`, `mimeType`, `expiresAt`, `verified`

`skep-v2`에서는 이 타입을 그대로 쓰기보다 아래처럼 확장한다.

```text
previouslyUsedOnSite
lastUsedAt
documentStatus
verificationStatus
riskLevel
priorityScore
badges
```

### DOCX 생성 엔진

```text
C:\Users\dksej\Desktop\verify\skep\frontend\skep-web\src\lib\worksheet\engine.ts
```

확인할 내용:

- `/worksheet/template.docx` 템플릿 로딩
- `docxtemplater` + `pizzip` 기반 placeholder 치환
- 첨부 이미지 로딩
- 이미지 압축
- DOCX 뒤쪽 페이지에 첨부 이미지 삽입
- 자동 placeholder 매핑
- 템플릿 스캔/채움 로직

`skep-v2` 이식 시 매우 중요하다.

단, `skep-v2`에서는 가능하면 이 책임을 프론트에만 두지 말고 백엔드 처리도 검토한다.

권장 방향:

1. 초기: legacy 프론트 엔진 참고하여 브라우저 DOCX 생성
2. 안정화: 백엔드에서 DOCX/PDF 생성 API 제공
3. 추후: OnlyOffice 편집/메일 발송 추가

### 작업계획서 스키마

```text
C:\Users\dksej\Desktop\verify\skep\frontend\skep-web\src\lib\worksheet\schema.ts
```

확인할 내용:

- 작업계획서 입력 필드 구조
- 템플릿 섹션
- 기본값 생성
- 렌더링 전 정규화

`skep-v2`에서는 작업계획서 DB 모델과 UI 필드가 먼저 확정된 뒤, 이 스키마를 맞춰야 한다.

## legacy 백엔드 참고 파일

### 작업계획서 임시저장 API

```text
C:\Users\dksej\Desktop\verify\skep\services\document-service\src\main\java\com\skep\documentservice\controller\WorkPlanController.java
C:\Users\dksej\Desktop\verify\skep\services\document-service\src\main\java\com\skep\documentservice\domain\entity\WorkPlan.java
```

legacy 구조:

- `work_plans`
- `creator_id`
- `title`
- `site_name`
- `bp_company_id`
- `supplier_company_id`
- `equipment_id`
- `form_values` JSONB
- `status`: `DRAFT`, `COMPLETED`

`skep-v2` 이식 시 주의:

- legacy `WorkPlan`은 임시저장 성격이 강하다.
- `skep-v2`에서는 정규화된 작업계획서 모델이 필요하다.
- `form_values` JSONB만으로 끝내면 후보 추천, 서류 검증, 대시보드 집계가 어려워진다.

`skep-v2` 권장 구조:

```text
work_plans
work_plan_equipment
work_plan_persons
work_plan_compliance_checks
```

단, `form_values`는 DOCX 렌더링용 스냅샷으로 함께 저장할 수 있다.

### OnlyOffice 편집 세션/PDF 변환

```text
C:\Users\dksej\Desktop\verify\skep\services\document-service\src\main\java\com\skep\documentservice\controller\WorksheetEditorController.java
C:\Users\dksej\Desktop\verify\skep\services\document-service\src\main\java\com\skep\documentservice\controller\WorksheetMailController.java
```

확인할 내용:

- DOCX 편집 세션 생성
- OnlyOffice callback 처리
- DOCX 다운로드
- PDF 변환
- PDF 메일 발송

`skep-v2` 이식 우선순위:

1. 작업계획서 저장/조회
2. 작업계획서 후보 추천/서류 검증
3. DOCX 생성
4. PDF 다운로드
5. OnlyOffice 편집
6. 메일 발송

## Claude Code에게 먼저 시킬 분석 프롬프트

작업계획서 구현 전에 반드시 아래 분석을 먼저 시킨다.

```text
skep-v2에 작업계획서 기능을 구현하기 전에 legacy skep의 작업계획서/서류 자동화 구현을 분석해줘.

반드시 아래 파일을 읽어:
- C:\Users\dksej\Desktop\verify\skep\frontend\skep-web\src\pages\shared\WorkPlanCreate.tsx
- C:\Users\dksej\Desktop\verify\skep\frontend\skep-web\src\pages\shared\WorkPlanEditor.tsx
- C:\Users\dksej\Desktop\verify\skep\frontend\skep-web\src\lib\worksheet\data-source.ts
- C:\Users\dksej\Desktop\verify\skep\frontend\skep-web\src\lib\worksheet\types.ts
- C:\Users\dksej\Desktop\verify\skep\frontend\skep-web\src\lib\worksheet\schema.ts
- C:\Users\dksej\Desktop\verify\skep\frontend\skep-web\src\lib\worksheet\engine.ts
- C:\Users\dksej\Desktop\verify\skep\services\document-service\src\main\java\com\skep\documentservice\controller\WorkPlanController.java
- C:\Users\dksej\Desktop\verify\skep\services\document-service\src\main\java\com\skep\documentservice\controller\WorksheetEditorController.java
- C:\Users\dksej\Desktop\verify\skep\services\document-service\src\main\java\com\skep\documentservice\controller\WorksheetMailController.java
- C:\Users\dksej\Desktop\verify\skep\services\document-service\src\main\java\com\skep\documentservice\domain\entity\WorkPlan.java

그리고 skep-v2의 아래 문서도 같이 읽어:
- docs/CORE_BUSINESS_RULES.md
- docs/WORK_PLAN_CENTERED_SYSTEM_DESIGN.md
- docs/LEGACY_SKEP_WORKSHEET_REFERENCE.md

아직 구현하지 말고 분석만 해줘.

정리할 것:
1. legacy skep에서 그대로 가져올 수 있는 것
2. skep-v2 도메인에 맞게 바꿔야 하는 것
3. 버려야 하는 임시 구조
4. 작업계획서 DOCX 생성에 필요한 최소 필드
5. 장비/인원/서류 후보 추천 API와 어떻게 연결할지
6. 구현 순서
```

## 이식 시 금지 사항

- legacy `WorkPlan`의 `form_values` JSONB만 복사해서 끝내면 안 된다.
- 전체 공급사/전체 장비/전체 인원에서 작업계획서 후보를 고르면 안 된다.
- 현장 참여 공급사(`site_participants`) 필터를 반드시 거쳐야 한다.
- 서류 만료/누락/검증 상태를 작업계획서 생성에서 무시하면 안 된다.
- OnlyOffice/메일/PDF까지 한 번에 붙이면 안 된다.

## 최종 이식 방향

legacy `skep`의 작업계획서 자동화는 아래 책임으로 분리해서 `skep-v2`에 흡수한다.

| 책임 | legacy 참고 | skep-v2 방향 |
|---|---|---|
| 후보 데이터 로딩 | `data-source.ts` | site candidate API 기반 |
| 장비/인원/서류 타입 | `types.ts` | 추천/서류상태 필드 추가 |
| 입력 스키마 | `schema.ts` | 정규화된 work plan 모델 기반 |
| DOCX 생성 | `engine.ts` | 1차 프론트, 2차 백엔드 검토 |
| 임시저장 | `WorkPlanController`, `WorkPlan` | 정규 테이블 + form_values 스냅샷 |
| 편집 | `WorkPlanEditor`, `WorksheetEditorController` | 후순위 |
| PDF/메일 | `WorksheetMailController` | 후순위 |

작업계획서 기능을 구현할 때는 이 문서를 `CORE_BUSINESS_RULES.md`와 같은 우선순위로 참고한다.
