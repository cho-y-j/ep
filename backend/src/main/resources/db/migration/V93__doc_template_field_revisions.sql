-- V93: 서류 OCR 필드 조정(사용자 요청).
-- 건설기계등록증: 차대(serial_number)·형식(model) 제거 — 실무상 무의미. 등록번호+제작년도만.
-- 자동차등록증: 검사유효기간(expiry_date, 만료관리용) 추가 + model 밴드 타이트(차량번호행 겹침 방지). (검사유효기간은 수기 기재 많아 OCR 불안정→확인 필요)
UPDATE document_types SET ocr_region_template = '{"version": 1, "aspect": {"w": 1653, "h": 2339}, "fields": [{"key": "vehicle_no", "box": [0.08, 0.055, 0.35, 0.07], "kind": "plate"}, {"key": "model", "box": [0.08, 0.107, 0.35, 0.046], "kind": "hangul"}, {"key": "year", "box": [0.55, 0.045, 0.43, 0.065], "kind": "year"}, {"key": "expiry_date", "box": [0.45, 0.51, 0.4, 0.088], "kind": "date_end"}]}' WHERE id = 7;  -- 자동차등록증
UPDATE document_types SET ocr_region_template = '{"version": 1, "aspect": {"w": 1240, "h": 1754}, "fields": [{"key": "vehicle_no", "box": [0.7, 0.184, 0.17, 0.062], "kind": "plate"}, {"key": "year", "box": [0.62, 0.128, 0.2, 0.044], "kind": "year"}]}' WHERE id = 32; -- 건설기계등록증
