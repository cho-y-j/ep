# Phase 3 — 수퍼어드민 영역지정(템플릿) 도구 설계·결정

> 사용자 확정(2026-07-14). 코드 없이 문서종류별 `ocr_region_template`을 만드는 도구. 기존 무손상·최소변경.

## 워크플로
`DocumentTypeAdminPage` 행 **"영역 편집"** → 라우트 `/admin/document-types/:id/regions`(ADMIN):
1. **샘플 이미지 업로드**(jpg/png/webp — 이미지만)
2. **정렬(선택)**: 사진이면 `DocumentCornerAligner`(4모서리, detect-corners 프리필) → 서버 warp(`return_warped`)로 **반듯한 이미지** 획득(1회, 캐시). 스캔/스크린샷 등 평면이면 정렬 스킵(캔버스=원본).
3. **반듯한(warp된) 캔버스 위에 필드 박스 그리기/이동/리사이즈/삭제**(무라이브러리 SVG).
4. 박스별 **field key**(datalist: 표준키+required_fields, 커스텀 허용) + **parser** 지정.
5. **실시간 추출 미리보기**(디바운스 ~700ms): 현재 박스로 미리보기 엔드포인트 호출 → 각 필드 값 + 신뢰도(score) 표시(낮으면 경고색 → 박스 재조정 유도).
6. **저장**: `{version:1,aspect,fields}` 조립 → `PATCH /api/admin/document-types/{id}` body `{ocr_region_template: "<JSON>"}`.

## ★ 좌표계 불변식 (최중요)
박스는 **반드시 warp된 이미지(natural=aspect) 위에** 그린다. 추출 시 paddle가 동일 aspect로 warp하므로 좌표 100% 일치(`document_align.py` warp dst=aspect, box*[w,h]). 원본 사진 위에 그리면 어긋남 → 워크플로가 "정렬·warp 먼저 → warped 캔버스에서만 드로잉" 강제. no-warp(평면) 경로는 캔버스=원본·aspect=원본 natural, 추출도 corners 없이 원본 dims라 일치.

## 백엔드 (2개만, paddle·DB·PATCH 무변경)
- **신규** `POST /api/admin/document-types/region-extract` (ADMIN 전용, `DetectCornersController` 패턴 소형 컨트롤러). multipart 리터럴 파라미터: `file`, `template`(초안 JSON 문자열), `corners`(옵션 JSON), `returnWarped`(옵션 bool). paddle `/extract-regions`로 프록시 → 응답 passthrough `{aligned,fields,regions,warped_image_base64?}`. 파일검증(10MB·content-type)은 `OcrRegionPreviewController` 복제. **초안 템플릿을 바디로 받아 저장 전 미리보기 가능**(기존 preview는 DB 템플릿 로드라 초안 불가).
- **수정** `PaddleOcrClient.java`: `extractRegionsRaw(bytes,filename,cornersJson,templateJson,returnWarped): JsonNode` **추가만**(기존 `extractRegions` Map 반환 무손상). `return_warped` 파트 전송 + `readTree` 전체 반환, 실패 graceful null.

## FE 컴포넌트 (관심사 분리)
- `frontend/src/features/admin/regionTemplate/DocumentTypeRegionEditorPage.tsx`(신규, 라우트 페이지·오케스트레이터): 단계 `'upload'|'align'|'edit'`, 샘플/corners/warpedUrl/aspect/boxes/selectedIdx/preview 상태, 저장 PATCH.
- `RegionBoxEditor.tsx`(신규, SVG 캔버스 기하): warp 이미지 위 박스 draw/drag/resize(8핸들)/select/delete. 좌표 0..1 정규화. `DocumentCornerAligner`의 PointerEvent+setPointerCapture+정규화 패턴 재사용.
- `FieldInspector.tsx`(신규): 선택 박스 key(datalist)·parser(select) + 미리보기값/score + 삭제.
- `regionTemplateTypes.ts`(신규): `Box`/`RegionTemplate` 타입 + 표준키·parser 상수.
- **수정**: `App.tsx` 라우트 1줄(ADMIN), `DocumentTypeAdminPage.tsx` 행별 "영역 편집" 버튼(+useNavigate) + 템플릿 유무 배지.

## 스키마·상수 (기존 그대로)
- `ocr_region_template` = `{version:1, aspect:{w,h}, fields:[{key, box:[x,y,w,h] 0..1, parser}]}`. (V83 자동차·V84 건설기계 시드와 동일)
- **parser 선택지**(authoritative, `document_align.py` `_PARSERS`): `text`·`date`·`date_range_end`·`vehicle_no`·`year`·`biz_no`.
- **field key 팔레트**: 그 doc-type `required_fields` ∪ 표준키(`vehicle_no,model,year,vin,serial_number,expiry_date,biz_no,start_date,owner_name,business_name,address,license_no,birth_date,name,registration_no`). datalist 제안 + 커스텀 snake_case 허용.

## 결정 (미결 6 확정)
1. **doc-type 로드**: `/api/admin/document-types` 리스트 fetch 후 id find(신규 GET 불필요).
2. **PDF 샘플**: 이번 범위는 **이미지만**(jpg/png/webp). PDF는 사용자가 캡처/변환. (추출 시 실제 PDF는 paddle가 처리, 무관)
3. **aspect**: 기본 **A4 세로 1653×2339** + 커스텀 w/h 입력.
4. **field key**: datalist(표준+required_fields) + **커스텀 허용**(폼 자동채움 매칭은 required_fields에 있어야 의미 있음을 힌트).
5. **정렬 필수 아님**: 사진=정렬(warp), 평면=스킵 **둘 다 지원**.
6. **score 표시**: 필드별 신뢰도 노출(낮으면 경고색).

## 최소구현 순서
① 업로드→캔버스(정렬 없이) ② RegionBoxEditor 박스 draw/move/resize/delete(0..1) ③ FieldInspector key/parser ④ 저장 PATCH + 재진입 로드 ⑤ 미리보기(코너없이 region-extract) ⑥ 사진 정렬(DocumentCornerAligner+return_warped로 캔버스 교체) ⑦ 기존 템플릿 편집 로드.

## 위험
- 좌표계(위 불변식) — warped 캔버스에서만 드로잉.
- 미리보기 과호출 → 디바운스 700ms + in-flight 취소, warp이미지 최초 1회만(미리보기는 returnWarped=false).
- 무라이브러리 리사이즈 핸들 — DocumentCornerAligner HIT_R/capture 관례 재사용.
