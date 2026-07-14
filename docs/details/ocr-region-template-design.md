# 공식문서 영역-크롭 OCR (정렬→크롭→영역OCR) — 구현 설계

> 사용자 확정(2026-07-14). Vision 대안 = 로컬·무료·프라이버시·빠름. 실측 근거: 전체 124영역 90초 vs 20% 크롭 61영역 11.6초 → 타이트 필드박스 ~1-2초. 기존 등록/업로드/검증/백필/`ocr-preview`(Vision) 무손상 — 신규 경로는 "템플릿 보유 doc-type"에만 분기.

## 원칙
- **paddle-ocr는 stateless 유지** — 템플릿(영역맵)은 요청 바디로 받음(메인 백엔드가 DB 소유).
- 기존 `/ocr`·`ocr_engine`·`preprocess`·`reading_order` 재사용, 신규 엔드포인트만 얹음.
- 원근보정(warp)은 **서버 cv2**(FE는 4점만 전송). PDF/스캔은 평면이라 **warp 스킵**.
- 영역맵 저장 = `document_types` **JSON 컬럼 1개**(기존 `required_fields` 관례). 새 테이블 없음.
- FE 4모서리 UI는 **외부 라이브러리 없이 SVG+PointerEvent**.

## 엔드포인트 (paddle-ocr, 신규 `app/document_align.py` + main.py 라우트)
### `POST /extract-regions` (Phase 1 핵심)
- 요청 multipart: `image`(파일), `template`(JSON 문자열), `corners`(선택 JSON `[[x,y]x4]`; 없으면 warp 스킵), `return_warped`(선택 bool)
- 처리: `_load_image` 재사용 → (corners 있으면) `cv2.getPerspectiveTransform`+`warpPerspective`(dst=`aspect.w×aspect.h`) → field별 정규화 box를 픽셀 환산해 크롭 → `ocr_engine.run(crop)` → `reading_order.reconstruct` → `parser` 정규화
- 응답: `{"aligned":bool, "fields":{key:value}, "regions":[{key,text,score}], "warped_image_base64"?:str}`
### `POST /detect-corners` (Phase 2)
- 요청: `image`. 응답: `{"detected":bool, "corners":[[x,y]x4], "image_size":[w,h]}`. cv2 그레이→blur→Canny→findContours→최대 4점 approxPolyDP. 실패 시 이미지 꼭짓점 폴백.

## 템플릿 JSON 스키마 (`document_types.ocr_region_template`)
```json
{
  "version": 1,
  "aspect": { "w": 1653, "h": 2339 },
  "fields": [
    { "key": "vehicle_no",  "box": [x,y,w,h], "parser": "vehicle_no" },
    { "key": "model",       "box": [x,y,w,h], "parser": "text" },
    { "key": "year",        "box": [x,y,w,h], "parser": "year" },
    { "key": "expiry_date", "box": [x,y,w,h], "parser": "date_range_end" }
  ]
}
```
- `box` = `[x,y,w,h]` **0..1 정규화 분수**(warp 후 캔버스 기준). `aspect` = warp 목표 크기 비율.
- `key` = 그 doc-type의 `required_fields` 키(snake_case) → FE 재매핑 불필요.
- `parser`: `text`(기본)·`date`·`date_range_end`(검사유효기간 끝값)·`vehicle_no`·`biz_no`·`year`. `OcrExpiryParser` 범위-끝 로직과 동개념.

## DB / 백엔드
- **V83** `ALTER TABLE document_types ADD COLUMN ocr_region_template TEXT;` (+ Phase1 시드 UPDATE: 자동차등록증/정기검사증)
- `DocumentType`에 `ocrRegionTemplate`(text) + getter/setter. `DocumentTypeResponse`에 `ocr_region_template` 노출(FE 템플릿 유무 판단·admin 로드). `DocumentTypeAdminController.UpdateBody`에 필드 추가 → PATCH 저장.
- 신규 `OcrRegionPreviewController` `POST /api/documents/ocr-region-preview`(multipart: `file`, `documentTypeId`, `corners`?) — 권한/파일검증은 `OcrPreviewController` 복제. DocumentType 로드→template 취득(없으면 `{ok:false,reasonCode:'NO_TEMPLATE'}`)→`PaddleOcrClient.extractRegions`→`{ok,fields}`. 응답형태 기존 ocr-preview와 동일 → FE 리뷰 UI 재사용.
- `PaddleOcrClient.extractRegions(bytes, filename, cornersJson, templateJson)` — 기존 `ocrFullText`의 webClient/url/timeout 재사용, `/extract-regions`로 multipart POST, `fields` 파싱. graceful null.

## FE
- **Phase 1(최소):** `EquipmentCreateForm` 자동차등록증-우선 흐름에서 그 doc-type가 `ocr_region_template` 보유하면 `/api/documents/ocr-region-preview` 호출(warp 없이) → `fields` 자동채움. 기존 `ocr-preview`(Vision) 경로는 템플릿 없을 때만.
- **Phase 2:** 신규 `DocumentCornerAligner.tsx`(SVG 4핸들 드래그) + `OcrUploadDialog` step `'align'` 삽입(파일선택→정렬→extract-regions). 폰 capture·웹 공통.

## 만료 백필 업그레이드 (보너스)
- `OcrExpiryBackfillService.extractExpiry`: doc-type가 `expiry_date` field 템플릿 보유 시 `/extract-regions`(코너 자동 or 스킵)로 `fields["expiry_date"]` 직접 취득(수 초). 미보유/실패 시 기존 `ocrFullText`+`OcrExpiryParser` full-OCR 폴백(무손상).

## 단계
- **Phase 1**: extract-regions(warp 지원, 스킵 가능) + 백엔드(client/controller/V83/DTO/admin) + 자동차등록증·정기검사증 템플릿 시드 + 백필 업그레이드 + EquipmentCreateForm 최소 연동. → PDF/스캔 즉시 빠름.
- **Phase 2**: detect-corners + SVG 4모서리 UI(폰+웹).
- **Phase 3**: 수퍼어드민 영역맵 지정 도구(DocumentTypeAdminPage 연계 — 샘플 업로드→박스 드로잉→key 배정→PATCH 저장).
- **Phase 4**: 면허/사업자/자격증 템플릿 확장. VIN 컬럼·WMI는 조건부(도로차량만 신뢰, 건설기계 PIN은 불안정 → OCR 직접추출 우선).

## 결정/위험
- 영역맵 = JSON 컬럼(테이블 아님) — 양식 개정 이력이 실제 요구되면 테이블 승격.
- 서버 warp 확정. 정렬 미리보기는 `return_warped` 옵션.
- 정렬 UI 무라이브러리 SVG 확정(package.json에 크롭 라이브러리 없음).
- FE 분기 기준 = `ocr_region_template != null`.
- paddle 구동: 호스트 8100(`PADDLE_OCR_URL`), docker-compose 미등록 — 배포는 별건(도커화는 전체 완료 후).
- VIN: `equipment`에 vin 컬럼 없음 → 저장 필요 확정 시 신설. 건설기계 WMI 신뢰도 낮음.
