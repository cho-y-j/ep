-- ============================================================
-- V83: 영역-크롭 OCR 템플릿(영역맵) 컬럼 + 자동차등록증 시드.
--
-- document_types.ocr_region_template (JSON) — (옵션)정렬 후 필드별 정규화 박스[x,y,w,h]
-- 로 크롭해 로컬 PaddleOCR 로 영역 OCR. NULL 이면 기존 full-OCR 경로 유지(무손상).
--
-- 자동차등록증(EQUIPMENT): 프로토타입으로 검증된 5필드 템플릿 시드
-- (vehicle_no / model / year / vin / expiry_date). box=0..1 정규화 분수, aspect=warp 목표 비율.
--
-- 멱등: ADD COLUMN IF NOT EXISTS + UPDATE ... WHERE.
-- ============================================================
ALTER TABLE document_types ADD COLUMN IF NOT EXISTS ocr_region_template TEXT;

UPDATE document_types SET
    ocr_region_template = '{"version":1,"aspect":{"w":1653,"h":2339},"fields":[{"key":"vehicle_no","box":[0.215,0.185,0.105,0.024],"parser":"vehicle_no"},{"key":"model","box":[0.225,0.203,0.240,0.020],"parser":"text"},{"key":"year","box":[0.830,0.203,0.095,0.022],"parser":"year"},{"key":"vin","box":[0.225,0.219,0.175,0.020],"parser":"text"},{"key":"expiry_date","box":[0.500,0.459,0.170,0.024],"parser":"date_range_end"}]}'
    WHERE applies_to = 'EQUIPMENT' AND name = '자동차등록증';
