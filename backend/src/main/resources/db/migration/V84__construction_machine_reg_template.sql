-- 건설기계등록증(지게차·굴착기 등) 서류종류 추가 + 영역-크롭 OCR 템플릿 시드.
-- 자동차등록증(도로차량)과 별개 양식. 실샘플로 좌표 검증 완료(4필드 0.77초 추출).
-- 필드: 등록번호→vehicle_no, 형식→model, 제작년도→year, 차대일련번호→serial_number.
-- 멱등: 이미 있으면 skip.
INSERT INTO document_types
  (name, applies_to, ocr_enabled, ocr_extract_type, has_expiry, required, active,
   sort_order, blocks_assignment, requires_verification, ocr_region_template)
SELECT '건설기계등록증', 'EQUIPMENT', TRUE, 'EQUIPMENT_REGISTRATION', FALSE, FALSE, TRUE,
       11, FALSE, FALSE,
       '{"version":1,"aspect":{"w":1240,"h":1754},"fields":[{"key":"vehicle_no","box":[0.710,0.186,0.118,0.028],"parser":"text"},{"key":"model","box":[0.306,0.214,0.095,0.028],"parser":"text"},{"key":"year","box":[0.626,0.134,0.148,0.032],"parser":"year"},{"key":"serial_number","box":[0.708,0.238,0.185,0.028],"parser":"text"}]}'
WHERE NOT EXISTS (
  SELECT 1 FROM document_types WHERE name = '건설기계등록증' AND applies_to = 'EQUIPMENT'
);
