# 할 일 (TODO)

> `/wrap` 슬래시 커맨드가 세션 작업을 반영해 갱신합니다.
> **검증된 것만 완료로 옮기고**, 확인 안 한 것은 "확인 필요"에 남깁니다 (CLAUDE.md §1·§4).

## 진행 중
- **★전면 재기획 프로그램 (2026-07-17, 기획서: `docs/details/2026-07-17-ia-flow-replan.md`)**
  - [x] **P0 — BP 원스톱 심사: 구현+독립 QA PASS** — V96(심사 상태머신)·봉투 문서목록 API·BP 열람권한 분기(포함 200/미포함 403/쓰기 403 실측)·인라인 뷰어(naturalWidth 실측)·승인/반려(가드 400)·승인 후 [계획서 프리필]+[점검요청(계획서 없이, 공급사 수신 실측)]·메일 BP CC(코드)·회귀(zip·ADMIN큐·허브) 전부 PASS. Low 1건(인력공급사 프리필 타입 미구분) 픽스 위임됨.
  - [x] **P0.5a — 계약 등록+기통과 소급/구두승인+메일 링크: 구현+검수 PASS** — V97 contracts(OT 5분류 단가·이름만 BP·파일 md5 일치)·V98 온보딩(REQUESTED→BP 일괄승인/VERBAL, **ResourceCheck APPROVED 자동 주입**으로 게이트·readiness 무수정 통과, diff 0줄 확인)·보안 16종 차단·모바일 390 무붕괴. UX 권고 2건(공급사 메뉴 승격·원단위)은 P0.5b에 포함.
  - [x] **P0.5b — 일일 확인서 디지털: 구현+검수 PASS** — V99 daily_work_logs(OT 5분류·중복 409·서명건 수정 400)·BP 캔버스 실서명(SIGNED+PNG)·전표사진 PHOTO 갈음·**월간 원장 실양식 재현**(정산주기 settlementDay 실측 06-11~07-10·OT 5열 고정시간대 일치·금액 산술 ÷25+분류별 재계산 일치)·작업자 field-auth 내역(웹 미배선=User↔Person 링크 부재로 정당 판정)·보안 7종·회귀 6화면·사이드바 승격(공급사 주요 3메뉴). 비차단 관찰: 원장 헤더 BP/SKEP 확인란 미세차(설계 선택).
  - [x] **P1a — L3 사전판정+formValues 읽기+서명 실문서화: 구현+검수 PASS(재작업 1회)** — deploy-check 4게이트 합성(행동형 문구)·form_values 상세 read-back+`?planId=` 이어서수정+[임시저장](근본원인: hydrate bp 선복원 fix)·V100 서명 스냅샷(md5 일치 서빙)+**내용변경 시 전원 재서명 서버 강제**(SIGNED+PENDING 무효·"사인 유지" 구멍 제거)·온보딩 배지·보안 스코프 전 케이스. 미검증 잔여는 환경 제약(LibreOffice 렌더 — prod 도커 포함)뿐. 운영 교훈: 백엔드는 메인 세션 nohup으로(에이전트 백그라운드는 종료 시 정리됨 — 메모리 기록).
  - [x] **P1b — L1 자동생성: 구현+검수(결함1은 P1c 선행 픽스로)** — OCR 오토필(operatorLicense.ts: 화물>건설기계>운전면허 우선·manual* 우선·원천 뱃지 OCR/수기/없음)·주야 2인 슬롯·첨부 자동조립(attachmentOrder.ts §3.6.2 순서, DOCX zip 해부로 순서 실증·수동 재정렬 시 자동중단)·하드코딩 5/6 제거+하위호환·백엔드/템플릿 무수정. **잔여**: supervisor_company '(주)스켑중기' 1필드(P1c 선행) / 후속백로그: DOCX 템플릿에 면허번호·야간 placeholder 추가(바이너리 편집), OCR id13 취득일 박스(V10x), 워크시트 보험·검사 필드 신설 여부.
  - [x] **P1c — L2 교체: 구현+검수 PASS** — replace-resource(V101 cloned_from·formValues 복사·자원 치환·게이트 400+트랜잭션 롤백·원본 자동 CANCELLED·서명0)·**파생필드 재계산 실측**(차량번호·모델 새 장비로, 마커값 보존)·**조종원 단독 교체 실측**(재매칭·게이트 롤백)·[자원 교체] 다이얼로그(후보 deploy-check 배지 정확 일치)·업체변경 신청서 v0(V102, l3_snapshot=deploy-check 일치·인쇄뷰 §7 재현·모바일390)·supervisor_company 픽스(기본값 제거+공급사명 오토필+로드값 보존)·보안 교차 403·회귀 0. 경미 3건(P2a 선행 2 + 백로그 1: l3 JSONB 내부 camelCase).
  - **P1 웨이브 완료** — 락인 해제 체인 가동: 자동생성(L1)+사전판정(L3)+교체(L2)+신청서(L2a)
  - [x] **P2a — IA 6허브 재편+오늘 할 일 대시보드: 구현+디자인 검수 PASS(재작업 1회)** — 4역할 최상위 정확히 6허브(ADMIN+시스템관리 7, 링크 중복 0·데드링크 0)·BP 장비/인원 4→2링크+화면 내 범위 탭·허브 자동 펼침·헤더 배지 합산·오늘 할 일 카드(실데이터 대조 일치·행동형 라벨·0건 숨김)·CSS 차트(값 대조 일치)·모바일 390 드로어. 재작업=감사 라벨 19종(타깃5+액션14) 한글 매핑+break-all → 원시 enum 0·오버플로우 390 해소 실측. 경미픽스 2건(COMPLIANCE 인쇄뷰 런타임 확인·교체 자기자신 제외).
  - [x] **P2b — 견적 템플릿+투입-정산 디커플링+정산 5분류: 구현+정밀 검수 PASS** — V103 템플릿(rows JSONB·CRUD·격리 403·[불러오기]→note 표+프리필→BP 수신 가독)·디커플링 option b(FieldDeployment 두 번째 정산 원천, **기존 DISPATCH 바이트 동일 sha256 일치**·SettlementCalculator 무수정)·OtBreakdownCalculator(SIGNED/PHOTO만·미연결 제외·장비 우선 귀속·**독립 수기계산 완전 일치 161,056**·단위테스트 4/4)·이중집계 미합산·명세서 PDF/XLSX 무회귀. 비차단 관찰 3(백로그): 로그별 반올림 정책 문서화 / 진행중 투입 후행 월창 표시 / setQuantity id 공간(UI 게이트로 차단됨).
  - [x] **P2c — 원청 CLIENT 역할+관제 허브+혼잡도 v1: 구현+검수 PASS** — V104/V105·Role.CLIENT·관제 API(집계 전 수치 DB 독립 재계산 일치·혼잡도 24h 배열 일치·피크 강조)·SecurityConfig 전역 격리(**4역할·공개경로·field-token 무회귀 실측**, CLIENT 8종 403·타원청 403)·ADMIN 원청연결+CLIENT 생성 폼·모바일390. 부수 성과: EquipmentService 미열거 역할 전체반환 누수 발견→전역 격리로 무해화(코드 관찰 백로그). 잔존: client1@skep.local/client1234(SK). 관찰: 야간 걸침 자정 이후 미반영(혼잡도 v1 한계 허용).
  - **P2 웨이브 완료** — IA 6허브·오늘 할 일·견적 템플릿·정산 5분류·투입-정산 디커플·원청 관제
  - [x] **P3a — 강풍경보+안전설정 법정가드+점검게이트+가동시간 정비: 구현+검수 PASS** — V106(설정 UNIQUE·전이상태·정비기준)·법정 가드 경계값 매트릭스 전부 400(동일값=허용 의도 확인)·**S1 강풍 크론 실발화 실측**(14:50 ENTER·alert+BP/공급사 알림+전이 1회+증거 타임스탬프·관제 "강풍중지" 렌더)·S3 게이트(미점검 400 장비식별→점검 후 200/경고 모드·기존 G-1 무손상)·S4′ 누적 27h 수기일치(철야 자정보정·무시간 8h)·배지 점등/해제·폭염 무회귀(legalDefault=기존 하드코딩 일치)·KMA 키 backend.sh 배선. 라이브 미검증 2(크론 타이밍): CLEAR 전이·정비알림 SEND(단위테스트·배선으로 대체). **고지**: QA 정리 시 이전 세션 잔재 DRAFT 계획서 2건(id1·2 "QA-A") 오삭제(기능 영향 낮음·데모 픽스처).
  - [x] **P3b — 알림 3등급+ack 루프+폰앱 TTS: 구현+검수 PASS(결함 0)** — V107(severity·ack·escalated)·분류기(강풍/응급/38℃=긴급, 폭염·휴식=주의, §5 표 일치)·FCM 페이로드 4필드(하위호환)·ack API(본인만 403 매트릭스·멱등·resolve 독립)·**에스컬레이션 크론 실발화**(5분 경계 4분/6분 실측·재알림 1회만·BP+공급사 통지·SOS/resolved/acked 제외)·관제 확인컬럼+미확인 필터+CLIENT overview·폰앱 3등급 분기(EMERGENCY 알람채널+DND우회+풀스크린+반복진동·TTS 한국어·[확인] 대형버튼) 컴파일 성공·PiiMaskerTest 갱신. 실기기 검증(TTS 음성·무음우회·진동)=사용자 몫.
  - [x] **P3c — NFC 법정점검+안전점검회사: 구현+검수 PASS** — CompanyType.SAFETY_INSPECTION(파급 최소·참여가드 400·5역할 무회귀)·V108(템플릿 8문 시드=실양식 p4 일치·legal_inspections UNIQUE·증거필드)·HMAC 오픈토큰(위조/무토큰/변조 403·토큰-장비 바인딩 구조적 확인)·**NFC 강제: MANUAL도 태그값 검증 우회 불가**(수동 폴백=dev 플래그 뒤·tag_verified=false 구분)·필수항목 400·서명 없음 400·409 중복·점검원 모바일 E2E 실서명·어드민 템플릿 CRUD(ADMIN만)·BP 현황+CLIENT 2트랙 실집계·조종원 트랙 무회귀. **백로그(제품 결정)**: 점검원 대상 목록 전현장 노출 — 안전점검사 2곳째 온보딩 전 현장 스코핑 필요.
  - [x] **P3d — 안전관리 이행 보고서: 구현+검수 PASS** — `/api/safety-reports`(ADMIN·BP 자기현장·CLIENT 자기원청, 화이트리스트 예외에도 CLIENT 전역차단 무약화 실증)·수치 독립 재계산 전항목 일치(확인율 60%·평균 12.0분·NFC 50% 등)·subjectToAck=에스컬 규칙 동일 코드대조·타임라인 발송→확인 사슬·미이행 4목록·현행기준 법정 병기·인쇄뷰(표지·A4·중대재해처벌법 문구·**서명 PNG 완전 부재** — 응답 grep+DOM 실증)·빈기간 graceful·모바일390·단위테스트 6/6. 경미 3(백로그): 메인 강풍 "최근" 라벨·대상장비 기준 각주·WS dev 프록시.
  - **★P3 웨이브 완료 = 재기획 로드맵 P0→P3 전체 완료 (2026-07-18)** — 전 웨이브 구현+독립 검수 PASS
  - [x] **P4a/b/c — IA·안전 시정(사용자 불만 대응): 통합 검수 PASS(교정 기준·결함 0)** — ①메뉴: 사이드바 **BP 30→12·장비 30→10·인력 27→10**(중복 0)·전 기능 **≤3클릭 실측**·⚙ 유틸 소메뉴·SubNav 탭바(투입 6탭 등)·라우트 삭제 0 ②안전 상황판: 요약 7카드 DB 일치·미확인 경보 빨강 펄스·**공지 확인율 실증(1/3→2/3·미확인자 명단·V109)**·CLIENT 읽기전용·페르소나 "5초 내 파악" PASS·지도는 카카오 키 대기(폴백 동작) ③파이프라인: 필터+URL 공유+드릴다운+1대 타임라인(deploy-check 일치)+PERSON 경로. 백로그: 인력 daily-work-logs 무해 403콜·타임라인 비선형 표시(기존)·모바일 알림표 압축.
  - [x] **P4d — 알림 필터+발신자·서류허브 필터·⚙ 슬라이드 패널: 구현+검수 PASS** — V110 notifications.sender_label(발신 27지점 "회사명 이름"·기존행 "—" 폴백)·서버 필터 findFiltered(unread/6그룹/기간/검색, **PG null바인드 타입추론 실패→boolean 가드 픽스**)·알림 페이지(그룹 pill·기간·검색, 페이징 경계 검색 정확)·서류허브 필터(장비종류=마스터 라벨·서류종류·상태·차량번호 검색·URL 동기화·D-badge 정확)·⚙ 미리캔버스식 우측 슬라이드 패널(3섹션+프로필·백드롭·ESC·모바일 전폭). 
  - [x] **폰앱·워치 UX 전수 검토(코드 수준 PASS·실기기 미검증)** — 빌드 성공(스텁 확인)·워치 FallDetectorTest 3/3·여정 전 단계 연결·[확인] 88dp·풀스크린·3등급 차등 확인. **결함 D1~D5 식별**(핵심 D2=워치 `/skep/safety` 송신 vs 폰 `/skep/alert` 데드 리스너 → 피재자 폰 로컬 경보 미실현) → P4f로 픽스. **실기기 체크리스트 A~D 산출**(google-services.json 배치·TTS/DND/풀스크린·출근맵/지오펜스/NFC·워치 낙상/중계) — 실행=사용자 몫.
  - [x] **P4e — 작업자 모바일 달력 뷰: 구현+독립 검수 PASS(재작업 1회)** — `/worker` 공개 모바일웹(출근코드 로그인) — 정산주기(26~25) 앵커 6주 그리드·근무일+OT 뱃지+서명 아이콘(✓/📷/⏳)·날짜 탭 상세·주기 밖 탭→주기 전환·◀▶·합계 카드·[내역] 탭. 신규 API `work-calendar?period=`(ledger 주기계산 재사용, 기존 daily-work-logs 무수정). 검수: 주기 경계(06-25/07-26 제외) DB 수기 재계산 완전 일치·합계 SQL 대조 일치·sd null 달력월 폴백·회귀(inspector·admin) 무손상·pageerror 0. 재작업=터치타깃 44px(로그인 32px 등 5요소→전부 실측 44.00px·헤더 52px 유지). 관찰: 다현장 혼재 시 주기 앵커=최근 로그 현장 기준(단일현장 정확)·health DOWN=dev SMTP 부재(MailHealthIndicator·기능 무관).
  - **★P4 웨이브 전체 완료(a~f, 2026-07-19)** — IA 시정·안전 상황판·파이프라인 드릴다운·알림 발신자/필터·허브 필터·⚙ 패널·작업자 달력·앱 결함 픽스 전부 구현+독립 검수 PASS.
  - [x] **P4f — 앱 결함·폴리시 픽스: 구현+독립 검수 PASS(결함 0)** — D2 배선 정정(**로컬 경보가 토큰/중계보다 먼저** 발동·`/skep/status` 처리 1곳·죽은 `/skep/alert`·`/skep/sensor` 제거·오발동 없음 — 경로 매트릭스 코드 재구성으로 확인)·워치 ACK 실시간 카운트다운(45s/300s 상수 SensorService와 일치·재시작 가드 실효·타이머 취소 전 경로)·본문 13sp 4곳·뒤로 48dp 7화면(아이콘 불변)·오류 메시지 한국어화+Log 보존·D4 힌트. 강제 재컴파일(--rerun-tasks) 폰·워치 SUCCESS+FallDetectorTest 3/3·diff 17파일 app/ 국한. 관찰 4(저위험): `/skep/ack` 사장(기존)·JSON 파싱 실패 시 경보 미발동(비현실 엣지)·deprecation 기존 패턴. 실기기 위임: 로컬 경보 실발동·잠금/DND 풀스크린·A14 권한·카운트다운 실틱.
  - [ ] **★P5 — 워치 스마트 안전(사용자: "프로그램의 핵심"·뇌출혈/혈압 최다·10h 배터리 필수)**: 기획서 §4-C 전면 재검토 확정(2026-07-19) — **W0 저전력 기반**(3색 예외보고 상태머신·BLE 묶음·워치 GPS 제거·서버 데드맨 30분 무수신·원격 정책·관제 워커 타일) → **W1 자기학습**(베이스라인 확장·맥락 3패턴·자가취소→개인 임계 자동보정 루프) → **W2 대응 체인**(RSSI>GPS>작업조 동료 3인·[제가 갑니다]·파인드미 사이렌+플래시 스트로브+근접 게이지) → **W3 릴레이+증거**(제3자 BLE 대리중계·골든타임 타임라인·도착 자동판정→이행 보고서) / **W4 건강 3겹**(혈압 체크인 게이트·고위험군 태깅·Health Connect 스팟·과로 경고 — 서버·웹 중심, 병렬 가능). 갤럭시 워치5 혈압 측정 가능(커프 보정 28일·사람별·스팟만 — 조사 완료).
  - [ ] **★특허 출원 준비(사용자 지시 2026-07-19)**: 발명 신고서+청구항 8개 초안 완성 = `docs/details/2026-07-19-watch-safety-patent-draft.md`. **출원 전 공개 금지**(앱 출시·영업자료 전에 출원). 외부(사용자): 변리사 선임→문서 전달→KIPRIS 선행조사→출원. 동봉 도면(구성도·상태천이도·시퀀스도)은 P5 설계 산출물로 작성 예정.
  - [ ] 외부(사용자): 알림톡 템플릿 3종 다온톡 등록(심사도착·반입검사 날짜·안전교육 날짜) / 실양식 잔여(업체변경 신청서·계약서·견적서·법정 체크리스트) / 카카오 Web 도메인 등록(도메인 확정 후 — VITE_KAKAO_JS_KEY+백엔드 KAKAO_JS_KEY 2곳) / 실기기 체크리스트 실행 / 현장용 블루투스 혈압계 기종(P5a·수기 폴백 가능) / Vision 결제(KOSHA·선택)
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
