-- 인력 서류 4종 영역-크롭 OCR 템플릿 시드 (실샘플로 좌표 검증 완료, 12필드 중 11 정확).
-- 운전면허(1)·화물운송자격(13)·안전교육이수증(3)은 기존 타입 UPDATE, 건설기계조종사면허증은 신규 INSERT.
-- 검증 진위: 운전면허=RIMS·화물=CARGO(로컬추출 번호→정부API), 안전교육=QR, 조종사=만료관리(검증API 없음).

UPDATE document_types
SET ocr_region_template = '{"version":1,"aspect":{"w":1240,"h":1754},"fields":[{"key":"license_no","box":[0.348,0.202,0.505,0.092],"parser":"text"},{"key":"name","box":[0.36,0.298,0.185,0.07],"parser":"text"},{"key":"expiry_date","box":[0.486,0.679,0.353,0.084],"parser":"date_range_end"}]}'
WHERE id = 1 AND name = '운전면허증';

UPDATE document_types
SET ocr_region_template = '{"version":1,"aspect":{"w":1754,"h":1240},"fields":[{"key":"license_no","box":[0.352,0.495,0.295,0.078],"parser":"text"},{"key":"name","box":[0.353,0.26,0.145,0.095],"parser":"text"},{"key":"birth_date","box":[0.074,0.349,0.535,0.127],"parser":"date"}]}'
WHERE id = 13 AND name = '화물운송자격증';

UPDATE document_types
SET ocr_region_template = '{"version":1,"aspect":{"w":1754,"h":1240},"fields":[{"key":"registration_no","box":[0.575,0.415,0.295,0.078],"parser":"text"},{"key":"name","box":[0.542,0.26,0.136,0.082],"parser":"text"},{"key":"completed_date","box":[0.387,0.487,0.377,0.083],"parser":"date"}]}'
WHERE id = 3 AND name = '안전교육 이수증';

INSERT INTO document_types
  (name, applies_to, ocr_enabled, has_expiry, required, active, sort_order,
   blocks_assignment, requires_verification, ocr_region_template)
SELECT '건설기계조종사면허증', 'PERSON', TRUE, TRUE, FALSE, TRUE, 20, FALSE, FALSE,
       '{"version":1,"aspect":{"w":1754,"h":1240},"fields":[{"key":"license_no","box":[0.148,0.1,0.29,0.066],"parser":"text"},{"key":"name","box":[0.155,0.168,0.092,0.066],"parser":"text"},{"key":"expiry_date","box":[0.155,0.521,0.322,0.072],"parser":"date_range_end"}]}'
WHERE NOT EXISTS (
  SELECT 1 FROM document_types WHERE name = '건설기계조종사면허증' AND applies_to = 'PERSON'
);
