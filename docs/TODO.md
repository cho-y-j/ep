# 할 일 (TODO)

> `/wrap` 슬래시 커맨드가 세션 작업을 반영해 갱신합니다.
> **검증된 것만 완료로 옮기고**, 확인 안 한 것은 "확인 필요"에 남깁니다 (CLAUDE.md §1·§4).

## 진행 중
- **인원 등록 흐름 개편 + 회전버튼 전체적용 + 진위검증 로컬 활성화** (2026-07-15, 브라우저 실검증)
  - [x] **회전버튼 공통화·전체적용**: `rotateImage90`을 `features/document/imageRotate.ts`로 추출(중복제거) → 장비(`EquipmentCreateForm`)·인원서류(`OcrUploadDialog`) 모두 적용. 정렬모달 즉시오픈+백그라운드 코너감지+`↻ 90° 회전`(캔버스 회전→파일교체→재감지). 인원 서류업로드 정렬단계 회전버튼 브라우저 확인.
  - [x] **인원 먼저 → 역할별 서류단계**: `PersonCreateForm`에서 하드코딩 "운전면허증으로 시작" 박스 제거(역할무관 항상 운전면허 강요 문제). 이제 이름·역할·전화 먼저 등록 → 역할기반 필수서류 단계(`DocumentSection` ownerRoles). 브라우저 확인: 면허박스 없음, 6개 역할 체크박스(인력공급사), 등록→서류단계→서류업로드 정렬+회전.
  - [x] **진위검증 로컬 활성화·실증**: `verify-api`(8081)·`main-api`(8080) 컨테이너는 호스트 미노출(도커DNS)이라 dev가 `VERIFY_ENABLED=false`였음. **컨테이너 IP로 도달 가능**(172.24.0.2/6 health200) → `backend.sh` env override 지원화 후 `VERIFY_ENABLED=true`+URL을 컨테이너IP로 재기동. **실증**: 화물자격증 verify → `POST http://172.24.0.6:8080/api/verify/cargo` 실제 도달(가짜번호라 400, 즉 파이프라인 라이브). "업로드 안됨"의 한 원인=`EXPIRY_REQUIRED`(만료일 필수) 확인.
  - [x] **운전면허 전체 웹 E2E + 실제 RIMS 진위검증 성공** — `test/면허증 여백.png`(실면허 18-08-019753-23/차영민/1종보통, 스티어링휠 위 비스듬). 브라우저: 조종원 등록→운전면허증 업로드→즉시정렬(회전버튼)→맞추기→**로컬 OCR 정확추출**(면허번호·성명·만료 2035-12-31, 주민번호 자동마스킹)→업로드→**RIMS 실검증 VALID→UI "검증완료"**. 
    - **버그 픽스(검증 400의 진짜 원인)**: main-api `RimsLicenseVerifyRequest.f_licn_con_code`는 `@Pattern(\d{2})` 2자리 코드(11=1종대형/12=1종보통/13/21/22/23) 필수인데 skep `VerifyClient`가 `"1종 보통"` 텍스트/`"01"` 전송→@Valid 400. `VerifyClient.toRimsLicenseCode()` 추가(한글종별→코드, 미상 폴백12). 이게 "검증 안됨"의 실제 원인이었음.
  - [x] **화물(cargo) 검증도 동일 버그 픽스** — main-api `CargoVerifyRequest.birth`가 `@Pattern(\d{4}-\d{2}-\d{2})`인데 OCR이 `1970.03.27`(점) 추출 → 400. `VerifyClient.toIsoBirth()`(점/슬래시/YYYYMMDD→YYYY-MM-DD) 추가. 실검증: 오석주/1-18-232925/1970.03.27 → 정부 PUBLIC_API **VALID/VERIFIED**. (RIMS·CARGO 둘 다 필드포맷 버그였고 둘 다 픽스)
  - [~] **KOSHA(안전교육) 검증 = 코드버그 아님·Google Vision 과금 미활성(403)**: 안전교육 이수증은 verify-api가 **Google Vision OCR**로 읽는데 그 Google 프로젝트 billing 비활성 → `403 This API method requires billing to be enabled`. **외부 설정**(Google Cloud 결제 활성화) 필요. 면허(RIMS)·화물(CARGO)은 Vision 미사용·정부API 직접이라 통과. ※Vision 의존 경로(KOSHA, ocr-preview Vision)는 결제 켜야 동작.
  - [x] **인력 서류 8종 웹+모바일 QA (독립 에이전트·실브라우저, 2026-07-15)** — 전 종류 업로드·로컬OCR추출·정렬·회전·검증 **PASS**. 운전면허(RIMS)·화물(CARGO) 검증완료+추출정확, 건설기계조종사 추출+만료 OCR백필 실증, 안전교육 추출OK(검증=Vision403 기지), 신분증/건강진단서/자격증/기타 첨부·만료가드 정상. 모바일(390px) 정렬모달·회전버튼 조작 가능. **폰앱(phone-app)은 네이티브 안드로이드로 인력서류 미취급(출근사진만)** → 인력서류=웹전용, "웹앱"=모바일웹.
  - [x] **결함1 픽스·검증 완료(고아 서류 누수)** — `PersonService.delete()`·`EquipmentService.delete()`에 소유 서류 캐스케이드 삭제 추가(기존 주입된 `docRepo`+`storage` 재사용, `findByOwnerTypeAndOwnerIdOrderByIdDesc`→`deleteAll`+파일삭제, 새 의존성 없음·최소). 실증: 인원/장비 각각 서류 업로드 후 삭제 → 서류 DB행 0·디스크 파일 정리·고아 0(둘 다). 제 이전 테스트가 남긴 고아 2건도 정리.
  - [x] **인력 서류 실사용 버그 5건 픽스·검증(사용자 실테스트 지적, 화면 재현)** — (a) **만료일 자동입력**: 추출 만료일이 최상단 필수 피커에 자동반영 안돼 "만료일 입력" 에러 → `OcrUploadDialog.runRegionOcr`에서 `expiry_date`→피커(`toIsoDate`). (b) **업로드 타임아웃**: 서버 마스킹 23초>10초라 abort → 업로드 POST 60초. (c) **저장 이미지 크롭+마스킹(정석)**: 크롭본은 mask-pii 미검출(PII노출) 실증 → **원본 마스킹→마스킹본을 코너로 warp(display와 동일 `extractRegionsRaw`)→저장**. `DocumentService.upload(+corners)`·`DocumentController`(allParams.corners)·FE(alignCorners 전송). 저장 이미지 크롭+주민번호 검정박스 육안 확인, 파일 `…-masked-cropped.png`. (d) **역할 확장**: 장비공급사도 전 역할(조종원 외 신호수·유도원 등 7개) 등록 — `PersonRole.allowedFor(EQUIPMENT)`·FE `rolesAllowedFor`. 인력공급사는 인력만 유지. 정산은 `SettlementService`가 공급사유형 무관 허용이라 자동 가능. (e) 검증 진짜 확인(RIMS VALID). 회전버튼도 유지.
  - [x] **결함2 픽스·검증 완료(모바일 반응형)** — 정석 드로어 패턴: 데스크톱(md+)은 인플로우 사이드바 현행 유지, 모바일(&lt;md)은 사이드바를 `fixed` 드로어(`-translate-x-full`↔`translate-x-0`)+백드롭+X닫기로, `TopBar`에 햄버거(md:hidden), `AppShell`에 `mobileOpen` 상태+라우트이동 시 자동닫힘(useLocation). 접기버튼은 `hidden md:flex`(데스크톱 전용). 실증: 390px 오버플로우 **535→390(해소)**, 사이드바 화면밖(left -240)→햄버거로 left 0+백드롭, **데스크톱 회귀 없음**(인플로우 240). `AppShell.tsx`·`Sidebar.tsx`·`TopBar.tsx`.
  - [ ] **경미**: OCR추출 만료일이 필수 만료일 피커에 자동반영 안됨(person, 별도표시만→수동재입력) / 등록이름↔추출이름 대조 / NTS(사업자) 검증 미점검 / 운전면허 면허종별 추출(폴백12) / Vision 결제(KOSHA)
