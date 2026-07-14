# 서류 업로드 시 만료일 로컬 PaddleOCR 비동기 자동 백필 — 구현 설계·결정

> 사용자 확정(2026-07-13). "모든 과정 자동화 + 최고 UX + 최고의 방법". 이 문서대로 수술적 구현한다.
> 기존 등록/업로드/검증/BP전달 동작 **무손상**. 인터랙티브 ocr-preview(업로드 전 프리뷰)는 **범위 밖(미변경)**.

## ★ 엔진 라우팅 (사용자 확정 2026-07-13) — 서류 타입별 자동 분기
- **verify_endpoint 보유 타입 = Google Vision 즉시 진위확인** (기존 `AutoVerifyTrigger`+정부API, **미변경**). 실측 대상: 운전면허증(RIMS_LICENSE)·화물운송자격증(CARGO_LICENSE)·안전교육이수증(KOSHA)·사업자등록증(NTS_BIZ). **이유: 면허·화물자격은 즉시 확인 필요 → 빠른 Vision.** paddle(90초) 안 씀.
- **verify_endpoint 없고 만료+OCR 타입 = 로컬 paddle 비동기 백필**(이 기능). 대상: **정기검사증(id=8)** — 검사유효기간. 90초라도 등록 안 막음(비동기).
- 즉 **paddle 백필 게이트에 `verify_endpoint == null` 필수** → 면허/화물이 paddle로 새지 않음(Vision이 이미 즉시 처리).

## 목표
서류 업로드 → **즉시 저장**(사용자 안 기다림) → 트랜잭션 commit 후 **전용 스레드**에서 로컬 PaddleOCR(~90초 CPU) → 만료일 파싱 → `documents.expiry_date`(NULL일 때만) 백필 + 소유사 알림. Google Vision 경로는 `ocr.engine` 플래그로 유지·전환.

## 확정 결정 (6)
1. **장비 대상**: 마이그레이션으로 **정기검사증(id=8, has_expiry=TRUE·verify_endpoint=NULL)** 에 `ocr_enabled=TRUE, ocr_extract_type='EQUIPMENT_REGISTRATION'` 부여(검사유효기간 담지). 자동차등록증(id=7, `has_expiry=FALSE`)은 만료개념 없어 제외. **운전면허증·화물운송자격증은 paddle 대상 아님 — verify_endpoint 보유라 Google Vision 즉시경로(위 ★)가 처리.**
2. **알림**: 성공 = 신규 `NotificationType.DOCUMENT_EXPIRY_EXTRACTED`("만료일 자동추출됨"). 실패/미검출 = 기존 `DOCUMENT_OCR_REVIEW` 재사용("만료일 자동추출 실패 — 직접 입력").
3. **prod 배치**: `ocr.paddle.url` config(기본 `http://127.0.0.1:8100`). dev는 호스트 백엔드→localhost:8100 직접. prod 도커(`skep-v2-backend`)→host paddle 도달(host-gateway 또는 paddle 컨테이너화)은 **별건 defer**, URL만 주입 가능하게.
4. **스레드풀**: 신규 전용 `ocrExecutor`(core 1 / max 2 / 작은 큐 / 비차단 reject+로그). 공유 `taskExecutor`(CallerRunsPolicy) 재사용 금지 — 90초가 HTTP 스레드 점유 방지.
5. **만료 UX**: `verify_endpoint==null && ocr_enabled && has_expiry && ocr.engine!=off` 타입(=paddle 백필 대상, 정기검사증)만 업로드 시 만료일 **선택**(BE `EXPIRY_REQUIRED` 완화 + FE 안내 "비워두면 OCR이 ~1~2분 후 자동 입력"). 면허/화물 등 verify 타입·그 외 `has_expiry` 타입은 **기존대로 필수**(무손상).
6. **신뢰도**: 앵커 정규식 검출 필수 + 연도 sane(2000..now+30y) + 범위는 끝날짜. 미검출/비정상 → 안 채움. `expiry_date IS NULL`에만 write → 오탐이 기존값 못 덮음.

