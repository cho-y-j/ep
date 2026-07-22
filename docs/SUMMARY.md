# 프로젝트 요약 (SUMMARY)

> `/wrap` 슬래시 커맨드가 세션 작업을 반영해 갱신하는 **누적 상태 요약** 문서입니다.
> 상세 근거는 `docs/details/`, 할 일은 `docs/TODO.md` 를 참조하세요.

## 개요
SKEP v2 — BP(발주)·장비공급사·인력공급사·협력사·작업자를 잇는 장비/인력 투입관리 플랫폼(모놀리스). Spring Boot 3.2(Java17)+Flyway, Vite7/React19/Tailwind4. 로컬 dev: 백엔드 8091(dev 프로필)·프론트 5185·docker postgres 15433.

## 현재 상태 (2026-07-11)
- **정산 재설계 완료·독립검증**: 월대=(월대÷25)×근무일수+OT, 일대=일대×근무일수+OT. 근무일수·OT는 투입 시점 확정, 현장별 정산일, 날짜범위 조회. `SettlementCalculator`에 격리.
- **5역할 기능·성능 감사 완료**: 로그인·핵심기능·권한경계·크로스테넌트 격리 전부 정상(보안 우회 없음). 경미 결함은 아래 처리.
- **감사 수정 완료**: 클라이언트 오류 500→400, 견적/안전알림 직렬화 N+1 배치화(출력 불변).
- **UX P0 완료**: 거짓 안내문 3곳 수정, 견적 후보 자동조회, BP 대시보드 처리대기 위젯.
- **자동화 3종 완료·독립검증(실데이터, 0 결함)**: E 견적 후보 배지, D 정산 근무일수 자동파생(수동 우선), A 선정→배차 초안(V80 별도테이블 격리).
- **safe-8 자동화 완료·검증**: 서명대기 집계·OT 프리필·견적 단계칩·일괄수락·다이얼로그 통일·만료 알림 스케줄러·공급사 위젯 정합(+E/D/A). 독립 QA 부분검증(#7 스케줄러는 cron 미트리거로 로직만).
- **캡스톤 완료**: 4주체 기능테스트(협력사 V77 실동작 확인) + 전문가 UX·자동화 전략 → `docs/details/2026-07-11-ux-automation-strategy.md`.
- **투입 파이프라인 스펙 확정**: `docs/details/deployment-pipeline-spec.md`(서류→검사→투입대기→투입 정산율확정→작업확인서→거래내역서).
- **협력사 A/B/C/D 완료·QA 검증**: A 대행 전체수정(격리 유지) / B 자가로그인+부모승인 / C 투입대기 readiness(`GET /api/resources/readiness`, 게이트 미러링) / D 거래내역서(`GET /api/settlements/statement` PDF+Excel, 정산 숫자 그대로). 앞 캡스톤의 협력사 갭 2건 해소됨.
- **주체별 UX 검토 완료**: `docs/details/2026-07-11-per-role-ux-review.md`.
- **커밋+푸시 완료**: `fda8556`을 `cho-y-j/ep`(공개)에 반영(사용자 실행). **단, 그 이후 safe-8(R1 backend+R2 frontend) 변경은 미커밋** — 다음 커밋 대상.
- **미완/보류**: §7 결정 6건(협력사 2 + C/A-2/B/서류허브·네비). 워치 낙상감지 on-device 실검증은 사용자 몫.
- **(2026-07-14) 서류 파이프라인 검증 + OCR 만료 자동화**: 장비임대사업자 **등록→서류업로드→서류관리(만료)→BP 심사전달**이 **이미 실동작**함을 E2E 8단계 실데이터로 확인(새로 지을 것 거의 없음). 신규 구현 = **서류 업로드 시 만료일 로컬 PaddleOCR 비동기 자동 백필**(정기검사증, V82) — 업로드 즉시 완료 후 ~100초 뒤 검사유효기간 자동 채움+알림. **면허/화물자격은 `verify_endpoint` 게이트로 Google Vision 즉시확인 경로 유지(무손상)**. 독립 QA가 파서 버그(40자 window vs paddle 읽기순서 186자) 발견→범위 끝날짜 캡처+window300 수정→재검증 통과(단위테스트 7/7). 상세: `docs/details/ocr-expiry-backfill-design.md`, 엔진 라우팅은 메모리 `server-hardware-ocr`.
- **(2026-07-14 이어서) 장비등록 등록증 정합화 + 맞춘이미지 미리보기**: ① 4모서리 정렬 후 warp 이미지가 미리보기·추출로 안 뜨던 버그 수정 — `OcrRegionPreviewController`가 corners 있을 때 `warped_image_base64` 반환(인증API 실측 3.9MB), `EquipmentCreateForm`이 "맞춘 이미지(자동 크롭·원근보정)"로 렌더. ② 장비추가 폼이 **자동차등록증 하드코딩** → **장비종류 먼저 선택 → 종류에 걸린 필수 등록증(자동차/건설기계) 설정기반 자동선택**(`pickRegistrationType`: applies_to_categories+required, EquipmentFields `hideCategory`). ③ **V86**: 현 EquipmentCategory 9종이 전부 건설기계라 **건설기계등록증 필수(9종 스코프)**·자동차등록증 비필수. 건설기계 경로 E2E 검증(연식/차량번호/차명/차대번호 **전 필드 추출** + warp 2.4MB). ④ **자동차등록증 템플릿 재보정(V87)**: 기존 박스가 발급본 레이아웃과 어긋나 빈 값이던 것 → canonical warp+전체OCR로 좌표 재측정·교체 → vehicle_no/model/year/vin 추출 성공(검사유효기간은 수기라 수동보정). **미결(사용자 확인대기)**: 차량종류(트레일러/화물 등) enum 추가해야 자동차등록증을 그쪽 필수로 지정 가능. detect-corners는 자동차 샘플(어두운배경)에서 오검출→4모서리 UI 보정 필요. 상세: `docs/details/equipment-registration-by-category.md`.

- **(2026-07-18) ★재기획 로드맵 P0→P3 전체 완료(자동 파이프라인: 오퍼스 구현→오퍼스 독립검수→통과시 자동 다음 웨이브)** — V96~V108, 전 웨이브 검수 PASS: **P0** BP 원스톱 심사(뷰어·승인·후속액션) / **P0.5** 현장 즉시 도입(계약 5분류 단가·기통과 소급·구두승인 단독모드·일일확인서→월간원장·작업자 내역) / **P1** 작업계획서 자동화(L3 사전판정·formValues 이어수정·서명 실문서화+전원재서명 강제·L1 OCR 오토필+주야2인+첨부조립·L2 자원교체+원본 자동종료·업체변경 신청서 v0) / **P2** IA 6허브+오늘할일 대시보드·견적 템플릿·정산 5분류(OtBreakdownCalculator, 기존 정산 바이트 무회귀)·투입-정산 디커플·원청 CLIENT 관제+혼잡도 / **P3** 안전(강풍경보 크론 실발화·법정가드·점검게이트·정비도래·알림 3등급+TTS+ack 에스컬 루프·NFC 법정점검+안전점검회사·이행 보고서 인쇄). 기획서: `docs/details/2026-07-17-ia-flow-replan.md`. 대기: 사용자 외부액션(알림톡 템플릿 3종·실양식 잔여·실기기 TTS/NFC 검증).
- **(2026-07-19 새벽) ★★P5 워치 스마트 안전 전체 완료 + 최종 종합 검수 + 제품소개서**: W0 저전력·데드맨(V111) → W1 자기학습(V112·오탐 보정 루프) → W2 파인드미·대응체인 → W3 BLE 릴레이·골든타임(V113) → W4 건강 3겹(V114) 전 웨이브 구현+독립 검수 PASS(워치 체인 E2E 9/9 결함 0·돈 체인 수기=API=SQL 3중 일치). 최종 종합 검수 4분할 병렬 → 개선 13건 일괄(죽은 위젯 제거·정산 링크·UTC·삭제 2페이지·혈압임계 UI·워치 키 정정 등) 완료. **산출물 3종: `docs/제품소개서.pdf`(9p 실화면 12컷) · `docs/특허_발명신고서_초안.pdf`(청구항 8+도면 3) · `docs/실기기-검증-체크리스트.md`(8영역 40항목)**. 데모 DB 자연화(판교 물류센터 신축현장·대한건설(주) 등). 커밋 ae9d47e 이후 전량 미커밋 — 푸시 대기.
- **(2026-07-19) ★P4 시정 웨이브 전체 완료(a~f) + 특허 초안 + P5 재편**: 사용자 불만("메뉴 많고 정신없다") 대응 — **P4a/b/c** 사이드바 다이어트(BP 30→12)·⚙ 유틸 소메뉴·SubNav 탭바·안전 상황판(카카오 지도 자리+요약7카드+공지 확인추적 V109)·파이프라인 필터/드릴다운 / **P4d** 알림 발신자(V110 sender_label)+서버 필터(6그룹·기간·검색)·서류허브 필터(장비종류·상태·차량번호)·⚙ 미리캔버스식 슬라이드 패널 / **P4e** 작업자 모바일 달력 `/worker`(정산주기 26~25 앵커·OT 뱃지·서명 아이콘·합계, 검수 수기 재계산 일치·터치타깃 44px) / **P4f** 폰·워치 결함 픽스(D2 워치→폰 로컬 경보 배선·ACK 카운트다운·가독성 13sp/48dp — 검수 결함0). 폰/워치 전수 검토=코드수준 PASS+실기기 체크리스트 산출. **특허**: 워치 안전 발명 신고서+청구항 8개 초안 `docs/details/2026-07-19-watch-safety-patent-draft.md`(출원 전 공개 금지·변리사=사용자 액션). **P5 전면 재편**: 워치 중심 W0(저전력·데드맨)→W1(자기학습)→W2(대응체인)→W3(릴레이·증거)+W4(건강 3겹) — 기획서 §4-C. 지도는 카카오 키 도메인 등록 대기(구글 지도 이력 없음 확인).
- **(2026-07-17) 전면 재기획(작업1: 기획=페이블/구현=오퍼스) + P0 완료**: 기획서 `docs/details/2026-07-17-ia-flow-replan.md` — 코드 실사 2회(BP흐름·IA / 작업계획서 도메인) + 실양식 3종(작업계획서 67p·일일/월간 작업확인서) 분석. **확정**: 계약 단계 신설(단가 원천, 견적은 근거자료)·OT 5분류 단가(조출/점심/연장/야간/철야, 정산주기 26~25)·기통과 소급(BP 일괄승인)·**공급사 단독모드(구두승인 기록+전표사진)**·6허브 IA·작업계획서 자동화 L3→L1→L2(전원 재서명·1계획서=1장비·OCR 면허원천·복제+원본종료)·안전 1순위 S1강풍경보/S2유도원경보/S3점검게이트. **P0(BP 원스톱 심사) 구현+독립 QA PASS**: V96 상태머신·문서목록 API·BP 열람권한(심사 수신분만 200, 경계 403 실측)·인라인 뷰어·승인/반려·승인 후 계획서 프리필+점검요청(계획서 없이 발행 실측)·메일 CC. Low 1건(인력공급사 프리필) 픽스 진행.
- **(2026-07-23) 무로그인 수집 업로드 자동 진위확인 + 만료일 3단계 관리**: `publicUpload`가 기존 `DocumentUploadedEvent`(AFTER_COMMIT 비동기 AutoVerify/OCR백필 리스너 재사용)를 발행 — **item당 첫 업로드 1회만**(남용 방지), 시스템 액터·전 예외 삼킴(fail-open, 업로드 절대 안 막음). 만료일 우선순위 ①자동추출(`VerificationService`가 OCR/보충 필드 expiry를 **null일 때만** 세팅 — 기존값 불덮음, 진위 API 응답엔 만료일 필드 없음 확인) ②협력사 입력(`CollectPublicPage` 스테이징에 "만료일 (모르면 비워두세요)" date 입력, `has_expiry` 타입만) ③관리자 수기. 서류관리(my-supplier)에 **"만료일 없음" 필터**(has_expiry 타입 한정)+**! 검토 뱃지**(OCR_REVIEW_REQUIRED). 실브라우저(puppeteer)+DB로 6시나리오 검증(만료일 저장/null 업로드/verify 실패에도 업로드 성공/재검증 시 만료일 유지/재업로드 rate limit/필터·뱃지).

## 주요 구성요소
- **backend**: com.skep.{quotation(견적·제안·배차·초안), settlement, site, company, workconfirmation, safety, fieldDeployment, verify(OcrExpiryBackfill·PaddleOcrClient·OcrExpiryParser), document(OcrRegionPreviewController·DetectCornersController), equipment(EquipmentType 마스터·EquipmentTypeDocRequirement junction·EquipmentDocRequirementService·EquipmentTypeService), ...}. Flyway 최신 = **V110**(V96~V108 재기획 P0~P3, V109 공지 확인추적, V110 알림 발신자). category 는 enum→**String**(equipment_type 마스터 검증). 종류×서류·역할×서류 필수/선택은 `equipment_type_doc_requirement`·`person_role_doc_requirement` junction(3상태). Hibernate ddl-auto=validate. JSON 전역 SNAKE_CASE.
- **frontend**: features/{quotation, settlement, site, dashboard, fieldDeployment, ...}. 공용 AppShell·CollapsibleSection.
- **app**: watch-app(Wear OS 낙상감지), 모바일 앱.
- **infra**: docker-compose(postgres/redis/onlyoffice). dev-local/ 스크립트(backend.sh·frontend.sh).

## 최근 변경 이력
| 날짜 | 세션 요약 | 상세 |
|------|-----------|------|
| 2026-07-14 | **장비종류 마스터 데이터화 + 종류별 서류 체크리스트 + OCR 6종 밴드전환**: ①equipment_type 마스터(25종, V90) ②종류 CRUD 어드민 ③종류별 서류 junction(V91, 필수/선택/해당없음 3상태·만료표시, 백필 byte-identical 무회귀) ④체크리스트 UI ⑤`EquipmentCategory` enum→String(어드민 새 종류 추가→등록·견적 실동작, 매칭 `Objects.equals`+단위테스트5/5, 라벨 DB조회) ⑥OCR 문서 6종 밴드+패턴 전환(V92, kind 4종 추가, 실샘플 검증). clean-boot 통합검증·독립QA 통과 | docs/details/equipment-type-master-design.md |
| 2026-07-14 | 장비등록 정합화: 4모서리 정렬 후 **맞춘 이미지 미리보기** + 폼 종류별 필수 등록증 설정기반 자동선택 + OCR 자동차등록증 밴드전환(V88, 코너강건) + 영역편집기 종횡비 왜곡 수정(비-A4 대응) | docs/details/equipment-registration-by-category.md |
| 2026-07-14 | 로컬 영역-템플릿 OCR + 4모서리 정렬(Phase1·2): 공식문서 필드만 크롭 OCR ~1-2초(전체 90초 대신), 폰사진 배경제거+원근보정(DocumentCornerAligner). 자동차등록증(V83)·건설기계등록증(V84) 템플릿, detect_corners Otsu개선. admin PATCH 영속 선재버그 수정. 독립QA 검증(회귀0, 유닛7/7) | docs/details/ocr-region-template-design.md |
| 2026-07-14 | 서류 파이프라인 실동작 검증(등록→서류업로드→서류관리→만료→BP전달 8단계 실데이터 통과) + OCR 만료 자동 백필(정기검사증 로컬 paddle 비동기 V82, 면허/화물 Vision 즉시확인 유지). 독립 QA 파서버그 발견→수정→재검증(단위테스트 7/7) | docs/details/ocr-expiry-backfill-design.md |
| 2026-07-12 | 추천 자동화·편의성 12개(통합발송·검사추천·월마감·만료외부발송OFF·온보딩·OT제안 / 관제위젯·허브·롤업 등), QA 검증, 커밋 0178dfd | docs/details/2026-07-11-per-role-ux-review.md |
| 2026-07-11 | 인력 OT(V81)·자원 파이프라인 보드·BP 네비 4그룹·서류허브 + 종합보고(사용자중심 UX), QA 검증, 커밋 9589c09 | docs/details/2026-07-11-final-report.md |
| 2026-07-11 | 협력사 A/B/C/D(대행수정·자가로그인승인·투입대기·거래내역서) + 주체별 UX 검토, 독립 QA 검증 | docs/details/2026-07-11-per-role-ux-review.md, deployment-pipeline-spec.md |
| 2026-07-11 | safe-8 자동화 + 캡스톤(4주체 기능테스트·UX/자동화 전략) | docs/details/2026-07-11-ux-automation-strategy.md |
| 2026-07-11 | 감사 수정(500→400·N+1) + UX P0 + 자동화 E/D/A, 실데이터 독립검증 | docs/details/2026-07-11-audit-remediation-automation.md |
| 2026-07-10 | 정산 재설계(÷25·근무일수·현장정산일 V79), 협력사 설계, 워치 낙상감지 | docs/details/2026-07-10-*.md |

## 참고 문서
- `docs/details/` — 세션·주제별 상세 기록
- `docs/TODO.md` — 미완료/후속 작업
- `docs/API_SPEC.md` · `docs/ERD.md` · `docs/CORE_BUSINESS_RULES.md` — 기존 명세
