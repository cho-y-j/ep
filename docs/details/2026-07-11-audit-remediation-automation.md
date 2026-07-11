# 2026-07-11 — 감사 수정 + UX P0 + 자동화 E/D/A

세션 목표: (1) 5역할 기능·성능 감사에서 나온 결함 수정, (2) "너무 복잡" 해소 위한 UX 즉시개선, (3) 자동화 3종 구현. 전부 서브에이전트로 오케스트레이션(planning→development→독립 qa), 실데이터 독립 검증.

## 오케스트레이션 원칙 (이번 세션 확립)
- 백엔드/DB/컴파일 산출물(8091 단일 인스턴스·postgres 15433·target/classes)이 **공유 자원**이라, 백엔드를 컴파일·재기동하는 에이전트는 **순차** 실행(동시 `mvn compile`·재기동 충돌 회피). 프론트(typecheck만)는 병렬 가능.
- development는 컴파일/typecheck까지만, **서버 재기동·라이브검증은 독립 qa 몫**(예외: 마이그레이션 있는 A는 부팅+validate 확인까지).
- 검수는 구현에 참여 안 한 깨끗한 qa가 실데이터로 판정.

## Phase 1 — 감사 수정 + UX P0

### 로버스트니스 (500→400)
`common/GlobalExceptionHandler.java` — `MissingRequestHeaderException`(=ServletRequestBindingException), `HttpMessageNotReadableException`를 기존 `handleBadRequest` @ExceptionHandler 배열에 편입(새 코드 없이 기존 400 패턴). field-auth 헤더 누락·무body POST가 500 대신 400.

### N+1 배치 (출력 형태 불변)
- `quotation/QuotationService.java` — `Lookups` record + `buildLookups`/`mapById` 신설. `toResponse`가 행별 findById 대신 `findAllById` 일괄 로드 Map 조회. 목록(list/listOpenBids/listBundles)은 공유 Lookups 1개. 단건 상세는 1건 Lookups 위임. `toResponseSummary`(고아화)만 제거.
- `safety/SafetyAlertController.java` — `toAlertMaps`가 personId 모아 `findAllById` 배치. `siteWeather`는 좌표 키 캐시로 외부 `weatherClient.fetch()` 중복 제거.
- QA 실측: `/api/quotations`(8건) → SQL 4개(findAllById 배치), 선형 findById 없음, 값 정상.

### UX P0
- 거짓/불일치 안내문 3곳: `QuotationDetailPage.tsx`(선정≠작업계획서 자동반영), `BpInboxPage.tsx`(수락≠현장배치), `QuotationListPage.tsx`(메뉴↔제목 일관).
- 견적 후보 **자동조회**: `QuotationCreatePage.tsx` — 조회 버튼 제거, 카테고리/역할 선택 시 useEffect 자동 fetch.
- BP 대시보드 **처리 대기 위젯**: 신규 `BpPendingQueueWidget.tsx`(받은 투입요청·서류심사). 서명대기는 전역 집계 엔드포인트 부재로 제외(백엔드 추가 필요).

## Phase 2 — 자동화 3종

### E. 견적 후보 배지 (마이그레이션 없음)
후보조회가 이미 서류 미비 자원을 제외(`readyForWorkPlan` 필터)하므로 배지는 4종: 이전투입/신규 · 서류완비/만료임박(30일).
- `DispatchedEquipment/PersonRepository` — `findDispatched{Equipment,Person}IdsForBp(bp, ids)` 배치쿼리(이 BP 배차 이력).
- `QuotationCandidateResponse`/`QuotationManpowerCandidateResponse` — `previouslyDispatched, docsReady, expiringDocsCount` 추가.
- `QuotationService.candidates/manpowerCandidates` 2-pass(적격수집→이전투입 1회 조회→배지 채움, 만료는 기존 `rc.expiringCount()` 재사용). BP=`actor.companyId()`, ADMIN은 null(배지 미표시, 파라미터 미추가).
- 프론트 `QuotationCreatePage.tsx` `CandidateBadges` 컴포넌트.
- **후보 개수·구성·필터 불변**(additive).

### D. 정산 근무일수 자동파생 (읽기시점, 마이그레이션 없음)
- `WorkConfirmationRepository.findByPersonIdInAndWorkDateBetween`.
- `SettlementService` — 인력행 `settlement_work_days=NULL`이면 그 person의 **COMPLETED** 확인서 중 계약기간 내 distinct workDate 수로 파생(OT=overtime_hours>0 날 수, 인력은 OT단가 없어 금액영향 0). `effective = 수동 ?? 파생`으로 `SettlementCalculator.calc` 호출.
- `SettlementDtos.SettlementItem` — `derivedWorkDays, derivedOtDays, workDaysSource`(MANUAL/DERIVED/null).
- 프론트 `SettlementPage.tsx` — "자동 N일" 표시, 안내문 보강. **수동 입력·저장 UX 불변.**
- **불변**: `calc` 무수정, 수동 우선, 장비행 무변경.
- **한계**: WorkPlan에 quotation_request FK 없어 BP정밀매칭 대신 person+기간 매칭 → 같은 인원 기간겹침 배차행 2개면 파생모드 이중계상 가능(수동 무영향). 근본해결=확인서↔견적 링크(범위 외).