## 파일별 touchpoints
| 경로 | 신규/수정 | 무엇을 |
|---|---|---|
| `backend/.../verify/OcrExpiryBackfillTrigger.java` | 신규 | `@Async("ocrExecutor") @TransactionalEventListener(AFTER_COMMIT) onUpload(DocumentUploadedEvent)`. 게이트 검사 후 서비스 호출. `AutoVerifyTrigger.java:41-52` 패턴 복제 |
| `backend/.../verify/OcrExpiryBackfillService.java` | 신규 | 엔진 분기 OCR + 파싱 + write + 알림 (`@Transactional`) |
| `backend/.../verify/PaddleOcrClient.java` | 신규 | `POST {ocr.paddle.url}/ocr` 멀티파트 `image` → `fullText`. timeout=`ocr.paddle.timeout-seconds`(120). 실패 시 null(graceful). `verify.enabled`와 무관 |
| `backend/.../verify/OcrExpiryParser.java` | 신규 | `parse(extractType, fullText) → Optional<LocalDate>`. verify-api `OcrExtractController` 정규식 이식: LICENSE(:406), CARGO(:587), EQUIPMENT_REGISTRATION(:667, 범위 끝날짜 :669-675) |
| `backend/.../config/AsyncConfig.java` | 수정 | `@Bean("ocrExecutor")` 추가 |
| `backend/.../document/DocumentRepository.java` | 수정 | `@Modifying @Query("update Document d set d.expiryDate=:date, d.updatedAt=:now where d.id=:id and d.expiryDate is null")` — verification_status clobber 방지(lost-update 회피) |
| `backend/.../document/DocumentService.java` | 수정 | `EXPIRY_REQUIRED`(:182-184) 완화: `ocrEnabled && hasExpiry && engine!=off`면 만료일 null 허용 |
| `backend/.../notification/NotificationType.java` | 수정 | `DOCUMENT_EXPIRY_EXTRACTED` 상수 1줄 |
| `backend/src/main/resources/application.yml` | 수정 | `ocr.engine=${OCR_ENGINE:off}`, `ocr.paddle.url=${PADDLE_OCR_URL:http://127.0.0.1:8100}`, `ocr.paddle.timeout-seconds=120` |
| `backend/.../application-dev.yml` | 수정 | `ocr.engine: paddle` (VERIFY_ENABLED과 독립) |
| `backend/.../db/migration/V82__ocr_expiry_backfill.sql` | 신규 | 정기검사증 `ocr_enabled=TRUE, ocr_extract_type='EQUIPMENT_REGISTRATION'` (다음 가용 V번호 확인) |
| `frontend/.../document/OcrUploadDialog.tsx` | 수정 | OCR 대상+has_expiry면 만료일 필수 완화(`:363`) + 안내문구. (프리뷰 로직 미변경) |

## 데이터 흐름
업로드(만료 미입력, 완화 통과) → doc 저장(expiry NULL) → 즉시 응답 → `DocumentUploadedEvent`(`DocumentService.java:247`) → commit 후:
- `AutoVerifyTrigger`(기존, taskExecutor) — verify_endpoint 검증(dev no-op)
- `OcrExpiryBackfillTrigger`(신규, ocrExecutor) — 게이트[`engine!=off && type.verifyEndpoint==null && ocr_enabled && has_expiry && expiry==null`] 통과 → `FileStorage.load` → engine=paddle: `PaddleOcrClient.ocrFullText`→`OcrExpiryParser.parse`(EQUIPMENT_REGISTRATION 앵커); engine=google: `VerifyClient.extractOcr`의 `expiryDate` 재사용 → 유효시 `updateExpiryIfNull` + audit + `sendToCompany(DOCUMENT_EXPIRY_EXTRACTED)`; 미검출시 안 채움(+선택 실패알림). **verify_endpoint 보유 타입(면허/화물/KOSHA/사업자)은 게이트에서 제외 — Vision 즉시경로가 처리.**
→ FE 목록 재조회 시 만료일 표시 + 알림 벨.

## 위험/주의
1. 90초 스레드 점유 → 전용 `ocrExecutor` 비차단.
2. AutoVerify와 whole-row lost-update → `@Modifying` 타깃 UPDATE + `expiry_date IS NULL` 가드(idempotent).
3. google 엔진 시 verify_endpoint 타입 중복 OCR 허용(목적 상이).
4. prod docker→host paddle 도달 = 별건(URL config).
5. 파서 오탐 → null에만 write라 기존값 안전. anchor+sane로 최소화.
6. dev 분리: 백필 게이트=`ocr.engine`(별개). engine=paddle은 `verify.enabled=false`여도 동작.
7. collection 업로드(`uploadViaCollection`, 이벤트 미발행)는 대상 아님(현행 유지).

## 범위 밖(이번 미변경)
- 인터랙티브 ocr-preview(업로드 전 프리뷰, 90초 블로킹 위험) — 현행 유지.
- prod paddle 도커 패키징 — URL만 config, 배치는 별건.
- ADMIN/BP 통합 서류관리, BP 심사 상태머신 — 별건.

## 검증·수정 이력 (2026-07-14)
- 구현: `PaddleOcrClient`·`OcrExpiryParser`·`OcrExpiryBackfillService`·`OcrExpiryBackfillTrigger` + `AsyncConfig`(ocrExecutor)·`DocumentRepository.updateExpiryIfNull`·`DocumentService`(만료 완화)·`NotificationType.DOCUMENT_EXPIRY_EXTRACTED`·`V82`·`OcrUploadDialog`. 백엔드 compile + FE tsc 통과.
- **독립 QA 1차(실데이터): 파서 버그 발견** — `VEHICLE_EXPIRY` 정규식의 앵커 window 40자가 paddle 읽기순서(앵커 idx460 ↔ 검사유효기간 날짜 idx646, **186자 이격**)를 못 넘어 만료일 미검출(B/C 실패, A즉시응답·D게이트·E무손상은 통과). 비동기 인프라(트리거·paddle호출·엔진라우팅·null가드)는 정상.
- **수정**: `OcrExpiryParser`를 "범위(시작 끝)의 **끝날짜 캡처** + window 300 + 구분자 `[\s~]`(공백/개행/물결)"로 재작성. 회귀 단위테스트 `OcrExpiryParserTest` 7/7(실 OCR 발췌 포함).
- **재검증 통과**: 정기검사증 만료 미지정 업로드 → 즉시응답 **0.303초** → ~100초 후 `expiry_date=2026-05-19` 자동 백필 + `DOCUMENT_EXPIRY_EXTRACTED` 알림.