- **장비종류 마스터 + 종류별 서류 체크리스트** (설계: `docs/details/equipment-type-master-design.md`)
  - [x] stage① `equipment_type` 마스터(V90) + 25종 시드 + 엔티티/레포
  - [x] stage②③④ 완료·**독립 QA 통과** — 종류 CRUD 어드민 + 종류별 서류 junction(V91, 377행 백필 **byte-identical 무회귀**) + 체크리스트 UI(필수/선택/해당없음 + 만료관리). 공개 `/api/equipment-types`, 어드민 `/{code}/documents`(3상태 PATCH), FE `EquipmentTypeDocsPage`. 백필=기존 컴플라이언스 동일 확인
  - [x] stage⑤⑥ enum→String **완료·독립 QA 통과** — `EquipmentCategory` enum 삭제→String, `EquipmentTypeService`(labelOf/existsActive), 매칭 `Objects.equals`(null의미 보존) + **매칭 단위테스트 5/5**, 라벨 DB조회 이전, 쓰기게이트(active 코드 검증→400), FE `useEquipmentTypes` 훅. **독립 QA: 새 종류 POST→등록→매칭 실동작, CRANE 무회귀(required 4)**. 백엔드 새코드로 8091 구동중
  - [x] **인력(PERSON) 서류관리 = 장비 미러** — `person_role_doc_requirement` junction(V94, 65행 백필 diff0 무회귀) + `PersonDocRequirementService`(다중역할 합집합/OR) + 컴플라이언스/WorkPlan PERSON분기 + `PersonRoleDocsPage`(역할별 필수/선택/해당없음+만료). 독립 QA: OPERATOR 필수4 무회귀
  - [x] **미리보기 새탭**(Blob→window.open, 모달제거) + **체크리스트 종류추가·서류추가 버튼**(POST equipment-types/document-types)
  - [x] **템플릿 조정(V93)**: 건설기계 차대·형식 제외 / 자동차 검사유효기간(만료) 추가 + model밴드 타이트
  - [x] **차량번호→모델명 버그**: 번호판(전북83사1725=한글3자·긴문자열)이 짧은 차명을 길이로 이겨 model로 뽑히던 것 → hangul kind에 번호판 패턴 배제(document_align.py). 단위검증 `['전북83사1725','쏘나타']→'쏘나타'`
  - [x] **OCR 브라우저 실검증(puppeteer)**: axios `timeout:10_000`이 OCR(13~27초) abort → "OCR 실패"의 진짜 원인. api.ts 인터셉터로 OCR계열 60초. 자동차·건설기계·인력 3종 브라우저 실추출 확인. 미리보기 확대=컨트롤 있는 새창(축소/확대/맞춤/닫기)
  - [x] **수동 회전 버튼(정렬 모달)** — 브라우저 실검증 완료. 근본원인: `onRegFilePicked`가 detect-corners(회전된 큰 사진 15초+)를 await한 뒤에야 모달을 열어 회전 버튼조차 못 봄. 수정: 이미지 픽 시 **정렬 모달 즉시 오픈**, 자동 코너감지는 **백그라운드**(`detecting` 힌트+`alignKey` 리마운트). "↻ 90° 회전"=캔버스 90° CW 회전→파일 교체(똑바로 세워 저장)→코너 재감지(백그라운드). 검증: 모달 즉시 오픈, 회전 1회로 신서식 자동차등록증 upright, 맞춘이미지 미리보기 정방향, regFile=회전본이 저장/warp에 사용 (`EquipmentCreateForm.tsx` rotateImage90/rotateAlignImage)
  - [x] **신서식 필드 추출(라벨기반) + 검사만료일 자동채움 — 완료·브라우저+DB 실검증**
    - paddle `document_align.py`(에이전트 a55ac54ec277eee4e): `_natural_warp`(강제 portrait 제거) + `_auto_orient`(방향 자동보정) + 라벨앵커(`_extract_label`, 자동차등록번호/차명/최초등록일→옆값). **독립 재현**: 고소작업차 신서식 회전본 vehicle_no 경기99사9489·model 호룡SKY4504N고소작업차·year 2023. 트레일러(구서식) 무회귀(에이전트 검증).
    - **V95**: doc-type 7 템플릿을 라벨+auto_orient 버전으로 교체(구밴드 V93 대체). `OcrRegionPreviewController`가 DB 템플릿을 paddle로 그대로 전달(요청마다 조회 → DB 갱신 즉시 반영).
    - **검사만료일(만료관리)**: 폼에 날짜 입력란 추가 + 자동차등록증 "검사유효기간" 표에서 OCR 자동채움. `expiry_date` 밴드 `[0.49,0.42,0.26,0.17]`+`date_end`(표 전체 덮어 프레이밍 시프트 흡수 → 최신 만료일). **밴드 폭 주의**: 좁으면(초기 [0.52,0.46,0.18,0.1]) 코너 프레이밍차로 마지막 행 잘려 2025-04-20 오추출됐음 → 넓혀 2026-04-19 안정.
    - **배선**: `CreateEquipmentRequest.inspectionDueDate` + `Equipment.setInspectionDueDate` + `EquipmentService.create`(파싱→저장) + FE `EquipmentCreateForm`(state·입력란·`toIsoDate`·onAlignConfirm 매핑·submit `inspection_due_date`).
    - **E2E 실검증(브라우저+DB)**: 업로드→회전→정렬→맞추기완료 → 4필드 자동채움(경기99사9489/2023/호룡SKY4504N고소작업차/**검사만료일 2026-04-19**) → 등록 id=27 `inspection_due_date=2026-04-19` DB 저장 확인. 테스트 장비 정리 완료.
    - 잔여(경미): 트레일러 등 **타 서식의 검사유효기간 밴드 위치**는 다를 수 있음 — 만료일은 수동+자동채움(사용자 확인·수정)이라 오추출해도 보정 가능. 필요시 서식별 밴드 분기.
  - [ ] **후속(경미)**: 순수 표시 컴포넌트가 새 어드민 코드를 code로 표시(정적 폴백)→훅 승격. `DocumentTypeAdminPage`·`OutgoingQuotationService` stale·`applies_to_person_roles`/`applies_to_categories` CSV(이제 junction으로 대체됨) 정리
  - [x] **OCR 문서 6종 전부 밴드전환·검증 완료** — 자동차(V88)+건설기계등록증(32)·운전면허(1)·안전교육(3)·화물자격(13)·조종사면허(33) V92. paddle kind 4종 추가(alnum·name·licno·date_end)+구등록번호 배제. **백엔드 API 실검증: 5종 필드키 보존 추출 성공**(인력3종 코너6/6 정답, 건설기계 주요필드 5~6/6, 조종사 면허번호만 저해상 글자오독=수동보정). 6개 코너세트 강건성 확인
  - [x] **최종 clean-boot 통합검증** — V90~V92 적용, 종류25·OCR밴드6, CRANE 무회귀(required4), 새 종류 추가→등록→매칭 실동작, 백엔드1개·paddle무사, 테스트잔여물 정리(25종/377junction)
  - 원청사(client)별 서류 차원은 **보류**(SK 공통, 삼성 별도 미래). 작업계획서 추출은 수정가능 확인됨
- **OCR 자동차등록증**: 밴드+패턴+스캔보정으로 재작성(V88, 코너 편차 강건 — 3필드 차량번호·차명·연식). FE 자동입력 재활성+확대(줌). **브라우저 E2E는 사용자 확인 대기**. 건설기계등록증도 동일 밴드 전환 예정

## 예정 / 백로그
- [ ] 이번 세션 전체 커밋 + 푸시 (사용자 요청 시) — OCR 만료 백필(paddle-ocr 서비스 + verify/OcrExpiry*·V82) + **영역-템플릿 OCR/4모서리 정렬**(paddle document_align·detect개선 + backend Ocr*Region*·DetectCorners·V83/V84 + FE DocumentCornerAligner) + **admin PATCH 영속 수정** + 수집링크·필터·미리보기·체크리스트 + 이전 미커밋분(safe-8 등)
- [ ] Phase 3: 수퍼어드민 영역지정 도구(샘플→4모서리→박스 드로잉→field key 배정→ocr_region_template 저장, DocumentCornerAligner 재사용, admin PATCH로 저장)
- [ ] 운전면허·화물자격·조종사면허·안전교육이수증(QR) 템플릿 + 검증배선 — 각 실샘플 필요
- [ ] 로컬 영역추출→정부API(RIMS/CARGO) 검증 배선으로 Vision 대체(면허/화물, Vision 결제 불필요화). 안전교육=QR 추출 진위, 조종사면허=만료관리
- [ ] prod paddle 배치(도커화 or host-gateway) + prod `OCR_ENGINE=paddle` 주입 — 현재 dev(호스트 백엔드→localhost:8100)만 동작
- [ ] FE 만료필수 완화 게이트가 `ocr.engine` 미인지 → prod engine=off면 FE(선택)/BE(필수) 불일치. engine 상태 FE 노출로 정합화(현재 의도배포 engine=paddle에선 일치)
- [ ] 추가 장비 만료타입 OCR 백필 확장(보험증권·안전인증서 등, 데이터 추가로 ocr_enabled=TRUE+extract 지정)
- [ ] ADMIN/BP 통합 서류관리 화면(현재 board·검토큐·만료페이지·자원상세 4분할 — 공급사는 한 화면 OK)
- [ ] BP 서류심사 상태머신(심사중→승인/반려) + DocumentReview↔ResourceCheck/WorkPlan 연결(현재 단방향 우편함)
- [ ] 수퍼어드민 대행 등록 시 공급사의 하위협력사를 소유자로 선택하는 UI(현재 create 폼 미노출)
- [ ] 자동화 나머지 (UX 리포트 B/F/G/H/I): 서명 대기 알림·현황 자동화 등
- [ ] BP 대시보드 "서명 대기" 집계 — 전역 집계 엔드포인트 백엔드 신설 후 위젯에 추가
- [ ] D 근본 개선: 작업확인서↔견적요청 링크 신설(이중계상 제거)
- [ ] 워치 낙상감지 Phase3(Enforce 모드)·임계값 튜닝 — 실기기 데이터 확보 후

## 완료 (최근, 검증까지)
- [x] (2026-07-14) 장비등록 등록증 정합화 + 맞춘이미지 미리보기 — ① 4모서리 정렬 후 warp 이미지 미리보기/추출 안 뜨던 버그 수정(`OcrRegionPreviewController` corners시 `warped_image_base64` 반환 + `EquipmentCreateForm` "맞춘 이미지" 렌더, 인증API 실측). ② 폼 자동차등록증 하드코딩 제거 → 장비종류 먼저 선택 → 종류별 필수 등록증 설정기반 자동선택(`pickRegistrationType`). ③ V86: 건설기계 9종→건설기계등록증 필수. 건설기계 경로 E2E 전필드 추출 검증(연식/차번/차명/차대). FE tsc·BE compile OK
- [x] (2026-07-14) 로컬 OCR 문서 6종 템플릿 + Phase3 도구 — 자동차·건설기계 등록증 + 운전면허·화물·조종사·안전교육(V83/84/85), 실샘플 detect→warp→추출 검증, Phase3 수퍼어드민 영역지정 도구(코드+백엔드 검증)
- [x] (2026-07-14) 로컬 영역-템플릿 OCR Phase1·2 — 공식문서 필드 크롭추출 ~1-2초, 폰사진 4모서리 정렬(배경제거+원근보정), 자동차/건설기계 등록증 템플릿(V83/V84), detect_corners Otsu개선, admin PATCH 영속버그 수정 — 독립QA 검증(회귀0)
- [x] (2026-07-14) 서류 파이프라인 실동작 검증 — 장비임대사업자 등록→서류업로드→서류관리→만료→BP전달 8단계 실데이터 통과(이미 동작)
- [x] (2026-07-14) OCR 만료 자동 백필(정기검사증 로컬 paddle 비동기, V82) — 독립 QA 검증됨(A즉시응답0.3s·B 2026-05-19·C알림, 단위테스트 7/7). 면허/화물=Vision 즉시확인 무손상
- [x] 정산 재설계(÷25·근무일수·OT·현장정산일 V79) — 독립 QA 검증됨
- [x] 5역할 기능·성능 감사 — 부분검증(핵심 통과, 경미 결함 식별)
- [x] 감사 수정: 클라이언트 오류 500→400, 견적/안전알림 N+1 배치 — 독립 QA 검증됨
- [x] UX P0: 거짓 안내문 3곳·후보 자동조회·BP 처리대기 위젯 — 독립 QA 검증됨
- [x] 자동화 E 견적 후보 배지 — 독립 QA 검증됨(실데이터)
- [x] 자동화 D 정산 근무일수 자동파생(수동 우선) — 독립 QA 검증됨(실데이터)
- [x] 자동화 A 선정→배차 초안(V80 격리) — 독립 QA 검증됨(격리 확증)

## 블로커 / 확인 필요 (사용자 결정)
- **차량 종류(EquipmentCategory) 추가 여부**: 현 9종 전부 건설기계(→건설기계등록증 필수, V86 반영). 자동차등록증을 **필수**로 하려면 트레일러/화물/카고 등 '차량' 종류를 enum(백엔드 `EquipmentCategory` + FE `types/equipment`)에 추가하고 그 종류에 자동차등록증을 `applies_to_categories`+`required`로 스코프해야 함. 어떤 차량 종류가 필요한지 사용자 확인 대기. (저상트레일러 실샘플 있음)
- **detect-corners 자동검출 개선(후속)**: 자동차 샘플처럼 어두운 배경·저대비 하단에서 오른쪽아래 코너 오검출 → 현재는 4모서리 UI에서 사용자가 보정하면 정확 추출됨. 자동검출 정확도 개선은 별도(건설기계 샘플은 양호).
- **자동화 C (투입 수락=배치)**: 현재 "수락"은 상태변경+알림만, 배치는 작업계획서(서명) 기준. 수락=배치가 의도인가 미구현인가? → 답에 따라 설계 분기(보류).
- **A-2 (TARGETED 지정견적도 초안)**: 지정견적은 현재 배차 자체가 막힌 기존 제약. 확장은 send 선정게이트 변경 필요 → 별도 결정.
- **UX 미결 6문**: BP가 개별 차량을 고르는 게 실제 요구인지, 등(자동화 방향 좌우).
- **현장 PATCH full-replace**: 부분 바디 전송 시 정산일 null 위험(기존 site 전 필드 동일 패턴, UI는 full-form 전송이라 안전). 부분수정 클라이언트 대비 필요 시 별도 처리.
- **@Valid vs @PreAuthorize 순서**: 미인가 역할+빈 body → 403 아닌 400(검증메시지 노출). 보안 우회 아님·Spring 표준 → 저가치, 미수정.