### A. 선정→배차 초안 (V80 별도 테이블, 격리)
- `V80__quotation_dispatch_drafts.sql` — 신규 테이블(모든 Long→BIGINT, enum→VARCHAR(16), TEXT, TIMESTAMP; Hibernate validate 통과, V79 SMALLINT 교훈 반영).
- 신규 패키지 `quotation/dispatch/draft/`(DispatchDraft 엔티티, 2 enum, Repository, Service, Controller, Response DTO). GET `/api/quotations/{id}/dispatch-drafts`, POST `/api/quotations/dispatch-drafts/{id}/confirm`.
- `QuotationProposalService.finalize` 훅: `markFinalAccepted` 직후 try/catch로 초안 생성(OPEN_BID만, 이미 dispatched면 skip, MANPOWER 단가-only는 person_id NOT NULL이라 skip). **finalize 본체 무변경.**
- confirm = 초안→기존 `DispatchedEquipmentService.send`/`DispatchedPersonService.send` 재사용(선정검증·멱등 409·V77·BP알림 그대로). 성공 시 초안 CONFIRMED.
- 수동 send 말미: 해당 공급사 DRAFT를 DISCARDED(잔존 방지).
- 프론트 `DispatchSection.tsx`(초안 패널+확인후발송+차량변경 프리필), `DispatchSendDialog.tsx`(초기값 props).
- **격리**: 초안은 별도 테이블에만. 정산·작업계획서 자동추가·PDF·Excel·투입목록·서류묶음·갱신알림 등 dispatched 리더 **한 줄도 미수정** → confirm 전 어디에도 미노출.
- **설계 판단(정직)**: finalize가 request/proposal 행을 `FOR UPDATE` 잠금 → 초안을 `REQUIRES_NEW`로 빼면 FK가 FOR KEY SHARE 요구해 self-deadlock 위험. 그래서 **같은 tx 참여 + try/catch**(순수 로직 예외 완전 격리=finalize 성공 보장). 극단적 영속성 예외까지 100% 격리하려면 FK 제거 필요(V80 체크섬 확정+정상운영선 도달불가라 미채택).

## 통합 독립 QA — 판정: 검증됨 (실데이터, 0 기능결함)
- **A 격리**: confirm 전 정산/투입목록/작업계획서(work_plan_equipment 0행)/PDF(400 NO_LINES) 전부 미노출 → confirm 후 노출. 멱등 재confirm 409, 수동send→DRAFT DISCARDED→confirm 409, 비소유 confirm 403, TARGETED 초안 0.
- **D**: DERIVED 10일→2,000,000(5M/25×10), 수동 20일→4,000,000(MANUAL, 파생무시), PENDING/기간외 배제, 장비 무파생, calc 정합(9M·10+OT 500k·2=4,600,000).
- **E**: previously_dispatched true/false 정확, expiring 1↔0 토글, 후보집합 불변.
- **14**: field-auth 무헤더 400, 무body POST 400(500 없음). N+1 4쿼리.
- **회귀**: 수동 월 9,000,000+20일→7,200,000(÷25), 수동send 정상, 타사 403, 5역할 로그인 200.
- 범위 밖 관찰: health DOWN(MailHealthIndicator, dev SMTP 미설정), `@Valid`가 `@PreAuthorize`보다 먼저(미인가+빈body→403아닌 400, 우회아님·Spring표준), CreateWorkPlanRequest 전역 SNAKE_CASE 의존(기존).
- DB: seed 원복, 잔존은 QA가 API로 만든 additive 신규행만(무해).

## 보류 (사용자 결정 필요)
- **자동화 C (투입 수락=배치)**: 수락이 배치를 안 만드는 게 의도인가 미구현인가 — 답에 따라 설계 분기.
- **A-2 (TARGETED 지정견적도 초안)**: 지정견적은 현재 배차 자체가 막힌 기존 제약 → 확장은 send 선정게이트 변경이라 별도 결정.
- UX 리포트 미결 6문(BP가 개별 차량 고르는 게 실제 요구인지 등), 나머지 자동화 B/F/G/H/I.
- 현장 PATCH full-replace(부분 바디→정산일 null, 기존 패턴·UI 안전).
